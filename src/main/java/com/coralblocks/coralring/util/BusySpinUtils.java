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
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.coralblocks.coralring.util;

public final class BusySpinUtils {

	private BusySpinUtils() {
	}

	public static void waitFor(long nanos) {
		
		Thread thread = Thread.currentThread();

		if (nanos <= 0 || thread.isInterrupted()) return;

		long start = System.nanoTime();

		while (System.nanoTime() - start < nanos) {
			if (thread.isInterrupted()) return;
			Thread.onSpinWait();
		}
	}
}
