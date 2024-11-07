package com.coralblocks.coralring.memory;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
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

@SuppressWarnings("restriction")
public class SharedMemory implements Memory {
	
	// Long.MAX_VALUE = 9,223,372,036,854,775,807 bytes
	// Long.MAX_VALUE = 8,388,608 terabytes
	// Let's not go overboard and set a max of 4,194,304 terabytes (half of MAX_VALUE)
	public static final long MAX_SIZE = Long.MAX_VALUE / 2L;
	
	private static Unsafe unsafe;
	private static boolean UNSAFE_AVAILABLE = false;
	private static boolean MAP_UNMAP_AVAILABLE = false;
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
	
	private static boolean isNewSyncMap = false;
	private static boolean isJava21 = false;
	private static Method mmap;
	private static Method unmmap;
	private static final Field addressField;
	
	private static Method getMethod(Class<?> cls, String name, Class<?>... params) throws Exception {
		Method m = cls.getDeclaredMethod(name, params);
		m.setAccessible(true);
		return m;
	}
 
	static {
		
		Field addrField = null;
		
		try {
			try {
				mmap = getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class);
				isNewSyncMap = false;
			} catch(Exception e) {
				try {
					mmap = getMethod(FileChannelImpl.class, "map0", int.class, long.class, long.class, boolean.class);
					isNewSyncMap = true;
				} catch(Exception ee) {
					mmap = getMethod(FileChannelImpl.class, "map", MapMode.class, long.class, long.class);
					isJava21 = true;
				}
			}
			
			try {
				unmmap = getMethod(FileChannelImpl.class, "unmap0", long.class, long.class);
			} catch(Exception e) {
				unmmap = getMethod(FileChannelImpl.class, "unmap", MappedByteBuffer.class);
				isJava21 = true;
			}
			
			MAP_UNMAP_AVAILABLE = true;
			
			addrField = Buffer.class.getDeclaredField("address");
			addrField.setAccessible(true);
			
			ADDRESS_AVAILABLE = true;
			
		} catch (Exception e) {
			// throw exception later when we try to allocate memory in the constructor
		}
		
		addressField = addrField;
	}
	
	public static boolean isAvailable() {
		return UNSAFE_AVAILABLE && MAP_UNMAP_AVAILABLE && ADDRESS_AVAILABLE;
	}

	private final long pointer;
	private final long size;
	private final MappedByteBuffer mbb;
	private final String filename;
	
	public SharedMemory(long size) {
		this(size, createFilename(size));
	}
	
	public SharedMemory(long size, String filename) {
		
		if (!UNSAFE_AVAILABLE) {
			throw new IllegalStateException("sun.misc.Unsafe is not accessible!");
		}
		
		if (!MAP_UNMAP_AVAILABLE) {
			throw new IllegalStateException("Cannot get map and unmap methods from FileChannel through reflection!");
		}
		
		if (!ADDRESS_AVAILABLE) {
			throw new IllegalStateException("Cannot get address field from Buffer through reflection!");
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
						throw new RuntimeException("Cannot create store dir: " + fileDir + " for " + filename);
					}
				}
			}

			this.filename = filename;
			RandomAccessFile file = new RandomAccessFile(filename, "rw");
			file.setLength(size);
			FileChannel fileChannel = file.getChannel();
			if (isJava21) {
				this.mbb = (MappedByteBuffer) mmap.invoke(fileChannel, MapMode.READ_WRITE, 0L, this.size);
				this.pointer = (long) addressField.get(this.mbb);
			} else if (isNewSyncMap) {
				this.pointer = (long) mmap.invoke(fileChannel, 1, 0L, this.size, false);
				this.mbb = null;
			} else {
				this.pointer = (long) mmap.invoke(fileChannel, 1, 0L, this.size);
				this.mbb = null;
			}
			fileChannel.close();
			file.close();
		} catch(Exception e) {
			throw new RuntimeException("Cannot mmap shared memory!", e);
		}
	}
	
	private final static String createFilename(long size) {
		return SharedMemory.class.getSimpleName() + "-" + size + ".mmap";
	}
	
	public String getFilename() {
		return filename;
	}
	
	@Override
	public long getPointer() {
		return pointer;
	}

	@Override
	public void release(boolean deleteFileIfUsed) {
		try {
			if (isJava21) {
				unmmap.invoke(null, this.mbb);
			} else {
				unmmap.invoke(null, pointer, size);
			}
		} catch(Exception e) {
			throw new RuntimeException("Cannot release mmap shared memory!", e);
		} finally {
			if (deleteFileIfUsed) deleteFile();
		}
	}
	
	private void deleteFile() {
		Path path = Paths.get(filename);
        try {
            Files.deleteIfExists(path); // if someone else deleted it
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete the file: " + filename, e);
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
			throw new RuntimeException("putByteBuffer can only take a direct byte buffer!");
		}
		try {
			long srcPointer = (long) addressField.get(src);
			unsafe.copyMemory(srcPointer, address, len);
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void getByteBuffer(long address, ByteBuffer dst, int len) {
		if (!dst.isDirect()) {
			throw new RuntimeException("getByteBuffer can only take a direct byte buffer!");
		}
		try {
			long dstPointer = (long) addressField.get(dst);
			dstPointer += dst.position();
			unsafe.copyMemory(address, dstPointer, len);
			dst.position(dst.position() + len);
		} catch(Exception e) {
			throw new RuntimeException(e);
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