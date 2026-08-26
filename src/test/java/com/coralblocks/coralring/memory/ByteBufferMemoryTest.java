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
package com.coralblocks.coralring.memory;

import java.nio.ByteBuffer;

import org.junit.Assert;
import org.junit.Test;

public class ByteBufferMemoryTest {

	@Test
	public void testPrimitiveAccessDoesNotChangeBufferState() {
		assertPrimitiveAccessDoesNotChangeBufferState(false);
		assertPrimitiveAccessDoesNotChangeBufferState(true);
	}

	private static void assertPrimitiveAccessDoesNotChangeBufferState(boolean useDirectByteBuffer) {
		ByteBufferMemory memory = new ByteBufferMemory(32, useDirectByteBuffer);
		try {
			ByteBuffer buffer = memory.getByteBuffer();
			buffer.position(0).limit(1);

			memory.putByte(0, (byte) 1);
			memory.putShort(2, (short) 2);
			memory.putInt(4, 3);
			memory.putLong(8, 4L);

			Assert.assertEquals(1, memory.getByte(0));
			Assert.assertEquals(2, memory.getShort(2));
			Assert.assertEquals(3, memory.getInt(4));
			Assert.assertEquals(4L, memory.getLong(8));

			Assert.assertEquals(0, buffer.position());
			Assert.assertEquals(1, buffer.limit());
		} finally {
			memory.release(false);
		}
	}
}
