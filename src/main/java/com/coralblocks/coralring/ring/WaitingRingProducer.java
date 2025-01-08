/* 
 * Copyright 2015-2024 (c) CoralBlocks LLC - http://www.coralblocks.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package com.coralblocks.coralring.ring;

import java.util.Iterator;

import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.MemorySerializable;
import com.coralblocks.coralring.memory.SharedMemory;
import com.coralblocks.coralring.util.ArrayLinkedObjectList;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.MathUtils;
import com.coralblocks.coralring.util.MemoryVolatileLong;

/**
 * <p>
 * The implementation of a waiting {@link RingProducer} that uses shared memory instead of heap memory so that communication can happen across JVMs.
 * It can wait if the ring becomes full, in other words, if the consumer on the other side is falling behind or not fetching new messages fast enough.
 * It uses shared memory through a memory-mapped file.
 * </p>
 * <p>
 * The shared memory allocated for the ring contains a header space where the producer and consumer sequence numbers are kept and maintained for mutual access.
 * Memory barriers are implemented through the {@link MemoryVolatileLong} class, which uses the <code>putLongVolatile</code> and <code>getLongVolatile</code> native 
 * memory operations.
 * </p>
 * <p>
 * We assume a CPU cache line of 64 bytes and we place each sequence number (consumer one and producer one) on each cache line. The sequence number is a <code>long</code>
 * with 8 bytes. So the memory layout for the header is: <code>24 bytes (padding) + 8 bytes (sequence) + 32 bytes (padding)</code>, for a total of 64 bytes.
 * </p>
 * 
 * @param <E> The message mutable class implementing {@link MemorySerializable} that will be transferred through this ring
 */
public class WaitingRingProducer<E extends MemorySerializable> implements RingProducer<E> {
	
	// The default capacity for this shared memory ring
	static final int DEFAULT_CAPACITY = 1024;
	
	// So that the sequence lands in the middle of the cache line
	static final int SEQ_PREFIX_PADDING = 24;

	// A typical CPU cache line
	static final int CPU_CACHE_LINE = 64;
	
	// Two cache lines, one for each sequence number
	static final int HEADER_SIZE = CPU_CACHE_LINE + CPU_CACHE_LINE;
	
	private final int capacity;
	private final int capacityMinusOne;
	private final Memory memory;
	private final long headerAddress;
	private final long dataAddress;
	private long lastOfferedSeq;
	private long maxSeqBeforeWrapping;
	private final MemoryVolatileLong offerSequence;
	private final MemoryVolatileLong fetchSequence;
	private final Builder<E> builder;
	private final int maxObjectSize;
	private final ObjectPool<E> dataPool;
	private final ArrayLinkedObjectList<E> dataList;
	private final boolean isPowerOfTwo;

	/**
	 * Creates a new ring producer
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
    public WaitingRingProducer(final int capacity, final int maxObjectSize, final Builder<E> builder, final String filename) {
		this.isPowerOfTwo = MathUtils.isPowerOfTwo(capacity);
		this.capacity = capacity;
		this.capacityMinusOne = capacity - 1;
		this.maxObjectSize = maxObjectSize;
		long totalMemorySize = calcTotalMemorySize(capacity, maxObjectSize);
		this.memory = new SharedMemory(totalMemorySize, filename);
		this.headerAddress = memory.getPointer();
		this.dataAddress = headerAddress + HEADER_SIZE;
		this.builder = builder;
		this.offerSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING, memory);
		this.fetchSequence = new MemoryVolatileLong(headerAddress + CPU_CACHE_LINE + SEQ_PREFIX_PADDING, memory);
		this.lastOfferedSeq = offerSequence.get();
		final com.coralblocks.coralpool.util.Builder<E> poolBuilder = new com.coralblocks.coralpool.util.Builder<E>() {
			@Override
			public E newInstance() {
				return builder.newInstance();
			}
		};
		this.dataPool = new ArrayObjectPool<E>(256, poolBuilder);
		this.dataList = new ArrayLinkedObjectList<E>(256);
		this.maxSeqBeforeWrapping = calcMaxSeqBeforeWrapping();
	}
	
    /**
     * Creates a new ring producer
     * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
     */
	public WaitingRingProducer(int capacity, int maxObjectSize, Class<E> klass, String filename) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	/**
	 * Creates a new ring producer with the default capacity (i.e. 1024)
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public WaitingRingProducer(int maxObjectSize, Builder<E> builder, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename);
	}
	
	/**
	 * Creates a new ring producer with the default capacity (i.e. 1024)
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public WaitingRingProducer(int maxObjectSize, Class<E> klass, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	@Override
	public final long getLastOfferedSequence() {
		return lastOfferedSeq;
	}
	
	@Override
	public final void setLastOfferedSequence(long lastOfferedSeq) {
		this.lastOfferedSeq = lastOfferedSeq;
	}
	
	@Override
	public final Memory getMemory() {
		return memory;
	}
	
	@Override
	public final int getCapacity() {
		return capacity;
	}
	
	private static final long calcTotalMemorySize(int capacity, int maxObjectSize) {
		return HEADER_SIZE + ((long) capacity) * maxObjectSize;
	}

	@Override
	public final Builder<E> getBuilder() {
		return builder;
	}
	
	private final long calcMaxSeqBeforeWrapping() {
		return fetchSequence.get() + capacity;
	}
	
	@Override
	public final E nextToDispatch() {
		
		if (++lastOfferedSeq > maxSeqBeforeWrapping) {
			// this would wrap the buffer... calculate the new one...
			this.maxSeqBeforeWrapping = calcMaxSeqBeforeWrapping();
			if (lastOfferedSeq > maxSeqBeforeWrapping) {
				lastOfferedSeq--;
				return null;				
			}
		}
		
		E obj = dataPool.get();
		dataList.addLast(obj);
		return obj;
	}
	
	private final long calcDataOffset(long index) {
		return dataAddress + index * maxObjectSize;
	}
	
	private final int calcIndex(long value) {
		if (isPowerOfTwo) {
			return (int) ((value - 1) & capacityMinusOne);
		} else {
			return (int) ((value - 1) % capacity);
		}
	}
	
	@Override
	public final void flush() {
		
		long seq = lastOfferedSeq - dataList.size() + 1;
		
		Iterator<E> iter = dataList.iterator();
		
		while(iter.hasNext()) {
			
			int index = calcIndex(seq);
			long offset = calcDataOffset(index);
			
			E obj = iter.next();
			obj.writeTo(offset, memory);
			dataPool.release(obj);
			
			seq++;
		}
		
		dataList.clear(false); // no need to nullify because elements are in the pool anyway
		
		offerSequence.set(lastOfferedSeq);
	}
	
	@Override
	public final void close(boolean deleteFile) {
		memory.release(deleteFile);
	}
}