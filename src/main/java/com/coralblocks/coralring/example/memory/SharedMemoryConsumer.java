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

public class SharedMemoryConsumer {
	
	public static void main(String[] args) {
		
		final String filename = "shared-pipe.mmap";
		
		Memory memory = new SharedMemory(filename); // size will be taken from file
		final long address = memory.getPointer();
		
		final int headerOffset = 4;
		int producerIndex = -1;
		int lastIndexRead = -1;
		
		OUTER: while(true) { // busy spin

			producerIndex = memory.getIntVolatile(address);
			
			if (producerIndex > lastIndexRead) {
			
				int availableToRead = producerIndex - lastIndexRead;
				
				for(int i = 0; i < availableToRead; i++) {
					
					long offset = headerOffset + (lastIndexRead + i + 1) * 4;
					
					int value = memory.getInt(address + offset);

					if (value == -1) break OUTER;

					if (value != 0) System.out.print(",");
					
					System.out.print(value);
				}
				
				lastIndexRead = producerIndex;
			}
		}
		
		memory.release(true); // also delete the file when done (producer will be done too)
		
		System.out.println("\nConsumer DONE!");
	}
}