package org.droolsassert;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Test;

@DroolsSession("classpath:/org/droolsassert/complex name * ${with}(*)[or].drl")
public class ComplexNameTest {
	
	@RegisterExtension
	public DroolsAssert drools = new DroolsAssert();
	
	@Test
	@TestRules(expected = "atomic int rule", ignore = "* ${with}(and)[??]<>")
	public void testInt() {
		drools.insertAndFire(new AtomicInteger(), new AtomicLong());
		assertEquals(1, drools.getObject(AtomicInteger.class).get());
		assertEquals(1, drools.getObject(AtomicLong.class).get());
	}
}
