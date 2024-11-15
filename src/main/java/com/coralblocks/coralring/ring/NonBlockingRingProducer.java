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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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

public class NonBlockingRingProducer<E extends MemorySerializable> implements RingProducer<E> {
	
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
	private final MemoryPaddedLong offerSequence;
	private final Builder<E> builder;
	private final int maxObjectSize;
	private final ObjectPool<E> dataPool;
	private final LinkedObjectList<E> dataList;
	private final boolean isPowerOfTwo;

    public NonBlockingRingProducer(int capacity, int maxObjectSize, Builder<E> builder, String filename) {
		this.isPowerOfTwo = MathUtils.isPowerOfTwo(capacity);
		this.capacity = capacity;
		this.capacityMinusOne = capacity - 1;
		this.maxObjectSize = maxObjectSize;
		boolean fileExists = validateHeaderValues(filename, capacity, maxObjectSize);
		long totalMemorySize = calcTotalMemorySize(capacity, maxObjectSize);
		if (fileExists) validateFileLength(filename, totalMemorySize);
		this.memory = new SharedMemory(totalMemorySize, filename);
		this.headerAddress = memory.getPointer();
		if (!fileExists) {
			this.memory.putInt(headerAddress + 2 * CPU_CACHE_LINE, capacity);
			this.memory.putInt(headerAddress + 2 * CPU_CACHE_LINE + 4, maxObjectSize);
		}
		this.dataAddress = headerAddress + HEADER_SIZE;
		this.builder = builder;
		this.offerSequence = new MemoryPaddedLong(headerAddress + SEQ_PREFIX_PADDING, memory);
		this.lastOfferedSeq = offerSequence.get();
		this.dataPool = new LinkedObjectPool<E>(64, builder);
		this.dataList = new LinkedObjectList<E>(64);
	}
	
	public NonBlockingRingProducer(int capacity, int maxObjectSize, Class<E> klass, String filename) {
		this(capacity, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	public NonBlockingRingProducer(int maxObjectSize, Builder<E> builder, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, builder, filename);
	}
	
	public NonBlockingRingProducer(int maxObjectSize, Class<E> klass, String filename) {
		this(DEFAULT_CAPACITY, maxObjectSize, Builder.createBuilder(klass), filename);
	}
	
	/*
	 * Return true if the file exists
	 */
	private static boolean validateHeaderValues(String filename, int capacity, int maxObjectSize) {
		int[] headerValues = getHeaderValuesIfFileExists(filename);
		if (headerValues != null) {
			if (capacity != headerValues[0]) {
				throw new RuntimeException("The provided capacity does not match the one in the header of the file!"
								+ " capacity=" + capacity + " header=" + headerValues[0]);
			}
			if (maxObjectSize != headerValues[1]) {
				throw new RuntimeException("The provided maxObjectSize does not match the one in the header of the file!"
						+ " maxObjectSize=" + maxObjectSize + " header=" + headerValues[1]);
				
			}
			return true;
		}
		return false;
	}
	
	static void validateFileLength(String filename, long fileLength) {
		File file = new File(filename);
		if (!file.exists() || file.isDirectory()) throw new RuntimeException("File does not exist: " + filename);
		if (file.length() != fileLength) {
			throw new RuntimeException("File length does not match!"
							+ " fileLength=" + file.length() + " expected=" + fileLength);
		}
	}
	
	/*
	 * This method tries to recover the capacity and the max object size from the header in the file
	 * It returns null if and only if the file does not exist
	 * It can throw a RuntimeException if there is an IOException or any other problem
	 * The first int in the array is the capacity. The second one is the max object size.
	 */
	static int[] getHeaderValuesIfFileExists(String filename) {
		File file = new File(filename);
		if (!file.exists() || file.isDirectory()) return null;
		if (file.length() < HEADER_SIZE) throw new RuntimeException("File does not contain the full header: " + filename);
		byte[] header = new byte[HEADER_SIZE];
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
			int bytesRead = fis.read(header);
			if (bytesRead != header.length) throw new IOException("Cannot read header data from file: " + filename);
			ByteBuffer bb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
			bb.position(2 * CPU_CACHE_LINE);
			int[] ret = new int[2];
			ret[0] = bb.getInt();
			ret[1] = bb.getInt();
			return ret;
		} catch(IOException e) {
			throw new RuntimeException(e);
		} finally {
			if (fis != null) try { fis.close(); } catch(IOException e) { throw new RuntimeException(e); }
		}
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
	
	@Override
	public final E nextToDispatch() {
		E data = dataPool.get();
		dataList.addLast(data);
		lastOfferedSeq++;
		return data;
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