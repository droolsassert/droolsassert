package org.droolsassert;

import static java.lang.Math.PI;
import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.util.concurrent.TimeUnit.HOURS;
import static org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals;
import static org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@DroolsSession("org/droolsassert/insertLogical.drl")
public class InsertLogicalTest extends DroolsAssert {
	
	@Rule
	public DroolsAssert droolsAssert = this;
	
	@Before
	public void before() {
		setGlobal("stdout", System.out);
	}
	
	@Test
	public void testInsertLogical() throws InterruptedException {
		advanceTime(3, HOURS);
		for (double i = 0; i > -3 * PI; i -= PI / 6) {
			insertAndFire(new SensorData("sin", sin(i)));
			insertAndFire(new SensorData("cos", cos(i)));
			advanceTime(1, HOURS);
		}
		
		printPerformanceStatistic();
		printFacts();
	}
	
	@Test
	public void testInsertStated() throws InterruptedException {
		advanceTime(3, HOURS);
		int h = 3;
		for (double i = 0; i > -3 * PI; i -= PI / 6) {
			if (h == 8)
				// https://issues.redhat.com/browse/DROOLS-6072
				insertAndFire(new SensorAlarm("cos", "negative value"));
			insertAndFire(new SensorData("sin", sin(i)));
			insertAndFire(new SensorData("cos", cos(i)));
			advanceTime(1, HOURS);
			h++;
		}
		
		printPerformanceStatistic();
		printFacts();
	}
	
	public static class SensorData {
		private String isensorId;
		private double value;
		
		public SensorData(String sensorId, double value) {
			this.isensorId = sensorId;
			this.value = value;
		}
		
		public String getSensorId() {
			return isensorId;
		}
		
		public double getValue() {
			return value;
		}
	}
	
	public static class SensorAlarm {
		private String sensorId;
		private String message;
		
		public SensorAlarm(String sensorId, String message) {
			this.sensorId = sensorId;
			this.message = message;
		}
		
		public String getSensorId() {
			return sensorId;
		}
		
		public String getMessage() {
			return message;
		}
		
		@Override
		public boolean equals(Object obj) {
			return reflectionEquals(this, obj, false);
		}
		
		@Override
		public int hashCode() {
			return reflectionHashCode(this, false);
		}
	}
	
	public static class RedLightOn {
	}
}
