package org.droolsassert.util;

import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import static org.apache.commons.io.FileUtils.forceMkdirParent;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Combines {@link ReentrantLock} with {@link FileLock}<br>
 * Suitable to synchronize threads from different VMs via file system.<br>
 * <br>
 * 
 * <pre>
 * private static final ReentrantFileLockFactory fileLockFactory = newReentrantFileLockFactory("target/droolsassert/lock");
 * private static final ReentrantFileLock consolidatedReportLock = fileLockFactory.newLock(ActivationReportBuilder.class.getName());
 * 
 * 
 * consolidatedReportLock.lock();
 * try {
 *   ...
 * } finally {
 *   consolidatedReportLock.unlock();
 * }
 * </pre>
 * 
 * @see FileLock
 */
public class ReentrantFileLock extends ReentrantLock {
	
	private static final long serialVersionUID = 6495726261995738151L;
	private static final long RETRY_NS = 50;
	private static final Map<Integer, AtomicInteger> fileLockHoldCount = new ConcurrentHashMap<>();
	
	public static final ReentrantFileLockFactory newReentrantFileLockFactory(String filePath) {
		return new ReentrantFileLockFactory(new File(filePath));
	}
	
	public static final ReentrantFileLockFactory newReentrantFileLockFactory(File file) {
		return new ReentrantFileLockFactory(file);
	}
	
	private final FileChannel lockFileChannel;
	private final int id;
	private volatile FileLock fileLock;
	
	public ReentrantFileLock(int id, FileChannel lockFileChannel) {
		this.id = id;
		this.lockFileChannel = lockFileChannel;
	}
	
	@Override
	public void lock() {
		super.lock();
		if (incrementAndGetFileLockHoldCount() == 1) {
			try {
				fileLock = lockFileChannel.lock(id, 1, false);
			} catch (Exception e) {
				decrementAndGetFileLockHoldCount();
				super.unlock();
				throw new RuntimeException("Cannot acquire file lock", e);
			}
		}
	}
	
	@Override
	public void lockInterruptibly() throws InterruptedException {
		super.lockInterruptibly();
		if (incrementAndGetFileLockHoldCount() == 1) {
			for (;;) {
				try {
					fileLock = lockFileChannel.tryLock(id, 1, false);
				} catch (IOException e) {
					decrementAndGetFileLockHoldCount();
					super.unlock();
					throw new RuntimeException("Cannot acquire file lock", e);
				}
				if (fileLock != null)
					break;
				parkNanos(RETRY_NS);
				if (currentThread().isInterrupted()) {
					decrementAndGetFileLockHoldCount();
					super.unlock();
					throw new InterruptedException();
				}
			}
		}
	}
	
	@Override
	public boolean tryLock() {
		boolean locked = super.tryLock();
		if (locked && incrementAndGetFileLockHoldCount() == 1) {
			try {
				fileLock = lockFileChannel.tryLock(id, 1, false);
			} catch (IOException e) {
				decrementAndGetFileLockHoldCount();
				super.unlock();
				throw new RuntimeException("Cannot acquire file lock", e);
			}
			if (fileLock == null) {
				decrementAndGetFileLockHoldCount();
				super.unlock();
				locked = false;
			}
		}
		return locked;
	}
	
	@Override
	public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
		long deadline = nanoTime() + unit.toNanos(timeout);
		boolean locked = super.tryLock(timeout, unit);
		if (locked && incrementAndGetFileLockHoldCount() == 1) {
			for (;;) {
				try {
					fileLock = lockFileChannel.tryLock(id, 1, false);
				} catch (IOException e) {
					decrementAndGetFileLockHoldCount();
					super.unlock();
					throw new RuntimeException("Cannot acquire file lock", e);
				}
				if (fileLock != null)
					break;
				parkNanos(RETRY_NS);
				if (currentThread().isInterrupted() || nanoTime() > deadline) {
					super.unlock();
					locked = false;
					break;
				}
			}
		}
		return locked;
	}
	
	@Override
	public void unlock() {
		if (decrementAndGetFileLockHoldCount() == 0) {
			try {
				fileLock.release();
				fileLock = null;
			} catch (IOException e) {
				throw new RuntimeException("Cannot release file lock", e);
			} finally {
				super.unlock();
			}
		} else {
			super.unlock();
		}
	}
	
	private int incrementAndGetFileLockHoldCount() {
		AtomicInteger lockCount = fileLockHoldCount.get(id);
		if (lockCount == null) {
			synchronized (getClass()) {
				lockCount = fileLockHoldCount.get(id);
				if (lockCount == null) {
					lockCount = new AtomicInteger();
					fileLockHoldCount.put(id, lockCount);
				}
			}
		}
		return lockCount.incrementAndGet();
	}
	
	private int decrementAndGetFileLockHoldCount() {
		int decremented = fileLockHoldCount.get(id).decrementAndGet();
		if (decremented == 0) {
			synchronized (getClass()) {
				if (fileLockHoldCount.get(id).get() == 0)
					fileLockHoldCount.remove(id);
			}
		}
		return decremented;
	}
	
	public static class ReentrantFileLockFactory {
		
		private final FileChannel lockFileChannel;
		
		@SuppressWarnings("resource")
		private ReentrantFileLockFactory(File file) {
			try {
				File absoluteFile = file.getAbsoluteFile();
				forceMkdirParent(absoluteFile);
				lockFileChannel = new FileOutputStream(absoluteFile).getChannel();
			} catch (IOException e) {
				throw new RuntimeException("Cannot initialize file lock factory", e);
			}
		}
		
		/**
		 * Creates new lock for the given name.<br>
		 * Hash code of the name will be used to derive lock id (position) in the file.
		 * 
		 * @see FileChannel#lock(long, long, boolean)
		 */
		public ReentrantFileLock newLock(String name) {
			return newLock(name.hashCode());
		}
		
		/**
		 * Creates new lock for the given name.<br>
		 * Id will be used to uniquely identify the lock (position) in the file.
		 * 
		 * @see FileChannel#lock(long, long, boolean)
		 */
		public ReentrantFileLock newLock(int id) {
			return new ReentrantFileLock(id, lockFileChannel);
		}
	}
}
