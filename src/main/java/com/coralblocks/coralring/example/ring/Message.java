package com.coralblocks.coralring.example.ring;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.util.MemorySerializable;

public class Message implements MemorySerializable {
	
	private final static int SIZE = 8 + 1;
	
	public long value; // 8 bytes
	public boolean last; // 1 byte
	
	public final static int getMaxSize() {
		return SIZE;
	}
	
	@Override
	public int writeTo(long pointer, Memory memory) {
		memory.putLong(pointer, value);
		memory.putByte(pointer + 8, last ? (byte) 'Y' : (byte) 'N');
		return SIZE;
	}
	
	@Override
	public int readFrom(long pointer, Memory memory) {
		this.value = memory.getLong(pointer);
		this.last = memory.getByte(pointer + 8) == 'Y';
		return SIZE;
	}

	@Override
	public String toString() {
		return "Message [value=" + value + ", last=" + last + "]";
	}
}