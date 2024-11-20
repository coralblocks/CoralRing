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

/**
 * An interface describing the behavior of a <i>piece of memory</i>. The memory space will have a size and will be allocated somewhere. It can then be
 * accessed and later released. It can support <i>volatile</i> operations for primitives. It also support bulk and fast operations on <code>ByteBuffer</code>s
 * and <code>byte[]</code>s.
 */
public interface Memory {
	
	/**
	 * Return the size (i.e. length) that this memory has.
	 * 
	 * @return the size of the memory
	 */
	public long getSize();
	
	/**
	 * Return the memory address pointing to the start of this memory space.
	 * 
	 * @return the memory address of the start of this memory
	 */
	public long getPointer();
	
	/**
	 * Release this memory and all its associated resources. If this memory has a file you can choose to delete the file or not.
	 * 
	 * @param deleteFile true to delete the associated file if it exists, such in the case of a shared memory through a memory-mapped file
	 */
	public void release(boolean deleteFile);
	
	// ==========
	
	/**
	 * Reads a long from memory
	 * 
	 * @param address the memory address from where to read the long
	 * @return the long
	 */
	public long getLong(long address);
	
	/**
	 * Writes a long to memory
	 * 
	 * @param address the memory address to where to write the long
	 * @param value the long
	 */
	public void putLong(long address, long value);
	
	/**
	 * Reads am int from memory
	 * 
	 * @param address the memory address from where to read the int
	 * @return the int
	 */
	public int getInt(long address);
	
	/**
	 * Writes an int to memory
	 * 
	 * @param address the memory address to where to write the int
	 * @param value the int
	 */
	public void putInt(long address, int value);
	
	/**
	 * Reads a short from memory
	 * 
	 * @param address the memory address from where to read the short
	 * @return the short
	 */
	public short getShort(long address);
	
	/**
	 * Writes a short to memory
	 * 
	 * @param address the memory address to where to write the short
	 * @param value the short
	 */
	public void putShort(long address, short value);
	
	/**
	 * Reads a byte from memory
	 * 
	 * @param address the memory address from where to read the byte
	 * @return the byte
	 */
	public byte getByte(long address);
	
	/**
	 * Writes a byte to memory
	 * 
	 * @param address the memory address to where to write the byte
	 * @param value the byte
	 */
	public void putByte(long address, byte value);
	
	// ==========

	/**
	 * Reads a long from memory. Read it in a volatile way.
	 * 
	 * @param address the memory address from where to read the long
	 * @return the long
	 */
	public long getLongVolatile(long address);

	/**
	 * Writes a long to memory. Write it in a volatile way.
	 * 
	 * @param address the memory address to where to write the long
	 * @param value the long
	 */
	public void putLongVolatile(long address, long value);	
	
	/**
	 * Reads an int from memory. Read it in a volatile way.
	 * 
	 * @param address the memory address from where to read the int
	 * @return the int
	 */
	public int getIntVolatile(long address);
	
	/**
	 * Writes an int to memory. Write it in a volatile way.
	 * 
	 * @param address the memory address to where to write the int
	 * @param value the int
	 */
	public void putIntVolatile(long address, int value);
	
	/**
	 * Reads a short from memory. Read it in a volatile way.
	 * 
	 * @param address the memory address from where to read the short
	 * @return the short
	 */
	public short getShortVolatile(long address);
	
	/**
	 * Writes a short to memory. Write it in a volatile way.
	 * 
	 * @param address the memory address to where to write the short
	 * @param value the short
	 */
	public void putShortVolatile(long address, short value);
	
	/**
	 * Reads a byte from memory. Read it in a volatile way.
	 * 
	 * @param address the memory address from where to read the byte
	 * @return the byte
	 */
	public byte getByteVolatile(long address);
	
	/**
	 * Writes a byte to memory. Write it in a volatile way.
	 * 
	 * @param address the memory address to where to write the byte
	 * @param value the byte
	 */
	public void putByteVolatile(long address, byte value);
	
	// ==========
	
	/**
	 * Writes this <code>ByteBuffer</code> to memory
	 * 
	 * @param address the memory address to where to write the <code>ByteBuffer</code>
	 * @param src the <code>ByteBuffer</code>
	 * @param len the number of bytes to write from the <code>ByteBuffer</code>
	 */
	public void putByteBuffer(long address, ByteBuffer src, int len);
	
	/**
	 * Reads data from memory to this <code>ByteBuffer</code>.
	 * 
	 * @param address the memory address from where to read
	 * @param dst the <code>ByteBuffer</code> receiving the data
	 * @param len the number of bytes that will be read
	 */
	public void getByteBuffer(long address, ByteBuffer dst, int len);
	
	/**
	 * Writes this <code>byte[]</code> to memory
	 * 
	 * @param address the memory address to where to write the <code>byte[]</code>
	 * @param src the <code>byte[]</code>
	 * @param len the number of bytes to write from the <code>byte[]</code>
	 */
	public void putByteArray(long address, byte[] src, int len);
	
	/**
	 * Reads data from memory to this <code>byte[]</code>.
	 * 
	 * @param address the memory address from where to read
	 * @param dst the <code>byte[]</code> receiving the data
	 * @param len the number of bytes that will be read
	 */
	public void getByteArray(long address, byte[] dst, int len);
}