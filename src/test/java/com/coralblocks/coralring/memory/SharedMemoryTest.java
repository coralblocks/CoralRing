/* 
 * Copyright Copyright 2015-2024 (c) CoralBlocks LLC LLC - http://www.coralblocks.com
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

import org.junit.Assert;
import org.junit.Test;


public class SharedMemoryTest {

	@Test
	public void testPutByteBufferUsesAndAdvancesSourcePosition() {

		Memory memory = new SharedMemory(4);
		try {
			ByteBuffer source = ByteBuffer.allocateDirect(4);
			source.put(new byte[] { 10, 20, 30, 40 });
			source.position(1).limit(3);

			long address = memory.getPointer();
			memory.putByteBuffer(address, source, 2);

			Assert.assertEquals(3, source.position());
			Assert.assertEquals(20, memory.getByte(address));
			Assert.assertEquals(30, memory.getByte(address + 1));
		} finally {
			memory.release(true);
		}
	}

	@Test
	public void testPutByteBufferRejectsLengthBeyondRemainingBeforeCopying() {

		Memory memory = new SharedMemory(4);
		try {
			ByteBuffer source = ByteBuffer.allocateDirect(8);
			source.put(new byte[] { 10, 20, 30, 40, 50 });
			source.position(2).limit(4);

			long address = memory.getPointer();
			Assert.assertThrows(IllegalArgumentException.class,
					() -> memory.putByteBuffer(address, source, 3));

			Assert.assertEquals(2, source.position());
			Assert.assertEquals(0, memory.getByte(address));
			Assert.assertEquals(0, memory.getByte(address + 1));
			Assert.assertEquals(0, memory.getByte(address + 2));
		} finally {
			memory.release(true);
		}
	}

	@Test
	public void testGetByteBufferRejectsNegativeLength() {

		Memory memory = new SharedMemory(8);
		try {
			ByteBuffer destination = ByteBuffer.allocateDirect(8);
			Assert.assertThrows(IllegalArgumentException.class,
					() -> memory.getByteBuffer(memory.getPointer(), destination, -1));
			Assert.assertEquals(0, destination.position());
		} finally {
			memory.release(true);
		}
	}

	@Test
	public void testGetByteBufferRejectsLengthBeyondRemainingBeforeCopying() {

		Memory memory = new SharedMemory(8);
		try {
			long address = memory.getPointer();
			memory.putByte(address, (byte) 1);
			memory.putByte(address + 1, (byte) 2);
			memory.putByte(address + 2, (byte) 3);

			ByteBuffer destination = ByteBuffer.allocateDirect(8);
			destination.position(2).limit(4);

			Assert.assertThrows(IllegalArgumentException.class,
					() -> memory.getByteBuffer(address, destination, 3));

			destination.limit(destination.capacity());
			Assert.assertEquals(2, destination.position());
			Assert.assertEquals(0, destination.get(2));
			Assert.assertEquals(0, destination.get(3));
			Assert.assertEquals(0, destination.get(4));
		} finally {
			memory.release(true);
		}
	}
	
	@Test
	public void testRegularPutAndGet() {

		// 64 bytes = 8 longs, 16 ints, 32 shorts and 64 bytes
		final long size = 64; // 8 * 8 = 64
		
		Memory memory = new SharedMemory(size);
		final long address = memory.getPointer();
		
		// Adding bytes
		for(int x = 0; x < size; x++) {
			memory.putByte(address + x, (byte) x);
		}
		for(int x = 0; x < size; x++) {
			byte b = memory.getByte(address + x);
			Assert.assertEquals(x, b);
		}
		
		// Adding shorts
		for(int x = 0; x < size; x += 2) {
			memory.putShort(address + x, (short) x);
		}
		for(int x = 0; x < size;  x += 2) {
			short s = memory.getShort(address + x);
			Assert.assertEquals(x, s);
		}
		
		// Adding ints
		for(int x = 0; x < size; x += 4) {
			memory.putInt(address + x, x);
		}
		for(int x = 0; x < size;  x += 4) {
			int i = memory.getInt(address + x);
			Assert.assertEquals(x, i);
		}
		
		// Adding longs
		for(int x = 0; x < size; x += 8) {
			memory.putLong(address + x, x);
		}
		for(int x = 0; x < size;  x += 8) {
			long l = memory.getLong(address + x);
			Assert.assertEquals(x, l);
		}
		
		memory.release(true); // true to delete the memory-mapped file
	}
	
	@Test
	public void testVolatilePutAndGet() {

		// 64 bytes = 8 longs, 16 ints, 32 shorts and 64 bytes
		final long size = 64; // 8 * 8 = 64
		
		Memory memory = new SharedMemory(size);
		final long address = memory.getPointer();
		
		// Adding bytes
		for(int x = 0; x < size; x++) {
			memory.putByteVolatile(address + x, (byte) x);
		}
		for(int x = 0; x < size; x++) {
			byte b = memory.getByteVolatile(address + x);
			Assert.assertEquals(x, b);
		}
		
		// Adding shorts
		for(int x = 0; x < size; x += 2) {
			memory.putShortVolatile(address + x, (short) x);
		}
		for(int x = 0; x < size;  x += 2) {
			short s = memory.getShortVolatile(address + x);
			Assert.assertEquals(x, s);
		}
		
		// Adding ints
		for(int x = 0; x < size; x += 4) {
			memory.putIntVolatile(address + x, x);
		}
		for(int x = 0; x < size;  x += 4) {
			int i = memory.getIntVolatile(address + x);
			Assert.assertEquals(x, i);
		}
		
		// Adding longs
		for(int x = 0; x < size; x += 8) {
			memory.putLongVolatile(address + x, x);
		}
		for(int x = 0; x < size;  x += 8) {
			long l = memory.getLongVolatile(address + x);
			Assert.assertEquals(x, l);
		}
		
		memory.release(true); // true to delete the memory-mapped file
	}
	
	@Test
	public void testSharedRegularPutAndGet() {

		// 64 bytes = 8 longs, 16 ints, 32 shorts and 64 bytes
		final long size = 64; // 8 * 8 = 64
		
		final String mmapFileUsedAsBridge = "shared-memory.mmap";
		
		Memory memory1 = new SharedMemory(size, mmapFileUsedAsBridge);
		final long address1 = memory1.getPointer();
		
		Memory memory2 = new SharedMemory(size, mmapFileUsedAsBridge);
		final long address2 = memory2.getPointer();
		
		// Adding bytes
		for(int x = 0; x < size; x++) {
			memory1.putByte(address1 + x, (byte) x);
		}
		for(int x = 0; x < size; x++) {
			byte b = memory2.getByte(address2 + x);
			Assert.assertEquals(x, b);
		}
		
		// Adding shorts
		for(int x = 0; x < size; x += 2) {
			memory2.putShort(address2 + x, (short) x);
		}
		for(int x = 0; x < size;  x += 2) {
			short s = memory1.getShort(address1 + x);
			Assert.assertEquals(x, s);
		}
		
		// Adding ints
		for(int x = 0; x < size; x += 4) {
			memory1.putInt(address1 + x, x);
		}
		for(int x = 0; x < size;  x += 4) {
			int i = memory2.getInt(address2 + x);
			Assert.assertEquals(x, i);
		}
		
		// Adding longs
		for(int x = 0; x < size; x += 8) {
			memory2.putLong(address2 + x, x);
		}
		for(int x = 0; x < size;  x += 8) {
			long l = memory1.getLong(address1 + x);
			Assert.assertEquals(x, l);
		}
		
		memory2.release(false); // we'll delete the file below
		memory1.release(true); // true to delete the memory-mapped file
	}
	
	@Test
	public void testSharedVolatilePutAndGet() {

		// 64 bytes = 8 longs, 16 ints, 32 shorts and 64 bytes
		final long size = 64; // 8 * 8 = 64
		
		final String mmapFileUsedAsBridge = "shared-memory.mmap";
		
		Memory memory1 = new SharedMemory(size, mmapFileUsedAsBridge);
		final long address1 = memory1.getPointer();
		
		Memory memory2 = new SharedMemory(size, mmapFileUsedAsBridge);
		final long address2 = memory2.getPointer();
		
		// Adding bytes
		for(int x = 0; x < size; x++) {
			memory1.putByteVolatile(address1 + x, (byte) x);
		}
		for(int x = 0; x < size; x++) {
			byte b = memory2.getByteVolatile(address2 + x);
			Assert.assertEquals(x, b);
		}
		
		// Adding shorts
		for(int x = 0; x < size; x += 2) {
			memory2.putShortVolatile(address2 + x, (short) x);
		}
		for(int x = 0; x < size;  x += 2) {
			short s = memory1.getShortVolatile(address1 + x);
			Assert.assertEquals(x, s);
		}
		
		// Adding ints
		for(int x = 0; x < size; x += 4) {
			memory1.putIntVolatile(address1 + x, x);
		}
		for(int x = 0; x < size;  x += 4) {
			int i = memory2.getIntVolatile(address2 + x);
			Assert.assertEquals(x, i);
		}
		
		// Adding longs
		for(int x = 0; x < size; x += 8) {
			memory2.putLongVolatile(address2 + x, x);
		}
		for(int x = 0; x < size;  x += 8) {
			long l = memory1.getLongVolatile(address1 + x);
			Assert.assertEquals(x, l);
		}
		
		memory2.release(false); // we'll delete the file below
		memory1.release(true); // true to delete the memory-mapped file
	}
}
