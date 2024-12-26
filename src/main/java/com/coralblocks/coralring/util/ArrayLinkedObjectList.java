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

import java.util.Iterator;

/**
 * A hybrid list implementation that stores elements in a fixed-size array first,
 * and then overflows to a linked list as needed.
 * 
 * <p>This class is designed to provide efficient performance for lists that often
 * remain within a specified capacity, but can still handle growth beyond that
 * capacity. Elements are initially added to an internal array; if the list size
 * exceeds the array capacity, subsequent elements are stored in an underlying
 * {@link LinkedObjectList}.</p>
 * 
 *  <p><b>NOTE:</b> This data structure is designed on purpose to be used by <b>single-threaded systems</b>, in other words, 
 *  it will break if used concurrently by multiple threads.</p>
 * 
 * @param <E> the type of elements in this list
 */
public class ArrayLinkedObjectList<E> implements Iterable<E> {
	
	private final E[] array;
	private final LinkedObjectList<E> linkedList;
	private int count;
	
    /**
     * Constructs a new <code>ArrayLinkedObjectList</code> with a specified array size.
     * This determines the capacity of the array portion. If the number of elements
     * added exceeds this capacity, additional elements are stored in the linked list.
     *
     * @param arraySize the fixed capacity for the array portion
     */
	@SuppressWarnings("unchecked")
	public ArrayLinkedObjectList(int arraySize) {
		this.array = (E[]) new Object[arraySize];
		this.linkedList = new LinkedObjectList<E>(arraySize);
	}
	
    /**
     * Adds a new element at the end of the list. If the current size is still
     * within the array's capacity, the element is placed into the array.
     * Otherwise, it is stored in the underlying linked list.
     *
     * @param entry the element to be added
     */
	public final void addLast(E entry) {
		if (count >= array.length) {
			linkedList.addLast(entry);
		} else {
			array[count] = entry;
		}
		count++;
	}
	
    /**
     * Removes and returns the last element in the list. If the current size is
     * within the array's capacity, the element is removed from the array portion.
     * Otherwise, it is removed from the underlying linked list. If the list
     * is empty, this method returns null.
     *
     * @return the removed element, or null if the list is empty
     */
	public final E removeLast() {
		if (count == 0) return null;
		E toReturn;
		if (count > array.length) {
			toReturn = linkedList.removeLast();
		} else {
			int index = count - 1;
			toReturn = array[index];
			array[index] = null;
		}
		count--;
		return toReturn;
	}
	
    /**
     * Returns the total number of elements currently in the list.
     *
     * @return the number of elements in this list
     */
	public final int size() {
		return count;
	}
	
    /**
     * Removes all elements from this list. If the list size exceeds the array
     * capacity, the overflow elements are cleared from the linked list.
     * 
     * @param nullifyArray true if you want to set the added array elements to null
     */
	public final void clear(boolean nullifyArray) {
		if (count > array.length) linkedList.clear();
		if (nullifyArray) {
			int max = Math.min(array.length, count);
			for(int i = 0; i < max; i++) array[i] = null;
		}
		count = 0;
	}
	
	private class ReusableIterator implements Iterator<E> {

		private int counter;
		private Iterator<E> iter;
		
		public void reset() {
			this.counter = 0;
			this.iter = linkedList.iterator();
		}

		@Override
		public final boolean hasNext() {
			return counter < count;
		}

		@Override
		public final E next() {
			
			E toReturn;
			
			if (counter < array.length) {
				toReturn = array[counter];
			} else {
				toReturn = iter.next();
			}
			
			counter++;

			return toReturn;
		}

		@Override
		public final void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	private ReusableIterator reusableIter = new ReusableIterator();
	
	/**
	 * Return the same iterator instance (garbage-free operation)
	 * 
	 * @return the same instance of the iterator
	 */
	@Override
	public Iterator<E> iterator() {
		reusableIter.reset();
		return reusableIter;
	}
	
}
