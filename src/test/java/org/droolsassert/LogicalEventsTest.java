package org.droolsassert;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

@DroolsSession("classpath:/org/droolsassert/logicalEvents.drl")
public class LogicalEventsTest {
	
	@Rule
	public DroolsAssert drools = new DroolsAssert();
	
	@Before
	public void before() {
		drools.setGlobal("stdout", System.out);
	}
	
	@Test
	@AssertRules({
			"input call",
			"drop dial-up if callee is talking",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped",
			"input call dropped"
	})
	public void testCallsConnectAndDisconnectLogic() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		drools.insertAndFire(caller1Dial);
		drools.assertRetracted(caller1Dial);
		CallInProgress call = drools.getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		drools.advanceTime(5, MINUTES);
		Dialing caller3Dial = new Dialing("33333", "22222");
		drools.insertAndFire(caller3Dial);
		drools.assertExists(caller3Dial);
		
		drools.advanceTime(5, SECONDS);
		drools.assertExists(call, caller3Dial);
		
		drools.advanceTime(5, SECONDS);
		drools.assertExists(call);
		drools.assertRetracted(caller3Dial);
		
		drools.advanceTime(1, HOURS);
		drools.assertRetracted(call);
		
		drools.assertAllRetracted();
	}
	
	@Test
	@AssertRules({
			"input call",
			"drop dial-up if callee is talking",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped",
			"input call dropped"
	})
	public void testCallsConnectAndDisconnectLogicStickToEvents() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		drools.insertAndFire(caller1Dial);
		drools.assertActivated("input call");
		drools.assertRetracted(caller1Dial);
		CallInProgress call = drools.getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		Dialing caller3Dial = new Dialing("33333", "22222");
		drools.insertAndFire(caller3Dial);
		drools.assertActivated();
		drools.assertExists(call, caller3Dial);
		
		drools.awaitFor("drop dial-up if callee is talking");
		drools.assertActivated("drop dial-up if callee is talking", "input call dropped");
		drools.assertExists(call);
		drools.assertRetracted(caller3Dial);
		
		drools.awaitFor("drop the call if caller is talking more than permitted time");
		drools.assertActivated(ImmutableMap.of(
				"drop the call if caller is talking more than permitted time", 1, 
				"call in progress dropped", 1));
		drools.assertRetracted(call);
		
		drools.assertNoScheduledActivations();
		drools.assertAllRetracted();
	}
	
	@Test
	@AssertRules("input call")
	public void testAssertActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
	}
	
	@Test
	@AssertRules(expected = {
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped"
	}, checkScheduled = true)
	public void testAssertScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
	}
	
	@Test(expected = AssertionError.class)
	@AssertRules({
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped" })
	public void testAssertNoScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		drools.assertNoScheduledActivations();
	}
	
	@Test
	@AssertRules({
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped" })
	public void testAwaitForAnyScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		drools.awaitFor();
	}
	
	@Test(expected = AssertionError.class)
	@AssertRules({
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped" })
	public void testAwaitScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		drools.awaitFor("blah");
	}
	
	@Test(expected = AssertionError.class)
	@AssertRules
	public void testAwaitForAnyScheduledActivationsFailed() {
		drools.awaitFor();
	}
	
	@Test(expected = AssertionError.class)
	@AssertRules("input call")
	public void testAssertActivatedFailed() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		drools.assertActivated(ImmutableMap.of("input call", 1));
		drools.insertAndFire(new Dialing("33333", "44444"));
		drools.insertAndFire(new Dialing("55555", "66666"));
		drools.assertActivated(ImmutableMap.of("input call", 2));
		drools.insertAndFire(new Dialing("77777", "88888"));
		drools.insertAndFire(new Dialing("99999", "01010"));
		drools.assertActivated(ImmutableMap.of("input call", 1));
	}
	
	@Test
	@AssertRules({
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped"
	})
	public void testAwaitForScheduledActivations() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		drools.insertAndFire(caller1Dial);
		drools.assertRetracted(caller1Dial);
		CallInProgress call = drools.getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		drools.awaitFor("drop the call if caller is talking more than permitted time");
		drools.assertNoScheduledActivations();
		drools.assertAllRetracted();
		
		Dialing caller3Dial = new Dialing("33333", "22222");
		drools.insertAndFire(caller3Dial);
		drools.assertActivated(ImmutableMap.of(
				"input call", 2,
				"drop the call if caller is talking more than permitted time", 1,
				"call in progress dropped", 1));
		drools.assertRetracted(caller3Dial);
		call = drools.getObject(CallInProgress.class);
		assertEquals("33333", call.callerNumber);
		
		drools.assertFactsCount(1);
		drools.printFacts();
	}
	
	public static class Dialing {
		public String callerNumber;
		public String calleeNumber;
		
		public Dialing(String callerNumber, String calleeNumber) {
			this.callerNumber = callerNumber;
			this.calleeNumber = calleeNumber;
		}
	}
	
	public static class CallInProgress {
		public String callerNumber;
		public String calleeNumber;
		
		public CallInProgress(String callerNumber, String calleeNumber) {
			this.callerNumber = callerNumber;
			this.calleeNumber = calleeNumber;
		}
	}
	
	public static class CallDropped {
		public String number;
		public String reason;
		
		public CallDropped(String number, String reason) {
			this.number = number;
			this.reason = reason;
		}
	}
}
