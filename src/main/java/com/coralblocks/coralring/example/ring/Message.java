/* 
 * Copyright 2015-2024 (c) CoralBlocks LLC - http://www.coralblocks.com
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

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.util.MemorySerializable;

public class Message implements MemorySerializable {
	
	private static final int SIZE = 8 + 1;
	
	public long value; // 8 bytes
	public boolean last; // 1 byte
	
	public static final int getMaxSize() {
		return SIZE;
	}
	
	@Override
	public int writeTo(long address, Memory memory) {
		memory.putLong(address, value);
		memory.putByte(address + 8, last ? (byte) 'Y' : (byte) 'N');
		return SIZE;
	}
	
	@Override
	public int readFrom(long address, Memory memory) {
		this.value = memory.getLong(address);
		this.last = memory.getByte(address + 8) == 'Y';
		return SIZE;
	}

	@Override
	public String toString() {
		return "Message [value=" + value + ", last=" + last + "]";
	}
}