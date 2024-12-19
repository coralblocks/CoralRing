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
package com.coralblocks.coralring.example.ring;

import java.util.Random;

import com.coralblocks.coralring.ring.NonBlockingRingProducer;
import com.coralblocks.coralring.ring.RingProducer;

public class NonBlockingProducer {
	
	static final String FILENAME = "shared-ring.mmap";
	static final int RING_CAPACITY = 1024 * 16;
	
	public static void main(String[] args) {
		
		final int messagesToSend = args.length > 0 ? Integer.parseInt(args[0]) : 100_000;
		final int maxBatchSize = args.length > 1 ? Integer.parseInt(args[1]) : 100;
		final int sleepTime = args.length > 2 ? Integer.parseInt(args[2]) : 1_000_000 * 10; // 10 milliseconds
		final boolean writeChecksum = args.length > 3 ? Boolean.parseBoolean(args[3]) : false;
		
		final RingProducer<Message> ringProducer = new NonBlockingRingProducer<Message>(RING_CAPACITY, Message.getMaxSize(), Message.class, FILENAME, writeChecksum);
		
		int idToSend = 1; // each message from this producer will contain a unique value (id)
		long busySpinCount = 0;
		
		System.out.println("Producer will send " + messagesToSend + " messages in max batches of " + maxBatchSize + " messages,"
							+ (writeChecksum ? "" : " not") + " writing checksum"
							+ " and with sleepTime of " + sleepTime + " nanoseconds (lastOfferedSeq=" + ringProducer.getLastOfferedSequence() + ")"
							+ "...\n");
		
		Random rand = new Random();
		
		int remaining = messagesToSend;
		while(remaining > 0) {
			int batchToSend = Math.min(rand.nextInt(maxBatchSize) + 1, remaining);
			for(int i = 0; i < batchToSend; i++) {
				Message m;
				while((m = ringProducer.nextToDispatch()) == null) { // <=========
					// NOTE: For a non-blocking ring it will never return null
					busySpinCount++; // this must always be zero for a non-blocking ring
				}
				m.value = idToSend++; // sending a unique value so the messages sent are unique
				m.last = m.value == messagesToSend; // is it the last message I'll be sending?
			}
			ringProducer.flush(); // <=========
			remaining -= batchToSend;
			if (sleepTime > 0) sleepFor(sleepTime);
		}
		
		System.out.println("Producer DONE!");
		
		ringProducer.close(false); // don't delete file, consumer might still be reading it
		
		System.out.println("Producer busy-spin count: " + busySpinCount);
	}
	
    private static final void sleepFor(long nanos) {
        long time = System.nanoTime();
        while((System.nanoTime() - time) < nanos);
    }
}