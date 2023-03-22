package org.droolsassert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.extension.RegisterExtension;

@DroolsSession(source = "dialect 'mvel'\n"
		+ "import java.util.concurrent.atomic.AtomicInteger\n"
		+ ""
		+ "rule 'atomic int rule'\n"
		+ "when\n"
		+ "    $atomicInteger: AtomicInteger()\n"
		+ "then\n"
		+ "    $atomicInteger.incrementAndGet()\n"
		+ "end")
public class InlineSourceTest {
	
	@RegisterExtension
	public DroolsAssert drools = new DroolsAssert();
	
	@TestRules(expected = "atomic int rule")
	public void testInlineSource() {
		drools.insertAndFire(new AtomicInteger(), new AtomicLong());
		assertEquals(1, drools.getObject(AtomicInteger.class).get());
		assertEquals(0, drools.getObject(AtomicLong.class).get());
	}
}
