package org.droolsassert;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.Assert.assertEquals;

import org.droolsassert.ComplexEventProcessingTest.CallDropped;
import org.droolsassert.ComplexEventProcessingTest.CallInProgress;
import org.droolsassert.ComplexEventProcessingTest.Dialing;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@DroolsSession("org/droolsassert/complexEventProcessing.drl")
public class ComplexEventProcessing2Test extends DroolsAssert {
	
	@Rule
	public DroolsAssert droolsAssert = this;
	
	@Before
	public void before() {
		setGlobal("stdout", System.out);
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogic() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		insertAndFire(caller1Dial);
		assertRetracted(caller1Dial);
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
		assertRetracted(caller3Dial);
		
		advanceTime(1, HOURS);
		assertRetracted(call);
		
		assertAllRetracted();
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogic2() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		insertAndFire(caller1Dial);
		assertRetracted(caller1Dial);
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
		assertRetracted(call, caller3Dial, callDropped);
		CallInProgress call2 = getObject(CallInProgress.class);
		assertExist(call2);
		
		advanceTime(10, SECONDS);
		assertExist(call2);
		
		advanceTime(1, HOURS);
		assertRetracted(call2);
		
		assertAllRetracted();
	}
	
	@Test
	public void testCallsConnectAndDisconnectLogicStickToEvents() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		insertAndFire(caller1Dial);
		assertActivated("input call");
		assertRetracted(caller1Dial);
		CallInProgress call = getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		Dialing caller3Dial = new Dialing("33333", "22222");
		insertAndFire(caller3Dial);
		assertActivated();
		assertExist(call, caller3Dial);
		
		awaitFor("drop dial-up if callee is talking");
		assertActivated("drop dial-up if callee is talking", "input call dropped");
		assertExist(call);
		assertRetracted(caller3Dial);
		
		awaitFor("drop the call if caller is talking more than permitted time");
		assertActivatedCount(
				1, "drop the call if caller is talking more than permitted time",
				1, "call in progress dropped");
		assertRetracted(call);
		
		assertNoScheduledActivations();
		assertAllRetracted();
	}
	
	@Test
	@TestRules(expected = "input call")
	public void testAssertActivations() {
		insertAndFire(new Dialing("11111", "22222"));
	}
	
	@Test
	@TestRules(expected = {
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped"
	}, checkScheduled = true)
	public void testAssertScheduledActivations() {
		insertAndFire(new Dialing("11111", "22222"));
	}
	
	@Test(expected = AssertionError.class)
	public void testAssertNoScheduledActivations() {
		insertAndFire(new Dialing("11111", "22222"));
		assertNoScheduledActivations();
	}
	
	@Test
	@TestRules(expected = {
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped" })
	public void testAwaitForAnyScheduledActivations() {
		insertAndFire(new Dialing("11111", "22222"));
		awaitForAny();
	}
	
	@Test(expected = AssertionError.class)
	public void testAwaitScheduledActivations() {
		insertAndFire(new Dialing("11111", "22222"));
		awaitFor("blah");
	}
	
	@Test(expected = AssertionError.class)
	public void testAwaitForAnyScheduledActivationsFailed() {
		awaitForAny();
	}
	
	@Test(expected = AssertionError.class)
	public void testAssertActivatedFailed() {
		insertAndFire(new Dialing("11111", "22222"));
		insertAndFire(new Dialing("33333", "44444"));
		assertActivatedCount(1, "input call");
	}
	
	@Test
	@TestRules(expected = {
			"input call",
			"drop the call if caller is talking more than permitted time",
			"call in progress dropped"
	})
	public void testAwaitForScheduledActivations() {
		Dialing caller1Dial = new Dialing("11111", "22222");
		insertAndFire(caller1Dial);
		assertRetracted(caller1Dial);
		CallInProgress call = getObject(CallInProgress.class);
		assertEquals("11111", call.callerNumber);
		
		awaitFor("drop the call if caller is talking more than permitted time");
		assertNoScheduledActivations();
		assertAllRetracted();
		
		Dialing caller3Dial = new Dialing("33333", "22222");
		insertAndFire(caller3Dial);
		assertActivatedCount(
				2, "input call",
				1, "drop the call if caller is talking more than permitted time",
				1, "call in progress dropped");
		assertRetracted(caller3Dial);
		call = getObject(CallInProgress.class);
		assertEquals("33333", call.callerNumber);
		
		assertFactsCount(1);
		printFacts();
	}
}
