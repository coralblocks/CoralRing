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
import com.coralblocks.coralring.util.MemoryPaddedLong;
import com.coralblocks.coralring.util.MemorySerializable;
import com.coralblocks.coralring.util.ObjectPool;

public class NonBlockingRingProducer<E extends MemorySerializable> implements RingProducer<E> {
	
	// The default capacity for this shared memory ring
	final static int DEFAULT_CAPACITY = 1024;
	
	// So that the sequence lands in the middle of the cache line
	final static int SEQ_PREFIX_PADDING = 24;

	// A typical CPU cache line
	final static int CPU_CACHE_LINE = 64;
	
	// The length of the checksum that can be written with every message
	final static int CHECKSUM_LENGTH = 8;
	
	// The length of the sequence number used to calculate the checksum
	final static int SEQUENCE_LENGTH = 8; // long
	
	// Two cache lines, one for each sequence number
	final static int HEADER_SIZE = CPU_CACHE_LINE;
	
	// The default mode is to not write the checksum
	final static boolean DEFAULT_WRITE_CHECKSUM = false;
	
	private final int capacity;
	private final int capacityMinusOne;
	private final Memory memory;
	private final long headerAddress;
	private final long dataAddress;
	private long lastOfferedSeq;
	private final MemoryPaddedLong offerSequence;
	private final Builder<E> builder;
	private final int maxObjectSize;
	private final ObjectPool<E> dataPool;
	private final LinkedObjectList<E> dataList;
	private final boolean isPowerOfTwo;
	private final boolean writeChecksum;
	private final ByteBufferMemory bbMemory;

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
		this.offerSequence = new MemoryPaddedLong(headerAddress + SEQ_PREFIX_PADDING, memory);
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
	
	public NonBlockingRingProducer(int capacity, int maxObjectSize, Class<E> klass, String filename) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_WRITE_CHECKSUM);
	}
	
	public NonBlockingRingProducer(int capacity, int maxObjectSize, Class<E> klass, String filename, boolean writeChecksum) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, writeChecksum);
	}
	
	public NonBlockingRingProducer(int maxObjectSize, Builder<E> builder, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, DEFAULT_WRITE_CHECKSUM);
	}
	
	public NonBlockingRingProducer(int maxObjectSize, Builder<E> builder, String filename, boolean writeChecksum) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, writeChecksum);
	}
	
	public NonBlockingRingProducer(int maxObjectSize, Class<E> klass, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_WRITE_CHECKSUM);
	}
	
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