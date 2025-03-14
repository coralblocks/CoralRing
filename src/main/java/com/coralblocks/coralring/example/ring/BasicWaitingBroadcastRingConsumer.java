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
package com.coralblocks.coralring.example.ring;

import java.util.ArrayList;
import java.util.List;

import com.coralblocks.coralring.ring.WaitingBroadcastRingConsumer;
import com.coralblocks.coralring.ring.RingConsumer;

public class BasicWaitingBroadcastRingConsumer {
	
	static final String FILENAME = BasicWaitingRingProducer.FILENAME;
	
	public static void main(String[] args) {
		
		final int expectedMessagesToReceive = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
		final int sleepTime = args.length > 1 ? Integer.parseInt(args[1]) : 1_000_000 * 5; // 5 millis
		final int consumerIndex = args.length > 2 ? Integer.parseInt(args[2]) : 0;
		final int numberOfConsumers = args.length > 3 ? Integer.parseInt(args[3]) : 3;
		final boolean deleteFile = args.length > 4 ? Boolean.parseBoolean(args[4]) : true;

		final RingConsumer<Message> ringConsumer = new WaitingBroadcastRingConsumer<Message>(Message.getMaxSize(), Message.class, FILENAME, consumerIndex, numberOfConsumers);
		final List<Long> messagesReceived  = new ArrayList<Long>();
		final List<Long> batchesReceived = new ArrayList<Long>();
		long busySpinCount = 0;
		
		System.out.println("Consumer (index=" + consumerIndex + ") expects to receive " + expectedMessagesToReceive + " messages"
								+ " with sleepTime of " + sleepTime + " nanoseconds (lastFetchedSeq=" + ringConsumer.getLastFetchedSequence() + ")"
								+ "...\n");
		
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
				if (sleepTime > 0) sleepFor(sleepTime);
			} else {
				// busy spin while waiting (default and fastest wait strategy)
				busySpinCount++; // save the number of busy-spins, just for extra info later
			}
		}
		
		System.out.println("Consumer DONE!");
		
		ringConsumer.close(deleteFile); // delete file
		
		// Did we receive all messages?
		if (messagesReceived.size() == expectedMessagesToReceive) System.out.println("SUCCESS: All messages received! => " + expectedMessagesToReceive);
		else System.out.println("ERROR: Wrong number of messages received! => " + messagesReceived.size());
		
		// Did we receive all messages correctly?
		if (hasReceivedCorrectly(messagesReceived)) System.out.println("SUCCESS: Messages were received correctly!");
		else System.out.println("ERROR: Messages were not received correctly!");
		
		// If we sum all batches received do we get the correct number of messages?
		long sumOfAllBatches = batchesReceived.stream().mapToLong(Long::longValue).sum();
		if (sumOfAllBatches == expectedMessagesToReceive) System.out.println("SUCCESS: The sum of messages from the batches received is correct! => " + sumOfAllBatches);
		else System.out.println("ERROR: The sum of messages from the batches received is incorrect! => " + sumOfAllBatches);
		
		System.out.println("\nMore info:\n");
		
		System.out.println("Number of batches received: " + batchesReceived.size());
		System.out.println("Consumer busy-spin count: " + busySpinCount);
	}
	
	private static boolean hasReceivedCorrectly(List<Long> list) {
	    if (list == null || list.isEmpty()) return false;
	    if (list.get(0) != 1) return false;
	    for (int i = 1; i < list.size(); i++) {
	        if (list.get(i - 1) != list.get(i) - 1) {
	            return false;
	        }
	    }
	    return true;
	}
	
    private static final void sleepFor(long nanos) {
        long time = System.nanoTime();
        while((System.nanoTime() - time) < nanos);
    }
}