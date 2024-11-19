package com.coralblocks.coralring.example.ring.minimal;

import com.coralblocks.coralring.ring.NonBlockingRingConsumer;
import com.coralblocks.coralring.ring.RingConsumer;

public class MinimalNonBlockingConsumer {
	
	private final static String FILENAME = MinimalBlockingProducer.FILENAME;
	
	public static void main(String[] args) {
		
		final int messagesToSend = 10;
		
		final RingConsumer<MutableLong> ringConsumer = new NonBlockingRingConsumer<MutableLong>(MutableLong.getMaxSize(), MutableLong.class, FILENAME); // default size is 1024
		
		boolean isRunning = true;
		
		while(isRunning) {
			
			long avail = ringConsumer.availableToPoll(); // read available batches as fast as possible
			
			if (avail == 0) continue; // busy spin
			
			for(int i = 0; i < avail; i++) {
				
				MutableLong ml = ringConsumer.poll();
				
				System.out.print(ml.get());
				
				if (ml.get() == messagesToSend - 1) isRunning = false; // done receiving all messages
			}
			
			ringConsumer.donePolling(); // don't forget to notify producer
		}
		
		ringConsumer.close(true);
		
		System.out.println();
		
		// OUTPUT: 0123456789
	}
}
