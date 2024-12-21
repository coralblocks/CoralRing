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
 * A fast and garbage-free double-linked list.
 * 
 * <p><b>NOTE:</b> This data structure is designed on purpose to be used by <b>single-threaded systems</b>, in other words, 
 *  it will break if used concurrently by multiple threads.</p>
 * 
 * @param <E> the type of objects this object list will hold
 */
public class LinkedObjectList<E> implements Iterable<E> {

	private static class Entry<E> {
		E value = null;
		Entry<E> next = null;
		Entry<E> prev = null;
	}
	
	private Entry<E> poolHead = null;
	
	private Entry<E> head = null;
	private Entry<E> tail = null;
	private int size = 0;

	/**
	 * Creates a LinkedObjectList
	 * 
	 * @param initialCapacity the initial number of preallocated internal entries
	 */
	public LinkedObjectList(int initialCapacity) {
		for(int i = 0; i < initialCapacity; i++) {
			releaseEntryBackToPool(new Entry<E>());
		}
	}
	
	private Entry<E> getEntryFromPool() {
		if (poolHead != null) {
			Entry<E> toReturn = poolHead;
			poolHead = poolHead.next;
			toReturn.next = null;
			toReturn.prev = null;
			return toReturn;
		} else {
			return new Entry<E>();
		}
	}
	
	private void releaseEntryBackToPool(Entry<E> entry) {
		entry.value = null;
		entry.prev = null; // the pool does not need/use this reference for anything...
		entry.next = poolHead;
		poolHead = entry;
	}
	
	/**
	 * Clears this list. The list will be empty after this operation.
	 */
	public void clear() {
		while(head != null) {
			Entry<E> saveNext = head.next;
			releaseEntryBackToPool(head);
			head = saveNext;
		}
		tail = null;
		size = 0;
	}
	
	/**
	 * Adds an element to the head of the list.
	 * 
	 * @param value the value to be added
	 */
	public void addFirst(E value) {
		Entry<E> entry = getEntryFromPool();
		entry.value = value;
		if (head == null) {
			// entry.next = null; // redundant
			// entry.prev = null; // redundant
			head = entry;
			tail = entry;
		} else {
			// entry.prev = null; // redundant
			entry.next = head;
			head.prev = entry;
			head = entry;
		}
		size++;
	}
	
	/**
	 * Adds an element to the tail of the list.
	 * 
	 * @param value the value to be added
	 */
	public void addLast(E value) {
		Entry<E> entry = getEntryFromPool();
		entry.value = value;
		if (tail == null) {
			// entry.next = null; // redundant
			// entry.prev = null; // redundant
			tail = entry;
			head = entry;
		} else {
			// entry.next = null; // redundant
			entry.prev = tail;
			tail.next = entry;
			tail = entry;
		}
		size++;
	}
	
	/**
	 * Returns the element on the head of the list.
	 * 
	 * @return the first element in the list or null if the list is empty
	 */
	public E first() {
		if (head == null) return null;
		return head.value;
	}
	
	/**
	 * Removes the element from the head of the list.
	 * 
	 * @return the first element from the list that was removed or null if the list is empty
	 */
	public E removeFirst() {
		if (head == null) return null;
		Entry<E> entry = head;
		head = head.next;
		if (head != null) head.prev = null;
		E toReturn = entry.value;
		releaseEntryBackToPool(entry);
		if (--size == 0) tail = null;
		return toReturn;
	}
	
	
	/**
	 * Returns the element on the tail of the list.
	 * 
	 * @return the last element in the list or null if the list is empty
	 */
	public E last() {
		if (tail == null) return null;
		return tail.value;
	}
	
	/**
	 * Removes the element from the tail of the list.
	 * 
	 * @return the last element from the list that was removed or null if the list is empty
	 */
	public E removeLast() {
		if (tail == null) return null;
		Entry<E> entry = tail;
		tail = tail.prev;
		if (tail != null) tail.next = null;
		E toReturn = entry.value;
		releaseEntryBackToPool(entry);
		if (--size == 0) head = null;
		return toReturn;
	}
	
	/**
	 * Is this list empty? (with size 0)
	 * 
	 * @return true if empty
	 */
	public boolean isEmpty() {
		return size == 0;
	}
	
	/**
	 * Returns the number of elements currently present in the list
	 * 
	 * @return the number of elements currently present in the list
	 */
	public int size() {
		return size;
	}
	
	private class ReusableIterator implements Iterator<E> {

		Entry<E> start;
		Entry<E> curr;

		public void reset() {
			this.start = head;
			this.curr = null;
		}

		@Override
		public final boolean hasNext() {
			return start != null;
		}

		@Override
		public final E next() {

			this.curr = start;
			
			E toReturn = start.value;
			
			start = start.next;

			return toReturn;
		}

		@Override
		public final void remove() {
			
			boolean isTail = curr == tail;
			boolean isHead = curr == head;

			if (isTail) {
				removeLast();
			} else if (isHead) {
				removeFirst();
			} else {
				curr.prev.next = curr.next;
				curr.next.prev = curr.prev;
				releaseEntryBackToPool(curr);
				size--;
			}
			curr = null;
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
