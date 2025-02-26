package org.droolsassert.issue;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.droolsassert.DroolsAssert;
import org.droolsassert.DroolsSession;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.kie.api.runtime.rule.FactHandle;

@DroolsSession("org/droolsassert/issue/DeletionTest.drl")
public class DeletionTest {

	public static class CountedModel {
		public long ordinal;

		public CountedModel(long ordinal) {
			this.ordinal = ordinal;
		}
	}

	@Rule
	public DroolsAssert drools = new DroolsAssert();

	@Before
	public void beforeEach() {
		drools.setGlobal("maxCount", new AtomicLong(3));
	}

	@Test
	public void testDeleteFactHandle() {
		List<CountedModel> facts = asList(new CountedModel(1), new CountedModel(2), new CountedModel(3), new CountedModel(4), new CountedModel(5));
		for (int i = 0; i < 5; i++)
			drools.insertAndFire(facts.get(i));
		assertEquals(3, drools.getSession().getFactCount());
		
		FactHandle last = drools.getFactHandle(facts.get(facts.size() - 1));
		drools.delete(last);
		assertEquals(2, drools.getSession().getFactCount());
	}

	@Test
	public void testMaxCount() {
		AtomicLong ordinal = new AtomicLong();
		for (int i = 0; i < 5; i++)
			drools.insert(new CountedModel(ordinal.incrementAndGet()));
		drools.fireAllRules();
		assertEquals(3, drools.getObjects().size());
	}
}
