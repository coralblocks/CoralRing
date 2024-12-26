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


public class NonWaitingMulticastRingTest {
	
	@Test
	public void testNotWrapping() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonwaiting-ring.mmap";
		
		final int messagesToSend = 1_000; // less than the capacity (1024) so it will never wrap
		final int maxBatchSize = 50;
		
		final RingProducer<Message> ringProducer = new NonWaitingRingProducer<Message>(Message.getMaxSize(), Message.class, filename);
		
		Thread producer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				int idToSend = 1; // each message from this producer will contain a unique value (id)
				
				Random rand = new Random();
				
				int remaining = messagesToSend;
				while(remaining > 0) {
					int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
					for(int i = 0; i < batchToSend; i++) {
						Message m;
						if((m = ringProducer.nextToDispatch()) == null) { // <=========
							throw new IllegalStateException("Non-waiting ring producer can never get a null here!");
						}
						m.value = idToSend++; // sending a unique value so the messages sent are unique
						m.last = m.value == messagesToSend; // is it the last message I'll be sending?
					}
					ringProducer.flush(); // <=========
					remaining -= batchToSend;
				}
			}
			
		}, "RingProducer");

		Thread[] consumers = new Thread[3];
		
		final List<List<Long>> messagesReceived  = new ArrayList<List<Long>>(consumers.length);
		final List<List<Long>> batchesReceived = new ArrayList<List<Long>>(consumers.length);
		
		for(int i = 0; i < consumers.length; i++) {
			
			final List<Long> mr = new ArrayList<Long>(messagesToSend);
			final List<Long> br = new ArrayList<Long>(messagesToSend);
			
			messagesReceived.add(mr);
			batchesReceived.add(br);
		
			consumers[i] = new Thread(new Runnable() {
	
				@Override
				public void run() {
					
					final RingConsumer<Message> ringConsumer = new NonWaitingRingConsumer<Message>(Message.getMaxSize(), Message.class, filename);
					
					boolean isRunning = true;
					while(isRunning) {
						long avail = ringConsumer.availableToFetch(); // <=========
						if (avail == -1) throw new RuntimeException("Consumer fell behind!");
						if (avail > 0) {
							for(long i = 0; i < avail; i++) {
								Message m = ringConsumer.fetch(); // <=========
								if (m == null) throw new RuntimeException("Bad checksum!");
								mr.add(m.value); // save just the long value from this message
								if (m.last) isRunning = false; // I'm done!
							}
							ringConsumer.doneFetching(); // <=========
							br.add(avail); // save the batch sizes received, just so we can double check
						} else {
							// busy spin while waiting (default and fastest wait strategy)
						}
					}	
					
					ringConsumer.close(false);
				}
				
			}, "RingConsumer-" + i);
		}
		
		producer.start();
		for(Thread consumer : consumers) consumer.start();
		
		producer.join();
		for(Thread consumer : consumers) consumer.join();
		
		// now close producer and delete file
		ringProducer.close(true);
		
		// Did we receive all messages?
		for(int i = 0; i < consumers.length; i++) {
			Assert.assertEquals(messagesToSend, messagesReceived.get(i).size());
		}
		
		// Where there any duplicates?
		for(int i = 0; i < consumers.length; i++) {
			Assert.assertEquals(messagesReceived.get(i).size(), messagesReceived.get(i).stream().distinct().count());
		}
		
		// Were the messages received in order?
		for(int i = 0; i < consumers.length; i++) {
			List<Long> sortedList = new ArrayList<Long>(messagesReceived.get(i));
			Collections.sort(sortedList);
			Assert.assertEquals(messagesReceived.get(i), sortedList);
		}
		
		// If we sum all batches received do we get the correct number of messages?
		for(int i = 0; i < consumers.length; i++) {
			long sumOfAllBatches = batchesReceived.get(i).stream().mapToLong(Long::longValue).sum();
			Assert.assertEquals(messagesToSend, sumOfAllBatches);
		}
	}
	
	@Test
	public void testWrapping() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonwaiting-ring.mmap";
		
		final int messagesToSend = 1_025; // one more than capacity (1024) to wrap
		final int maxBatchSize = 50;
		
		final RingProducer<Message> ringProducer = new NonWaitingRingProducer<Message>(Message.getMaxSize(), Message.class, filename);
		
		Thread producer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				int idToSend = 1; // each message from this producer will contain a unique value (id)
				
				Random rand = new Random();
				
				int remaining = messagesToSend;
				while(remaining > 0) {
					int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
					for(int i = 0; i < batchToSend; i++) {
						Message m;
						if((m = ringProducer.nextToDispatch()) == null) { // <=========
							throw new IllegalStateException("Non-waiting ring producer can never get a null here!");
						}
						m.value = idToSend++; // sending a unique value so the messages sent are unique
						m.last = m.value == messagesToSend; // is it the last message I'll be sending?
					}
					ringProducer.flush(); // <=========
					remaining -= batchToSend;
					
					// sleep so that the consumers NEVER fall behind...
					try { Thread.sleep(5); } catch(InterruptedException e) { throw new RuntimeException(e); }
				}
			}
			
		}, "RingProducer");

		Thread[] consumers = new Thread[3];
		
		final List<List<Long>> messagesReceived  = new ArrayList<List<Long>>(consumers.length);
		final List<List<Long>> batchesReceived = new ArrayList<List<Long>>(consumers.length);
		
		for(int i = 0; i < consumers.length; i++) {
			
			final List<Long> mr = new ArrayList<Long>(messagesToSend);
			final List<Long> br = new ArrayList<Long>(messagesToSend);
			
			messagesReceived.add(mr);
			batchesReceived.add(br);
		
			consumers[i] = new Thread(new Runnable() {
	
				@Override
				public void run() {
					
					final RingConsumer<Message> ringConsumer = new NonWaitingRingConsumer<Message>(Message.getMaxSize(), Message.class, filename);
					
					boolean isRunning = true;
					while(isRunning) {
						long avail = ringConsumer.availableToFetch(); // <=========
						if (avail == -1) throw new RuntimeException("Consumer fell behind!");
						if (avail > 0) {
							for(long i = 0; i < avail; i++) {
								Message m = ringConsumer.fetch(); // <=========
								if (m == null) throw new RuntimeException("Bad checksum!");
								mr.add(m.value); // save just the long value from this message
								if (m.last) isRunning = false; // I'm done!
							}
							ringConsumer.doneFetching(); // <=========
							br.add(avail); // save the batch sizes received, just so we can double check
						} else {
							// busy spin while waiting (default and fastest wait strategy)
						}
					}	
					
					ringConsumer.close(false);
				}
				
			}, "RingConsumer-" + i);
		}
		
		for(Thread consumer : consumers) consumer.start();
		producer.start();

		for(Thread consumer : consumers) consumer.join();
		producer.join();
		
		// now close producer and delete file
		ringProducer.close(true);
		
		// Did we receive all messages?
		for(int i = 0; i < consumers.length; i++) {
			Assert.assertEquals(messagesToSend, messagesReceived.get(i).size());
		}
		
		// Where there any duplicates?
		for(int i = 0; i < consumers.length; i++) {
			Assert.assertEquals(messagesReceived.get(i).size(), messagesReceived.get(i).stream().distinct().count());
		}
		
		// Were the messages received in order?
		for(int i = 0; i < consumers.length; i++) {
			List<Long> sortedList = new ArrayList<Long>(messagesReceived.get(i));
			Collections.sort(sortedList);
			Assert.assertEquals(messagesReceived.get(i), sortedList);
		}
		
		// If we sum all batches received do we get the correct number of messages?
		for(int i = 0; i < consumers.length; i++) {
			long sumOfAllBatches = batchesReceived.get(i).stream().mapToLong(Long::longValue).sum();
			Assert.assertEquals(messagesToSend, sumOfAllBatches);
		}
	}
	
	@Test
	public void testConsumerFallingBehind() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonwaiting-ring.mmap";
		
		final int messagesToSend = 2_000;
		final int maxBatchSize = 50;
		
		final RingProducer<Message> ringProducer = new NonWaitingRingProducer<Message>(Message.getMaxSize(), Message.class, filename);
		
		Thread producer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				int idToSend = 1; // each message from this producer will contain a unique value (id)
				
				Random rand = new Random();
				
				int remaining = messagesToSend;
				while(remaining > 0) {
					int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
					for(int i = 0; i < batchToSend; i++) {
						Message m;
						if((m = ringProducer.nextToDispatch()) == null) { // <=========
							throw new IllegalStateException("Non-waiting ring producer can never get a null here!");
						}
						m.value = idToSend++; // sending a unique value so the messages sent are unique
						m.last = m.value == messagesToSend; // is it the last message I'll be sending?
					}
					ringProducer.flush(); // <=========
					remaining -= batchToSend;
				}
			}
			
		}, "RingProducer");
		
		producer.start();
		producer.join(); // wait for producer to wrap the 1024 ring...
		
		for(int i = 0; i < 3; i++) {
			final RingConsumer<Message> ringConsumer = new NonWaitingRingConsumer<Message>(Message.getMaxSize(), Message.class, filename);
			long avail = ringConsumer.availableToFetch();
			Assert.assertEquals(-1, avail); // wrapped
			ringConsumer.close(false);
		}
		
		ringProducer.close(true);
	}
	
	@Test
	public void testWrappingOk() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonwaiting-ring.mmap";
		
		final RingProducer<Message> ringProducer = new NonWaitingRingProducer<Message>(8, Message.getMaxSize(), Message.class, filename);
		final List<RingConsumer<Message>> ringConsumers = new ArrayList<RingConsumer<Message>>(3);
		for(int i = 0; i < 3; i++) {
			ringConsumers.add(new NonWaitingRingConsumer<Message>(8, Message.getMaxSize(), Message.class, filename));
		}

		Message m;
		int ids = 1;
		
		// first write and read normally
		
		for(int i = 0; i < 4; i++) {
			m = ringProducer.nextToDispatch();
			m.value = ids++;
		}
		ringProducer.flush();
		
		for(RingConsumer<Message> ringConsumer : ringConsumers) {
			long avail = ringConsumer.availableToFetch();
			Assert.assertEquals(4, avail);
			for(int i = 0; i < 4; i++) {
				m = ringConsumer.fetch();
				Assert.assertEquals(i + 1, m.value);
			}
		}
		
		ids = 1;
		
		// now write until the limit to wrapping
		
		for(int i = 0; i < 8; i++) {
			m = ringProducer.nextToDispatch();
			m.value = ids++;
		}
		ringProducer.flush();
		
		for(RingConsumer<Message> ringConsumer : ringConsumers) {
			long avail = ringConsumer.availableToFetch();
			Assert.assertEquals(8, avail);
			for(int i = 0; i < 8; i++) {
				m = ringConsumer.fetch();
				Assert.assertEquals(i + 1, m.value);
			}
		}
		
		// check ring is empty
		
		for(RingConsumer<Message> ringConsumer : ringConsumers) {
			long avail = ringConsumer.availableToFetch();
			Assert.assertEquals(0, avail); // empty
		}
		
		// now wrap
		
		for(int i = 0; i < 9; i++) {
			m = ringProducer.nextToDispatch();
			m.value = ids++;
		}
		ringProducer.flush();
		
		for(RingConsumer<Message> ringConsumer : ringConsumers) {
			long avail = ringConsumer.availableToFetch();
			Assert.assertEquals(-1, avail); // wrapped
		}
		
		for(RingConsumer<Message> ringConsumer : ringConsumers) ringConsumer.close(false);
		ringProducer.close(true);
	}
	
	@Test
	public void testChecksum() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonwaiting-ring.mmap";
		
		final int messagesToSend = 1_000;
		final int maxBatchSize = 50;
		
		final RingProducer<Message> ringProducer = new NonWaitingRingProducer<Message>(Message.getMaxSize(), Message.class, filename, true);
		
		Thread producer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				int idToSend = 1; // each message from this producer will contain a unique value (id)
				
				Random rand = new Random();
				
				int remaining = messagesToSend;
				while(remaining > 0) {
					int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
					for(int i = 0; i < batchToSend; i++) {
						Message m;
						if((m = ringProducer.nextToDispatch()) == null) { // <=========
							throw new IllegalStateException("Non-waiting ring producer can never get a null here!");
						}
						m.value = idToSend++; // sending a unique value so the messages sent are unique
						m.last = m.value == messagesToSend; // is it the last message I'll be sending?
					}
					ringProducer.flush(); // <=========
					remaining -= batchToSend;
				}
			}
			
		}, "RingProducer");

		Thread[] consumers = new Thread[3];
		
		final List<List<Long>> messagesReceived  = new ArrayList<List<Long>>(consumers.length);
		final List<List<Long>> batchesReceived = new ArrayList<List<Long>>(consumers.length);
		
		for(int i = 0; i < consumers.length; i++) {
			
			final List<Long> mr = new ArrayList<Long>(messagesToSend);
			final List<Long> br = new ArrayList<Long>(messagesToSend);
			
			messagesReceived.add(mr);
			batchesReceived.add(br);
		
			consumers[i] = new Thread(new Runnable() {
	
				@Override
				public void run() {
					
					final RingConsumer<Message> ringConsumer = new NonWaitingRingConsumer<Message>(Message.getMaxSize(), Message.class, filename, true);
					
					boolean isRunning = true;
					while(isRunning) {
						long avail = ringConsumer.availableToFetch(); // <=========
						if (avail == -1) throw new RuntimeException("Consumer fell behind!");
						if (avail > 0) {
							for(long i = 0; i < avail; i++) {
								Message m = ringConsumer.fetch(); // <=========
								if (m == null) throw new RuntimeException("Consumer tripped over producer! (checksum failed)");
								mr.add(m.value); // save just the long value from this message
								if (m.last) isRunning = false; // I'm done!
							}
							ringConsumer.doneFetching(); // <=========
							br.add(avail); // save the batch sizes received, just so we can double check
						} else {
							// busy spin while waiting (default and fastest wait strategy)
						}
					}	
					
					ringConsumer.close(false);
				}
				
			}, "RingConsumer-" + i);
		}
		
		producer.start();
		for(Thread consumer : consumers) consumer.start();
		
		producer.join();
		for(Thread consumer : consumers) consumer.join();
		
		ringProducer.close(true);
		
		// Did we receive all messages?
		for(int i = 0; i < consumers.length; i++) {
			Assert.assertEquals(messagesToSend, messagesReceived.get(i).size());
		}
		
		// Where there any duplicates?
		for(int i = 0; i < consumers.length; i++) {
			Assert.assertEquals(messagesReceived.get(i).size(), messagesReceived.get(i).stream().distinct().count());
		}
		
		// Were the messages received in order?
		for(int i = 0; i < consumers.length; i++) {
			List<Long> sortedList = new ArrayList<Long>(messagesReceived.get(i));
			Collections.sort(sortedList);
			Assert.assertEquals(messagesReceived.get(i), sortedList);
		}
		
		// If we sum all batches received do we get the correct number of messages?
		for(int i = 0; i < consumers.length; i++) {
			long sumOfAllBatches = batchesReceived.get(i).stream().mapToLong(Long::longValue).sum();
			Assert.assertEquals(messagesToSend, sumOfAllBatches);
		}
	}
}