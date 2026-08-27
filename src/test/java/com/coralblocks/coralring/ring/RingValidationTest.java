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

import java.io.File;

import org.junit.Assert;
import org.junit.Test;

import com.coralblocks.coralring.MmapTestBase;
import com.coralblocks.coralring.example.ring.Message;
import com.coralblocks.coralring.util.Builder;

public class RingValidationTest extends MmapTestBase {

	@Test
	public void testProducersRejectInvalidArgumentsBeforeCreatingFile() {
		final String unusedFilename = mmapFile("test-invalid-ring-arguments.mmap");
		Builder<Message> nullBuilder = null;

		Assert.assertThrows(IllegalArgumentException.class,
				() -> new WaitingRingProducer<Message>(0, Message.getMaxSize(), Message.class, unusedFilename));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new WaitingRingProducer<Message>(1, 0, Message.class, unusedFilename));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new WaitingRingProducer<Message>(1, Message.getMaxSize(), nullBuilder, unusedFilename));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new NonWaitingRingProducer<Message>(-1, Message.getMaxSize(), Message.class, unusedFilename));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new NonWaitingRingProducer<Message>(1, Message.getMaxSize(), Message.class, null));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new WaitingBroadcastRingProducer<Message>(1, Message.getMaxSize(), Message.class, unusedFilename, 0));

		Assert.assertFalse(new File(unusedFilename).exists());
	}

	@Test
	public void testConsumersRejectInvalidArgumentsBeforeOpeningFile() {
		final String unusedFilename = mmapFile("test-invalid-ring-arguments.mmap");
		Builder<Message> nullBuilder = null;

		Assert.assertThrows(IllegalArgumentException.class,
				() -> new WaitingRingConsumer<Message>(-2, Message.getMaxSize(), Message.class, unusedFilename));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new WaitingRingConsumer<Message>(1, 0, Message.class, unusedFilename));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new WaitingRingConsumer<Message>(1, Message.getMaxSize(), nullBuilder, unusedFilename));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new NonWaitingRingConsumer<Message>(1, Message.getMaxSize(), Message.class, null));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new NonWaitingRingConsumer<Message>(1, Message.getMaxSize(), Message.class, unusedFilename, false, Float.NaN));
		Assert.assertThrows(IllegalArgumentException.class,
				() -> new WaitingBroadcastRingConsumer<Message>(1, Message.getMaxSize(), Message.class, unusedFilename, 0, 0));

		Assert.assertFalse(new File(unusedFilename).exists());
	}

	@Test
	public void testInvalidRollbacksUseIllegalArgumentException() {
		final String waitingFilename = mmapFile("test-invalid-waiting-rollback.mmap");
		RingProducer<Message> waitingProducer = new WaitingRingProducer<Message>(1, Message.getMaxSize(), Message.class, waitingFilename);
		RingConsumer<Message> waitingConsumer = new WaitingRingConsumer<Message>(1, Message.getMaxSize(), Message.class, waitingFilename);
		try {
			Assert.assertThrows(IllegalArgumentException.class, () -> waitingConsumer.rollBack(-1));
			Assert.assertThrows(IllegalArgumentException.class, () -> waitingConsumer.rollBack(1));
		} finally {
			waitingConsumer.close(false);
			waitingProducer.close(true);
		}

		final String broadcastFilename = mmapFile("test-invalid-broadcast-rollback.mmap");
		RingProducer<Message> broadcastProducer = new WaitingBroadcastRingProducer<Message>(1, Message.getMaxSize(), Message.class, broadcastFilename, 1);
		RingConsumer<Message> broadcastConsumer = new WaitingBroadcastRingConsumer<Message>(1, Message.getMaxSize(), Message.class, broadcastFilename, 0, 1);
		try {
			Assert.assertThrows(IllegalArgumentException.class, () -> broadcastConsumer.rollBack(-1));
		} finally {
			broadcastConsumer.close(false);
			broadcastProducer.close(true);
		}

		final String nonWaitingFilename = mmapFile("test-invalid-non-waiting-rollback.mmap");
		RingProducer<Message> nonWaitingProducer = new NonWaitingRingProducer<Message>(1, Message.getMaxSize(), Message.class, nonWaitingFilename);
		RingConsumer<Message> nonWaitingConsumer = new NonWaitingRingConsumer<Message>(1, Message.getMaxSize(), Message.class, nonWaitingFilename);
		try {
			Assert.assertThrows(IllegalArgumentException.class, () -> nonWaitingConsumer.rollBack(-1));
		} finally {
			nonWaitingConsumer.close(false);
			nonWaitingProducer.close(true);
		}
	}
}
