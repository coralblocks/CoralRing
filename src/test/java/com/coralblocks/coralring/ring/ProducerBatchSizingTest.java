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
import com.coralblocks.coralring.util.Builder;

public class ProducerBatchSizingTest extends MmapTestBase {

	private static final int CAPACITY = 16;
	private static final int INITIAL_BATCH_SIZE = 4;

	private static class CountingBuilder implements Builder<Message> {

		private int count;

		@Override
		public Message newInstance() {
			count++;
			return new Message();
		}
	}

	@Test
	public void testWaitingRingProducerBatchSizing() {
		CountingBuilder builder = new CountingBuilder();
		RingProducer<Message> producer = new WaitingRingProducer<Message>(CAPACITY, Message.getMaxSize(), builder, newFilename(), INITIAL_BATCH_SIZE);
		assertBatchSizing(producer, builder);
	}

	@Test
	public void testWaitingBroadcastRingProducerBatchSizing() {
		CountingBuilder builder = new CountingBuilder();
		RingProducer<Message> producer = new WaitingBroadcastRingProducer<Message>(CAPACITY, Message.getMaxSize(), builder, newFilename(), 1, INITIAL_BATCH_SIZE);
		assertBatchSizing(producer, builder);
	}

	@Test
	public void testNonWaitingRingProducerBatchSizing() {
		CountingBuilder builder = new CountingBuilder();
		RingProducer<Message> producer = new NonWaitingRingProducer<Message>(CAPACITY, Message.getMaxSize(), builder, newFilename(), false, INITIAL_BATCH_SIZE);
		assertBatchSizing(producer, builder);
	}

	private String newFilename() {
		return mmapFile("coralring-batch-sizing.mmap");
	}

	private static void assertBatchSizing(RingProducer<Message> producer, CountingBuilder builder) {
		try {
			Assert.assertEquals(INITIAL_BATCH_SIZE, builder.count);
			for(int i = 0; i < INITIAL_BATCH_SIZE; i++) {
				Assert.assertNotNull(producer.nextToDispatch());
			}
			Assert.assertEquals(INITIAL_BATCH_SIZE, builder.count);

			Assert.assertNotNull(producer.nextToDispatch());
			Assert.assertEquals(INITIAL_BATCH_SIZE + 1, builder.count);
		} finally {
			producer.close(true);
		}
	}
}
