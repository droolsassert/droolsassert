package org.droolsassert;

import static java.util.concurrent.TimeUnit.HOURS;
import static org.drools.core.impl.KnowledgeBaseFactory.newKnowledgeSessionConfiguration;
import static org.junit.Assert.assertEquals;

import org.droolsassert.SpringIntegration2Test.AppConfig;
import org.droolsassert.SpringIntegrationTest.Weather;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.internal.io.ResourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.client.RestTemplate;

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = { AppConfig.class })
public class SpringIntegration2Test {
	
	@Autowired
	private KieContainer kieContainer;
	@Autowired
	private RestTemplate restTemplate;
	
	@Rule
	public DroolsAssert drools = new DroolsAssert() {
		@Override
		protected KieSession newSession(DroolsSession droolsSessionMeta) {
			KieSessionConfiguration config = newKnowledgeSessionConfiguration();
			config.setProperty("drools.clockType", "pseudo");
			config.setProperty("drools.eventProcessingMode", "stream");
			return kieContainer.newKieSession(config);
		};
	};
	
	@Before
	public void before() {
		drools.setGlobal("weatherUrl", "https://samples.openweathermap.org/data/2.5/weather?q=London,uk&appid=b6907d289e10d714a6e88b30761fae22");
		drools.setGlobal("restTemplate", restTemplate);
	}
	
	@Test
	@TestRules(expected = { "Check weather", "Humidity is high" })
	public void testWeatherInLongon() {
		drools.advanceTime(1, HOURS);
		assertEquals(81, drools.getObject(Weather.class).humidity);
	}
	
	public static class AppConfig {
		@Bean
		public RestTemplate restTemplate() {
			return new RestTemplate();
		}
		
		@Bean
		public KieContainer kieContainer() {
			KieServices kieServices = KieServices.Factory.get();
			
			KieFileSystem kieFileSystem = kieServices.newKieFileSystem();
			kieFileSystem.write(ResourceFactory.newClassPathResource("org/droolsassert/weather.drl"));
			KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
			kieBuilder.buildAll();
			KieModule kieModule = kieBuilder.getKieModule();
			
			return kieServices.newKieContainer(kieModule.getReleaseId());
		}
	}
}