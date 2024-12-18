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
package com.coralblocks.coralring.util;

import com.coralblocks.coralring.memory.Memory;

/**
 * An interface that describes how an object can be serialized to and from memory so that it can be
 * transmitted through shared memory.
 */
public interface MemorySerializable {

	/**
	 * Write the contents of this object to {@link Memory}
	 * 
	 * @param address the address in memory to use when writing the contents of the object
	 * @param memory the memory where the contents of the object will be written to
	 * @return the size written in bytes
	 */
	public int writeTo(long address, Memory memory);
	
	/**
	 * Read the contents of this object from {@link Memory}
	 * 
	 * @param address the address in memory to use when reading the contents of the object
	 * @param memory the memory where the contents of the object will be read from
	 * @return the size read in bytes
	 */
	public int readFrom(long address, Memory memory);
}