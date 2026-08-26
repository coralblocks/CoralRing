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

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import com.coralblocks.coralring.memory.Memory;


public class PayloadByteBufferMessageTest {

	@Test
	public void testReadRejectsPayloadLargerThanCapacity() {
		assertReadRejectsInvalidPayloadSize(9);
	}

	@Test
	public void testReadRejectsNegativePayloadSize() {
		assertReadRejectsInvalidPayloadSize(-1);
	}

	private void assertReadRejectsInvalidPayloadSize(int invalidPayloadSize) {

		final int maxPayloadSize = 8;
		final long address = 100;
		Memory source = Mockito.mock(Memory.class);
		Mockito.when(source.getInt(address)).thenReturn(invalidPayloadSize);

		PayloadByteBufferMessage message = new PayloadByteBufferMessage(maxPayloadSize);

		Assert.assertEquals(Integer.BYTES, message.readFrom(address, source));
		Assert.assertEquals(invalidPayloadSize, message.payloadSize);
		Assert.assertFalse(message.payload.hasRemaining());
		Mockito.verify(source, Mockito.never()).getByteBuffer(Mockito.anyLong(), Mockito.same(message.payload), Mockito.anyInt());

		Memory destination = Mockito.mock(Memory.class);
		Assert.assertEquals(Integer.BYTES, message.writeTo(address, destination));
		Mockito.verify(destination).putInt(address, invalidPayloadSize);
		Mockito.verify(destination, Mockito.never()).putByteBuffer(Mockito.anyLong(), Mockito.same(message.payload), Mockito.anyInt());
	}
}
