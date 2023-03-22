package org.droolsassert;

import static java.util.concurrent.TimeUnit.SECONDS;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;

@DroolsSession("classpath:/org/droolsassert/slidingTimeWindow.drl")
public class SlidingTimeWindowTest {
	
	@RegisterExtension
	public DroolsAssert drools = new DroolsAssert();
	
	@BeforeEach
	public void before() {
		drools.setGlobal("stdout", System.out);
	}
	
	@TestRules(expectedCount = { "5", "Sound the alarm if temperature rises above threshold" })
	public void testSlidingTimeWindow() {
		drools.insert(new TemperatureThreshold(2));
		
		for (int i = 0; i < 6; i++) {
			drools.insertAndFire(new SensorReading(i));
			drools.advanceTime(1, SECONDS);
		}
		
		drools.assertFactsCount(3);
		drools.printFacts();
	}
	
	public static class SensorReading {
		public double temperature;
		
		public SensorReading(double temperature) {
			this.temperature = temperature;
		}
		
		public double getTemperature() {
			return temperature;
		}
	}
	
	public static class TemperatureThreshold {
		public double max;
		
		public TemperatureThreshold(double max) {
			this.max = max;
		}
		
		public double getMax() {
			return max;
		}
	}
}
