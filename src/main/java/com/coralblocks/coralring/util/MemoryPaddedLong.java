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
package com.coralblocks.coralring.util;

import com.coralblocks.coralring.memory.Memory;

public class MemoryPaddedLong {

	private final long address;
	private final Memory memory;
	
	public MemoryPaddedLong(long address, Memory memory, Long value) {
		this.address = address;
		this.memory = memory;
		if (value != null) set(value.longValue());
	}
	
	public MemoryPaddedLong(long address,  Memory memory) {
		this(address, memory, null);
	}
	
	public final void set(long value) {
		memory.putLongVolatile(address, value);
	}
	
	public final long get() {
		return memory.getLongVolatile(address);
	}
}
