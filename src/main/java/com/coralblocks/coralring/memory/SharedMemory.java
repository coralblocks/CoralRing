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
package com.coralblocks.coralring.memory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import sun.misc.Unsafe;
import sun.nio.ch.FileChannelImpl;

/**
 * This class employs many different reflection tricks and <code>sun.misc.Unsafe</code> to allocate, access and release native memory in Java through memory-mapped files.
 */
public class SharedMemory implements Memory {
	
	// Long.MAX_VALUE = 9,223,372,036,854,775,807 bytes
	// Long.MAX_VALUE = 8,388,608 terabytes
	// Let's not go overboard and set a max of 4,194,304 terabytes (half of MAX_VALUE)
	public static final long MAX_SIZE = Long.MAX_VALUE / 2L;

	private static final String REQUIRED_JVM_FLAGS = "--add-opens java.base/sun.nio.ch=ALL-UNNAMED "
			+ "--add-opens java.base/java.nio=ALL-UNNAMED";
	private static final String JVM_ACCESS_HELP = " Ensure this is a supported JDK and start the JVM with: "
			+ REQUIRED_JVM_FLAGS;
	
	private static Unsafe unsafe;
	private static boolean UNSAFE_AVAILABLE = false;
	private static boolean ADDRESS_AVAILABLE = false;
	
	static {
		try {
			Field field = Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			unsafe = (Unsafe) field.get(null);
			UNSAFE_AVAILABLE = true;
		} catch(Exception e) {
			// throw exception later when we try to allocate memory in the constructor
		}
    }
	
	private enum MappingStrategy {
		MAP0_LEGACY,
		MAP0_SYNC,
		PUBLIC_MAP_UNMAP_BUFFER
	}

	private static final MappingStrategy mappingStrategy;
	private static final Method mmap;
	private static final Method unmmap;
	private static final Field addressField;
	
	private static Method getMethod(Class<?> cls, String name, Class<?>... params) throws Exception {
		Method m = cls.getDeclaredMethod(name, params);
		m.setAccessible(true);
		return m;
	}
 
	static {
		
		MappingStrategy strategy = null;
		Method mapMethod = null;
		Method unmapMethod = null;
		Field addrField = null;
		
		try {
			try {
				mapMethod = getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class);
				unmapMethod = getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);
				strategy = MappingStrategy.MAP0_LEGACY;
			} catch(Exception e) {
				try {
					mapMethod = getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class, boolean.class);
					unmapMethod = getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);
					strategy = MappingStrategy.MAP0_SYNC;
				} catch(Exception ee) {
					mapMethod = null;
					unmapMethod = getMethod(FileChannelImpl.class, "unmap", MappedByteBuffer.class);
					strategy = MappingStrategy.PUBLIC_MAP_UNMAP_BUFFER;
				}
			}
			
			addrField = Buffer.class.getDeclaredField("address");
			addrField.setAccessible(true);
			
			ADDRESS_AVAILABLE = true;
			
		} catch (Exception | LinkageError e) {
			// throw exception later when we try to allocate memory in the constructor
		}
		
		mappingStrategy = strategy;
		mmap = mapMethod;
		unmmap = unmapMethod;
		addressField = addrField;
	}
	
	/**
	 * Returns true is this class can be used and is available in your platform
	 * 
	 * @return true if available
	 */
	public static boolean isAvailable() {
		return UNSAFE_AVAILABLE && mappingStrategy != null && ADDRESS_AVAILABLE;
	}

	private final long address;
	private final long size;
	private final MappedByteBuffer mbb;
	private final String filename;
	private boolean released;
	
	/**
	 * Creates a shared memory with the given size. The filename will be implied.
	 * 
	 * @param size the size of the memory
	 */
	public SharedMemory(long size) {
		this(size, createFilename(size));
	}
	
	/**
	 * Creates a shared memory with the given filename. The size will be implied from the file.
	 * 
	 * @param filename the name of the memory mapped file containing this memory
	 */
	public SharedMemory(String filename) {
		this(-1, filename);
	}
	
	/**
	 * Creates a shared memory with the given size and filename.
	 * 
	 * @param size the size of the memory or -1 to imply from the file
	 * @param filename the name of the file
	 */
	public SharedMemory(long size, String filename) {
		if (filename == null) {
			throw new IllegalArgumentException("filename cannot be null");
		}
		
		if (!UNSAFE_AVAILABLE) {
			throw new IllegalStateException("sun.misc.Unsafe is not accessible!" + JVM_ACCESS_HELP);
		}
		
		if (mappingStrategy == null) {
			throw new IllegalStateException("Cannot get map and unmap methods from FileChannel through reflection!"
					+ JVM_ACCESS_HELP);
		}
		
		if (!ADDRESS_AVAILABLE) {
			throw new IllegalStateException("Cannot get address field from Buffer through reflection!" + JVM_ACCESS_HELP);
		}
		
		if (size == -1) {
			size = findFileSize(filename);
		} else if (size <= 0) {
			throw new IllegalArgumentException("Invalid size: " + size);
		}
		
		if (size > MAX_SIZE) throw new IllegalArgumentException("This size is not supported: " + size + " (MAX = " + MAX_SIZE + ")");
		
		this.size = size;
		
		try {
			
			int index = filename.lastIndexOf(File.separatorChar);
			
			if (index > 0) {
				String fileDir = filename.substring(0, index);
				File file = new File(fileDir);
				if (!file.exists()) {
					if (!file.mkdirs()) {
						throw new IllegalStateException("Cannot create store dir: " + fileDir + " for " + filename);
					}
				}
			}

			this.filename = filename;
			try (RandomAccessFile file = new RandomAccessFile(filename, "rw");
					FileChannel fileChannel = file.getChannel()) {
				long fileSize = file.length();
				if (fileSize == 0) {
					file.setLength(size);
				} else if (fileSize != size) {
					throw new IllegalArgumentException("Shared memory file size mismatch for " + filename
							+ ": expected " + size + " bytes but found " + fileSize + " bytes");
				}
				switch(mappingStrategy) {
					case MAP0_LEGACY:
						this.address = (long) mmap.invoke(fileChannel, 1, 0L, this.size);
						this.mbb = null;
						break;
					case MAP0_SYNC:
						this.address = (long) mmap.invoke(fileChannel, 1, 0L, this.size, false);
						this.mbb = null;
						break;
					case PUBLIC_MAP_UNMAP_BUFFER:
						this.mbb = fileChannel.map(MapMode.READ_WRITE, 0L, this.size);
						this.address = (long) addressField.get(this.mbb);
						break;
					default:
						throw new IllegalStateException("Unsupported mapping strategy: " + mappingStrategy);
				}
			}
		} catch(IllegalArgumentException | IllegalStateException e) {
			throw e;
		} catch(Exception e) {
			throw new IllegalStateException("Cannot mmap shared memory: " + filename, e);
		}
	}
	
	/**
	 * Return the size in bytes of the given file.
	 * 
	 * @param filename the name of the file
	 * @return the size in bytes of the file
	 */
	public static final long findFileSize(String filename) {
		if (filename == null) throw new IllegalArgumentException("filename cannot be null");
		File file = new File(filename);
		if (!file.exists()) throw new IllegalArgumentException("File not found: " + filename);
		if (file.isDirectory()) throw new IllegalArgumentException("File is a directory: " + filename);
		return file.length();
	}
	
	private static final String createFilename(long size) {
		if (size <= 0) throw new IllegalArgumentException("Cannot create file with this size: " + size);
		return SharedMemory.class.getSimpleName() + "-" + size + ".mmap";
	}
	
	/**
	 * Return the name of the file containing this memory.
	 * 
	 * @return the name of the file
	 */
	public String getFilename() {
		return filename;
	}
	
	@Override
	public long getSize() {
		return size;
	}
	
	@Override
	public long getPointer() {
		return address;
	}

	@Override
	public synchronized void release(boolean deleteFileIfUsed) {
		if (released) return;
		released = true;

		RuntimeException firstException = null;
		try {
			switch(mappingStrategy) {
				case MAP0_LEGACY:
				case MAP0_SYNC:
					unmmap.invoke(null, address, size);
					break;
				case PUBLIC_MAP_UNMAP_BUFFER:
					unmmap.invoke(null, this.mbb);
					break;
				default:
					throw new IllegalStateException("Unsupported mapping strategy: " + mappingStrategy);
			}
		} catch(Exception e) {
			firstException = new IllegalStateException("Cannot release mmap shared memory!", e);
		}

		if (deleteFileIfUsed) {
			try {
				deleteFile();
			} catch (RuntimeException e) {
				if (firstException == null) {
					firstException = e;
				} else {
					firstException.addSuppressed(e);
				}
			}
		}

		if (firstException != null) throw firstException;
	}
	
	private void deleteFile() {
		Path path = Paths.get(filename);
        try {
            Files.deleteIfExists(path); // if someone else deleted it
        } catch (IOException e) {
			throw new UncheckedIOException("Failed to delete the file: " + filename, e);
        }
	}

	@Override
	public byte getByte(long address) {
		return unsafe.getByte(address);
	}

	@Override
	public byte getByteVolatile(long address) {
		return unsafe.getByteVolatile(null, address);
	}
 
	@Override
	public int getInt(long address) {
		return unsafe.getInt(address);
	}

	@Override
	public int getIntVolatile(long address) {
		return unsafe.getIntVolatile(null, address);
	}

	@Override
	public long getLong(long address) {
		return unsafe.getLong(address);
	}
	
	@Override
	public long getLongVolatile(long address) {
		return unsafe.getLongVolatile(null, address);
	}
	
	@Override
	public void putByte(long address, byte val) {
		unsafe.putByte(address, val);
	}
	
	@Override
	public void putByteVolatile(long address, byte val) {
		unsafe.putByteVolatile(null, address, val);
	}

	@Override
	public void putInt(long address, int val) {
		unsafe.putInt(address, val);
	}

	@Override
	public void putIntVolatile(long address, int val) {
		unsafe.putIntVolatile(null, address, val);
	}

	@Override
	public void putLong(long address, long val) {
		unsafe.putLong(address, val);
	}
	
	@Override
	public void putLongVolatile(long address, long val) {
		unsafe.putLongVolatile(null, address, val);
	}

	@Override
	public short getShort(long address) {
		return unsafe.getShort(null, address);
	}

	@Override
	public void putShort(long address, short value) {
		unsafe.putShort(null, address, value);
	}

	@Override
	public short getShortVolatile(long address) {
		return unsafe.getShortVolatile(null, address);
	}

	@Override
	public void putShortVolatile(long address, short value) {
		unsafe.putShortVolatile(null, address, value);
	}

	@Override
	public void putByteBuffer(long address, ByteBuffer src, int len) {
		if (!src.isDirect()) {
			throw new IllegalArgumentException("putByteBuffer can only take a direct byte buffer!");
		}
		if (len < 0 || len > src.remaining()) {
			throw new IllegalArgumentException("Invalid length: " + len + " (remaining=" + src.remaining() + ")");
		}
		try {
			long srcAddress = (long) addressField.get(src);
			srcAddress += src.position();
			unsafe.copyMemory(srcAddress, address, len);
			src.position(src.position() + len);
		} catch(Exception e) {
			throw new IllegalStateException("Cannot access direct source buffer address", e);
		}
	}

	@Override
	public void getByteBuffer(long address, ByteBuffer dst, int len) {
		if (!dst.isDirect()) {
			throw new IllegalArgumentException("getByteBuffer can only take a direct byte buffer!");
		}
		if (len < 0 || len > dst.remaining()) {
			throw new IllegalArgumentException("Invalid length: " + len + " (remaining=" + dst.remaining() + ")");
		}
		try {
			long dstAddress = (long) addressField.get(dst);
			dstAddress += dst.position();
			unsafe.copyMemory(address, dstAddress, len);
			dst.position(dst.position() + len);
		} catch(Exception e) {
			throw new IllegalStateException("Cannot access direct destination buffer address", e);
		}
	}

	@Override
	public void putByteArray(long address, byte[] src, int len) {
		unsafe.copyMemory(src, Unsafe.ARRAY_BYTE_BASE_OFFSET, null, address, len);
	}

	@Override
	public void getByteArray(long address, byte[] dst, int len) {
		unsafe.copyMemory(null, address, dst, Unsafe.ARRAY_BYTE_BASE_OFFSET, len);
	}
	
}
