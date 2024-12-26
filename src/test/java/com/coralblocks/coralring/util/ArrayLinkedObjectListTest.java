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

import static org.junit.Assert.*;

import java.util.Iterator;

import org.junit.Before;
import org.junit.Test;

public class ArrayLinkedObjectListTest {

    private ArrayLinkedObjectList<String> list;
    private static final int ARRAY_SIZE = 3;  // Example array size

    @Before
    public void setUp() {
        list = new ArrayLinkedObjectList<>(ARRAY_SIZE);
    }

    /**
     * Test adding elements within the array capacity.
     * Ensure that these elements are correctly placed in the array portion.
     */
    @Test
    public void testAddLastWithinArray() {
        list.addLast("A");
        list.addLast("B");
        list.addLast("C");

        assertEquals("Size should be 3 after adding 3 elements", 3, list.size());

        // Validate by iterating
        Iterator<String> it = list.iterator();
        assertTrue("Should have next", it.hasNext()); 
        assertEquals("A", it.next());
        assertTrue("Should have next", it.hasNext());
        assertEquals("B", it.next());
        assertTrue("Should have next", it.hasNext());
        assertEquals("C", it.next());
        assertFalse("Should not have next", it.hasNext());
    }

    /**
     * Test adding elements beyond the array capacity.
     * The extra elements should go into the linked list portion.
     */
    @Test
    public void testAddLastBeyondArray() {
        // Fill up the array portion
        list.addLast("A");
        list.addLast("B");
        list.addLast("C");

        // Now these should go into the linked list
        list.addLast("D");
        list.addLast("E");

        assertEquals("Size should be 5 after adding 5 elements", 5, list.size());

        // Validate by iterating
        Iterator<String> it = list.iterator();
        assertTrue(it.hasNext()); assertEquals("A", it.next());
        assertTrue(it.hasNext()); assertEquals("B", it.next());
        assertTrue(it.hasNext()); assertEquals("C", it.next());
        assertTrue(it.hasNext()); assertEquals("D", it.next());
        assertTrue(it.hasNext()); assertEquals("E", it.next());
        assertFalse(it.hasNext());
    }

    /**
     * Test removing elements when the list is within array capacity.
     */
    @Test
    public void testRemoveLastWithinArray() {
        list.addLast("A");
        list.addLast("B");

        assertEquals(2, list.size());
        assertEquals("B", list.removeLast());
        assertEquals(1, list.size());
        assertEquals("A", list.removeLast());
        assertEquals(0, list.size());
        assertNull("Removing from empty list should return null", list.removeLast());
    }

    /**
     * Test removing elements when the list size exceeds the array capacity
     * (i.e., elements in the linked list).
     */
    @Test
    public void testRemoveLastBeyondArray() {
        // Fill array portion
        list.addLast("A");
        list.addLast("B");
        list.addLast("C");

        // Add into linked list portion
        list.addLast("D");
        list.addLast("E");

        assertEquals(5, list.size());
        
        // Remove from the linked portion first
        assertEquals("E", list.removeLast());
        assertEquals(4, list.size());
        
        assertEquals("D", list.removeLast());
        assertEquals(3, list.size());

        // Now we are back within the array portion
        assertEquals("C", list.removeLast());
        assertEquals("B", list.removeLast());
        assertEquals("A", list.removeLast());
        assertEquals(0, list.size());
        
        // Removing beyond empty
        assertNull("Removing from empty should return null", list.removeLast());
    }

    /**
     * Test removing last on an empty list.
     */
    @Test
    public void testRemoveLastOnEmpty() {
        assertEquals("Size should be 0 initially", 0, list.size());
        assertNull("Removing from empty list should return null", list.removeLast());
        assertEquals("Size should still be 0 after removing from empty", 0, list.size());
    }

    /**
     * Test the size method in different scenarios.
     */
    @Test
    public void testSize() {
        assertEquals(0, list.size());
        list.addLast("A");
        assertEquals(1, list.size());
        list.addLast("B");
        assertEquals(2, list.size());
        list.removeLast();
        assertEquals(1, list.size());
        list.removeLast();
        assertEquals(0, list.size());
    }

    /**
     * Test clearing the list.
     */
    @Test
    public void testClear() {
        // Clear an empty list
        list.clear(false);
        assertEquals(0, list.size());

        // Fill array portion
        list.addLast("A");
        list.addLast("B");
        list.addLast("C");
        // Add more (linked portion)
        list.addLast("D");
        list.addLast("E");

        assertEquals(5, list.size());
        list.clear(true);
        assertEquals("Size should be 0 after clear", 0, list.size());

        // Ensure subsequent additions work fine after clear
        list.addLast("X");
        list.addLast("Y");
        assertEquals(2, list.size());
    }

    /**
     * Test iteration over the list, including edge cases.
     */
    @Test
    public void testIterator() {
        // Initially empty
        Iterator<String> it = list.iterator();
        assertFalse("No elements, should not have next", it.hasNext());

        // Add some elements (within array limit)
        list.addLast("A");
        list.addLast("B");
        list.addLast("C");

        it = list.iterator();
        assertTrue(it.hasNext());
        assertEquals("A", it.next());
        assertTrue(it.hasNext());
        assertEquals("B", it.next());
        assertTrue(it.hasNext());
        assertEquals("C", it.next());
        assertFalse(it.hasNext());

        // Add beyond array limit
        list.addLast("D");
        list.addLast("E");
        it = list.iterator();

        // We expect A, B, C, D, E in that order
        assertTrue(it.hasNext());
        assertEquals("A", it.next());
        assertEquals("B", it.next());
        assertEquals("C", it.next());
        assertEquals("D", it.next());
        assertEquals("E", it.next());
        assertFalse(it.hasNext());
    }
}
