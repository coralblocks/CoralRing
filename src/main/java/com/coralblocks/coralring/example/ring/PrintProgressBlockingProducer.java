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

import com.coralblocks.coralring.ring.BlockingRingProducer;
import com.coralblocks.coralring.ring.RingProducer;

public class PrintProgressBlockingProducer {
	
	public static void main(String[] args) {
		
		final String filename = "shared-ring-prog.mmap";
		
		final int sleepTime = args.length > 0 ? Integer.parseInt(args[0]) : 1_000_000_000; // 1s
		
		final RingProducer<Message> ring = new BlockingRingProducer<Message>(8, Message.getMaxSize(), Message.class, filename);
		
		long idToSend = ring.getLastOfferedSequence() + 1;
		
		System.out.println("Producer started! lastOfferedSeq=" + ring.getLastOfferedSequence() + "\n");
		
		Random rand = new Random();
		
		boolean first = true;
		
		while(true) {
			
			int batchToSend = rand.nextInt(4);
			
			for(int i = 0; i < batchToSend; i++) {
				Message m;
				while((m = ring.nextToDispatch()) == null) { // <=========
					// busy spin while blocking (default and fastest wait strategy)
				}
				m.value = idToSend++; // sending an unique value so the messages sent are unique
				if (first) {
					first = false;
				} else {
					System.out.print(",");
				}
				System.out.print(m.value);
				sleepFor(sleepTime);
			}
			ring.flush(); // <=========
			sleepFor(sleepTime);
		}
	}
	
    private final static void sleepFor(long nanos) {
        long time = System.nanoTime();
        while((System.nanoTime() - time) < nanos);
    }
}