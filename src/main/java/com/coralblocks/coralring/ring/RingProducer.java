package com.coralblocks.coralring.ring;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.MemorySerializable;

public interface RingProducer<E extends MemorySerializable> {
	
	public Memory getMemory();
	
	public Builder<E> getBuilder();
	
	public long getLastOfferedSequence();
	
	public E nextToDispatch();
	
	public void flush();
	
	public void close(boolean deleteFile);
	
}