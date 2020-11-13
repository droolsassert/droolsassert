package org.droolsassert;

import static java.util.concurrent.TimeUnit.SECONDS;

import org.droolsassert.SlidingTimeWindowTest.SensorReading;
import org.droolsassert.SlidingTimeWindowTest.TemperatureThreshold;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@DroolsSession("classpath:/org/droolsassert/slidingLengthWindow.drl")
public class SlidingLengthWindowTest {
	
	@Rule
	public DroolsAssert drools = new DroolsAssert();
	
	@Before
	public void before() {
		drools.setGlobal("stdout", System.out);
	}
	
	@Test
	@TestRules(expectedCount = { "2", "Sound the alarm if temperature rises above threshold" })
	public void testSlidingLengthWindow() {
		drools.insert(new TemperatureThreshold(2));
		
		for (int i = 0; i < 6; i++) {
			drools.insertAndFire(new SensorReading(i));
			drools.advanceTime(1, SECONDS);
		}
		
		drools.assertFactsCount(7);
		drools.printFacts();
	}
}
