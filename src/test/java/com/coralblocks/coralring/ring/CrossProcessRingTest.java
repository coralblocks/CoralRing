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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.Test;

import com.coralblocks.coralring.MmapTestBase;
import com.coralblocks.coralring.example.ring.Message;

public class CrossProcessRingTest extends MmapTestBase {

	private static final int CAPACITY = 8;
	private static final int MESSAGE_COUNT = 100;
	private static final int TIMEOUT_SECONDS = 30;

	@Test
	public void testWaitingRingAcrossProcesses() throws Exception {
		final String filename = mmapFile("test-cross-process-waiting-ring.mmap");
		final RingProducer<Message> producer = new WaitingRingProducer<Message>(
				CAPACITY, Message.getMaxSize(), Message.class, filename);
		Process consumer = null;

		try {
			consumer = startConsumer(filename);
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);

			for (int expectedValue = 1; expectedValue <= MESSAGE_COUNT; expectedValue++) {
				Message message;
				while ((message = producer.nextToDispatch()) == null) {
					if (!consumer.isAlive()) {
						Assert.fail("Consumer process exited early:\n" + readOutput(consumer));
					}
					if (System.nanoTime() >= deadline) {
						Assert.fail("Timed out waiting for the consumer process");
					}
					Thread.onSpinWait();
				}

				message.value = expectedValue;
				message.last = expectedValue == MESSAGE_COUNT;
				producer.flush();
			}

			if (!consumer.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
				Assert.fail("Timed out waiting for the consumer process to finish");
			}
			Assert.assertEquals("Consumer process failed:\n" + readOutput(consumer), 0, consumer.exitValue());
		} finally {
			try {
				if (consumer != null && consumer.isAlive()) {
					consumer.destroyForcibly();
					consumer.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
				}
			} finally {
				producer.close(true);
			}
		}
	}

	private static Process startConsumer(String filename) throws IOException {
		List<String> command = new ArrayList<String>();
		command.add(new File(System.getProperty("java.home"), "bin/java").getAbsolutePath());
		command.add("--add-opens");
		command.add("java.base/sun.nio.ch=ALL-UNNAMED");
		command.add("--add-opens");
		command.add("java.base/java.nio=ALL-UNNAMED");
		if (Runtime.version().feature() >= 24) {
			command.add("--enable-native-access=ALL-UNNAMED");
			command.add("--sun-misc-unsafe-memory-access=allow");
		}
		command.add("-cp");
		command.add(System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")));
		command.add(CrossProcessRingTest.class.getName());
		command.add(filename);
		command.add(Integer.toString(MESSAGE_COUNT));

		return new ProcessBuilder(command).redirectErrorStream(true).start();
	}

	private static String readOutput(Process process) throws IOException {
		return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
	}

	public static void main(String[] args) {
		final String filename = args[0];
		final int messageCount = Integer.parseInt(args[1]);
		final RingConsumer<Message> consumer = new WaitingRingConsumer<Message>(
				CAPACITY, Message.getMaxSize(), Message.class, filename);

		try {
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
			int expectedValue = 1;

			while (expectedValue <= messageCount) {
				long available = consumer.availableToFetch();
				if (available == 0) {
					if (System.nanoTime() >= deadline) {
						throw new IllegalStateException("Timed out waiting for the producer process");
					}
					Thread.onSpinWait();
					continue;
				}

				while (available-- > 0 && expectedValue <= messageCount) {
					Message message = consumer.fetch();
					if (message.value != expectedValue || message.last != (expectedValue == messageCount)) {
						throw new IllegalStateException("Unexpected message: " + message + ", expectedValue=" + expectedValue);
					}
					expectedValue++;
				}
				consumer.doneFetching();
			}
		} finally {
			consumer.close(false);
		}
	}
}
