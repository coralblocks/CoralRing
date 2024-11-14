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

import com.coralblocks.coralring.ring.BlockingRingConsumer;

public class PrintProgressConsumer {
	
	public static void main(String[] args) {
		
		final String filename = "shared-ring-prog.mmap";
		
		final int sleepTime = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000_000; // 1s
		final boolean implyFromFile = args.length > 1 ? Boolean.parseBoolean(args[1]) : false;
		
		int capacity = implyFromFile ? -1 : 8;
		int maxObjectSize = implyFromFile ? -1 : Message.getMaxSize();

		final BlockingRingConsumer<Message> ring = new BlockingRingConsumer<Message>(capacity, maxObjectSize, Message.class, filename);
		
		System.out.println("Consumer started! lastPolledSeq=" + ring.getLastPolledSequence() + "\n");
		
		boolean first = true;
		
		while(true) {
			long avail = ring.availableToPoll(); // <=========
			if (avail > 0) {
				for(long i = 0; i < avail; i++) {
					Message m = ring.poll(); // <=========
					if (first) {
						first = false;
					} else {
						System.out.print(",");
					}
					System.out.print(m.value);
					sleepFor(sleepTime);
				}
				ring.donePolling(); // <=========
				sleepFor(sleepTime);
			} else {
				// busy spin while blocking (default and fastest wait strategy)
			}
		}
	}
	
    private final static void sleepFor(long nanos) {
        long time = System.nanoTime();
        while((System.nanoTime() - time) < nanos);
    }
}