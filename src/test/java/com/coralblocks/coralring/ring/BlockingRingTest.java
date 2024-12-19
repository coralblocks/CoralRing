/* 
 * Copyright 2024 (c) CoralBlocks - http://www.coralblocks.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package com.coralblocks.coralring.ring;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.Assert;
import org.junit.Test;

import com.coralblocks.coralring.example.ring.Message;


public class BlockingRingTest {
	
	@Test
	public void testAll() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-blocking-ring.mmap";
		
		final int messagesToSend = 1_000_000;
		final int maxBatchSize = 100;
		
		final List<Long> messagesReceived  = new ArrayList<Long>();
		final List<Long> batchesReceived = new ArrayList<Long>();
		
		Thread producer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				final RingProducer<Message> ringProducer = new BlockingRingProducer<Message>(Message.getMaxSize(), Message.class, filename);
				
				int idToSend = 1; // each message from this producer will contain a unique value (id)
				
				Random rand = new Random();
				
				int remaining = messagesToSend;
				while(remaining > 0) {
					int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
					for(int i = 0; i < batchToSend; i++) {
						Message m;
						while((m = ringProducer.nextToDispatch()) == null) { // <=========
							// busy spin while blocking (default and fastest wait strategy)
						}
						m.value = idToSend++; // sending a unique value so the messages sent are unique
						m.last = m.value == messagesToSend; // is it the last message I'll be sending?
					}
					ringProducer.flush(); // <=========
					remaining -= batchToSend;
				}
				
				ringProducer.close(false); // don't delete file, consumer might still be reading it
			}
			
		}, "RingProducer");
		
		Thread consumer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				final RingConsumer<Message> ringConsumer = new BlockingRingConsumer<Message>(Message.getMaxSize(), Message.class, filename);
				
				boolean isRunning = true;
				while(isRunning) {
					long avail = ringConsumer.availableToFetch(); // <=========
					if (avail > 0) {
						for(long i = 0; i < avail; i++) {
							Message m = ringConsumer.fetch(); // <=========
							messagesReceived.add(m.value); // save just the long value from this message
							if (m.last) isRunning = false; // I'm done!
						}
						ringConsumer.doneFetching(); // <=========
						batchesReceived.add(avail); // save the batch sizes received, just so we can double check
					} else {
						// busy spin while blocking (default and fastest wait strategy)
					}
				}	
				
				ringConsumer.close(true); // delete the file
			}
			
		}, "RingConsumer");
		
		producer.start();
		consumer.start();
		
		producer.join();
		consumer.join();
		
		// Did we receive all messages?
		Assert.assertEquals(messagesToSend, messagesReceived.size());
		
		// Where there any duplicates?
		Assert.assertEquals(messagesReceived.size(), messagesReceived.stream().distinct().count());
		
		// Were the messages received in order?
		List<Long> sortedList = new ArrayList<Long>(messagesReceived);
		Collections.sort(sortedList);
		Assert.assertEquals(messagesReceived, sortedList);
		
		// If we sum all batches received do we get the correct number of messages?
		long sumOfAllBatches = batchesReceived.stream().mapToLong(Long::longValue).sum();
		Assert.assertEquals(messagesToSend, sumOfAllBatches);
	}
	
	@Test
	public void testFindingCapacity() {
		
		final String filename = "test-ring-capacity.mmap";
		
		final RingProducer<Message> ringProducer = new BlockingRingProducer<Message>(2048, Message.getMaxSize(), Message.class, filename);
		final RingConsumer<Message> ringConsumer = new BlockingRingConsumer<Message>(-1, Message.getMaxSize(), Message.class, filename);
		
		Assert.assertEquals(ringProducer.getCapacity(), ringConsumer.getCapacity());
		
		ringProducer.close(false);
		ringConsumer.close(true);
	}
}