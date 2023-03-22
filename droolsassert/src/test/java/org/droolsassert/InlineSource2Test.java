package org.droolsassert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.extension.RegisterExtension;

@DroolsSession(source = {
		"DRL", ""
				+ "dialect 'mvel'\n"
				+ "import java.util.concurrent.atomic.AtomicInteger\n"
				+ ""
				+ "rule 'atomic int rule'\n"
				+ "when\n"
				+ "    $atomicInteger: AtomicInteger()\n"
				+ "then\n"
				+ "    $atomicInteger.incrementAndGet()\n"
				+ "end",
		"DRL", ""
				+ "dialect 'mvel'\n"
				+ "import java.util.concurrent.atomic.AtomicLong\n"
				+ ""
				+ "rule 'atomic long rule'\n"
				+ "when\n"
				+ "    $atomicLong: AtomicLong()\n"
				+ "then\n"
				+ "    $atomicLong.incrementAndGet()\n"
				+ "end" })
public class InlineSource2Test {
	
	@RegisterExtension
	public DroolsAssert drools = new DroolsAssert();
	
	@TestRules(expected = { "atomic int rule", "atomic long rule" })
	public void testInlineSource() {
		drools.insertAndFire(new AtomicInteger(), new AtomicLong());
		assertEquals(1, drools.getObject(AtomicInteger.class).get());
		assertEquals(1, drools.getObject(AtomicLong.class).get());
	}
}
