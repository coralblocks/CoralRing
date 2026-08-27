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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.coralblocks.coralring.util;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

import com.coralblocks.coralring.MmapTestBase;
import com.coralblocks.coralring.memory.ByteBufferMemory;
import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.SharedMemory;

public class FastHashTest extends MmapTestBase {

	private static final int[] LENGTHS = { 0, 1, 3, 4, 7, 8, 12, 15, 16, 31, 32, 33, 36, 39, 40, 63, 64, 65, 80 };

	@Test
	public void testCanonicalXXHash64ReferenceVectors() {
		Memory sharedMemory = new SharedMemory(64, mmapFile("test-fast-hash-reference-vectors.mmap"));
		try {
			assertReferenceVector(new byte[0], 0xef46db3751d8e999L, sharedMemory);
			assertReferenceVector("hello".getBytes(StandardCharsets.US_ASCII), 0x26c7827d889f6da3L, sharedMemory);
			assertReferenceVector(new byte[] { 0, 0, 0, (byte) 0x80 }, 0x822e51211bf08373L, sharedMemory);
			assertReferenceVector("0123456789abcdefghijklmnopqrstuvwxyz".getBytes(StandardCharsets.US_ASCII), 0x69196c1b3af0bff9L, sharedMemory);
		} finally {
			sharedMemory.release(true);
		}
	}

	private static void assertReferenceVector(byte[] bytes, long expected, Memory sharedMemory) {
		ByteBuffer buffer = ByteBuffer.wrap(bytes);
		Assert.assertEquals(ByteOrder.BIG_ENDIAN, buffer.order());
		Assert.assertEquals(expected, FastHash.hash64(buffer, 0));
		Assert.assertEquals(ByteOrder.BIG_ENDIAN, buffer.order());

		ByteBufferMemory memory = new ByteBufferMemory(bytes.length, false);
		try {
			for(int i = 0; i < bytes.length; i++) memory.putByte(i, bytes[i]);
			Assert.assertEquals(expected, FastHash.hash64(memory, memory.getPointer(), bytes.length, 0));
		} finally {
			memory.release(false);
		}

		for(int i = 0; i < bytes.length; i++) sharedMemory.putByte(sharedMemory.getPointer() + i, bytes[i]);
		Assert.assertEquals(expected, FastHash.hash64(sharedMemory, sharedMemory.getPointer(), bytes.length, 0));
	}

	@Test
	public void testMemoryHashMatchesByteBufferHash() {
		assertMemoryHashMatchesByteBufferHash(false);
		assertMemoryHashMatchesByteBufferHash(true);
	}

	private static void assertMemoryHashMatchesByteBufferHash(boolean useDirectByteBuffer) {
		ByteBufferMemory memory = new ByteBufferMemory(80, useDirectByteBuffer);
		try {
			for(int i = 0; i < memory.getSize(); i++) {
				memory.putByte(i, (byte) (i * 31 + 7));
			}

			ByteBuffer buffer = memory.getByteBuffer();
			for(int len : LENGTHS) {
				buffer.limit(len).position(0);
				Assert.assertEquals(FastHash.hash64(buffer, 123), FastHash.hash64(memory, memory.getPointer(), len, 123));
			}
		} finally {
			memory.release(false);
		}
	}
}
