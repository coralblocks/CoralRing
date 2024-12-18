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
package com.coralblocks.coralring.util;

import com.coralblocks.coralring.memory.Memory;

/**
 * The class maintains a long in {@link Memory} in a <i>volatile</i> way.
 */
public class MemoryVolatileLong {

	private final long address;
	private final Memory memory;
	
	/**
	 * Creates a new long in {@link Memory} with the given value at the given address.
	 * 
	 * @param address the address in memory to write the long value
	 * @param memory the memory to use
	 * @param value the value to write to memory or null to write nothing
	 */
	public MemoryVolatileLong(long address, Memory memory, Long value) {
		this.address = address;
		this.memory = memory;
		if (value != null) set(value.longValue());
	}
	
	/**
	 * Creates a new long in {@link Memory} at the given address.
	 * 
	 * @param address the address in memory to write the long value
	 * @param memory the memory to use
	 */
	public MemoryVolatileLong(long address,  Memory memory) {
		this(address, memory, null);
	}
	
	/**
	 * Writes the given long value to memory in a volatile way. See {@link Memory#putLongVolatile(long, long)}.
	 * 
	 * @param value the long value to write
	 */
	public final void set(long value) {
		memory.putLongVolatile(address, value);
	}
	
	/**
	 * Reads the long value from memory in a volatile way. See {@link Memory#getLongVolatile(long)}.
	 * 
	 * @return the long value read from memory
	 */
	public final long get() {
		return memory.getLongVolatile(address);
	}
}
