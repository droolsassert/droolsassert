package org.droolsassert;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.Rule;
import org.junit.Test;

@DroolsSession(resources = {
		"classpath*:/org/droolsassert/rules.drl",
		"classpath*:/com/company/project/*/{regex:.*.(drl|dsl|xlsx|gdst)}",
		"classpath*:/com/company/project/*/ruleUnderTest.rdslr" },
		ignoreRules = { "before", "after" },
		keepFactsHistory = false,
		logResources = true)
public class DroolsAssertTest {
	
	@Rule
	public DroolsAssert drools = new DroolsAssert();
	
	@Test
	@TestRules(expected = "atomic int rule")
	public void testInt() {
		drools.insertAndFire(new AtomicInteger());
		assertEquals(1, drools.getObject(AtomicInteger.class).get());
	}
	
	@Test
	@TestRules(expected = { "atomic int rule", "atomic long rule" })
	public void testLong() {
		drools.insert(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		drools.fireAllRules();
		drools.assertFactsCount(3);
		assertEquals(2, drools.getObjects(AtomicLong.class).size());
	}
	
	@Test
	@TestRules(expectedCount = { "atomic long rule", "2" }, ignore = "* int rule")
	public void testActivationCount() {
		drools.insertAndFire(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		assertEquals(2, drools.getObjects(AtomicLong.class).size());
	}
	
	@Test
	@TestRules(expected = {})
	public void testNoRulesWereTriggered() {
		drools.insertAndFire("string");
		drools.assertFactsCount(1);
		assertEquals("string", drools.getObject(String.class));
	}
	
	@Test(expected = AssertionError.class)
	@TestRules
	public void testNoObjectFound() {
		assertEquals(0, drools.getObject(BigDecimal.class).intValue());
	}
	
	@Test(expected = AssertionError.class)
	@TestRules(expected = "atomic long rule")
	public void testNoUniqueObjectFound() {
		drools.insertAndFire(new AtomicLong(), new AtomicLong());
		assertEquals(2, drools.getObject(AtomicLong.class));
	}
	
	@Test
	public void testSessionCreationSkipped() {
		assertNull(drools.getSession());
	}
	
	@Test
	@TestRules
	public void testPrintFactsSkippedWhenHistoryIsDisabled() {
		drools.printFacts();
	}
}
