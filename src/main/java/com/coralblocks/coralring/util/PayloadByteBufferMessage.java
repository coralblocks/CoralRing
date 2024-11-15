package com.coralblocks.coralring.util;

import java.nio.ByteBuffer;

import com.coralblocks.coralring.memory.Memory;

public class PayloadByteBufferMessage implements MemorySerializable {
	
	private static final boolean DEFAULT_USE_DIRECT_BYTE_BUFFER = true;
	
	public int payloadLength;
	public final ByteBuffer payload;
	
	public final static int getMaxSize(int maxPayloadSize) {
		return 4 /* payloadLength int size */ + maxPayloadSize;
	}
	
	public PayloadByteBufferMessage(int maxPayloadSize, boolean useDirectByteBuffer) {
		if (useDirectByteBuffer) {
			this.payload = ByteBuffer.allocateDirect(maxPayloadSize);
		} else {
			this.payload = ByteBuffer.allocate(maxPayloadSize);
		}
	}
	
	public PayloadByteBufferMessage(int maxPayloadSize) {
		this(maxPayloadSize, DEFAULT_USE_DIRECT_BYTE_BUFFER);
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