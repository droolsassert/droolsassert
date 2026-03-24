package org.droolsassert.util;

import static java.lang.System.currentTimeMillis;
import static java.lang.System.out;

import org.junit.Ignore;
import org.junit.Test;

public class PerfStatTest {
	
	@Test
	@Ignore("for manual run")
	public void test() {
		float cycles = 1_000_000;
		PerfStat domainPerf = new PerfStat("domain.under.test"); // ~0.006185 ms
		
		long start = currentTimeMillis();
		for (int i = 0; i < cycles; i++) {
			//new PerfStat("" + i);
			domainPerf.start();
			domainPerf.stop();
		}
		
		out.printf("cycle time %f", ((currentTimeMillis() - start) / cycles)); // ~0.000160 ms
	}
}
