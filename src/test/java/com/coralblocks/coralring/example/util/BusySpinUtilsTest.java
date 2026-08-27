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
package com.coralblocks.coralring.example.util;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Assert;
import org.junit.Test;

public class BusySpinUtilsTest {

	@Test
	public void testWaitForStopsWhenInterrupted() throws InterruptedException {
		CountDownLatch started = new CountDownLatch(1);
		AtomicBoolean interruptionPreserved = new AtomicBoolean();
		Thread worker = new Thread(() -> {
			started.countDown();
			BusySpinUtils.waitFor(Long.MAX_VALUE);
			interruptionPreserved.set(Thread.currentThread().isInterrupted());
		});
		worker.setDaemon(true);
		worker.start();

		Assert.assertTrue(started.await(5, TimeUnit.SECONDS));
		worker.interrupt();
		worker.join(TimeUnit.SECONDS.toMillis(5));

		Assert.assertFalse(worker.isAlive());
		Assert.assertTrue(interruptionPreserved.get());
	}
}
