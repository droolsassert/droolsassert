package org.droolsassert;

import static org.junit.runners.model.MultipleFailureException.assertEmpty;

import java.util.ArrayList;

import org.junit.Rule;
import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@DroolsSession("classpath:/org/droolsassert/logicalEvents.drl")
public class ExtensionTest {
	
	@Rule
	public ExtendedDroolsAssert drools = new ExtendedDroolsAssert();
	
	@Test(expected = RuntimeException.class)
	public void testInt() throws Exception {
		drools.insertAndFire(new RuntimeException("Something reported"));
		drools.assertNoErrors();
	}
	
	public static class ExtendedDroolsAssert extends DroolsAssert {
		
		private ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
		
		@Override
		protected String factToString(Object fact) {
			try {
				return yaml.writeValueAsString(fact);
			} catch (JsonProcessingException e) {
				throw new RuntimeException("Cannot format to yaml", e);
			}
		}
		
		public void assertNoErrors() throws Exception {
			assertEmpty(new ArrayList<Throwable>(getObjects(Throwable.class)));
		}
	}
}
