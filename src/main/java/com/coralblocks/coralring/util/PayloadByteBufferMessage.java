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

import java.nio.ByteBuffer;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.MemorySerializable;

/**
 * <p>A special {@link MemorySerializable} object that allows you to send anything through CoralRing as a ByteBuffer.
 * That effectively makes CoralRing <i>message agnostic</i>.</p>
 * 
 * <p>It has a 4-byte integer denoting the payload size and the payload.</p>
 * 
 * <p>Note that the max payload size must be known beforehand.</p>
 */
public class PayloadByteBufferMessage implements MemorySerializable {

	private static final int PAYLOAD_SIZE_LENGTH = Integer.BYTES; // 4
	
	public int payloadSize;
	public final ByteBuffer payload;
	private boolean invalidPayloadSize;
	
	/**
	 * Return the max possible size of this object
	 * 
	 * @param maxPayloadSize the max possible payload size
	 * @return the max size of this object
	 */
	public static final int getMaxSize(int maxPayloadSize) {
		return PAYLOAD_SIZE_LENGTH + maxPayloadSize;
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
		if (invalidPayloadSize) return PAYLOAD_SIZE_LENGTH;
		payload.limit(payloadSize).position(0);
		memory.putByteBuffer(address + PAYLOAD_SIZE_LENGTH, payload, payloadSize);
		return PAYLOAD_SIZE_LENGTH + payloadSize;
	}

	@Override
	public int readFrom(long address, Memory memory) {
		this.payloadSize = memory.getInt(address);
		payload.clear();
		this.invalidPayloadSize = payloadSize < 0 || payloadSize > payload.capacity();
		if (invalidPayloadSize) {
			payload.flip();
			return PAYLOAD_SIZE_LENGTH;
		}
		memory.getByteBuffer(address + PAYLOAD_SIZE_LENGTH, payload, payloadSize);
		payload.flip();
		return PAYLOAD_SIZE_LENGTH + payloadSize;
	}
}
