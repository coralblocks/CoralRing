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

import java.nio.ByteBuffer;
import java.util.Iterator;

import com.coralblocks.coralring.memory.ByteBufferMemory;
import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.SharedMemory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.FastHash;
import com.coralblocks.coralring.util.LinkedObjectList;
import com.coralblocks.coralring.util.LinkedObjectPool;
import com.coralblocks.coralring.util.MathUtils;
import com.coralblocks.coralring.util.MemoryVolatileLong;
import com.coralblocks.coralring.util.MemorySerializable;
import com.coralblocks.coralring.util.ObjectPool;

/**
 * <p>
 * The implementation of a non-blocking {@link RingProducer} that uses shared memory instead of heap memory so that communication can happen across JVMs.
 * It never blocks! It continues to write to the ring even when the ring becomes full because the ring is a circular data structure, in other words, the
 * oldest entries in the ring will be overwritten by newest entries.
 * It uses shared memory through a memory-mapped file.
 * </p>
 * <p>
 * The shared memory allocated for the ring contains a header space where the producer sequence number is kept and maintained for mutual access.
 * A memory barrier is implemented through the {@link MemoryVolatileLong} class, which uses the <code>putLongVolatile</code> and <code>getLongVolatile</code> native 
 * memory operations.
 * </p>
 * <p>
 * We assume a CPU cache line of 64 bytes and we place the sequence number in the middle of cache line. The sequence number is a <code>long</code>
 * with 8 bytes. So the memory layout for the header is: <code>24 bytes (padding) + 8 bytes (sequence) + 32 bytes (padding)</code>, for a total of 64 bytes.
 * </p>
 * 
 * @param <E> The message mutable class implementing {@link MemorySerializable} that will be transferred through this ring
 */
public class NonBlockingRingProducer<E extends MemorySerializable> implements RingProducer<E> {
	
	// The default capacity for this shared memory ring
	static final int DEFAULT_CAPACITY = 1024;
	
	// So that the sequence lands in the middle of the cache line
	static final int SEQ_PREFIX_PADDING = 24;

	// A typical CPU cache line
	static final int CPU_CACHE_LINE = 64;
	
	// The length of the checksum that can be written with every message
	static final int CHECKSUM_LENGTH = 8;
	
	// The length of the sequence number used to calculate the checksum
	static final int SEQUENCE_LENGTH = 8; // long
	
	// One cache line for the producer sequence
	static final int HEADER_SIZE = CPU_CACHE_LINE;
	
	// The default mode is to not write the checksum
	static final boolean DEFAULT_WRITE_CHECKSUM = false;
	
	private final int capacity;
	private final int capacityMinusOne;
	private final Memory memory;
	private final long headerAddress;
	private final long dataAddress;
	private long lastOfferedSeq;
	private final MemoryVolatileLong offerSequence;
	private final Builder<E> builder;
	private final int maxObjectSize;
	private final ObjectPool<E> dataPool;
	private final LinkedObjectList<E> dataList;
	private final boolean isPowerOfTwo;
	private final boolean writeChecksum;
	private final ByteBufferMemory bbMemory;

	/**
	 * Creates a new non-blocking ring producer.
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param writeChecksum true to calculate and write the checksum with each message sent
	 */
    public NonBlockingRingProducer(final int capacity, final int maxObjectSize, final Builder<E> builder, final String filename, boolean writeChecksum) {
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
		this.lastOfferedSeq = offerSequence.get();
		this.dataPool = new LinkedObjectPool<E>(64, builder);
		this.dataList = new LinkedObjectList<E>(64);
		this.writeChecksum = writeChecksum;
		if (writeChecksum) {
			this.bbMemory = new ByteBufferMemory(SEQUENCE_LENGTH + maxObjectSize);
		} else {
			this.bbMemory = null;
		}
	}
	
	/**
	 * Creates a new non-blocking ring producer.
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public NonBlockingRingProducer(int capacity, int maxObjectSize, Class<E> klass, String filename) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_WRITE_CHECKSUM);
	}
	
	/**
	 * Creates a new non-blocking ring producer.
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param writeChecksum true to calculate and write the checksum with each message sent
	 */
	public NonBlockingRingProducer(int capacity, int maxObjectSize, Class<E> klass, String filename, boolean writeChecksum) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, writeChecksum);
	}
	
	/**
	 * Creates a new non-blocking ring producer with the default capacity (i.e. 1024).
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public NonBlockingRingProducer(int maxObjectSize, Builder<E> builder, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, DEFAULT_WRITE_CHECKSUM);
	}
	
	/**
	 * Creates a new non-blocking ring producer with the default capacity (i.e. 1024).
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param writeChecksum true to calculate and write the checksum with each message sent
	 */
	public NonBlockingRingProducer(int maxObjectSize, Builder<E> builder, String filename, boolean writeChecksum) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, writeChecksum);
	}
	
	/**
	 * Creates a new non-blocking ring producer with the default capacity (i.e. 1024).
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public NonBlockingRingProducer(int maxObjectSize, Class<E> klass, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_WRITE_CHECKSUM);
	}
	
	/**
	 * Creates a new non-blocking ring producer with the default capacity (i.e. 1024).
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param writeChecksum true to calculate and write the checksum with each message sent
	 */
	public NonBlockingRingProducer(int maxObjectSize, Class<E> klass, String filename, boolean writeChecksum) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, writeChecksum);
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
	
	private final long calcTotalMemorySize(int capacity, int maxObjectSize) {
		return HEADER_SIZE + ((long) capacity) * (CHECKSUM_LENGTH + maxObjectSize);
	}

	@Override
	public final Builder<E> getBuilder() {
		return builder;
	}
	
	@Override
	public final int getCapacity() {
		return capacity;
	}
	
	@Override
	public final E nextToDispatch() {
		E data = dataPool.get();
		dataList.addLast(data);
		lastOfferedSeq++;
		return data;
	}
	
	private final long calcDataOffset(long index) {
		return dataAddress + index * (CHECKSUM_LENGTH + maxObjectSize);
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
			
			if (writeChecksum) {
				bbMemory.putLong(bbMemory.getPointer(), seq); // use the sequence too
				int len = obj.writeTo(bbMemory.getPointer() + SEQUENCE_LENGTH, bbMemory);
				ByteBuffer bb = bbMemory.getByteBuffer();
				bb.limit(len).position(0);
				memory.putLong(offset, FastHash.hash64(bb));
			} else {
				memory.putLong(offset, 0L);
			}
			
			obj.writeTo(offset + CHECKSUM_LENGTH, memory);
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