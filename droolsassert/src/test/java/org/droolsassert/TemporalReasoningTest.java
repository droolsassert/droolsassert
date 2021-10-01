package org.droolsassert;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.Test;

@DroolsSession("classpath:/org/droolsassert/temporalReasoning.drl")
public class TemporalReasoningTest {
	
	@RegisterExtension
	public DroolsAssert drools = new DroolsAssert();
	
	@BeforeEach
	public void before() {
		drools.setGlobal("stdout", System.out);
	}
	
	@Test
	@TestRules(expected = {})
	public void testRegularHeartbeat() {
		Heartbeat heartbeat1 = new Heartbeat(1);
		drools.insertAndFireAt("MonitoringStream", heartbeat1);
		drools.advanceTime(5, SECONDS);
		drools.assertExist(heartbeat1);
		
		Heartbeat heartbeat2 = new Heartbeat(2);
		drools.insertAndFireAt("MonitoringStream", heartbeat2);
		drools.assertExist(heartbeat1, heartbeat2);
		drools.advanceTime(5, SECONDS);
		drools.assertDeleted(heartbeat1);
		drools.assertExist(heartbeat2);
		
		Heartbeat heartbeat3 = new Heartbeat(3);
		drools.insertAndFireAt("MonitoringStream", heartbeat3);
		drools.assertExist(heartbeat2, heartbeat3);
		drools.advanceTime(5, SECONDS);
		drools.assertDeleted(heartbeat2);
		drools.assertExist(heartbeat3);
		
		Heartbeat heartbeat4 = new Heartbeat(4);
		drools.insertAndFireAt("MonitoringStream", heartbeat4);
		drools.assertExist(heartbeat3, heartbeat4);
		drools.advanceTime(5, SECONDS);
		drools.assertDeleted(heartbeat3);
		drools.assertExist(heartbeat4);
		
		drools.assertFactsCount(1);
		assertEquals(4, drools.getObject(Heartbeat.class).ordinal);
		drools.printFacts();
	}
	
	@Test
	@TestRules(expectedCount = { "1", "Sound the Alarm" })
	public void testIrregularHeartbeat() {
		drools.insertAndFireAt("MonitoringStream", new Heartbeat(1));
		drools.advanceTime(5, SECONDS);
		drools.advanceTime(5, SECONDS);
		drools.insertAndFireAt("MonitoringStream", new Heartbeat(2), new Heartbeat(3));
		drools.advanceTime(5, SECONDS);
		
		drools.assertFactsCount(2);
		drools.printFacts();
	}
	
	public static class Heartbeat {
		public int ordinal;
		
		public Heartbeat(int ordinal) {
			this.ordinal = ordinal;
		}
	}
}
