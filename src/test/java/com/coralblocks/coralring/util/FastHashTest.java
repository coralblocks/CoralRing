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

import org.junit.Assert;
import org.junit.Test;

import com.coralblocks.coralring.memory.ByteBufferMemory;

public class FastHashTest {

	private static final int[] LENGTHS = { 0, 1, 3, 4, 7, 8, 12, 15, 16, 31, 32, 33, 36, 39, 40, 63, 64, 65, 80 };

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
