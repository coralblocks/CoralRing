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
package com.coralblocks.coralring.example.ring.minimal;

import com.coralblocks.coralring.ring.BlockingBroadcastRingConsumer;
import com.coralblocks.coralring.ring.RingConsumer;

public class MinimalBlockingBroadcastConsumer {
	
	private static final String FILENAME = MinimalBlockingBroadcastProducer.FILENAME;
	private static final int NUMBER_OF_CONSUMERS = MinimalBlockingBroadcastProducer.NUMBER_OF_CONSUMERS;
	
	public static void main(String[] args) {
		
		final int messagesToSend = 10;
		final int consumerIndex = Integer.parseInt(args[0]); // you must specify the index of the consumer
		
		final RingConsumer<MutableLong> ringConsumer = new BlockingBroadcastRingConsumer<MutableLong>(8, MutableLong.getMaxSize(), 
										MutableLong.class, FILENAME, consumerIndex, NUMBER_OF_CONSUMERS); // default size is 1024
		
		boolean isRunning = true;
		
		while(isRunning) {
			
			long avail = ringConsumer.availableToFetch(); // read available batches as fast as possible
			
			if (avail == 0) continue; // busy spin
			
			for(long i = 0; i < avail; i++) {
				
				MutableLong ml = ringConsumer.fetch();
				
				System.out.print(ml.get());
				
				if (ml.get() == messagesToSend - 1) isRunning = false; // done receiving all messages
			}
			
			ringConsumer.doneFetching(); // don't forget to notify producer
		}
		
		ringConsumer.close(true);
		
		System.out.println();
		
		// OUTPUT: 0123456789
	}
}
