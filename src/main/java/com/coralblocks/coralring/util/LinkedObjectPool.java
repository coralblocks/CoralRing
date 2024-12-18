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
 * An object pool backed up by an internal linked list. Note that instances will be created on demand if the pool runs out of instances.
 * 
 * <p><b>NOTE:</b> This data structure is designed on purpose to be used by <b>single-threaded systems</b>, in other words, 
 *  it will break if used concurrently by multiple threads.</p>
 * 
 * @param <E> the type of objects this object pool will hold
 */
public class LinkedObjectPool<E> implements ObjectPool<E> {

	private final LinkedObjectList<E> queue;

	private final Builder<E> builder;

	/**
	 * Creates a LinkedObjectPool.
	 * 
	 * @param initialSize the initial size of the pool (how many instance it will initially have)
	 * @param klass the class with a default constructor that will be used to create the instances
	 */
	public LinkedObjectPool(int initialSize, final Class<E> klass) {

		this(initialSize, new Builder<E>() {
			@Override
			public E newInstance() {
				try {
					return klass.getDeclaredConstructor().newInstance();
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
	}

	/**
	 * Creates a LinkedObjectPool.
	 * 
	 * @param initialSize the initial size of the pool (how many instance it will initially have)
	 * @param builder the builder that will be used to create the instances
	 */
	public LinkedObjectPool(int initialSize, Builder<E> builder) {

		this.builder = builder;

		this.queue = new LinkedObjectList<E>(initialSize);

		for (int i = 0; i < initialSize; i++) {
			queue.addLast(builder.newInstance());
		}
	}
	
	/**
	 * The number of instance currently inside this pool. Note that if all the instances are checked-out from this pool, the size returned will be zero.
	 * 
	 * @return the number of instance currently sitting inside this pool (and not checked-out by anyone)
	 */
	public int size() {
		return queue.size();
	}

	/**
	 * Note that if the pool is empty, this method will instantiate and return a new instance. This will cause the pool to grow when this extra instance is
	 * returned back to the pool.
	 * 
	 * @return an instance from the pool
	 */
	@Override
	public E get() {

		if (queue.isEmpty()) {

			return builder.newInstance();

		}
		
		return queue.removeLast();
	}

	/**
	 * Returns an instance back to the pool. Note that this method can be used to grow the pool if the instance released was not in the pool in the first place.
	 * 
	 * @param e the instance to return back to the pool (i.e. release to the pool)
	 */
	@Override
	public void release(E e) {

		queue.addLast(e);
	}
	
}
