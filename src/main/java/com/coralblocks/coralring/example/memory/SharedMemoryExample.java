package com.coralblocks.coralring.example.memory;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.memory.SharedMemory;

public class SharedMemoryExample {
	
	public static void main(String[] args) {
		
		// 64 bytes = 8 longs, 16 ints, 32 shorts and 64 bytes
		final long size = 64; // 8 * 8 = 64
		
		final String mmapFileUsedAsBridge = "shared-memory.mmap";
		
		Memory memory1 = new SharedMemory(size, mmapFileUsedAsBridge);
		final long address1 = memory1.getPointer();
		
		Memory memory2 = new SharedMemory(size, mmapFileUsedAsBridge);
		final long address2 = memory2.getPointer();
		
		// Adding bytes
		for(int x = 0; x < size; x++) {
			memory1.putByte(address1 + x, (byte) x);
		}
		for(int x = 0; x < size; x++) {
			byte b = memory2.getByte(address2 + x);
			if (x > 0) System.out.print(",");
			System.out.print(b);
		}
		
		System.out.println();
		
		// Adding shorts
		for(int x = 0; x < size; x += 2) {
			memory2.putShort(address2 + x, (short) x);
		}
		for(int x = 0; x < size;  x += 2) {
			short s = memory1.getShort(address1 + x);
			if (x > 0) System.out.print(",");
			System.out.print(s);
		}
		
		System.out.println();
		
		// Adding ints
		for(int x = 0; x < size; x += 4) {
			memory1.putInt(address1 + x, x);
		}
		for(int x = 0; x < size;  x += 4) {
			int i = memory2.getInt(address2 + x);
			if (x > 0) System.out.print(",");
			System.out.print(i);
		}
		
		System.out.println();
		
		// Adding longs
		for(int x = 0; x < size; x += 8) {
			memory2.putLong(address2 + x, x);
		}
		for(int x = 0; x < size;  x += 8) {
			long l = memory1.getLong(address1 + x);
			if (x > 0) System.out.print(",");
			System.out.print(l);
		}
		
		System.out.println();
		
		memory2.release(false); // we'll delete the file below
		memory1.release(true); // true to delete the memory-mapped file
	}
}
