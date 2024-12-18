/* 
 * Copyright Copyright 2015-2024 (c) CoralBlocks LLC - http://www.coralblocks.com
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


public class NonBlockingRingTest {
	
	@Test
	public void testNotWrapping() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonblocking-ring.mmap";
		
		final int messagesToSend = 1_000; // less than the capacity (1024) so it will never wrap
		final int maxBatchSize = 50;
		
		final List<Long> messagesReceived  = new ArrayList<Long>();
		final List<Long> batchesReceived = new ArrayList<Long>();
		
		Thread producer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				final RingProducer<Message> ringProducer = new NonBlockingRingProducer<Message>(Message.getMaxSize(), Message.class, filename);
				
				int idToSend = 1; // each message from this producer will contain an unique value (id)
				
				Random rand = new Random();
				
				int remaining = messagesToSend;
				while(remaining > 0) {
					int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
					for(int i = 0; i < batchToSend; i++) {
						Message m;
						if((m = ringProducer.nextToDispatch()) == null) { // <=========
							throw new IllegalStateException("Non-blocking ring producer can never get a null here!");
						}
						m.value = idToSend++; // sending an unique value so the messages sent are unique
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
				
				final RingConsumer<Message> ringConsumer = new NonBlockingRingConsumer<Message>(Message.getMaxSize(), Message.class, filename);
				
				boolean isRunning = true;
				while(isRunning) {
					long avail = ringConsumer.availableToFetch(); // <=========
					if (avail == -1) throw new RuntimeException("Consumer fell behind!");
					if (avail > 0) {
						for(long i = 0; i < avail; i++) {
							Message m = ringConsumer.fetch(); // <=========
							if (m == null) throw new RuntimeException("Bad checksum!");
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
	public void testWrapping() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonblocking-ring.mmap";
		
		final int messagesToSend = 1_025; // one more than capacity (1024) to wrap
		final int maxBatchSize = 50;
		
		final List<Long> messagesReceived  = new ArrayList<Long>();
		final List<Long> batchesReceived = new ArrayList<Long>();
		
		Thread producer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				final RingProducer<Message> ringProducer = new NonBlockingRingProducer<Message>(Message.getMaxSize(), Message.class, filename);
				
				int idToSend = 1; // each message from this producer will contain an unique value (id)
				
				Random rand = new Random();
				
				int remaining = messagesToSend;
				while(remaining > 0) {
					int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
					for(int i = 0; i < batchToSend; i++) {
						Message m;
						if((m = ringProducer.nextToDispatch()) == null) { // <=========
							throw new IllegalStateException("Non-blocking ring producer can never get a null here!");
						}
						m.value = idToSend++; // sending an unique value so the messages sent are unique
						m.last = m.value == messagesToSend; // is it the last message I'll be sending?
					}
					ringProducer.flush(); // <=========
					remaining -= batchToSend;
					
					// sleep so that the consumer NEVER falls behind...
					try { Thread.sleep(1); } catch(InterruptedException e) { throw new RuntimeException(e); }
				}
				
				ringProducer.close(false); // don't delete file, consumer might still be reading it
			}
			
		}, "RingProducer");
		
		Thread consumer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				final RingConsumer<Message> ringConsumer = new NonBlockingRingConsumer<Message>(Message.getMaxSize(), Message.class, filename);
				
				boolean isRunning = true;
				while(isRunning) {
					long avail = ringConsumer.availableToFetch(); // <=========
					if (avail == -1) throw new RuntimeException("Consumer fell behind!");
					if (avail > 0) {
						for(long i = 0; i < avail; i++) {
							Message m = ringConsumer.fetch(); // <=========
							if (m == null) throw new RuntimeException("Bad checksum!");
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
		
		consumer.start();
		producer.start();
		
		consumer.join();
		producer.join();
		
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
	public void testConsumerFallingBehind() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonblocking-ring.mmap";
		
		final int messagesToSend = 2_000;
		final int maxBatchSize = 50;
		
		Thread producer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				final RingProducer<Message> ringProducer = new NonBlockingRingProducer<Message>(Message.getMaxSize(), Message.class, filename);
				
				int idToSend = 1; // each message from this producer will contain an unique value (id)
				
				Random rand = new Random();
				
				int remaining = messagesToSend;
				while(remaining > 0) {
					int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
					for(int i = 0; i < batchToSend; i++) {
						Message m;
						if((m = ringProducer.nextToDispatch()) == null) { // <=========
							throw new IllegalStateException("Non-blocking ring producer can never get a null here!");
						}
						m.value = idToSend++; // sending an unique value so the messages sent are unique
						m.last = m.value == messagesToSend; // is it the last message I'll be sending?
					}
					ringProducer.flush(); // <=========
					remaining -= batchToSend;
				}
				
				ringProducer.close(false); // don't delete file, consumer might still be reading it
			}
			
		}, "RingProducer");
		
		producer.start();
		producer.join(); // wait for producer to wrap the 1024 ring...
		
		final RingConsumer<Message> ringConsumer = new NonBlockingRingConsumer<Message>(Message.getMaxSize(), Message.class, filename);
		long avail = ringConsumer.availableToFetch();
		ringConsumer.close(true); // delete file
		
		Assert.assertEquals(-1, avail); // wrapped
	}
	
	@Test
	public void testWrappingOk() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonblocking-ring.mmap";
		
		final RingProducer<Message> ringProducer = new NonBlockingRingProducer<Message>(8, Message.getMaxSize(), Message.class, filename);
		final RingConsumer<Message> ringConsumer = new NonBlockingRingConsumer<Message>(8, Message.getMaxSize(), Message.class, filename);

		Message m;
		int ids = 1;
		
		// first write and read normally
		
		for(int i = 0; i < 4; i++) {
			m = ringProducer.nextToDispatch();
			m.value = ids++;
		}
		ringProducer.flush();
		
		long avail = ringConsumer.availableToFetch();
		
		Assert.assertEquals(4, avail);
		
		for(int i = 0; i < 4; i++) {
			m = ringConsumer.fetch();
			Assert.assertEquals(i + 1, m.value);
		}
		
		ids = 1;
		
		// now write until the limit to wrapping
		
		for(int i = 0; i < 8; i++) {
			m = ringProducer.nextToDispatch();
			m.value = ids++;
		}
		ringProducer.flush();
		
		avail = ringConsumer.availableToFetch();
		
		Assert.assertEquals(8, avail);
		
		for(int i = 0; i < 8; i++) {
			m = ringConsumer.fetch();
			Assert.assertEquals(i + 1, m.value);
		}
		
		// check ring is empty
		
		avail = ringConsumer.availableToFetch();
		
		Assert.assertEquals(0, avail); // empty
		
		// now wrap
		
		for(int i = 0; i < 9; i++) {
			m = ringProducer.nextToDispatch();
			m.value = ids++;
		}
		ringProducer.flush();
		
		avail = ringConsumer.availableToFetch();
		
		Assert.assertEquals(-1, avail); // wrapped
		
		ringProducer.close(false);
		ringConsumer.close(true);
	}
	
	@Test
	public void testChecksum() throws InterruptedException {
		
		// NOTE: Here we are testing on the same JVM for convenience
		
		final String filename = "test-nonblocking-ring.mmap";
		
		final int messagesToSend = 1_000;
		final int maxBatchSize = 50;
		
		final List<Long> messagesReceived  = new ArrayList<Long>();
		final List<Long> batchesReceived = new ArrayList<Long>();
		
		Thread producer = new Thread(new Runnable() {

			@Override
			public void run() {
				
				final RingProducer<Message> ringProducer = new NonBlockingRingProducer<Message>(Message.getMaxSize(), Message.class, filename, true);
				
				int idToSend = 1; // each message from this producer will contain an unique value (id)
				
				Random rand = new Random();
				
				int remaining = messagesToSend;
				while(remaining > 0) {
					int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
					for(int i = 0; i < batchToSend; i++) {
						Message m;
						if((m = ringProducer.nextToDispatch()) == null) { // <=========
							throw new IllegalStateException("Non-blocking ring producer can never get a null here!");
						}
						m.value = idToSend++; // sending an unique value so the messages sent are unique
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
				
				final RingConsumer<Message> ringConsumer = new NonBlockingRingConsumer<Message>(Message.getMaxSize(), Message.class, filename, true);
				
				boolean isRunning = true;
				while(isRunning) {
					long avail = ringConsumer.availableToFetch(); // <=========
					if (avail == -1) throw new RuntimeException("Consumer fell behind!");
					if (avail > 0) {
						for(long i = 0; i < avail; i++) {
							Message m = ringConsumer.fetch(); // <=========
							if (m == null) throw new RuntimeException("Consumer tripped over producer! (checksum failed)");
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