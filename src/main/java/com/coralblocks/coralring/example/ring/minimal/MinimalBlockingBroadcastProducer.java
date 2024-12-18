/* 
 * Copyright 2015-2024 (c) CoralBlocks - http://www.coralblocks.com
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

import com.coralblocks.coralring.ring.BlockingBroadcastRingProducer;
import com.coralblocks.coralring.ring.RingProducer;

public class MinimalBlockingBroadcastProducer {
	
	static final String FILENAME = "minimal-blocking-bcast.mmap";
	static final int NUMBER_OF_CONSUMERS = 2;
	
	public static void main(String[] args) {
		
		final int messagesToSend = 10;
		
		final RingProducer<MutableLong> ringProducer = new BlockingBroadcastRingProducer<MutableLong>(8, MutableLong.getMaxSize(), 
										MutableLong.class, FILENAME, NUMBER_OF_CONSUMERS); // default size is 1024
		
		for(int i = 0; i < messagesToSend; i += 2) { // note we are looping 2 by 2 (we are sending a batch of 2 messages)
			
			MutableLong ml; // our data transfer mutable object
			
			while((ml = ringProducer.nextToDispatch()) == null); // busy spin
			ml.set(i);
			
			while((ml = ringProducer.nextToDispatch()) == null); // busy spin
			ml.set(i + 1);
			
			ringProducer.flush(); // don't forget to notify consumer
		}
		
		ringProducer.close(false);
	}
}