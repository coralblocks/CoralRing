/* 
 * Copyright Copyright 2015-2024 (c) CoralBlocks LLC - http://www.coralblocks.com
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
package com.coralblocks.coralring.memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Implementation of {@link Memory} inside a <code>ByteBuffer</code>, which can be direct or non-direct. The default is for the <code>ByteBuffer</code> to be direct.
 */
public class ByteBufferMemory implements Memory {
	
	public static final boolean USE_DIRECT_BYTE_BUFFER = true;
	
	private ByteBuffer bb;
	
	/**
	 * Creates a new <code>ByteBufferMemory</code> with the given size.
	 * 
	 * @param size the size of this memory
	 * @param useDirectByteBuffer true to use a direct byte buffer
	 */
	public ByteBufferMemory(int size, boolean useDirectByteBuffer) {
		if (useDirectByteBuffer) {
			this.bb = ByteBuffer.allocateDirect(size).order(ByteOrder.LITTLE_ENDIAN);
		} else {
			this.bb = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN);
		}
	}
	
	/**
	 * Creates a new <code>ByteBufferMemory</code> with the given size. By default the <code>ByteBuffer</code> created is direct.
	 * 
	 * @param size the size of this memory
	 */
	public ByteBufferMemory(int size) {
		this(size, USE_DIRECT_BYTE_BUFFER);
	}
	
	/**
	 * Return the <code>ByteBuffer</code> used by this memory.
	 * 
	 * @return the <code>ByteBuffer</code> used by this memory
	 */
	public ByteBuffer getByteBuffer() {
		return bb;
	}

	@Override
	public long getSize() {
		return bb.capacity();
	}

	@Override
	public long getPointer() {
		return 0;
	}

	@Override
	public void release(boolean deleteFileIfUsed) {
		this.bb = null; // this is actually necessary to release native memory (believe it or not)
	}

	@Override
	public long getLong(long address) {
		int pos = (int) address;
		bb.limit(pos + 8).position(pos);
		return bb.getLong();
	}

	@Override
	public void putLong(long address, long value) {
		int pos = (int) address;
		bb.limit(pos + 8).position(pos);
		bb.putLong(value);
	}

	@Override
	public int getInt(long address) {
		int pos = (int) address;
		bb.limit(pos + 4).position(pos);
		return bb.getInt();
	}

	@Override
	public void putInt(long address, int value) {
		int pos = (int) address;
		bb.limit(pos + 4).position(pos);
		bb.putInt(value);		
	}

	@Override
	public byte getByte(long address) {
		int pos = (int) address;
		bb.limit(pos + 1).position(pos);
		return bb.get();
	}

	@Override
	public void putByte(long address, byte value) {
		int pos = (int) address;
		bb.limit(pos + 1).position(pos);
		bb.put(value);		
	}

	@Override
	public short getShort(long address) {
		int pos = (int) address;
		bb.limit(pos + 2).position(pos);
		return bb.getShort();
	}

	@Override
	public void putShort(long address, short value) {
		int pos = (int) address;
		bb.limit(pos + 2).position(pos);
		bb.putShort(value);
	}

	@Override
	public int getIntVolatile(long address) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putIntVolatile(long address, int value) {
		throw new UnsupportedOperationException();		
	}

	@Override
	public byte getByteVolatile(long address) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putByteVolatile(long address, byte value) {
		throw new UnsupportedOperationException();		
	}

	@Override
	public short getShortVolatile(long address) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putShortVolatile(long address, short value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public long getLongVolatile(long address) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putLongVolatile(long address, long value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void putByteBuffer(long address, ByteBuffer src, int len) {
		int pos = (int) address;
		bb.limit(pos + len).position(pos);
		int lim = src.limit();
		src.limit(src.position() + len);
		bb.put(src);
		src.limit(lim);
	}

	@Override
	public void getByteBuffer(long address, ByteBuffer dst, int len) {
		int pos = (int) address;
		bb.limit(pos + len).position(pos);
		dst.put(bb);
	}

	@Override
	public void putByteArray(long address, byte[] src, int len) {
		int pos = (int) address;
		bb.limit(pos + len).position(pos);
		bb.put(src, 0, len);
	}

	@Override
	public void getByteArray(long address, byte[] dst, int len) {
		int pos = (int) address;
		bb.limit(pos + len).position(pos);
		bb.get(dst, 0, len);
	}
}