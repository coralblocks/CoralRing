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

import java.io.File;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.MemorySerializable;
import com.coralblocks.coralring.memory.SharedMemory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.MathUtils;
import com.coralblocks.coralring.util.MemoryVolatileLong;

/**
 * <p>
 * The implementation of a waiting {@link RingConsumer}. It can wait if the ring becomes empty, in other words, if the producer
 * on the other side is falling behind or not offering new messages fast enough. It uses shared memory through a memory-mapped file.
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
public class WaitingRingConsumer<E extends MemorySerializable> implements RingConsumer<E> {
	
	private static final int DEFAULT_CAPACITY = WaitingRingProducer.DEFAULT_CAPACITY;
	
	private static final int SEQ_PREFIX_PADDING = WaitingRingProducer.SEQ_PREFIX_PADDING;

	private static final int CPU_CACHE_LINE = WaitingRingProducer.CPU_CACHE_LINE;
	
	private static final int HEADER_SIZE = WaitingRingProducer.HEADER_SIZE;
	
	private final int capacity;
	private final int capacityMinusOne;
	private final E data;
	private long lastFetchedSeq;
	private long fetchCount = 0;
	private final MemoryVolatileLong offerSequence;
	private final MemoryVolatileLong fetchSequence;
	private final int maxObjectSize;
	private final Memory memory;
	private final long headerAddress;
	private final long dataAddress;
	private final boolean isPowerOfTwo;
	
	private final Builder<E> builder;

	/**
	 * Creates a new ring consumer
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public WaitingRingConsumer(final int capacity, final int maxObjectSize, final Builder<E> builder, final String filename) {
		this.capacity = (capacity == -1 ? findCapacityFromFile(filename, maxObjectSize) : capacity);
		this.isPowerOfTwo = MathUtils.isPowerOfTwo(this.capacity);
		this.capacityMinusOne = this.capacity - 1;
		this.maxObjectSize = maxObjectSize;
		long totalMemorySize = calcTotalMemorySize(this.capacity, maxObjectSize);
		this.memory = new SharedMemory(totalMemorySize, filename);
		this.headerAddress = memory.getPointer();
		this.dataAddress = headerAddress + HEADER_SIZE;
		this.builder = builder;
		this.offerSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING, memory);
		this.fetchSequence = new MemoryVolatileLong(headerAddress + CPU_CACHE_LINE + SEQ_PREFIX_PADDING, memory);
		this.lastFetchedSeq = fetchSequence.get();
		this.data = builder.newInstance();
	}

	/**
	 * Creates a new ring consumer
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public WaitingRingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	/**
	 * Creates a new ring consumer with the default capacity (i.e. 1024)
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public WaitingRingConsumer(int maxObjectSize, Builder<E> builder, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename);
	}
	
	/**
	 * Creates a new ring consumer with the default capacity (i.e. 1024)
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public WaitingRingConsumer(int maxObjectSize, Class<E> klass, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	@Override
	public final long getLastFetchedSequence() {
		return lastFetchedSeq;
	}
	
	@Override
	public final void setLastFetchedSequence(long lastFetchedSeq) {
		this.lastFetchedSeq = lastFetchedSeq;
	}
	
	@Override
	public final long getLastOfferedSequence() {
		return offerSequence.get();
	}
	
	@Override
	public final Memory getMemory() {
		return memory;
	}
	
	private final long calcTotalMemorySize(int capacity, int maxObjectSize) {
		return HEADER_SIZE + ((long) capacity) * maxObjectSize;
	}
	
	private final int findCapacityFromFile(String filename, int maxObjectSize) {
		File file = new File(filename);
		if (!file.exists() || file.isDirectory()) throw new RuntimeException("Cannot find file: " + filename);
		long totalMemorySize = file.length();
		return calcCapacity(totalMemorySize, maxObjectSize);
	}
	
	private final int calcCapacity(long totalMemorySize, int maxObjectSize) {
		return (int) ((totalMemorySize - HEADER_SIZE) / maxObjectSize);
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
	public final long availableToFetch() {
		return offerSequence.get() - lastFetchedSeq;
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
	public final E fetch(boolean remove) {
		if (remove) return fetchTrue();
		else return fetchFalse();
	}
	
	@Override
	public final E fetch() {
		return fetch(true);
	}
	
	private final E fetchTrue() {
		fetchCount++;
		int index = calcIndex(++lastFetchedSeq);
		long offset = calcDataOffset(index);
		data.readFrom(offset, memory);
		return data;		
	}
	
	private final E fetchFalse() {
		int index = calcIndex(lastFetchedSeq + 1);
		long offset = calcDataOffset(index);
		data.readFrom(offset, memory);
		return data;		
	}
	
	@Override
	public final void rollBack() {
		rollBack(fetchCount);
	}
	
	@Override
	public final void rollBack(long count) {
		if (count < 0 || count > fetchCount) {
			throw new RuntimeException("Invalid rollback request! fetched=" + fetchCount + " requested=" + count);
		}
		lastFetchedSeq -= count;
		fetchCount -= count;
	}
	
	@Override
	public final void doneFetching() {
		fetchSequence.set(lastFetchedSeq);
		fetchCount = 0;
	}
	
	@Override
	public final void close(boolean deleteFile) {
		memory.release(deleteFile);
	}
}