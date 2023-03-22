package org.droolsassert;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.droolsassert.ComplexEventProcessingTest.CallDropped;
import org.droolsassert.ComplexEventProcessingTest.CallInProgress;
import org.droolsassert.ComplexEventProcessingTest.Dialing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

@DroolsSession("org/droolsassert/complexEventProcessing.drl")
public class ComplexEventProcessing2Test extends DroolsAssert {
	
	@RegisterExtension
	public DroolsAssert droolsAssert = this;
	
	@BeforeEach
	public void before() {
		setGlobal("stdout", System.out);
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogic() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		insertAndFire(caller1Dial);
		assertDeleted(caller1Dial);
		CallInProgress call = getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		advanceTime(5, MINUTES);
		Dialing caller3Dial = new Dialing("33333", "22222");
		insertAndFire(caller3Dial);
		assertExist(caller3Dial);
		
		advanceTime(5, SECONDS);
		assertExist(call, caller3Dial);
		
		advanceTime(5, SECONDS);
		assertExist(call);
		assertDeleted(caller3Dial);
		
		advanceTime(1, HOURS);
		assertDeleted(call);
		
		assertAllDeleted();
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogic2() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		insertAndFire(caller1Dial);
		assertDeleted(caller1Dial);
		CallInProgress call = getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		advanceTime(5, MINUTES);
		Dialing caller3Dial = new Dialing("33333", "22222");
		insertAndFire(caller3Dial);
		assertExist(caller3Dial);
		
		advanceTime(5, SECONDS);
		assertExist(call, caller3Dial);
		CallDropped callDropped = new CallDropped("11111", "22222", "Dismissed");
		insertAndFire(callDropped);
		assertDeleted(call, caller3Dial, callDropped);
		CallInProgress call2 = getObject(CallInProgress.class);
		assertExist(call2);
		
		advanceTime(10, SECONDS);
		assertExist(call2);
		
		advanceTime(1, HOURS);
		assertDeleted(call2);
		
		assertAllDeleted();
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogicStickToEvents() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		insertAndFire(caller1Dial);
		assertActivated("input call");
		assertDeleted(caller1Dial);
		CallInProgress call = getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		Dialing caller3Dial = new Dialing("33333", "22222");
		insertAndFire(caller3Dial);
		assertActivated();
		assertExist(call, caller3Dial);
		
		awaitFor("drop dial-up if callee is talking");
		assertActivated("drop dial-up if callee is talking", "input call dropped");
		assertExist(call);
		assertDeleted(caller3Dial);
		
		awaitFor("drop the call if caller is talking more than permitted time");
		assertActivatedCount(
				1, "drop the call if caller is talking more than permitted time",
				1, "call in progress dropped");
		assertDeleted(call);
		
		assertNoScheduledActivations();
		assertAllDeleted();
	}
	
	@TestRules(expected = "input call")
	public void testAssertActivations() {
		insertAndFire(new Dialing("11111", "22222"));
	}
	
	@TestRules(expected = {
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped"
	}, checkScheduled = true)
	public void testAssertScheduledActivations() {
		insertAndFire(new Dialing("11111", "22222"));
	}
	
	@Test
	public void testAssertNoScheduledActivations() {
		insertAndFire(new Dialing("11111", "22222"));
		assertThrows(AssertionError.class, () -> assertNoScheduledActivations());
	}
	
	@TestRules(expected = {
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped" })
	public void testAwaitForAnyScheduledActivations() {
		insertAndFire(new Dialing("11111", "22222"));
		awaitForAny();
	}
	
	@Test
	public void testAwaitScheduledActivations() {
		insertAndFire(new Dialing("11111", "22222"));
		assertThrows(AssertionError.class, () -> awaitFor("blah"));
	}
	
	@Test
	public void testAwaitForAnyScheduledActivationsFailed() {
		assertThrows(AssertionError.class, () -> awaitForAny());
	}
	
	@Test
	public void testAssertActivatedFailed() {
		insertAndFire(new Dialing("11111", "22222"));
		insertAndFire(new Dialing("33333", "44444"));
		assertThrows(AssertionError.class, () -> assertActivatedCount(1, "input call"));
	}
	
	@TestRules(expected = {
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped"
	})
	public void testAwaitForScheduledActivations() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		insertAndFire(caller1Dial);
		assertDeleted(caller1Dial);
		CallInProgress call = getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		awaitFor("drop the call if caller is talking more than permitted time");
		assertNoScheduledActivations();
		assertAllDeleted();
		
		Dialing caller3Dial = new Dialing("33333", "22222");
		insertAndFire(caller3Dial);
		assertActivatedCount(
				2, "input call",
				1, "drop the call if caller is talking more than permitted time",
				1, "call in progress dropped");
		assertDeleted(caller3Dial);
		call = getObject(CallInProgress.class);
		assertEquals("33333", call.callerNumber);
		
		assertFactsCount(1);
		printFacts();
	}
}
