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
package com.coralblocks.coralring.ring;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.MemorySerializable;

/**
 * An interface describing the behavior of a ring consumer using {@link Memory} to read data (i.e. messages) from the other side.
 * Each message is associated with an unique sequence number.
 * 
 * @param <E> The message mutable class implementing {@link MemorySerializable} that will be transferred through this ring
 */
public interface RingConsumer<E extends MemorySerializable> {
	
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
	 * The sequence number of the last message polled by the ring consumer.
	 * 
	 * @return the sequence number of the last polled message
	 */
	public long getLastPolledSequence();
	
	/**
	 * Sets the sequence number of the last message polled by the ring consumer.
	 * 
	 * @param lastPolledSequence the sequence number of the last polled message
	 */
	public void setLastPolledSequence(long lastPolledSequence);
	
	/**
	 * The sequence number of the last message offered by the ring producer from where this ring consumer is reading messages from.
	 * 
	 * @return the sequence number of the last offered message by the ring producer on the other side
	 */
	public long getLastOfferedSequence();
	
	/**
	 * The number of messages that can be polled by the ring consumer.
	 * 
	 * @return the number of messages available to be polled
	 */
	public long availableToPoll();
	
	/**
	 * Poll the next available message.
	 * 
	 * @return the next available message
	 */
	public E poll();
	
	/**
	 * Peek, but don't poll, the next available message.
	 * 
	 * @return the next available message
	 */
	public E peek();
	
	/**
	 * Roll back (and pretend they were never polled) any previously polled messages by the ring consumer.
	 */
	public void rollBack();
	
	/**
	 * Roll back (and pretend they were never polled) some messages previously polled by the ring consumer.
	 * 
	 * @param count the number of previous polls to roll back
	 */
	public void rollBack(long count);
	
	/**
	 * Must be called to indicate that the ring consumer has finished polling the available messages.
	 */
	public void donePolling();
	
	/**
	 * Closes this ring consumer and releases any of its associated resources, like its memory.
	 * 
	 * @param deleteFile true to delete the file if one is being used by the ring consumer associated memory
	 */
	public void close(boolean deleteFile);
	
}