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
package com.coralblocks.coralring.memory;

import java.nio.ByteBuffer;

public interface Memory {
	
	public long getSize();
	
	public long getPointer();
	
	public void release(boolean deleteFileIfUsed);
	
	// ==========
	
	public long getLong(long address);

	public void putLong(long address, long value);
	
	public int getInt(long address);
	
	public void putInt(long address, int value);
	
	public byte getByte(long address);
	
	public void putByte(long address, byte value);
	
	public short getShort(long address);
	
	public void putShort(long address, short value);
	
	// ==========
	
	public int getIntVolatile(long address);
	
	public void putIntVolatile(long address, int value);
	
	public byte getByteVolatile(long address);
	
	public void putByteVolatile(long address, byte value);
	
	public short getShortVolatile(long address);
	
	public void putShortVolatile(long address, short value);
	
	public long getLongVolatile(long address);

	public void putLongVolatile(long address, long value);
	
	// ==========
	
	public void putByteBuffer(long address, ByteBuffer src, int len);
	
	public void getByteBuffer(long address, ByteBuffer dst, int len);
	
	public void putByteArray(long address, byte[] src, int len);
	
	public void getByteArray(long address, byte[] dst, int len);
}