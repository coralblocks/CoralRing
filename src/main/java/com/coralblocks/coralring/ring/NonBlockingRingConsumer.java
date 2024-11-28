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

import java.io.File;
import java.nio.ByteBuffer;

import com.coralblocks.coralring.memory.ByteBufferMemory;
import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.SharedMemory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.FastHash;
import com.coralblocks.coralring.util.MathUtils;
import com.coralblocks.coralring.util.MemoryVolatileLong;
import com.coralblocks.coralring.util.MemorySerializable;

/**
 * <p>
 * The implementation of a non-blocking {@link RingConsumer}. Of course it will block only if the ring becomes empty, in other words, if the producer
 * on the other side is falling behind or not offering new messages fast enough. It uses shared memory through a memory-mapped file.
 * </p>
 * <p>
 * If the consumer is too slow and falls behind, its {@link NonBlockingRingConsumer#availableToFetch()} method will return <code>-1</code> to indicate that
 * the consumer has fallen behind too much, will lose messages and cannot proceed.
 * </p>
 * <p>
 * The shared memory allocated for the ring contains a header space where the producer sequence number is kept and maintained for mutual access.
 * A memory barrier is implemented through the {@link MemoryVolatileLong} class, which uses the <code>putLongVolatile</code> and <code>getLongVolatile</code> native 
 * memory operations.
 * </p>
 * <p>
 * We assume a CPU cache line of 64 bytes and we place the sequence number on the middle of cache line. The sequence number is a <code>long</code>
 * with 8 bytes. So the memory layout for the header is: <code>24 bytes (padding) + 8 bytes (sequence) + 32 bytes (padding)</code>, for a total of 64 bytes.
 * </p>
 * 
 * @param <E> The message mutable class implementing {@link MemorySerializable} that will be transferred through this ring
 */
public class NonBlockingRingConsumer<E extends MemorySerializable> implements RingConsumer<E> {
	
	private final static int DEFAULT_CAPACITY = NonBlockingRingProducer.DEFAULT_CAPACITY;
	
	private final static int SEQ_PREFIX_PADDING = NonBlockingRingProducer.SEQ_PREFIX_PADDING;

	private final static int HEADER_SIZE = NonBlockingRingProducer.HEADER_SIZE;
	
	private final static int CHECKSUM_LENGTH = NonBlockingRingProducer.CHECKSUM_LENGTH;
	
	private final static boolean DEFAULT_CHECK_CHECKSUM = NonBlockingRingProducer.DEFAULT_WRITE_CHECKSUM;
	
	private final static int SEQUENCE_LENGTH = NonBlockingRingProducer.SEQUENCE_LENGTH;
	
	private final static float FALL_BEHIND_TOLERANCE = 1.0f;
	
	private final int capacity;
	private final int capacityMinusOne;
	private final E data;
	private long lastFetchedSeq;
	private long fetchCount = 0;
	private final MemoryVolatileLong offerSequence;
	private final int maxObjectSize;
	private final Memory memory;
	private final long headerAddress;
	private final long dataAddress;
	private final boolean isPowerOfTwo;
	private final int fallBehindCapacity;
	private final Builder<E> builder;
	private final boolean checkChecksum;
	private final ByteBufferMemory bbMemory;

	/**
	 * <p>Creates a new non-blocking ring consumer.</p>
	 * 
	 * <p>NOTE: The <code>fallBehindTolerance</code> provided is ignored in case the checksum is used (i.e. in case checkChecksum == true)</p>
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param checkChecksum true to check the checksum in order to avoid reading a corrupt message in the situation where the consumer trips over the producer
	 * @param fallBehindTolerance the percentage of the capacity that the consumer can fall behind before giving up in order to avoid getting too close to the edge
	 */
	public NonBlockingRingConsumer(final int capacity, final int maxObjectSize, final Builder<E> builder, final String filename, boolean checkChecksum, float fallBehindTolerance) {
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
		this.lastFetchedSeq = 0;
		this.data = builder.newInstance();
		this.checkChecksum = checkChecksum;
		if (checkChecksum) {
			this.bbMemory = new ByteBufferMemory(SEQUENCE_LENGTH + maxObjectSize);
		} else {
			this.bbMemory = null;
		}
		if (checkChecksum) {
			this.fallBehindCapacity = capacity; // there is no need for tolerance when using checksum!
		} else {
			this.fallBehindCapacity = calcFallBehindCapacity(this.capacity, fallBehindTolerance);
		}
	}

	/**
	 * <p>Creates a new non-blocking ring consumer.</p>
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public NonBlockingRingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_CHECK_CHECKSUM, FALL_BEHIND_TOLERANCE);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer.</p>
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param checkChecksum true to check the checksum in order to avoid reading a corrupt message in the situation where the consumer trips over the producer
	 */
	public NonBlockingRingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename, boolean checkChecksum) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, checkChecksum, FALL_BEHIND_TOLERANCE);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer.</p>
	 * 
	 * <p>NOTE: The <code>fallBehindTolerance</code> provided is ignored in case the checksum is used (i.e. in case checkChecksum == true)</p>
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param checkChecksum true to check the checksum in order to avoid reading a corrupt message in the situation where the consumer trips over the producer
	 * @param fallBehindTolerance the percentage of the capacity that the consumer can fall behind before giving up in order to avoid getting too close to the edge
	 */
	public NonBlockingRingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename, boolean checkChecksum, float fallBehindTolerance) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, checkChecksum, fallBehindTolerance);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer.</p>
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param fallBehindTolerance the percentage of the capacity that the consumer can fall behind before giving up in order to avoid getting too close to the edge
	 */
	public NonBlockingRingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename, float fallBehindTolerance) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_CHECK_CHECKSUM, fallBehindTolerance);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer with the default capacity (i.e. 1024).</p>
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public NonBlockingRingConsumer(int maxObjectSize, Builder<E> builder, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, DEFAULT_CHECK_CHECKSUM, FALL_BEHIND_TOLERANCE);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer with the default capacity (i.e. 1024).</p>
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param checkChecksum true to check the checksum in order to avoid reading a corrupt message in the situation where the consumer trips over the producer
	 */
	public NonBlockingRingConsumer(int maxObjectSize, Builder<E> builder, String filename, boolean checkChecksum) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, checkChecksum, FALL_BEHIND_TOLERANCE);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer with the default capacity (i.e. 1024).</p>
	 * 
	 * <p>NOTE: The <code>fallBehindTolerance</code> provided is ignored in case the checksum is used (i.e. in case checkChecksum == true)</p>
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param checkChecksum true to check the checksum in order to avoid reading a corrupt message in the situation where the consumer trips over the producer
	 * @param fallBehindTolerance the percentage of the capacity that the consumer can fall behind before giving up in order to avoid getting too close to the edge
	 */
	public NonBlockingRingConsumer(int maxObjectSize, Builder<E> builder, String filename, boolean checkChecksum, float fallBehindTolerance) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, checkChecksum, fallBehindTolerance);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer with the default capacity (i.e. 1024).</p>
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param fallBehindTolerance the percentage of the capacity that the consumer can fall behind before giving up in order to avoid getting too close to the edge
	 */
	public NonBlockingRingConsumer(int maxObjectSize, Builder<E> builder, String filename, float fallBehindTolerance) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, DEFAULT_CHECK_CHECKSUM, fallBehindTolerance);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer with the default capacity (i.e. 1024).</p>
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 */
	public NonBlockingRingConsumer(int maxObjectSize, Class<E> klass, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_CHECK_CHECKSUM, FALL_BEHIND_TOLERANCE);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer with the default capacity (i.e. 1024).</p>
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param checkChecksum true to check the checksum in order to avoid reading a corrupt message in the situation where the consumer trips over the producer
	 */
	public NonBlockingRingConsumer(int maxObjectSize, Class<E> klass, String filename, boolean checkChecksum) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, checkChecksum, FALL_BEHIND_TOLERANCE);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer with the default capacity (i.e. 1024).</p>
	 * 
	 * <p>NOTE: The <code>fallBehindTolerance</code> provided is ignored in case the checksum is used (i.e. in case checkChecksum == true)</p>
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param checkChecksum true to check the checksum in order to avoid reading a corrupt message in the situation where the consumer trips over the producer
	 * @param fallBehindTolerance the percentage of the capacity that the consumer can fall behind before giving up in order to avoid getting too close to the edge
	 */
	public NonBlockingRingConsumer(int maxObjectSize, Class<E> klass, String filename, boolean checkChecksum, float fallBehindTolerance) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, checkChecksum, fallBehindTolerance);
	}
	
	/**
	 * <p>Creates a new non-blocking ring consumer with the default capacity (i.e. 1024).</p>
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param fallBehindTolerance the percentage of the capacity that the consumer can fall behind before giving up in order to avoid getting too close to the edge
	 */
	public NonBlockingRingConsumer(int maxObjectSize, Class<E> klass, String filename, float fallBehindTolerance) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_CHECK_CHECKSUM, fallBehindTolerance);
	}
	
	private final static int calcFallBehindCapacity(int capacity, float fallBehindTolerance) {
		if (fallBehindTolerance > 1 || fallBehindTolerance <= 0) {
			throw new IllegalArgumentException("Invalid fallBehindTolerance: " + fallBehindTolerance);
		}
		if (fallBehindTolerance == 1.0f) return capacity; // sanity
		int c = Math.round(capacity * fallBehindTolerance);
		if (c == 0) c = 1;
		return c;
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
	
	@Override
	public final int getCapacity() {
		return capacity;
	}
	
	private final long calcTotalMemorySize(int capacity, int maxObjectSize) {
		return HEADER_SIZE + ((long) capacity) * (CHECKSUM_LENGTH + maxObjectSize);
	}
	
	private final int findCapacityFromFile(String filename, int maxObjectSize) {
		File file = new File(filename);
		if (!file.exists() || file.isDirectory()) throw new RuntimeException("Cannot find file: " + filename);
		long totalMemorySize = file.length();
		return calcCapacity(totalMemorySize, maxObjectSize);
	}
	
	private final int calcCapacity(long totalMemorySize, int maxObjectSize) {
		return (int) ((totalMemorySize - HEADER_SIZE) / (CHECKSUM_LENGTH + maxObjectSize));
	}

	@Override
	public final Builder<E> getBuilder() {
		return builder;
	}
	
	@Override
	public final long availableToFetch() {
		long avail = offerSequence.get() - lastFetchedSeq;
		if (avail > fallBehindCapacity) return -1; // wrapped
		return avail;
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
		
		long checksum = 0L;
		
		if (checkChecksum) {
			checksum = memory.getLong(offset);
		}
		
		data.readFrom(offset + CHECKSUM_LENGTH, memory);
		
		if (checkChecksum) {
			bbMemory.putLong(bbMemory.getPointer(), lastFetchedSeq);
			int len = data.writeTo(bbMemory.getPointer() + SEQUENCE_LENGTH, bbMemory);
			ByteBuffer bb = bbMemory.getByteBuffer();
			bb.limit(len).position(0);
			long calculatedChecksum = FastHash.hash64(bb);
			
			if (checksum != calculatedChecksum) {
				fetchCount--;
				lastFetchedSeq--;
				return null;
			}
		}
		
		return data;
	}
	
	private final E fetchFalse() {
		int index = calcIndex(lastFetchedSeq + 1);
		long offset = calcDataOffset(index);
		
		long checksum = 0L;
		
		if (checkChecksum) {
			checksum = memory.getLong(offset);
		}
		
		data.readFrom(offset + CHECKSUM_LENGTH, memory);
		
		if (checkChecksum) {
			bbMemory.putLong(bbMemory.getPointer(), lastFetchedSeq + 1);
			int len = data.writeTo(bbMemory.getPointer() + SEQUENCE_LENGTH, bbMemory);
			ByteBuffer bb = bbMemory.getByteBuffer();
			bb.limit(len).position(0);
			long calculatedChecksum = FastHash.hash64(bb);
			
			if (checksum != calculatedChecksum) {
				return null;
			}
		}
		
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
		fetchCount = 0;
	}
	
	@Override
	public final void close(boolean deleteFile) {
		memory.release(deleteFile);
	}
}