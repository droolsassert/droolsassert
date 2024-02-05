package org.droolsassert;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;

import java.util.Collection;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
@DroolsSession(resources = {
		"classpath*:/org/droolsassert/rules.drl" },
		logResources = true)
public class ParameterizedTest {

	@Rule
	public DroolsAssert drools = new DroolsAssert();

	@Parameter(value = 0)
	public int num1;

	@Parameter(value = 1)
	public int num2;

	@Parameter(value = 2)
	public int num3;

	@Parameters
	public static Collection<Object[]> data() {
		return asList(new Object[][] {
				{ 1, 1, 1 },
				{ 2, 2, 4 },
				{ 8, 2, 16 },
				{ 4, 5, 20 },
				{ 5, 5, 24 }
		});
	}

	@Test
	@TestRules(expected = { "before", "after" })
	public void parameterized() {
		drools.insert(num1, num2, num3);
		drools.fireAllRules();
		assertTrue(drools.getObjects(Integer.class).size() > 0);
	}
}
