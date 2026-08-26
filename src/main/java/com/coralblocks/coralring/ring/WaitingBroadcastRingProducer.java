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

import com.coralblocks.coralds.list.ArrayLinkedList;
import com.coralblocks.coralpool.ArrayObjectPool;
import com.coralblocks.coralpool.ObjectBuilder;
import com.coralblocks.coralpool.ObjectPool;
import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.MemorySerializable;
import com.coralblocks.coralring.memory.SharedMemory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.MathUtils;
import com.coralblocks.coralring.util.MemoryVolatileLong;

/**
 * <p>
 * The implementation of a waiting broadcast {@link RingProducer} that uses shared memory instead of heap memory so that communication can happen across JVMs.
 * It can wait if the ring becomes full, in other words, if the consumers on the other side are falling behind or not fetching new messages fast enough.
 * It uses shared memory through a memory-mapped file.
 * </p>
 * <p>
 * Every declared consumer slot participates from the beginning. A consumer that has not started therefore prevents the producer from advancing
 * more than the ring capacity. Use {@link #getFetchSequence(int)} to inspect each slot and {@link #disableConsumer(int)} to disable a consumer
 * that will not run.
 * </p>
 * <p>
 * The shared memory allocated for the ring contains a header space where the producer and consumers sequence numbers are kept and maintained for mutual access.
 * Memory barriers are implemented through the {@link MemoryVolatileLong} class, which uses the <code>putLongVolatile</code> and <code>getLongVolatile</code> native 
 * memory operations.
 * </p>
 * <p>
 * We assume a CPU cache line of 64 bytes and we place each sequence number (consumers ones and producer one) on each cache line. The sequence number is a <code>long</code>
 * with 8 bytes. So the memory layout for the header is: <code>24 bytes (padding) + 8 bytes (sequence) + 32 bytes (padding)</code> for each sequence number, for a total of
 * 64 bytes per sequence number.
 * </p>
 * 
 * @param <E> The message mutable class implementing {@link MemorySerializable} that will be transferred through this ring
 */
public class WaitingBroadcastRingProducer<E extends MemorySerializable> implements RingProducer<E> {
	
	// The default capacity for this shared memory ring
	static final int DEFAULT_CAPACITY = 1024;

	// The default number of message instances preallocated for producer batches
	static final int DEFAULT_INITIAL_BATCH_SIZE = 256;
	
	// So that the sequence lands in the middle of the cache line
	static final int SEQ_PREFIX_PADDING = 24;

	// A typical CPU cache line
	static final int CPU_CACHE_LINE = 64;
	
	private final int capacity;
	private final int capacityMinusOne;
	private final Memory memory;
	private final long headerAddress;
	private final long dataAddress;
	private long lastOfferedSeq;
	private long maxSeqBeforeWrapping;
	private final MemoryVolatileLong offerSequence;
	private final MemoryVolatileLong[] fetchSequence;
	private final Builder<E> builder;
	private final long slotSize;
	private final ObjectPool<E> dataPool;
	private final ArrayLinkedList<E> dataList;
	private final boolean isPowerOfTwo;

	/**
	 * Creates a new ring producer
	 * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param numberOfConsumers the number of consumers that will be reading from this ring
	 */
    public WaitingBroadcastRingProducer(final int capacity, final int maxObjectSize, final Builder<E> builder, final String filename, final int numberOfConsumers) {
		this(capacity, maxObjectSize, builder, filename, numberOfConsumers, DEFAULT_INITIAL_BATCH_SIZE);
	}

	/**
	 * Creates a new ring producer.
	 *
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param numberOfConsumers the number of consumers that will be reading from this ring
	 * @param initialBatchSize the number of message instances to preallocate for producer batches
	 */
	public WaitingBroadcastRingProducer(final int capacity, final int maxObjectSize, final Builder<E> builder, final String filename, final int numberOfConsumers, final int initialBatchSize) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity (" + capacity + ") must be greater than zero");
		}
		if (maxObjectSize <= 0) {
			throw new IllegalArgumentException("maxObjectSize (" + maxObjectSize + ") must be greater than zero");
		}
		if (builder == null) {
			throw new IllegalArgumentException("builder cannot be null");
		}
		if (filename == null) {
			throw new IllegalArgumentException("filename cannot be null");
		}
		if (numberOfConsumers <= 0) {
			throw new IllegalArgumentException("numberOfConsumers (" + numberOfConsumers + ") must be greater than zero");
		}
		if (initialBatchSize < 1) {
			throw new IllegalArgumentException("initialBatchSize (" + initialBatchSize + ") must be greater than zero");
		}
		this.isPowerOfTwo = MathUtils.isPowerOfTwo(capacity);
		this.capacity = capacity;
		this.capacityMinusOne = capacity - 1;
		this.slotSize = MathUtils.alignTo8Bytes(maxObjectSize);
		int headerSize = CPU_CACHE_LINE + CPU_CACHE_LINE * numberOfConsumers; // 1 producer sequence + 1 sequence for each consumer
		long totalMemorySize = calcTotalMemorySize(capacity, slotSize, headerSize);
		this.memory = new SharedMemory(totalMemorySize, filename);
		this.headerAddress = memory.getPointer();
		this.dataAddress = headerAddress + headerSize;
		this.builder = builder;
		this.offerSequence = new MemoryVolatileLong(headerAddress + SEQ_PREFIX_PADDING, memory);
		this.fetchSequence = new MemoryVolatileLong[numberOfConsumers];
		for(int i = 0; i < fetchSequence.length; i++) {
			this.fetchSequence[i] = new MemoryVolatileLong(headerAddress + (i + 1) * CPU_CACHE_LINE + SEQ_PREFIX_PADDING, memory);
		}
		this.lastOfferedSeq = offerSequence.get();
		final ObjectBuilder<E> poolBuilder = new ObjectBuilder<E>() {
			@Override
			public E newInstance() {
				return builder.newInstance();
			}
		};
		this.dataPool = new ArrayObjectPool<E>(initialBatchSize, poolBuilder);
		this.dataList = new ArrayLinkedList<E>(initialBatchSize);
		this.maxSeqBeforeWrapping = calcMaxSeqBeforeWrapping();
	}
	
    /**
     * Creates a new ring producer
     * 
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param numberOfConsumers the number of consumers that will be reading from this ring
     */
	public WaitingBroadcastRingProducer(int capacity, int maxObjectSize, Class<E> klass, String filename, int numberOfConsumers) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, numberOfConsumers);
	}

	/**
	 * Creates a new ring producer.
	 *
	 * @param capacity the capacity in number of messages for this ring
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param numberOfConsumers the number of consumers that will be reading from this ring
	 * @param initialBatchSize the number of message instances to preallocate for producer batches
	 */
	public WaitingBroadcastRingProducer(int capacity, int maxObjectSize, Class<E> klass, String filename, int numberOfConsumers, int initialBatchSize) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename, numberOfConsumers, initialBatchSize);
	}
	
	/**
	 * Creates a new ring producer with the default capacity (i.e. 1024)
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param builder the builder producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param numberOfConsumers the number of consumers that will be reading from this ring
	 */
	public WaitingBroadcastRingProducer(int maxObjectSize, Builder<E> builder, String filename, int numberOfConsumers) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename, numberOfConsumers);
	}
	
	/**
	 * Creates a new ring producer with the default capacity (i.e. 1024)
	 * 
	 * @param maxObjectSize the max size of a single message
	 * @param klass the class producing new instances of the message
	 * @param filename the file to be used by its shared memory
	 * @param numberOfConsumers the number of consumers that will be reading from this ring
	 */
	public WaitingBroadcastRingProducer(int maxObjectSize, Class<E> klass, String filename, int numberOfConsumers) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename, numberOfConsumers);
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
	
	private static final long calcTotalMemorySize(int capacity, long slotSize, int headerSize) {
		return headerSize + capacity * slotSize;
	}

	@Override
	public final Builder<E> getBuilder() {
		return builder;
	}
	
	private final long minFetchSequence() {
		long min = Long.MAX_VALUE;
		for(int i = 0; i < this.fetchSequence.length; i++) {
			min = Math.min(this.fetchSequence[i].get(), min);
		}
		return min;
	}
	
	private final long calcMaxSeqBeforeWrapping() {
		long minFetchSequence = minFetchSequence();
		if (minFetchSequence > Long.MAX_VALUE - capacity) return Long.MAX_VALUE;
		return minFetchSequence + capacity;
	}

	private final void validateConsumerIndex(int consumerIndex) {
		if (consumerIndex < 0 || consumerIndex >= fetchSequence.length) {
			throw new IllegalArgumentException("Invalid consumerIndex: " + consumerIndex);
		}
	}

	/**
	 * Returns the last sequence completed by the given consumer. A value of zero means that the consumer has not made progress,
	 * and {@link Long#MAX_VALUE} means that the consumer has been disabled.
	 *
	 * @param consumerIndex the index of the consumer
	 * @return the last sequence completed by the consumer
	 * @throws IllegalArgumentException if the consumer index is invalid
	 */
	public final long getFetchSequence(int consumerIndex) {
		validateConsumerIndex(consumerIndex);
		return fetchSequence[consumerIndex].get();
	}
	
	/**
	 * This method disables a consumer and allows the producer to continue to operate and make progress without having to wait
	 * for a slow consumer. This is useful for when a consumer has a problem and stops fetching the ring. In that situation
	 * the ring will get full, causing the producer to wait, unless you disable the consumer.
	 * <p>
	 * The consumer must be fully stopped before this method is called. A live consumer can overwrite the disabled marker the
	 * next time it calls {@link RingConsumer#doneFetching()}.
	 * </p>
	 * 
	 * @param consumerIndex the index of the consumer that you want to disable
	 * @throws IllegalArgumentException if the consumer index is invalid
	 */
	public final void disableConsumer(int consumerIndex) {
		validateConsumerIndex(consumerIndex);
		this.fetchSequence[consumerIndex].set(Long.MAX_VALUE);
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
		return dataAddress + index * slotSize;
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
