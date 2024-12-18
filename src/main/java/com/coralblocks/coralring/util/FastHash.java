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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * <pre>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.

 * Based on XXHash from Apache Drill

 * https://github.com/apache/drill/blob/master/exec/java-exec/src/main/java/org/apache/drill/exec/expr/fn/impl/XXHash.java
 * </pre>
 */
public final class FastHash {

	static final long PRIME64_1 = 0x9e3779b185ebca87L;
	static final long PRIME64_2 = 0xc2b2ae3d27d4eb4fL;
	static final long PRIME64_3 = 0x165667b19e3779f9L;
	static final long PRIME64_4 = 0x85ebca77c2b2ae63L;
	static final long PRIME64_5 = 0x27d4eb2f165667c5L;

	private static final long hash64bytes(final ByteBuffer buffer, long seed) {

		final int start = buffer.position();
		final int bEnd = buffer.limit();
		final ByteOrder order = buffer.order();

		long len = bEnd - start;
		long h64;
		int p = start;

		if (len >= 32) {
			
			final long limit = bEnd - 32;
			
			long v1 = seed + PRIME64_1 + PRIME64_2;
			long v2 = seed + PRIME64_2;
			long v3 = seed + 0;
			long v4 = seed - PRIME64_1;

			do {
				v1 += buffer.getLong(p) * PRIME64_2;
				p = p + 8;
				v1 = Long.rotateLeft(v1, 31);
				v1 *= PRIME64_1;

				v2 += buffer.getLong(p) * PRIME64_2;
				p = p + 8;
				v2 = Long.rotateLeft(v2, 31);
				v2 *= PRIME64_1;

				v3 += buffer.getLong(p) * PRIME64_2;
				p = p + 8;
				v3 = Long.rotateLeft(v3, 31);
				v3 *= PRIME64_1;

				v4 += buffer.getLong(p) * PRIME64_2;
				p = p + 8;
				v4 = Long.rotateLeft(v4, 31);
				v4 *= PRIME64_1;
				
			} while (p <= limit);

			h64 = Long.rotateLeft(v1, 1) + Long.rotateLeft(v2, 7) + Long.rotateLeft(v3, 12) + Long.rotateLeft(v4, 18);

			v1 *= PRIME64_2;
			v1 = Long.rotateLeft(v1, 31);
			v1 *= PRIME64_1;
			h64 ^= v1;

			h64 = h64 * PRIME64_1 + PRIME64_4;

			v2 *= PRIME64_2;
			v2 = Long.rotateLeft(v2, 31);
			v2 *= PRIME64_1;
			h64 ^= v2;

			h64 = h64 * PRIME64_1 + PRIME64_4;

			v3 *= PRIME64_2;
			v3 = Long.rotateLeft(v3, 31);
			v3 *= PRIME64_1;
			h64 ^= v3;

			h64 = h64 * PRIME64_1 + PRIME64_4;

			v4 *= PRIME64_2;
			v4 = Long.rotateLeft(v4, 31);
			v4 *= PRIME64_1;
			h64 ^= v4;

			h64 = h64 * PRIME64_1 + PRIME64_4;
			
		} else {
			
			h64 = seed + PRIME64_5;
		}

		h64 += len;

		while (p + 8 <= bEnd) {
			long k1 = buffer.getLong(p);
			k1 *= PRIME64_2;
			k1 = Long.rotateLeft(k1, 31);
			k1 *= PRIME64_1;
			h64 ^= k1;
			h64 = Long.rotateLeft(h64, 27) * PRIME64_1 + PRIME64_4;
			p += 8;
		}

		if (p + 4 <= bEnd) {
			buffer.order(ByteOrder.LITTLE_ENDIAN);
			long finalInt = buffer.getInt(p);
			buffer.order(order);
			h64 ^= finalInt * PRIME64_1;
			h64 = Long.rotateLeft(h64, 23) * PRIME64_2 + PRIME64_3;
			p += 4;
		}
		
		while (p + 1 <= bEnd) {
			h64 ^= ((long) (buffer.get(p) & 0x00ff)) * PRIME64_5;
			h64 = Long.rotateLeft(h64, 11) * PRIME64_1;
			p++;
		}

		return applyFinalHashComputation(h64);
	}

	private static final long applyFinalHashComputation(long h64) {
		h64 ^= h64 >>> 33;
		h64 *= PRIME64_2;
		h64 ^= h64 >>> 29;
		h64 *= PRIME64_3;
		h64 ^= h64 >>> 32;
		return h64;
	}
	
	public static final int SEED = 7;
	
	public static final long hash64(ByteBuffer buffer) {
		return hash64(buffer, SEED);
	}
	
	public static final long hash64(ByteBuffer buffer, long seed) {
		return hash64bytes(buffer, seed);
	}

	public static final int hash32(ByteBuffer buffer) {
		return hash32(buffer, SEED);
	}
	
	public static final int hash32(ByteBuffer buffer, int seed) {
		return convert64To32(hash64(buffer, seed));
	}
	
	private static final int convert64To32(long val) {
		return (int) (val & 0x00FFFFFFFF);
	}
}