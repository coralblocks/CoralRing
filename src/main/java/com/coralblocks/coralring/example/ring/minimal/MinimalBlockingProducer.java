package com.coralblocks.coralring.example.ring.minimal;

import com.coralblocks.coralring.ring.BlockingRingProducer;
import com.coralblocks.coralring.ring.RingProducer;

public class MinimalBlockingProducer {
	
	final static String FILENAME = "minimal-blocking.mmap";
	
	public static void main(String[] args) {
		
		final int messagesToSend = 10;
		
		final RingProducer<MutableLong> ringProducer = new BlockingRingProducer<MutableLong>(MutableLong.getMaxSize(), MutableLong.class, FILENAME); // default size is 1024
		
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