/* 
 * Copyright 2024 (c) CoralBlocks - http://www.coralblocks.com
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

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.SharedMemory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.LinkedObjectList;
import com.coralblocks.coralring.util.LinkedObjectPool;
import com.coralblocks.coralring.util.MathUtils;
import com.coralblocks.coralring.util.MemoryPaddedLong;
import com.coralblocks.coralring.util.MemorySerializable;
import com.coralblocks.coralring.util.ObjectPool;

/**
 * <p>
 * The producer side of a queue that uses shared memory instead of heap memory so that communication can happen across JVMs.
 * Refer to the <a href="https://www.github.com/coralblocks/CoralQueue" target="_blank">CoralQueue</a> project for more details.
 * </p>
 * <p>
 * The shared memory allocated for the ring contains a header space where the producer and consumer sequence numbers are kept and maintained for mutual access.
 * We also keep in the header two ints: one for the ring capacity and one for the max object size.
 * A memory barrier is implemented through the {@link MemoryPaddedLong} class, which uses the <code>putVolatile</code> and <code>getVolatile</code> native memory operations.
 * </p>
 * <p>
 * We assume a CPU cache line of 64 bytes and we place each sequence number (consumer one and producer one) on each cache line. The sequence number is a <code>long</code>
 * with 8 bytes. So the memory layout for the header is: <code>24 bytes (padding) + 8 bytes (sequence) + 32 bytes (padding)</code>, for a total of 64 bytes.
 * </p>
 * 
 * @param <E> The data transfer {@link MemorySerializable} object to be used by this ring
 */
public class BlockingRingProducer<E extends MemorySerializable> implements RingProducer<E> {
	
	// The default capacity for this shared memory ring
	final static int DEFAULT_CAPACITY = 1024;
	
	// So that the sequence lands in the middle of the cache line
	final static int SEQ_PREFIX_PADDING = 24;

	// A typical CPU cache line
	final static int CPU_CACHE_LINE = 64;
	
	// Two cache lines, one for each sequence number plus two ints (capacity and max object size)
	final static int HEADER_SIZE = CPU_CACHE_LINE + CPU_CACHE_LINE + 4 + 4;
	
	private final int capacity;
	private final int capacityMinusOne;
	private final Memory memory;
	private final long headerAddress;
	private final long dataAddress;
	private long lastOfferedSeq;
	private long maxSeqBeforeWrapping;
	private final MemoryPaddedLong offerSequence;
	private final MemoryPaddedLong pollSequence;
	private final Builder<E> builder;
	private final int maxObjectSize;
	private final ObjectPool<E> dataPool;
	private final LinkedObjectList<E> dataList;
	private final boolean isPowerOfTwo;

    public BlockingRingProducer(int capacity, int maxObjectSize, Builder<E> builder, String filename) {
		this.isPowerOfTwo = MathUtils.isPowerOfTwo(capacity);
		this.capacity = capacity;
		this.capacityMinusOne = capacity - 1;
		this.maxObjectSize = maxObjectSize;
		long totalMemorySize = calcTotalMemorySize(capacity, maxObjectSize);
		this.memory = new SharedMemory(totalMemorySize, filename);
		this.headerAddress = memory.getPointer();
		this.memory.putInt(headerAddress + 2 * CPU_CACHE_LINE, capacity);
		this.memory.putInt(headerAddress + 2 * CPU_CACHE_LINE + 4, maxObjectSize);
		this.dataAddress = headerAddress + HEADER_SIZE;
		this.builder = builder;
		this.offerSequence = new MemoryPaddedLong(headerAddress + SEQ_PREFIX_PADDING, memory);
		this.pollSequence = new MemoryPaddedLong(headerAddress + CPU_CACHE_LINE + SEQ_PREFIX_PADDING, memory);
		this.lastOfferedSeq = offerSequence.get();
		this.dataPool = new LinkedObjectPool<E>(64, builder);
		this.dataList = new LinkedObjectList<E>(64);
		this.maxSeqBeforeWrapping = calcMaxSeqBeforeWrapping();
	}
	
	public BlockingRingProducer(int capacity, int maxObjectSize, Class<E> klass, String filename) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	public BlockingRingProducer(int maxObjectSize, Builder<E> builder, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename);
	}
	
	public BlockingRingProducer(int maxObjectSize, Class<E> klass, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	@Override
	public final long getLastOfferedSequence() {
		return lastOfferedSeq;
	}
	
	@Override
	public final Memory getMemory() {
		return memory;
	}
	
	private final static long calcTotalMemorySize(long capacity, int maxObjectSize) {
		return HEADER_SIZE + capacity * maxObjectSize;
	}

	@Override
	public final Builder<E> getBuilder() {
		return builder;
	}
	
	private final long calcMaxSeqBeforeWrapping() {
		return pollSequence.get() + capacity;
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
			return (int) (value & capacityMinusOne);
		} else {
			return (int) (value % capacity);
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
		
		dataList.clear();
		
		offerSequence.set(lastOfferedSeq);
	}
	
	@Override
	public final void close(boolean deleteFile) {
		memory.release(deleteFile);
	}
}