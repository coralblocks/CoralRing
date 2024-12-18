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

import java.nio.ByteBuffer;

import com.coralblocks.coralring.memory.Memory;

/**
 * <p>A special {@link MemorySerializable} object that allows you to send anything through CoralRing as a ByteBuffer.
 * That effectively makes CoralRing <i>message agnostic</i>.</p>
 * 
 * <p>It has a 4-byte integer denoting the payload size and the payload.</p>
 * 
 * <p>Note that the max payload size must be known beforehand.</p>
 */
public class PayloadByteBufferMessage implements MemorySerializable {
	
	public int payloadSize;
	public final ByteBuffer payload;
	
	/**
	 * Return the max possible size of this object
	 * 
	 * @param maxPayloadSize the max possible payload size
	 * @return the max size of this object
	 */
	public static final int getMaxSize(int maxPayloadSize) {
		return 4 /* payloadSize int size */ + maxPayloadSize;
	}
	
	/**
	 * Creates a new instance with the given max payload size.
	 * 
	 * @param maxPayloadSize the max possible payload size
	 */
	public PayloadByteBufferMessage(int maxPayloadSize) {
		this.payload = ByteBuffer.allocateDirect(maxPayloadSize);
	}
	
	@Override
	public int writeTo(long address, Memory memory) {
		memory.putInt(address, payloadSize);
		payload.limit(payloadSize).position(0);
		memory.putByteBuffer(address + 4, payload, payloadSize);
		return 4 + payloadSize;
	}

	@Override
	public int readFrom(long address, Memory memory) {
		this.payloadSize = memory.getInt(address);
		payload.clear();
		memory.getByteBuffer(address + 4, payload, payloadSize);
		payload.flip();
		return 4 + payloadSize;
	}
}