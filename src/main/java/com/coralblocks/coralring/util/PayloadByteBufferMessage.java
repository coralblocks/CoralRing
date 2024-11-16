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

import java.nio.ByteBuffer;

import com.coralblocks.coralring.memory.Memory;

public class PayloadByteBufferMessage implements MemorySerializable {
	
	public int payloadLength;
	public final ByteBuffer payload;
	
	public final static int getMaxSize(int maxPayloadSize) {
		return 4 /* payloadLength int size */ + maxPayloadSize;
	}
	
	public PayloadByteBufferMessage(int maxPayloadSize) {
		this.payload = ByteBuffer.allocateDirect(maxPayloadSize);
	}
	
	@Override
	public void writeTo(long pointer, Memory memory) {
		memory.putInt(pointer, payloadLength);
		payload.limit(payloadLength).position(0);
		memory.putByteBuffer(pointer + 4, payload, payloadLength);
	}

	@Override
	public void readFrom(long pointer, Memory memory) {
		this.payloadLength = memory.getInt(pointer);
		payload.clear();
		memory.getByteBuffer(pointer + 4, payload, payloadLength);
		payload.flip();
	}
	
	
}