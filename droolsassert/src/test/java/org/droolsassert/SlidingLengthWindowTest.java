package org.droolsassert;

import static java.util.concurrent.TimeUnit.SECONDS;

import org.droolsassert.SlidingTimeWindowTest.SensorReading;
import org.droolsassert.SlidingTimeWindowTest.TemperatureThreshold;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

@DroolsSession("classpath:/org/droolsassert/slidingLengthWindow.drl")
public class SlidingLengthWindowTest {
	
	@RegisterExtension
	public DroolsAssert drools = new DroolsAssert();
	
	@BeforeEach
	public void before() {
		drools.setGlobal("stdout", System.out);
	}
	
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
