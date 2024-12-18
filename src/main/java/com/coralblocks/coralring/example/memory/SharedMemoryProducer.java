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
	
    private static final void sleepFor(long nanos) {
        long time = System.nanoTime();
        while((System.nanoTime() - time) < nanos);
    }
}