package org.droolsassert;

import static org.junit.Assert.assertEquals;

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
public class DroolsAssert2Test extends DroolsAssert {
	
	@Rule
	public DroolsAssert droolsAssert = this;
	
	@Test
	@TestRules(expected = "atomic int rule")
	public void testInt() {
		insertAndFire(new AtomicInteger());
		assertEquals(1, getObject(AtomicInteger.class).get());
	}
	
	@Test
	@TestRules(expected = { "atomic int rule", "atomic long rule" })
	public void testLong() {
		insert(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		fireAllRules();
		assertFactsCount(3);
		assertEquals(2, getObjects(AtomicLong.class).size());
	}
	
	@Test
	@TestRules(expectedCount = { "2", "atomic long rule" }, ignore = "* int rule")
	public void testActivationCount() {
		insertAndFire(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		assertEquals(2, getObjects(AtomicLong.class).size());
	}
	
	@Test
	@TestRules(expectedSource = "org/droolsassert/expectedDroolsAssertTest.txt")
	public void testExpectedSource() {
		insert(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		fireAllRules();
		assertFactsCount(3);
		assertEquals(2, getObjects(AtomicLong.class).size());
	}
	
	@Test
	@TestRules(expectedCountSource = "**/expectedCountDroolsAssertTest.txt", ignoreSource = "**/ignoreDroolsAssertTest.txt")
	public void testExpectedCountSource() {
		insert(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		fireAllRules();
		assertFactsCount(3);
		assertEquals(2, getObjects(AtomicLong.class).size());
	}
	
	@Test
	@TestRules(expected = {})
	public void testNoRulesWereActivated() {
		insertAndFire("string");
		assertFactsCount(1);
		assertEquals("string", getObject(String.class));
	}
	
	@Test(expected = AssertionError.class)
	public void testNoObjectFound() {
		assertEquals(0, getObject(BigDecimal.class).intValue());
	}
	
	@Test(expected = AssertionError.class)
	@TestRules(expected = "atomic long rule")
	public void testNoUniqueObjectFound() {
		insertAndFire(new AtomicLong(), new AtomicLong());
		assertEquals(2, getObject(AtomicLong.class));
	}
	
	@Test
	public void testPrintFactsSkippedWhenHistoryIsDisabled() {
		printFacts();
	}
}
