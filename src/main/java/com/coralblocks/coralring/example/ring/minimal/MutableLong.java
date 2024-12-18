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
package com.coralblocks.coralring.example.ring.minimal;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.util.MemorySerializable;

/**
 * A mutable long class to encapsulate a long value.
 */
public class MutableLong implements MemorySerializable {
	
	private static final int LONG_SIZE = 8; // 8 bytes
	
	private long value;
	
	/**
	 * Constructs a new <code>MutableLong</code> with the given long value.
	 *
	 * @param value the initial long value
	 */
	public MutableLong(long value) {
		set(value);
	}
	
	/**
	 * Constructs a new <code>MutableLong</code> with a value of zero.
	 */
	public MutableLong() {
		this(0);
	}
	
	/**
	 * Changes the value of this <code>MutableLong</code>.
	 *
	 * @param value the new long value
	 */
	public void set(long value) {
		this.value = value;
	}
	
	/**
	 * Gets the current long value.
	 *
	 * @return the long value
	 * @throws NullPointerException if the value is null
	 */
	public long get() {
		return value;
	}
	
	/**
	 * Return the max size of this object
	 * 
	 * @return the max size of this object
	 */
	public static int getMaxSize() {
		return LONG_SIZE;
	}
	
	@Override
	public int writeTo(long address, Memory memory) {
		memory.putLong(address, value);
		return LONG_SIZE;
	}

	@Override
	public int readFrom(long address, Memory memory) {
		value = memory.getLong(address);
		return LONG_SIZE;
	}
	
	@Override
	public String toString() {
		return String.valueOf(value);
	}
}
