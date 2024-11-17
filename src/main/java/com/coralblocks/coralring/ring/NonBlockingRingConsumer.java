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
import com.coralblocks.coralring.util.MemoryPaddedLong;
import com.coralblocks.coralring.util.MemorySerializable;

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
	private long lastPolledSeq;
	private long pollCount = 0;
	private final MemoryPaddedLong offerSequence;
	private final int maxObjectSize;
	private final Memory memory;
	private final long headerAddress;
	private final long dataAddress;
	private final boolean isPowerOfTwo;
	private final int fallBehindCapacity;
	private final Builder<E> builder;
	private final boolean checkChecksum;
	private final ByteBufferMemory bbMemory;

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
		this.offerSequence = new MemoryPaddedLong(headerAddress + SEQ_PREFIX_PADDING, memory);
		this.lastPolledSeq = 0;
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

	public NonBlockingRingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_CHECK_CHECKSUM, FALL_BEHIND_TOLERANCE);
	}
	
	public NonBlockingRingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename, boolean checkChecksum) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, checkChecksum, FALL_BEHIND_TOLERANCE);
	}
	
	public NonBlockingRingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename, boolean checkChecksum, float fallBehindTolerance) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, checkChecksum, fallBehindTolerance);
	}
	
	public NonBlockingRingConsumer(int capacity, int maxObjectSize, Class<E> klass, String filename, float fallBehindTolerance) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_CHECK_CHECKSUM, fallBehindTolerance);
	}
	
	public NonBlockingRingConsumer(int maxObjectSize, Builder<E> builder, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, DEFAULT_CHECK_CHECKSUM, FALL_BEHIND_TOLERANCE);
	}
	
	public NonBlockingRingConsumer(int maxObjectSize, Builder<E> builder, String filename, boolean checkChecksum) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, checkChecksum, FALL_BEHIND_TOLERANCE);
	}
	
	public NonBlockingRingConsumer(int maxObjectSize, Builder<E> builder, String filename, boolean checkChecksum, float fallBehindTolerance) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, checkChecksum, fallBehindTolerance);
	}
	
	public NonBlockingRingConsumer(int maxObjectSize, Builder<E> builder, String filename, float fallBehindTolerance) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, DEFAULT_CHECK_CHECKSUM, fallBehindTolerance);
	}
	
	public NonBlockingRingConsumer(int maxObjectSize, Class<E> klass, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, DEFAULT_CHECK_CHECKSUM, FALL_BEHIND_TOLERANCE);
	}
	
	public NonBlockingRingConsumer(int maxObjectSize, Class<E> klass, String filename, boolean checkChecksum) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, checkChecksum, FALL_BEHIND_TOLERANCE);
	}
	
	public NonBlockingRingConsumer(int maxObjectSize, Class<E> klass, String filename, boolean checkChecksum, float fallBehindTolerance) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, checkChecksum, fallBehindTolerance);
	}
	
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
	public final long getLastPolledSequence() {
		return lastPolledSeq;
	}
	
	@Override
	public final void setLastPolledSequence(long lastPolledSeq) {
		this.lastPolledSeq = lastPolledSeq;
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
	public final long availableToPoll() {
		long avail = offerSequence.get() - lastPolledSeq;
		if (avail > fallBehindCapacity) return -1; // wrapped
		return avail;
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
	public final E poll() {
		pollCount++;
		int index = calcIndex(++lastPolledSeq);
		long offset = calcDataOffset(index);
		
		long checksum = 0L;
		
		if (checkChecksum) {
			checksum = memory.getLong(offset);
		}
		
		data.readFrom(offset + CHECKSUM_LENGTH, memory);
		
		if (checkChecksum) {
			bbMemory.putLong(bbMemory.getPointer(), lastPolledSeq);
			int len = data.writeTo(bbMemory.getPointer() + SEQUENCE_LENGTH, bbMemory);
			ByteBuffer bb = bbMemory.getByteBuffer();
			bb.limit(len).position(0);
			long calculatedChecksum = FastHash.hash64(bb);
			
			if (checksum != calculatedChecksum) {
				pollCount--;
				lastPolledSeq--;
				return null;
			}
		}
		
		return data;
	}
	
	@Override
	public final E peek() {
		int index = calcIndex(lastPolledSeq);
		long offset = calcDataOffset(index);
		
		long checksum = 0L;
		
		if (checkChecksum) {
			checksum = memory.getLong(offset);
		}
		
		data.readFrom(offset + CHECKSUM_LENGTH, memory);
		
		if (checkChecksum) {
			int len = data.writeTo(bbMemory.getPointer(), bbMemory);
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
		rollBack(pollCount);
	}
	
	@Override
	public final void rollBack(long count) {
		if (count < 0 || count > pollCount) {
			throw new RuntimeException("Invalid rollback request! polled=" + pollCount + " requested=" + count);
		}
		lastPolledSeq -= count;
		pollCount -= count;
	}
	
	@Override
	public final void donePolling() {
		pollCount = 0;
	}
	
	@Override
	public final void close(boolean deleteFile) {
		memory.release(deleteFile);
	}
}