package org.droolsassert;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.junit.Assert.assertNotEquals;

import org.droolsassert.SpringIntegrationTest.AppConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { AppConfig.class })
@DroolsSession("classpath:/org/droolsassert/weather.drl")
public class SpringIntegrationTest {
	
	@Autowired
	private RestTemplate restTemplate;
	@Rule
	public DroolsAssert drools = new DroolsAssert();
	
	@Before
	public void before() {
		drools.setGlobal("weatherUrl", "https://api.agromonitoring.com/agro/1.0/weather?lat=35&lon=139&appid=f4bacddfb3de281a5b88f8fb4c6c4237");
		drools.setGlobal("restTemplate", restTemplate);
	}
	
	@Test
	@TestRules(expected = { "Check weather", "Humidity is high" })
	public void testWeatherInLongon() {
		drools.advanceTime(1, HOURS);
		assertNotEquals(0, drools.getObject(Weather.class).humidity);
		drools.printPerformanceStatistic();
	}
	
	public static class AppConfig {
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}
	}
	
	public static class WeatherResponse {
		public Weather main;
	}
	
	public static class Weather {
		public double temp;
		public int pressure;
		public int humidity;
		public double temp_min;
		public double temp_max;
	}
}