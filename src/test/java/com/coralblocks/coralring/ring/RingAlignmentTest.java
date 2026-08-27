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
package com.coralblocks.coralring.ring;

import org.junit.Assert;
import org.junit.Test;

import com.coralblocks.coralring.MmapTestBase;
import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.MemorySerializable;

public class RingAlignmentTest extends MmapTestBase {

	public static class AlignedMessage implements MemorySerializable {

		private static final int SIZE = Long.BYTES + 1;
		private long value;

		public static int getMaxSize() {
			return SIZE;
		}

		private static void assertAligned(long address) {
			Assert.assertEquals(0L, address & (Long.BYTES - 1));
		}

		@Override
		public int writeTo(long address, Memory memory) {
			assertAligned(address);
			memory.putLong(address, value);
			memory.putByte(address + Long.BYTES, (byte) 0);
			return SIZE;
		}

		@Override
		public int readFrom(long address, Memory memory) {
			assertAligned(address);
			value = memory.getLong(address);
			return SIZE;
		}
	}

	private static void sendAndReceive(RingProducer<AlignedMessage> producer, RingConsumer<AlignedMessage> consumer) {
		for(int i = 1; i <= 2; i++) {
			AlignedMessage message = producer.nextToDispatch();
			Assert.assertNotNull(message);
			message.value = i;
		}
		producer.flush();

		Assert.assertEquals(2, consumer.availableToFetch());
		for(int i = 1; i <= 2; i++) {
			AlignedMessage message = consumer.fetch();
			Assert.assertNotNull(message);
			Assert.assertEquals(i, message.value);
		}
		consumer.doneFetching();
	}

	@Test
	public void testWaitingRingAlignsMessages() {
		final String filename = mmapFile("test-waiting-ring-alignment.mmap");
		RingProducer<AlignedMessage> producer = new WaitingRingProducer<AlignedMessage>(
				4, AlignedMessage.getMaxSize(), AlignedMessage.class, filename);
		RingConsumer<AlignedMessage> consumer = new WaitingRingConsumer<AlignedMessage>(
				-1, AlignedMessage.getMaxSize(), AlignedMessage.class, filename);
		try {
			sendAndReceive(producer, consumer);
		} finally {
			consumer.close(false);
			producer.close(true);
		}
	}

	@Test
	public void testWaitingBroadcastRingAlignsMessages() {
		final String filename = mmapFile("test-waiting-broadcast-ring-alignment.mmap");
		RingProducer<AlignedMessage> producer = new WaitingBroadcastRingProducer<AlignedMessage>(
				4, AlignedMessage.getMaxSize(), AlignedMessage.class, filename, 1);
		RingConsumer<AlignedMessage> consumer = new WaitingBroadcastRingConsumer<AlignedMessage>(
				-1, AlignedMessage.getMaxSize(), AlignedMessage.class, filename, 0, 1);
		try {
			sendAndReceive(producer, consumer);
		} finally {
			consumer.close(false);
			producer.close(true);
		}
	}

	@Test
	public void testNonWaitingRingAlignsMessages() {
		final String filename = mmapFile("test-nonwaiting-ring-alignment.mmap");
		RingProducer<AlignedMessage> producer = new NonWaitingRingProducer<AlignedMessage>(
				4, AlignedMessage.getMaxSize(), AlignedMessage.class, filename, true);
		RingConsumer<AlignedMessage> consumer = new NonWaitingRingConsumer<AlignedMessage>(
				-1, AlignedMessage.getMaxSize(), AlignedMessage.class, filename, true);
		try {
			sendAndReceive(producer, consumer);
		} finally {
			consumer.close(false);
			producer.close(true);
		}
	}
}
