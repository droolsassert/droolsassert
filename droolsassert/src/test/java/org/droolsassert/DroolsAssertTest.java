package org.droolsassert;

import static java.util.Arrays.asList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.kie.api.runtime.rule.FactHandle;

@DroolsSession(resources = {
		"classpath*:/org/droolsassert/rules.drl",
		"classpath*:/com/company/project/*/{regex:.*.(drl|dsl|xlsx|gdst)}",
		"classpath*:/com/company/project/*/ruleUnderTest.rdslr" },
		ignoreRules = { "before", "after" },
		keepFactsHistory = false,
		logResources = true)
public class DroolsAssertTest {
	
	@RegisterExtension
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
	@TestRules(expectedCount = { "2", "atomic long rule" }, ignore = "* int rule")
	public void testActivationCount() {
		drools.insertAndFire(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		assertEquals(2, drools.getObjects(AtomicLong.class).size());
	}
	
	@Test
	@TestRules(expectedSource = "org/droolsassert/expectedDroolsAssertTest.txt")
	public void testExpectedSource() {
		drools.insert(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		drools.fireAllRules();
		drools.assertFactsCount(3);
		assertEquals(2, drools.getObjects(AtomicLong.class).size());
	}
	
	@Test
	@TestRules(expectedCountSource = "**/expectedCountDroolsAssertTest.txt", ignoreSource = "**/ignoreDroolsAssertTest.txt")
	public void testExpectedCountSource() {
		drools.insert(new AtomicInteger(), new AtomicLong(), new AtomicLong());
		drools.fireAllRules();
		drools.assertFactsCount(3);
		assertEquals(2, drools.getObjects(AtomicLong.class).size());
	}
	
	@Test
	@TestRules(expected = {})
	public void testNoRulesWereActivated() {
		drools.insertAndFire("string");
		drools.assertFactsCount(1);
		assertEquals("string", drools.getObject(String.class));
	}
	
	@Test
	public void testNoObjectFound() {
		assertThrows(AssertionError.class, () -> drools.getObject(BigDecimal.class));
	}
	
	@Test
	@TestRules(expected = "atomic long rule")
	public void testNoUniqueObjectFound() {
		drools.insertAndFire(new AtomicLong(), new AtomicLong());
		assertThrows(AssertionError.class, () -> drools.getObject(AtomicLong.class));
	}
	
	@Test
	public void testPrintFactsSkippedWhenHistoryIsDisabled() {
		drools.printFacts();
	}
	
	@Test
	public void testGetFactHandle() {
		drools.insertAndFire(new AtomicInteger());
		assertNotNull(drools.getFactHandle(AtomicInteger.class));
		
		drools.insertAndFireAt("entrypoint", new AtomicLong());
		assertNotNull(drools.getFactHandle(AtomicLong.class));
		
		drools.insertAndFire(new AtomicLong());
		assertEquals(2, drools.getFactHandles(AtomicLong.class).size());
		assertEquals(2, drools.getFactHandles(AtomicLong.class, (l) -> l.get() > 0).size());
		assertEquals(2, drools.getFactHandles((Object o) -> o instanceof AtomicLong).size());
	}
	
	@Test
	public void testUpdate() {
		AtomicInteger atomicInteger = new AtomicInteger(8);
		drools.insertAndFire(atomicInteger);
		drools.assertActivated("atomic int rule");
		
		atomicInteger.incrementAndGet();
		drools.advanceTime(1, SECONDS);
		drools.assertActivated();
		drools.update(atomicInteger);
		drools.advanceTime(1, SECONDS);
		drools.assertActivated("atomic int rule", "increment 10");
		
		drools.update(drools.getFactHandle(atomicInteger));
		drools.advanceTime(1, SECONDS);
		drools.assertActivated("atomic int rule");
	}
	
	@Test
	public void testDelete() {
		AtomicInteger atomicInteger = new AtomicInteger();
		drools.insertAndFire(atomicInteger);
		assertEquals(1, drools.getFactHandles(AtomicInteger.class).size());
		drools.delete(atomicInteger);
		assertEquals(0, drools.getFactHandles(AtomicInteger.class).size());
		
		List<FactHandle> handles = drools.insertAndFire(new AtomicInteger());
		assertEquals(1, drools.getFactHandles(AtomicInteger.class).size());
		drools.delete(handles);
		assertEquals(0, drools.getFactHandles(AtomicInteger.class).size());
		
		handles = drools.insertAndFire(new AtomicInteger());
		assertEquals(1, drools.getFactHandles(AtomicInteger.class).size());
		drools.delete(handles.get(0));
		assertEquals(0, drools.getFactHandles(AtomicInteger.class).size());
		
		AtomicLong atomicLong = new AtomicLong();
		drools.insertAndFireAt("entrypoint", atomicLong);
		assertEquals(1, drools.getFactHandles(AtomicLong.class).size());
		drools.delete(atomicLong);
		assertEquals(0, drools.getFactHandles(AtomicLong.class).size());
	}
	
	@Test
	public void testActivationMeta() {
		drools.insertAndFire(new AtomicInteger());
		drools.assertActivated("atomic int rule");
		RuleImpl rule = drools.getActivationMeta("atomic int rule");
		assertEquals("MAIN", rule.getAgendaGroup());
		assertEquals(0, rule.getSalience().getValue());
	}
	
	@Test
	public void testToString() {
		drools.insertAndFire("string");
		drools.insertAndFire(asList("string1", "string2"));
		drools.insertAndFire(new LinkedHashMap<String, Object>() {{
			put("key1", "value1");
			put("key2", "value2");
		}});
	}
}
