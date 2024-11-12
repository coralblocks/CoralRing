package com.coralblocks.coralring.example.ring;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.util.MemorySerializable;

public class Message implements MemorySerializable {
	
	long value; // 8 bytes
	boolean last; // 1 byte
	
	public final static int getMaxSize() {
		return 8 + 1;
	}
	
	@Override
	public void writeTo(long pointer, Memory memory) {
		memory.putLong(pointer, value);
		memory.putByte(pointer + 8, last ? (byte) 'Y' : (byte) 'N');
	}
	
	@Override
	public void readFrom(long pointer, Memory memory) {
		this.value = memory.getLong(pointer);
		this.last = memory.getByte(pointer + 8) == 'Y';
	}

	@Override
	public String toString() {
		return "Message [value=" + value + ", last=" + last + "]";
	}
}