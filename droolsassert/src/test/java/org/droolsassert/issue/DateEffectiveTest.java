package org.droolsassert.issue;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.droolsassert.DroolsAssert;
import org.droolsassert.DroolsSession;
import org.droolsassert.TestRules;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@DroolsSession("**/issue/DateEffective.drl")
public class DateEffectiveTest {

	public static class Policy {
		public String policyNumber;
		public String stateCode;
	}

	@RegisterExtension
	public DroolsAssert drools = new DroolsAssert();

	@Test
	@TestRules(givenTime = "2024-09-15T00:00:00Z", expected = "Simple")
	public void testGivenTime20240915() {
		Policy policy = new Policy();
		policy.policyNumber = "1234";

		drools.insertAndFire(policy);

		assertEquals("OH", policy.stateCode);
	}

	@Test
	@TestRules(givenTime = "2024-09-14T00:00:00Z", expected = {})
	public void testGivenTime20240914() {
		Policy policy = new Policy();
		policy.policyNumber = "1234";

		drools.insertAndFire(policy);

		assertEquals(null, policy.stateCode);
	}

	@Test
	@TestRules(expectedCount = { "1", "Simple" })
	public void testAdvanceTimeTo() {
		Policy policy1 = new Policy();
		policy1.policyNumber = "1234";
		Policy policy2 = new Policy();
		policy2.policyNumber = "1234";

		drools.advanceTimeTo(Instant.parse("2024-09-14T00:00:00Z"));
		drools.insertAndFire(policy1);
		drools.assertActivated();
		drools.advanceTimeTo(Instant.parse("2024-09-16T00:00:00Z"));
		drools.assertActivated();
		drools.insertAndFire(policy2);
		drools.assertActivated("Simple");
		drools.assertFactsCount(2);

		assertEquals(null, policy1.stateCode);
		assertEquals("OH", policy2.stateCode);
	}
}