package org.droolsassert.util;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.out;
import static java.lang.management.ManagementFactory.getRuntimeMXBean;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SharedRollingFileAppenderTest {
	
	private static final Logger logger = LoggerFactory.getLogger(SharedRollingFileAppenderTest.class);
	private static final String jvmName = getRuntimeMXBean().getName();
	private PerfStat logStat = new PerfStat("log");
	
	@Test
	@Ignore("For manual run")
	public void test() {
		long threshold = currentTimeMillis() + SECONDS.toMillis(300);
		while (currentTimeMillis() < threshold) {
			// for (int i = 0; i < 1000; i++) {
			logStat.start();
			logger.info(jvmName);
			logStat.stop();
			// LockSupport.parkNanos(TimeUnit.SECONDS.toNanos(2));
		}
		out.println("count: " + logStat.getStat().getLeapsCount());
	}
}
