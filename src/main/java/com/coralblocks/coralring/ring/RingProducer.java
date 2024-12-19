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
package com.coralblocks.coralring.ring;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.MemorySerializable;

/**
 * An interface describing the behavior of a ring producer using {@link Memory} to send data (i.e. messages) to the other side.
 * Each message is associated with a unique sequence number.
 * 
 * @param <E> The message mutable class implementing {@link MemorySerializable} that will be transferred through this ring
 */
public interface RingProducer<E extends MemorySerializable> {
	
	/**
	 * Returns the memory that this ring is using.
	 * 
	 * @return the memory being used by this ring
	 */
	public Memory getMemory();
	
	/**
	 * Return the size of this ring in number of messages that can fit inside this ring.
	 * 
	 * @return the number of messages that can fit inside this ring
	 */
	public int getCapacity();
	
	/**
	 * The {@link Builder} for this ring, which produces its messages.
	 * 
	 * @return an instance of the message mutable class implementing {@link MemorySerializable}
	 */
	public Builder<E> getBuilder();
	
	/**
	 * The sequence number of the last message offered by the ring producer.
	 * 
	 * @return the sequence number of the last offered message
	 */
	public long getLastOfferedSequence();
	
	/**
	 * Sets the sequence number of the last message offered by the ring producer.
	 * 
	 * @param lastOfferedSequence the sequence number of the last offered message
	 */
	public void setLastOfferedSequence(long lastOfferedSequence);
	
	/**
	 * Gets the next available message that can be offered (i.e. dispatched) by the ring producer.
	 * 
	 * @return the next available message that can be offered (i.e. dispatched)
	 */
	public E nextToDispatch();
	
	/**
	 * Flushes (i.e. sends) any previously requested messages to be dispatched to the ring.
	 */
	public void flush();
	
	/**
	 * Closes this ring producer and releases any of its associated resources, like its memory.
	 * 
	 * @param deleteFile true to delete the file if one is being used by the ring producer associated memory
	 */
	public void close(boolean deleteFile);
	
}