package org.droolsassert;

import static com.google.common.base.Charsets.UTF_8;
import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.droolsassert.util.JsonUtils.fromYaml;
import static org.droolsassert.util.JsonUtils.toYaml;
import static org.joda.time.DateTimeConstants.SATURDAY;
import static org.joda.time.DateTimeConstants.SUNDAY;
import static org.junit.runners.model.MultipleFailureException.assertEmpty;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.droolsassert.util.MvelProcessor;
import org.joda.time.LocalDate;
import org.junit.Rule;
import org.junit.Test;
import org.kie.api.runtime.rule.FactHandle;
import org.mvel2.ParserContext;

import com.google.common.io.Resources;

@DroolsSession("classpath:/org/droolsassert/logicalEvents.drl")
public class ExtensionTest {
	
	@Rule
	public ExtendedDroolsAssert drools = new ExtendedDroolsAssert();
	
	@Test(expected = RuntimeException.class)
	public void testNoErrors() throws Exception {
		drools.insertAndFire(new RuntimeException("Something reported"));
		drools.assertNoErrors();
	}
	
	@Test
	@TestRules(expected = {})
	public void testInsertFromYaml() throws Exception {
		drools.insertFromYaml(Trade.class, "classpath:/org/droolsassert/trade.yaml");
		drools.fireAllRules();
	}
	
	public static class ExtendedDroolsAssert extends DroolsAssert {
		
		private MvelProcessor mvelProcessor = new ExtendedMvelProcessor();
		
		public void assertNoErrors() throws Exception {
			assertEmpty(new ArrayList<Throwable>(getObjects(Throwable.class)));
		}
		
		public FactHandle insertFromYaml(Class<?> type, String resource) {
			try {
				return super.insert(fromYaml(mvelProcessor.process(Resources.toString(resourceResolver.getResource(resource).getURL(), UTF_8)), type)).get(0);
			} catch (IOException e) {
				throw new RuntimeException(format("Cannot insert %s from %s", type.getSimpleName(), resource));
			}
		}
		
		@Override
		protected String factToString(Object fact) {
			return fact instanceof Throwable ? getStackTrace((Throwable) fact) : fact.getClass().getSimpleName() + SPACE + toYaml(fact);
		}
	}
	
	public static class ExtendedMvelProcessor extends MvelProcessor {
		
		@Override
		protected ParserContext parserContext() throws Exception {
			ParserContext parserContext = super.parserContext();
			parserContext.addImport("businessDay", ExtendedMvelProcessor.class.getMethod("businessDay", int.class));
			return parserContext;
		}
		
		@Override
		protected Map<String, Object> executionContext() {
			Map<String, Object> executionContext = super.executionContext();
			executionContext.put("T0", businessDay(0));
			executionContext.put("T1", businessDay(1));
			executionContext.put("T2", businessDay(2));
			executionContext.put("T3", businessDay(3));
			return executionContext;
		}
		
		public static LocalDate businessDay(int shift) {
			LocalDate result = LocalDate.now();
			while (isNonWorkingDay(result))
				result = result.plusDays(1);
			for (int i = 0; i < shift; i++) {
				result = result.plusDays(1);
				while (isNonWorkingDay(result))
					result = result.plusDays(1);
			}
			return result;
		}
		
		public static boolean isNonWorkingDay(LocalDate result) {
			return result.getDayOfWeek() == SATURDAY || result.getDayOfWeek() == SUNDAY;
		}
	}
	
	public static class Trade {
		public LocalDate tradeDate;
		public LocalDate settlementDate;
	}
}
