package com.coralblocks.coralring.example.memory;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.SharedMemory;

public class SharedMemoryProducer {
	
	public static void main(String[] args) {
		
		final String filename = "shared-pipe.mmap";
		
		final int size = 4 // header size = 4
					   + 4 * 32 // 32 integers
					   + 4; // last integer to send */
		
		Memory memory = new SharedMemory(size, filename);
		final long address = memory.getPointer();
		
		int currIndex = -1;
		int valueToSend = -1;
		int offset;
		
		memory.putIntVolatile(address, currIndex);
		
		for(offset = 4; // skip the header 
			offset < size - 4; // don't send the last message 
			offset += 4) { // sending integers (4 bytes)
				memory.putInt(address + offset, ++valueToSend);
				if (offset > 4) System.out.print(",");
				System.out.print(valueToSend);
				memory.putIntVolatile(address, ++currIndex); // write to the header
				sleepFor(1_000_000_000 / 4);
		}
		
		// now send the very last message to indicate we are done
		memory.putInt(address + offset, -1); // -1 to signal we are done!
		memory.putIntVolatile(address, ++currIndex);
		
		memory.release(false); // don't delete the file, consumer may still be reading it
		
		System.out.println("\nProducer DONE!");
	}
	
    private final static void sleepFor(long nanos) {
        long time = System.nanoTime();
        while((System.nanoTime() - time) < nanos);
    }
}