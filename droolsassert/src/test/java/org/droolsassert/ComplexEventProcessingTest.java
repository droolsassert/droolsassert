package org.droolsassert;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@DroolsSession("org/droolsassert/complexEventProcessing.drl")
public class ComplexEventProcessingTest {
	
	@RegisterExtension
	public DroolsAssert drools = new DroolsAssert();
	
	@BeforeEach
	public void before() {
		drools.setGlobal("stdout", System.out);
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogic() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		drools.insertAndFire(caller1Dial);
		drools.assertDeleted(caller1Dial);
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
		drools.assertDeleted(caller3Dial);
		
		drools.advanceTime(1, HOURS);
		drools.assertDeleted(call);
		
		drools.assertAllDeleted();
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogic2() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		drools.insertAndFire(caller1Dial);
		drools.assertDeleted(caller1Dial);
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
		drools.assertDeleted(call, caller3Dial, callDropped);
		CallInProgress call2 = drools.getObject(CallInProgress.class);
		drools.assertExist(call2);
		
		drools.advanceTime(10, SECONDS);
		drools.assertExist(call2);
		
		drools.advanceTime(1, HOURS);
		drools.assertDeleted(call2);
		
		drools.assertAllDeleted();
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogicStickToEvents() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		drools.insertAndFire(caller1Dial);
		drools.assertActivated("input call");
		drools.assertDeleted(caller1Dial);
		CallInProgress call = drools.getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		Dialing caller3Dial = new Dialing("33333", "22222");
		drools.insertAndFire(caller3Dial);
		drools.assertActivated();
		drools.assertExist(call, caller3Dial);
		
		drools.awaitFor("drop dial-up if callee is talking");
		drools.assertActivated("drop dial-up if callee is talking", "input call dropped");
		drools.assertExist(call);
		drools.assertDeleted(caller3Dial);
		
		drools.awaitFor("drop the call if caller is talking more than permitted time");
		drools.assertActivatedCount(
				1, "drop the call if caller is talking more than permitted time",
				1, "call in progress dropped");
		drools.assertDeleted(call);
		
		drools.assertNoScheduledActivations();
		drools.assertAllDeleted();
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
	
	@Test
	public void testAssertNoScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		assertThrows(AssertionError.class, () -> drools.assertNoScheduledActivations());
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
	
	@Test
	public void testAwaitScheduledActivations() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		assertThrows(AssertionError.class, () -> drools.awaitFor("blah"));
	}
	
	@Test
	public void testAwaitForAnyScheduledActivationsFailed() {
		assertThrows(AssertionError.class, () -> drools.awaitForAny());
	}
	
	@Test
	public void testAssertActivatedFailed() {
		drools.insertAndFire(new Dialing("11111", "22222"));
		drools.insertAndFire(new Dialing("33333", "44444"));
		assertThrows(AssertionError.class, () -> drools.assertActivatedCount(1, "input call"));
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
		drools.assertDeleted(caller1Dial);
		CallInProgress call = drools.getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		drools.awaitFor("drop the call if caller is talking more than permitted time");
		drools.assertNoScheduledActivations();
		drools.assertAllDeleted();
		
		Dialing caller3Dial = new Dialing("33333", "22222");
		drools.insertAndFire(caller3Dial);
		drools.assertActivatedCount(
				2, "input call",
				1, "drop the call if caller is talking more than permitted time",
				1, "call in progress dropped");
		drools.assertDeleted(caller3Dial);
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
