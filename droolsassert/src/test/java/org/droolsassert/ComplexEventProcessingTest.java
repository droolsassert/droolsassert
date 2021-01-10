package org.droolsassert;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@DroolsSession("org/droolsassert/complexEventProcessing.drl")
public class ComplexEventProcessingTest {
	
	@Rule
	public DroolsAssert drools = new DroolsAssert();
	
	@Before
	public void before() {
		drools.setGlobal("stdout", System.out);
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogic() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		drools.insertAndFire(caller1Dial);
		drools.assertRetracted(caller1Dial);
		CallInProgress call = drools.getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		drools.advanceTime(5, MINUTES);
		Dialing caller3Dial = new Dialing("33333", "22222");
		drools.insertAndFire(caller3Dial);
		drools.assertExist(caller3Dial);
		
		drools.advanceTime(5, SECONDS);
		drools.assertExist(call, caller3Dial);
		
		drools.advanceTime(5, SECONDS);
		drools.assertExist(call);
		drools.assertRetracted(caller3Dial);
		
		drools.advanceTime(1, HOURS);
		drools.assertRetracted(call);
		
		drools.assertAllRetracted();
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogic2() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		drools.insertAndFire(caller1Dial);
		drools.assertRetracted(caller1Dial);
		CallInProgress call = drools.getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		drools.advanceTime(5, MINUTES);
		Dialing caller3Dial = new Dialing("33333", "22222");
		drools.insertAndFire(caller3Dial);
		drools.assertExist(caller3Dial);
		
		drools.advanceTime(5, SECONDS);
		drools.assertExist(call, caller3Dial);
		CallDropped callDropped = new CallDropped("11111", "22222", "Dismissed");
		drools.insertAndFire(callDropped);
		drools.assertRetracted(call, caller3Dial, callDropped);
		CallInProgress call2 = drools.getObject(CallInProgress.class);
		drools.assertExist(call2);
		
		drools.advanceTime(10, SECONDS);
		drools.assertExist(call2);
		
		drools.advanceTime(1, HOURS);
		drools.assertRetracted(call2);
		
		drools.assertAllRetracted();
	}
	
	@Test
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
		drools.assertExist(call, caller3Dial);
		
		drools.awaitFor("drop dial-up if callee is talking");
		drools.assertActivated("drop dial-up if callee is talking", "input call dropped");
		drools.assertExist(call);
		drools.assertRetracted(caller3Dial);
		
		drools.awaitFor("drop the call if caller is talking more than permitted time");
		drools.assertActivatedCount(
				1, "drop the call if caller is talking more than permitted time",
				1, "call in progress dropped");
		drools.assertRetracted(call);
		
		drools.assertNoScheduledActivations();
		drools.assertAllRetracted();
	}
	
	@Test
	@TestRules(expected = "input call")
	public void testAssertActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
	}
	
	@Test
	@TestRules(expected = {
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped"
	}, checkScheduled = true)
	public void testAssertScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
	}
	
	@Test(expected = AssertionError.class)
	public void testAssertNoScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		drools.assertNoScheduledActivations();
	}
	
	@Test
	@TestRules(expected = {
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped" })
	public void testAwaitForAnyScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		drools.awaitForAny();
	}
	
	@Test(expected = AssertionError.class)
	public void testAwaitScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		drools.awaitFor("blah");
	}
	
	@Test(expected = AssertionError.class)
	public void testAwaitForAnyScheduledActivationsFailed() {
		drools.awaitForAny();
	}
	
	@Test(expected = AssertionError.class)
	public void testAssertActivatedFailed() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		drools.insertAndFire(new Dialing("33333", "44444"));
		drools.assertActivatedCount(1, "input call");
	}
	
	@Test
	@TestRules(expected = {
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
		drools.assertActivatedCount(
				2, "input call",
				1, "drop the call if caller is talking more than permitted time",
				1, "call in progress dropped");
		drools.assertRetracted(caller3Dial);
		call = drools.getObject(CallInProgress.class);
		assertEquals("33333", call.callerNumber);
		
		drools.assertFactsCount(1);
		drools.printFacts();
	}
	
	public static class Dialing {
		public String callerNumber;
		public String calleeNumber;
		
		public Dialing() {
		}
		
		public Dialing(String callerNumber, String calleeNumber) {
			this.callerNumber = callerNumber;
			this.calleeNumber = calleeNumber;
		}
	}
	
	public static class CallInProgress {
		public String callerNumber;
		public String calleeNumber;
		
		public CallInProgress() {
		}
		
		public CallInProgress(String callerNumber, String calleeNumber) {
			this.callerNumber = callerNumber;
			this.calleeNumber = calleeNumber;
		}
	}
	
	public static class CallDropped {
		public List<String> pair = new ArrayList<>();
		public String reason;
		
		public CallDropped(String callerNumber, String calleeNumber, String reason) {
			pair.add(calleeNumber);
			pair.add(callerNumber);
			this.reason = reason;
		}
	}
}
