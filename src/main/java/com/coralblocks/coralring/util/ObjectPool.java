/* 
 * Copyright 2015-2024 (c) CoralBlocks - http://www.coralblocks.com
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

/**
 * The basic contract of a simple object pool
 *
 * @param <E> the type of the objects in the pool
 */
public interface ObjectPool<E> {

	/**
	 * Returns an instance from the pool
	 * 
	 * @return the instance from the pool
	 */
	public E get();

	/**
	 * Releases an instance back to the pool
	 * 
	 * @param e the instance to return to the pool
	 */
	public void release(E e);
}
