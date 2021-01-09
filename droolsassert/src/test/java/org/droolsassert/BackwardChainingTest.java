package org.droolsassert;

import static java.lang.System.out;
import static org.droolsassert.util.JsonUtils.toYaml;

import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.kie.api.definition.type.Position;

/**
 * <a href="https://www.baeldung.com/drools-backward-chaining">An Example of Backward Chaining in Drools</a>
 */
@DroolsSession("**/backwardChaining.drl")
public class BackwardChainingTest extends DroolsAssert {
	
	@Rule
	public DroolsAssert droolsAssert = this;
	private Result result;
	
	@Before
	public void before() {
		setGlobal("result", result = new Result());
	}
	
	@Test
	@TestRules(expected = { "Great Wall of China BELONGS TO Planet Earth", "print all facts" })
	public void testIt() {
		
		insert(new Fact("Asia", "Planet Earth"));
		insert(new Fact("China", "Asia"));
		insert(new Fact("Great Wall of China", "China"));
		fireAllRules();
		
		out.println(toYaml(result));
	}
	
	public static class Fact {
		@Position(0)
		private String element;
		@Position(1)
		private String place;
		
		public Fact(String element, String place) {
			this.element = element;
			this.place = place;
		}
		
		public String getElement() {
			return element;
		}
		
		public String getPlace() {
			return place;
		}
	}
	
	public class Result {
		private String value;
		private List<String> facts = new ArrayList<>();
		
		public void setValue(String value) {
			this.value = value;
		}
		
		public List<String> getFacts() {
			return facts;
		}
		
		public void addFact(String fact) {
			facts.add(fact);
		}
	}
}
