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

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.SharedMemory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.MathUtils;
import com.coralblocks.coralring.util.MemoryPaddedLong;
import com.coralblocks.coralring.util.MemorySerializable;

public class RingConsumer<E extends MemorySerializable> {
	
	// The default capacity for this shared memory ring
	private final static int DEFAULT_CAPACITY = RingProducer.DEFAULT_CAPACITY;
	
	// So that the sequence lands in the middle of the cache line
	private final static int SEQ_PREFIX_PADDING = RingProducer.SEQ_PREFIX_PADDING;

	// A typical CPU cache line
	private final static int CPU_CACHE_LINE = RingProducer.CPU_CACHE_LINE;
	
	// Two cache lines, one for each sequence number
	private final static int HEADER_SIZE = RingProducer.HEADER_SIZE;
	
	private final int capacity;
	private final int capacityMinusOne;
	private final E data;
	private long lastPolledSeq;
	private long pollCount = 0;
	private final MemoryPaddedLong offerSequence;
	private final MemoryPaddedLong pollSequence;
	private final int maxObjectSize;
	private final Memory memory;
	private final long headerAddress;
	private final long dataAddress;
	private final boolean isPowerOfTwo;
	
	private final Builder<E> builder;

	public RingConsumer(int capacity, int maxObjectSize, Builder<E> builder, String filename) {
		
		this.isPowerOfTwo = MathUtils.isPowerOfTwo(capacity);
		
		int[] headerValues = RingProducer.getHeaderValuesIfFileExists(filename);
		
		boolean fileExists = headerValues != null;
		
		if (fileExists) {
			if (capacity == -1) {
				this.capacity = headerValues[0];
			} else {
				if (capacity != headerValues[0]) throw new RuntimeException("Capacity provided does not match file!"
														+ " provided=" + capacity + " expected=" + headerValues[0]);
				this.capacity = capacity;
			}
			if (maxObjectSize == -1) {
				this.maxObjectSize = headerValues[1];
			} else {
				if (maxObjectSize != headerValues[1]) throw new RuntimeException("Max object size provided does not match file!"
														+ " provided=" + maxObjectSize + " expected=" + headerValues[1]);
				this.maxObjectSize = maxObjectSize;
			}
		} else { // file does not exist
			
			if (capacity == -1) throw new RuntimeException("File does not exist but capacity was not provided!");
			if (maxObjectSize == -1) throw new RuntimeException("File does not exist but max object size was not provided!");
			
			this.capacity = capacity;
			this.maxObjectSize = maxObjectSize;
		}
		
		this.capacityMinusOne = this.capacity - 1;
		
		long totalMemorySize = calcTotalMemorySize(this.capacity, this.maxObjectSize);
		
		if (fileExists) RingProducer.validateFileLength(filename, totalMemorySize);
		
		this.memory = new SharedMemory(totalMemorySize, filename);
		this.headerAddress = memory.getPointer();
		
		if (!fileExists) {
			this.memory.putInt(headerAddress + 2 * CPU_CACHE_LINE, this.capacity);
			this.memory.putInt(headerAddress + 2 * CPU_CACHE_LINE + 4, this.maxObjectSize);
		}
		
		this.dataAddress = headerAddress + HEADER_SIZE;
		this.builder = builder;
		this.offerSequence = new MemoryPaddedLong(headerAddress + SEQ_PREFIX_PADDING, memory);
		this.pollSequence = new MemoryPaddedLong(headerAddress + CPU_CACHE_LINE + SEQ_PREFIX_PADDING, memory);
		this.lastPolledSeq = pollSequence.get();
		this.data = builder.newInstance();
	}

	public RingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	public RingConsumer(int maxObjectSize, Builder<E> builder, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename);
	}
	
	public RingConsumer(int maxObjectSize, Class<E> klass, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	public final long getLastPolledSequence() {
		return lastPolledSeq;
	}
	
	public final Memory getMemory() {
		return memory;
	}
	
	private final long calcTotalMemorySize(long capacity, int maxObjectSize) {
		return HEADER_SIZE + capacity * maxObjectSize;
	}

	public final Builder<E> getBuilder() {
		return builder;
	}
	
	public final long availableToPoll() {
		return offerSequence.get() - lastPolledSeq;
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
	
	public final E poll() {
		pollCount++;
		int index = calcIndex(++lastPolledSeq);
		long offset = calcDataOffset(index);
		data.readFrom(offset, memory);
		return data;
	}
	
	public final E peek() {
		int index = calcIndex(lastPolledSeq);
		long offset = calcDataOffset(index);
		data.readFrom(offset, memory);
		return data;
	}

	public final void rollback() {
		rollback(pollCount);
	}
	
	public final void rollback(long count) {
		if (count < 0 || count > pollCount) {
			throw new RuntimeException("Invalid rollback request! polled=" + pollCount + " requested=" + count);
		}
		lastPolledSeq -= count;
		pollCount -= count;
	}
	
	public final void donePolling() {
		pollSequence.set(lastPolledSeq);
		pollCount = 0;
	}
	
	public final void close(boolean deleteFileIfUsed) {
		memory.release(deleteFileIfUsed);
	}
}