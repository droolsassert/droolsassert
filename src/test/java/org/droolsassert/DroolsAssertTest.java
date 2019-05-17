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
		ignoreRules = { "before", "after" })
public class DroolsAssertTest {

	@Rule
	public DroolsAssert da = new DroolsAssert();

	@Test
	@AssertRules("atomic int rule")
	public void testInt() {
		da.insertAndFire(new AtomicInteger());
		assertEquals(1, da.getObject(AtomicInteger.class).get());
	}

	@Test
	@AssertRules({ "atomic int rule", "atomic long rule" })
	public void testLong() {
		da.insert(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		da.fireAllRules();
		da.assertFactsCount(3);
		assertEquals(2, da.getObjects(AtomicLong.class).size());
	}

	@Test
	@AssertRules(expectedCount = { "atomic long rule", "2" }, ignore = "* int rule")
	public void testActivationCount() {
		da.insertAndFire(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		assertEquals(2, da.getObjects(AtomicLong.class).size());
	}

	@Test
	@AssertRules
	public void testNoRulesWereTriggered() {
		da.insertAndFire(new BigDecimal(0));
		da.assertFactsCount(1);
		assertEquals(0, da.getObjects(AtomicLong.class).size());
	}
}
