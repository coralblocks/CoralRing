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
import com.coralblocks.coralring.example.ring.Message;

public class RingConsumerSemanticsTest extends MmapTestBase {

	private static final int CAPACITY = 8;

	@Test
	public void testWaitingRingPeekAndRollBack() {
		final String filename = mmapFile("test-waiting-ring-consumer-semantics.mmap");
		final RingProducer<Message> producer = new WaitingRingProducer<Message>(
				CAPACITY, Message.getMaxSize(), Message.class, filename);
		final RingConsumer<Message> consumer = new WaitingRingConsumer<Message>(
				CAPACITY, Message.getMaxSize(), Message.class, filename);

		try {
			assertPeekAndRollBackSemantics(producer, consumer);
		} finally {
			consumer.close(false);
			producer.close(true);
		}
	}

	@Test
	public void testWaitingBroadcastRingPeekAndRollBack() {
		final String filename = mmapFile("test-waiting-broadcast-ring-consumer-semantics.mmap");
		final RingProducer<Message> producer = new WaitingBroadcastRingProducer<Message>(
				CAPACITY, Message.getMaxSize(), Message.class, filename, 1);
		final RingConsumer<Message> consumer = new WaitingBroadcastRingConsumer<Message>(
				CAPACITY, Message.getMaxSize(), Message.class, filename, 0, 1);

		try {
			assertPeekAndRollBackSemantics(producer, consumer);
		} finally {
			consumer.close(false);
			producer.close(true);
		}
	}

	@Test
	public void testNonWaitingRingPeekAndRollBack() {
		final String filename = mmapFile("test-non-waiting-ring-consumer-semantics.mmap");
		final RingProducer<Message> producer = new NonWaitingRingProducer<Message>(
				CAPACITY, Message.getMaxSize(), Message.class, filename);
		final RingConsumer<Message> consumer = new NonWaitingRingConsumer<Message>(
				CAPACITY, Message.getMaxSize(), Message.class, filename);

		try {
			assertPeekAndRollBackSemantics(producer, consumer);
		} finally {
			consumer.close(false);
			producer.close(true);
		}
	}

	private static void assertPeekAndRollBackSemantics(RingProducer<Message> producer, RingConsumer<Message> consumer) {
		for (long value = 1; value <= 3; value++) {
			Message message = producer.nextToDispatch();
			Assert.assertNotNull(message);
			message.value = value;
			message.last = value == 3;
		}
		producer.flush();

		Assert.assertEquals(3, consumer.availableToFetch());
		assertMessage(1, false, consumer.fetch(false));
		Assert.assertEquals(0, consumer.getLastFetchedSequence());
		Assert.assertEquals(3, consumer.availableToFetch());
		assertMessage(1, false, consumer.fetch(false));
		Assert.assertEquals(0, consumer.getLastFetchedSequence());

		assertMessage(1, false, consumer.fetch());
		assertMessage(2, false, consumer.fetch());
		Assert.assertEquals(2, consumer.getLastFetchedSequence());

		consumer.rollBack(1);
		Assert.assertEquals(1, consumer.getLastFetchedSequence());
		Assert.assertEquals(2, consumer.availableToFetch());
		assertMessage(2, false, consumer.fetch());
		assertMessage(3, true, consumer.fetch());

		consumer.rollBack();
		Assert.assertEquals(0, consumer.getLastFetchedSequence());
		Assert.assertEquals(3, consumer.availableToFetch());
		assertMessage(1, false, consumer.fetch());
		consumer.doneFetching();
	}

	private static void assertMessage(long expectedValue, boolean expectedLast, Message actual) {
		Assert.assertNotNull(actual);
		Assert.assertEquals(expectedValue, actual.value);
		Assert.assertEquals(expectedLast, actual.last);
	}
}
