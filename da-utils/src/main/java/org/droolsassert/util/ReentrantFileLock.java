package org.droolsassert.util;

import static com.google.common.io.Resources.getResource;
import static java.lang.String.format;
import static java.lang.System.nanoTime;
import static java.lang.Thread.currentThread;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.locks.LockSupport.parkNanos;
import static org.apache.commons.io.FileUtils.forceMkdirParent;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import com.google.common.collect.MapMaker;

/**
 * Combines {@link ReentrantLock} with {@link FileLock}<br>
 * Suitable to synchronize threads from different VMs via file system.<br>
 * As per documentation, file locks are held on behalf of the entire Java virtual machine.<br>
 * Thus in most cases your {@link ReentrantFileLock} should be static to have single JVM instance of the lock object correspond to file lock for the entire JVM.<br>
 * But it should not be an error to have several exclusive locks on JVM level correspond to single file lock id when you have valid technical usecase to do so.<br>
 * <p>
 * Inner logic is fairly simple and imply acquiring first java {@link ReentrantLock} (fairness option belong here) and, when succeeded, continue further with acquiring file
 * lock.<br>
 * Release logic behaves in the opposite order.<br>
 * <p>
 * {@link ReentrantLock} is reentrant, meaning the same thread can re-acquire the same lock again (lock held count get incremented).<br>
 * The same logic implemented for the file lock, meaning any {@link ReentrantFileLock}(s) can re-acquire the same lock id on the file running within the same JVM (file lock held
 * count get incremented implying no interaction with file system).<br>
 * You can also synchronize on resources which are files on file system, like configuration files etc. Files will be locked for write though.<br>
 * <p>
 * Consider Initialization-on-demand holder idiom for lazy loading<br>
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
	private static final long LOCK_RETRY_MS = 10;
	private static final String cantAcquireFileLock = "Cannot acquire file lock";
	
	private static final ConcurrentMap<Integer, FileLockHolder> fileLocks = new MapMaker().weakValues().makeMap();
	
	public static final ReentrantFileLockFactory newReentrantFileLockFactory(String filePath) {
		return newReentrantFileLockFactory(false, filePath);
	}
	
	public static final ReentrantFileLockFactory newReentrantFileLockFactory(boolean fair, String filePath) {
		return new ReentrantFileLockFactory(fair, new File(filePath));
	}
	
	public static final ReentrantFileLockFactory newReentrantFileLockFactory(File file) {
		return newReentrantFileLockFactory(false, file);
	}
	
	public static final ReentrantFileLockFactory newReentrantFileLockFactory(boolean fair, File file) {
		return new ReentrantFileLockFactory(fair, file);
	}
	
	public static final ReentrantFileLockFactory newReentrantResourceLockFactory(String resourcePath) {
		return newReentrantResourceLockFactory(false, resourcePath);
	}
	
	public static final ReentrantFileLockFactory newReentrantResourceLockFactory(boolean fair, String resourcePath) {
		try {
			return new ReentrantFileLockFactory(fair, new File(getResource(resourcePath).toURI()));
		} catch (URISyntaxException | RuntimeException e) {
			throw new RuntimeException("Cannot create a lock from the resource " + resourcePath, e);
		}
	}
	
	private final File absoluteFile;
	private final FileChannel lockFileChannel;
	private final int id;
	private final FileLockHolder shared;
	
	private ReentrantFileLock(int id, FileChannel lockFileChannel, File absoluteFile) {
		this(false, id, lockFileChannel, absoluteFile);
	}
	
	private ReentrantFileLock(boolean fair, int id, FileChannel lockFileChannel, File absoluteFile) {
		super(fair);
		this.id = id;
		this.lockFileChannel = lockFileChannel;
		this.absoluteFile = absoluteFile;
		FileLockHolder defaultValue = new FileLockHolder();
		shared = defaultIfNull(fileLocks.putIfAbsent(id, defaultValue), defaultValue);
	}
	
	/**
	 * Unlike other lock methods this one served fairly when enqueued to heavy used file resource.
	 */
	@Override
	public void lock() {
		super.lock();
		shared.modificationLock.lock();
		if (shared.holdCount.get() == 0) {
			try {
				shared.fileLock = lockFileChannel.lock(id, 1, false);
				shared.holdCount.incrementAndGet();
			} catch (Exception e) {
				super.unlock();
				throw new RuntimeException(cantAcquireFileLock, e);
			} finally {
				shared.modificationLock.unlock();
			}
		} else {
			shared.holdCount.incrementAndGet();
			shared.modificationLock.unlock();
		}
	}
	
	@Override
	public void lockInterruptibly() throws InterruptedException {
		super.lockInterruptibly();
		shared.modificationLock.lockInterruptibly();
		if (shared.holdCount.get() == 0) {
			try {
				while (true) {
					try {
						shared.fileLock = lockFileChannel.tryLock(id, 1, false);
						if (shared.fileLock != null) {
							shared.holdCount.incrementAndGet();
							break;
						}
					} catch (Exception e) {
						super.unlock();
						throw new RuntimeException(cantAcquireFileLock, e);
					}
					parkNanos(MILLISECONDS.toNanos(LOCK_RETRY_MS));
					if (currentThread().isInterrupted()) {
						super.unlock();
						throw new InterruptedException();
					}
				}
			} finally {
				shared.modificationLock.unlock();
			}
		} else {
			shared.holdCount.incrementAndGet();
			shared.modificationLock.unlock();
		}
	}
	
	@Override
	public boolean tryLock() {
		boolean locked = super.tryLock();
		if (!locked)
			return false;
		if (!shared.modificationLock.tryLock()) {
			super.unlock();
			return false;
		}
		if (shared.holdCount.get() == 0) {
			try {
				shared.fileLock = lockFileChannel.tryLock(id, 1, false);
				if (shared.fileLock != null) {
					shared.holdCount.incrementAndGet();
				} else {
					super.unlock();
					return false;
				}
			} catch (Exception e) {
				super.unlock();
				throw new RuntimeException(cantAcquireFileLock, e);
			} finally {
				shared.modificationLock.unlock();
			}
		} else {
			shared.holdCount.incrementAndGet();
			shared.modificationLock.unlock();
		}
		return locked;
	}
	
	@Override
	public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
		long deadline = nanoTime() + unit.toNanos(timeout);
		boolean locked = super.tryLock(timeout, unit);
		if (!locked)
			return false;
		if (!shared.modificationLock.tryLock(deadline - nanoTime(), NANOSECONDS)) {
			super.unlock();
			return false;
		}
		if (shared.holdCount.get() == 0) {
			try {
				while (true) {
					try {
						shared.fileLock = lockFileChannel.tryLock(id, 1, false);
						if (shared.fileLock != null) {
							shared.holdCount.incrementAndGet();
							break;
						}
					} catch (Exception e) {
						super.unlock();
						throw new RuntimeException(cantAcquireFileLock, e);
					}
					parkNanos(MILLISECONDS.toNanos(LOCK_RETRY_MS));
					if (currentThread().isInterrupted()) {
						super.unlock();
						throw new InterruptedException();
					}
					if (nanoTime() > deadline) {
						super.unlock();
						locked = false;
						break;
					}
				}
			} finally {
				shared.modificationLock.unlock();
			}
		} else {
			shared.holdCount.incrementAndGet();
			shared.modificationLock.unlock();
		}
		return locked;
	}
	
	@Override
	public void unlock() {
		shared.modificationLock.lock();
		if (shared.holdCount.get() == 1) {
			try {
				shared.fileLock.release();
				shared.fileLock = null;
				shared.holdCount.decrementAndGet();
			} catch (Exception e) {
				throw new RuntimeException("Cannot release file lock", e);
			} finally {
				shared.modificationLock.unlock();
				super.unlock();
			}
		} else {
			shared.holdCount.decrementAndGet();
			shared.modificationLock.unlock();
			super.unlock();
		}
	}
	
	public File getAbsoluteFile() {
		return absoluteFile;
	}
	
	@Override
	public String toString() {
		return format("%s-%s (%s)", absoluteFile.getName(), id, shared.holdCount);
	}
	
	private class FileLockHolder {
		private final ReentrantLock modificationLock = new ReentrantLock(true);
		private final AtomicInteger holdCount = new AtomicInteger();
		private volatile FileLock fileLock;
	}
	
	public static class ReentrantFileLockFactory {
		
		private final boolean fair;
		private final File absoluteFile;
		private final FileChannel lockFileChannel;
		
		@SuppressWarnings("resource")
		private ReentrantFileLockFactory(boolean fair, File file) {
			try {
				this.fair = fair;
				absoluteFile = file.getAbsoluteFile();
				forceMkdirParent(absoluteFile);
				lockFileChannel = new FileOutputStream(absoluteFile).getChannel();
			} catch (IOException e) {
				throw new RuntimeException("Cannot initialize file lock factory", e);
			}
		}
		
		/**
		 * Creates new lock for the given name.<br>
		 * Hash code of the name will be used to compute lock id (position) in the file.
		 * 
		 * @see FileChannel#lock(long, long, boolean)
		 */
		public ReentrantFileLock newLock(String name) {
			return newLock(name.hashCode());
		}
		
		/**
		 * Creates new lock for the given id.<br>
		 * Id will be used to uniquely identify the lock (position) in the file.
		 * 
		 * @see FileChannel#lock(long, long, boolean)
		 */
		public ReentrantFileLock newLock(int id) {
			return new ReentrantFileLock(fair, id, lockFileChannel, absoluteFile);
		}
	}
}
