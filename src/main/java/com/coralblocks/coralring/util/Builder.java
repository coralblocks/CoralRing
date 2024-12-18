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
 * An interface that knows how to create instances of a class
 * 
 * @param <E> the class of the object that will be built
 */
public interface Builder<E> {

	/**
	 * Return a new instance of the class
	 * 
	 * @return a new instance
	 */
	public E newInstance();
	
	/**
	 * Convenient method to create a builder from a class through its default (no-arguments) constructor.
	 * 
	 * @param <E> the class of the object that will be built
	 * @param klass the class of the object that will be built
	 * @return a new builder for the given class
	 */
	public static <E> Builder<E> createBuilder(final Class<E> klass) {
		return new Builder<E>() {
			@Override
            public E newInstance() {
	            try {
	            	return klass.getDeclaredConstructor().newInstance();
	            } catch(Exception e) {
	            	throw new RuntimeException(e);
	            }
            }
		};
	}
}