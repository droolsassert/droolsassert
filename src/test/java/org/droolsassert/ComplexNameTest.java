package org.droolsassert;

import static org.junit.Assert.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Rule;
import org.junit.Test;

@DroolsSession("classpath:/org/droolsassert/complex name * ${with}(*)[or].drl")
public class ComplexNameTest {
	
	@Rule
	public DroolsAssert drools = new DroolsAssert();
	
	@Test
	@AssertRules(value = "atomic int rule", ignore = "complex name * \\$\\{with\\}\\(and\\)\\[__\\]")
	public void testInt() {
		drools.insertAndFire(new AtomicInteger(), new AtomicLong());
		assertEquals(1, drools.getObject(AtomicInteger.class).get());
		assertEquals(1, drools.getObject(AtomicLong.class).get());
	}
}
