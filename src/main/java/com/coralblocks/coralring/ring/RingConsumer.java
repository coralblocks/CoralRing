package com.coralblocks.coralring.ring;

import com.coralblocks.coralring.memory.Memory;
import com.coralblocks.coralring.util.Builder;
import com.coralblocks.coralring.util.MemorySerializable;

public interface RingConsumer<E extends MemorySerializable> {
	
	public Memory getMemory();
	
	public Builder<E> getBuilder();
	
	public long getLastPolledSequence();
	
	//TODO: public E getSequence(long sequence);
	
	public long availableToPoll();
	
	public E poll();
	
	public E peek();
	
	public void rollBack();
	
	public void rollBack(long count);
	
	public void donePolling();
	
	public void close(boolean deleteFile);
	
}