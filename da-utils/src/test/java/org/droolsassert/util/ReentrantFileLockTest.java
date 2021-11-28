package org.droolsassert.util;

import static java.lang.Long.parseLong;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.err;
import static java.lang.System.out;
import static java.lang.System.setOut;
import static java.lang.Thread.currentThread;
import static java.lang.Thread.sleep;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.concurrent.Executors.newCachedThreadPool;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.listFiles;
import static org.apache.commons.io.FileUtils.readLines;
import static org.apache.commons.lang3.RandomUtils.nextLong;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.droolsassert.util.ReentrantFileLock.newReentrantResourceLockFactory;
import static org.junit.Assert.assertFalse;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.filefilter.FalseFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.droolsassert.util.ReentrantFileLock.ReentrantFileLockFactory;
import org.junit.Ignore;
import org.junit.Test;

public class ReentrantFileLockTest {
	
	private static class LogLine {
		private String originalLine;
		private Instant time;
		private String threadId;
		
		@Override
		public String toString() {
			return reflectionToString(this);
		}
	}
	
	private static final long testRunTimeMin = 1;
	private static final long parkTimeMinMs = 1000;
	private static final long parkTimeMaxMs = 1000;
	private static final long workTimeMinMs = 0;
	private static final long workTimeMaxMs = 1000;
	private static final String jvmName = getRuntimeMXBean().getName();
	private File dumpDir = new File("dump");
	
	@Test
	@Ignore("For manual run. As precise as System.currentTimeMillis is")
	public void testConcurrentProcessesDidNotOwnTheSameFileLockAtTheSameTime() {
		boolean intersectionFound = false;
		ArrayList<File> files = new ArrayList<>(listFiles(dumpDir, TrueFileFilter.INSTANCE, FalseFileFilter.INSTANCE));
		for (int i = 0; i < files.size(); i++) {
			for (int j = i + 1; j < files.size(); j++) {
				out.printf("comparing %s - %s%n", files.get(i).getName(), files.get(j).getName());
				
				for (Entry<String, List<LogLine>> thread1 : threadOperations(files.get(i)).entrySet()) {
					for (Entry<String, List<LogLine>> thread2 : threadOperations(files.get(j)).entrySet()) {
						List<LogLine> lines1 = thread1.getValue();
						List<LogLine> lines2 = thread2.getValue();
						for (int m = 0; m < lines1.size(); m = m + 2) {
							Instant lockStartTime = lines1.get(m).time;
							if (m + 1 >= lines1.size())
								// file flushing issue, skip the last odd record
								break;
							Instant lockReleaseTime = lines1.get(m + 1).time;
							for (int n = 0; n < lines2.size(); n++) {
								Instant eventTime = lines2.get(n).time;
								if (lockStartTime.isBefore(eventTime) && lockReleaseTime.isAfter(eventTime)) {
									intersectionFound = true;
									err.printf("%s%n%s%n", lines1.get(m).originalLine, lines2.get(n).originalLine);
								}
							}
						}
					}
				}
			}
		}
		
		assertFalse(intersectionFound);
	}
	
	private Map<String, List<LogLine>> threadOperations(File file) {
		return readLinesSilently(file, defaultCharset()).stream()
				.map(this::parseLogLine)
				.collect(groupingBy(l -> l.threadId, TreeMap::new, toList()));
	}
	
	private static List<String> readLinesSilently(final File file, final Charset encoding) {
		try {
			return readLines(file, encoding);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	private LogLine parseLogLine(String logLine) {
		String[] values = logLine.split("\\s+");
		LogLine result = new LogLine();
		result.originalLine = logLine;
		result.time = Instant.ofEpochMilli(parseLong(values[0]));
		result.threadId = values[5];
		return result;
	}
	
	@Test
	@Ignore("For manual run. Start few times to run several JVMs in parallel")
	public void runConcurrentProcessing() throws InterruptedException, FileNotFoundException {
		err.println(jvmName);
		
		PerfStat lockTime = new PerfStat("lockTime");
		File file = new File(dumpDir, jvmName.substring(0, jvmName.indexOf("@")) + ".txt");
		file.getParentFile().mkdirs();
		setOut(new PrintStream(file));
		ReentrantFileLockFactory lockFactory = newReentrantResourceLockFactory("resource.lock");
		// ReentrantFileLock lock1 = lockFactory.newLock(1);
		// ReentrantFileLock lock2 = lockFactory.newLock(1);
		// ReentrantFileLock lock3 = lockFactory.newLock(1);
		// ReentrantFileLock lock4 = lockFactory.newLock(1);
		ExecutorService executor = newCachedThreadPool();
		AtomicBoolean running = new AtomicBoolean(true);
		for (int i = 0; i < 2; i++) {
			ReentrantFileLock lock1 = lockFactory.newLock(1);
			executor.execute(() -> {
				try {
					while (running.get()) {
						sleep(nextLong(parkTimeMinMs, parkTimeMaxMs));
						
						lockTime.start();
						lock1.lock();
						lockTime.stop();
						
						out.printf("%d | %s %s %s - start%n", currentTimeMillis(), jvmName, lock1, currentThread().getName());
						sleep(nextLong(workTimeMinMs, workTimeMaxMs));
						out.printf("%d | %s %s %s - end%n", currentTimeMillis(), jvmName, lock1, currentThread().getName());
						
						lock1.unlock();
					}
				} catch (Exception e) {
					err.println(e.getMessage());
					e.printStackTrace();
				}
			});
			ReentrantFileLock lock2 = lockFactory.newLock(1);
			executor.execute(() -> {
				try {
					while (running.get()) {
						sleep(nextLong(parkTimeMinMs, parkTimeMaxMs));
						
						lockTime.start();
						lock2.lockInterruptibly();
						lockTime.stop();
						
						out.printf("%d | %s %s %s - start interruptibly%n", currentTimeMillis(), jvmName, lock2, currentThread().getName());
						sleep(nextLong(workTimeMinMs, workTimeMaxMs));
						out.printf("%d | %s %s %s - end%n", currentTimeMillis(), jvmName, lock2, currentThread().getName());
						
						lock2.unlock();
					}
				} catch (Exception e) {
					err.println(e.getMessage());
					e.printStackTrace();
				}
			});
			ReentrantFileLock lock3 = lockFactory.newLock(1);
			executor.execute(() -> {
				try {
					while (running.get()) {
						sleep(nextLong(parkTimeMinMs, parkTimeMaxMs));
						
						if (!lock3.tryLock())
							continue;
						
						out.printf("%d | %s %s %s - start 0s%n", currentTimeMillis(), jvmName, lock3, currentThread().getName());
						sleep(nextLong(workTimeMinMs, workTimeMaxMs));
						out.printf("%d | %s %s %s - end%n", currentTimeMillis(), jvmName, lock3, currentThread().getName());
						
						lock3.unlock();
					}
				} catch (Exception e) {
					err.println(e.getMessage());
					e.printStackTrace();
				}
			});
			ReentrantFileLock lock4 = lockFactory.newLock(1);
			executor.execute(() -> {
				try {
					while (running.get()) {
						sleep(nextLong(parkTimeMinMs, parkTimeMaxMs));
						
						if (!lock4.tryLock(2, SECONDS))
							continue;
						
						out.printf("%d | %s %s %s - start 2s%n", currentTimeMillis(), jvmName, lock4, currentThread().getName());
						sleep(nextLong(workTimeMinMs, workTimeMaxMs));
						out.printf("%d | %s %s %s - end%n", currentTimeMillis(), jvmName, lock4, currentThread().getName());
						
						lock4.unlock();
					}
				} catch (Exception e) {
					err.println(e.getMessage());
					e.printStackTrace();
				}
			});
		}
		sleep(MINUTES.toMillis(testRunTimeMin));
		running.set(false);
		executor.shutdown();
		executor.awaitTermination(10, SECONDS);
		
		err.println(lockTime);
		err.println("finish");
	}
}
