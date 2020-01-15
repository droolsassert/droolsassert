package org.droolsassert;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.lang.System.lineSeparator;
import static java.lang.System.out;
import static java.util.Arrays.asList;
import static java.util.Collections.sort;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.subtract;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ZERO;
import static org.drools.core.impl.KnowledgeBaseFactory.newKnowledgeSessionConfiguration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.runners.model.MultipleFailureException.assertEmpty;
import static org.kie.internal.io.ResourceFactory.newUrlResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

import org.drools.core.common.DefaultAgenda;
import org.drools.core.time.SessionPseudoClock;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.kie.api.KieBase;
import org.kie.api.command.Command;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.DefaultRuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.internal.utils.KieHelper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

/**
 * JUnit {@link TestRule} for declarative drools tests.
 * 
 * @see <a href=https://github.com/droolsassert/droolsassert>Documentation on GitHub</a>
 */
public class DroolsAssert implements TestRule {
	private static PathMatcher pathMatcher = new AntPathMatcher();
	private static PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
	protected static Map<DroolsSession, KieBase> kieBases = new WeakHashMap<>();
	
	protected DroolsSession droolsSessionMeta;
	protected AssertRules assertRulesMeta;
	
	protected KieSession session;
	protected DefaultAgenda agenda;
	protected SessionPseudoClock clock;
	protected Map<String, Integer> activations = new HashMap<>();
	protected Set<String> ignored = new HashSet<>();
	protected Map<Object, Integer> facts = new IdentityHashMap<>();
	
	protected void init(KieSession session) {
		this.session = session;
		agenda = (DefaultAgenda) session.getAgenda();
		clock = session.getSessionClock();
		session.addEventListener(new LoggingAgendaEventListener());
		session.addEventListener(new LoggingWorkingMemoryEventListener());
	}
	
	protected KieSession newSession(DroolsSession droolsSessionMeta) {
		Map<String, String> properties = defaultSessionProperties();
		properties.putAll(toMap(false, droolsSessionMeta.properties()));
		KieSessionConfiguration config = newKnowledgeSessionConfiguration();
		for (Map.Entry<String, String> property : properties.entrySet())
			config.setProperty(property.getKey(), property.getValue());
		
		return kieBase(droolsSessionMeta).newKieSession(config, null);
	}
	
	protected KieBase kieBase(DroolsSession droolsSessionMeta) {
		if (kieBases.containsKey(droolsSessionMeta))
			return kieBases.get(droolsSessionMeta);
		
		try {
			KieHelper kieHelper = new KieHelper();
			for (Resource resource : getResources(droolsSessionMeta))
				kieHelper.addResource(newUrlResource(resource.getURL()));
			kieBases.put(droolsSessionMeta, kieHelper.build());
			return kieBases.get(droolsSessionMeta);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot create new session", e);
		}
	}
	
	private List<Resource> getResources(DroolsSession droolsSessionMeta) throws IOException {
		List<Resource> resources = new ArrayList<>();
		for (String resourceNameFilter : firstNonEmpty(droolsSessionMeta.value(), droolsSessionMeta.resources()))
			resources.addAll(asList(resourceResolver.getResources(resourceNameFilter)));
		assertTrue("No resources found", resources.size() > 0);
		
		if (droolsSessionMeta.logResources())
			resources.forEach(resource -> out.println(resource));
		return resources;
	}
	
	public KieSession getSession() {
		return session;
	}
	
	@SuppressWarnings("unchecked")
	public <T> T getObject(Class<T> clazz) {
		Collection<T> objects = getObjects(clazz);
		assertFalse("No object of type found " + clazz.getSimpleName(), objects.isEmpty());
		assertFalse("Non-unique object of type found " + clazz.getSimpleName(), objects.size() > 1);
		return (T) objects.toArray()[0];
	}
	
	@SuppressWarnings("unchecked")
	public <T> Collection<T> getObjects(Class<T> clazz) {
		return (Collection<T>) session.getObjects(obj -> clazz.isInstance(obj));
	}
	
	public void advanceTime(long amount, TimeUnit unit) {
		clock.advanceTime(amount, unit);
		// https://issues.jboss.org/browse/DROOLS-2240
		session.fireAllRules();
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less.
	 */
	public void assertActivations(String... expected) {
		Map<String, Integer> expectedMap = new HashMap<>();
		for (String rule : expected)
			expectedMap.put(rule, null);
		assertActivations(expectedMap);
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less.<br>
	 * Accepts the number of activations to assert.
	 */
	public void assertActivations(Map<String, Integer> expectedCount) {
		Collection<String> missing = subtract(expectedCount.keySet(), activations.keySet()).stream()
				.filter(this::isEligibleForAssertion).collect(toSet());
		Collection<String> extra = subtract(activations.keySet(), expectedCount.keySet()).stream()
				.filter(this::isEligibleForAssertion).collect(toSet());
		
		if (!missing.isEmpty() && !extra.isEmpty())
			fail(format("expected: %s unexpected: %s", missing, extra));
		else if (!missing.isEmpty())
			fail(format("expected: %s", missing));
		else if (!extra.isEmpty())
			fail(format("unexpected: %s", extra));
		
		for (Entry<String, Integer> actual : activations.entrySet()) {
			Integer expected = expectedCount.get(actual.getKey());
			if (expected != null && !expected.equals(actual.getValue()))
				fail(format("'%s' should be activated %s time(s) but actually it was activated %s time(s)", actual.getKey(), expected, actual.getValue()));
		}
	}
	
	/**
	 * Asserts the only rules listed will be activated no more no less.<br>
	 * Waits for scheduled rules if any.
	 */
	public void awaitForActivations(String... expected) {
		Map<String, Integer> expectedMap = new HashMap<>();
		for (String rule : expected)
			expectedMap.put(rule, null);
		awaitForActivations(expectedMap);
	}
	
	/**
	 * Asserts the only rules listed will be activated no more no less.<br>
	 * Waits for scheduled rules if any.<br>
	 * Accepts the number of activations to assert.
	 */
	public void awaitForActivations(Map<String, Integer> expectedCount) {
		awaitForScheduledActivations();
		assertActivations(expectedCount);
	}
	
	/**
	 * Await for any scheduled activations.
	 */
	public void awaitForScheduledActivations() {
		if (agenda.getActivations().length != 0)
			out.println("awaiting for scheduled activations");
		while (agenda.getActivations().length != 0)
			advanceTime(50, MILLISECONDS);
	}
	
	public void assertNoScheduledActivations() {
		assertTrue("There are some scheduled activations.", agenda.getActivations().length == 0);
	}
	
	/**
	 * Asserts object presence in drools knowledge base.
	 */
	public void assertExists(Object object) {
		for (Object obj : session.getObjects()) {
			if (obj == object)
				return;
		}
		fail("Object was not found in the session " + factToString(object));
	}
	
	/**
	 * Asserts object was retracted from knowledge base.
	 */
	public void assertRetracted(Object retracted) {
		for (Object obj : session.getObjects()) {
			if (obj == retracted)
				fail("Object was not retracted from the session " + factToString(retracted));
		}
	}
	
	/**
	 * Asserts all objects were retracted from knowledge base.
	 */
	public void assertAllRetracted() {
		List<Object> facts = new LinkedList<>(session.getObjects());
		assertTrue("Objects were not retracted from the session: " + join(facts, lineSeparator()), facts.isEmpty());
	}
	
	/**
	 * Asserts exact count of facts in knowledge base.
	 */
	public void assertFactsCount(long factsCount) {
		assertEquals(factsCount, session.getFactCount());
	}
	
	/**
	 * Define rules to be ignored while any assertions.
	 */
	public void ignoreActivations(String... rulePatterns) {
		ignored.addAll(asList(rulePatterns));
	}
	
	/**
	 * Define global variables for drools session
	 */
	public void setGlobal(String identifier, Object value) {
		session.setGlobal(identifier, value);
	}
	
	public <T> T execute(Command<T> command) {
		return session.execute(command);
	}
	
	public List<FactHandle> insert(Object... objects) {
		List<FactHandle> factHandles = new LinkedList<>();
		for (Object object : objects)
			factHandles.add(session.insert(object));
		return factHandles;
	}
	
	public int fireAllRules() {
		out.println("--> fireAllRules");
		return session.fireAllRules();
	}
	
	public List<FactHandle> insertAndFire(Object... objects) {
		List<FactHandle> result = new LinkedList<>();
		for (Object object : objects) {
			result.addAll(insert(object));
			fireAllRules();
		}
		return result;
	}
	
	public void printFacts() {
		List<Object> sortedFacts = new LinkedList<>(session.getObjects());
		sort(sortedFacts, (o1, o2) -> facts.get(o1).compareTo(facts.get(o2)));
		out.println(format("Facts (%s):", session.getFactCount()));
		for (Object fact : sortedFacts)
			out.println(factToString(fact));
	}
	
	@Override
	public Statement apply(Statement base, Description description) {
		droolsSessionMeta = description.getTestClass().getAnnotation(DroolsSession.class);
		assertRulesMeta = description.getAnnotation(AssertRules.class);
		if (assertRulesMeta == null)
			return base;
		
		init(newSession(droolsSessionMeta));
		
		return new Statement() {
			@Override
			public void evaluate() throws Throwable {
				DroolsAssert.this.evaluate(base);
			}
		};
	}
	
	protected void evaluate(Statement base) throws Throwable {
		List<Throwable> errors = new ArrayList<>();
		try {
			base.evaluate();
		} catch (Throwable th) {
			errors.add(th);
		}
		try {
			ignoreActivations(droolsSessionMeta.ignoreRules());
			ignoreActivations(assertRulesMeta.ignore());
			if (assertRulesMeta.expectedCount().length != 0)
				awaitForActivations(toMap(true, assertRulesMeta.expectedCount()));
			else
				awaitForActivations(firstNonEmpty(assertRulesMeta.value(), assertRulesMeta.expected()));
		} catch (Throwable th) {
			errors.add(0, th);
		}
		session.dispose();
		assertEmpty(errors);
	}
	
	protected Map<String, String> defaultSessionProperties() {
		return toMap(false, "drools.eventProcessingMode", "stream", "drools.clockType", "pseudo");
	}
	
	protected boolean isEligibleForAssertion(String rule) {
		return !ignored.stream().filter(pattern -> pathMatcher.match(pattern, rule)).findFirst().isPresent();
	}
	
	protected String factToString(Object fact) {
		return reflectionToString(fact, SHORT_PREFIX_STYLE);
	}
	
	protected String tupleToString(List<Object> tuple) {
		return "" + tuple.stream().map(o -> o.getClass().getSimpleName()).collect(toList());
	}
	
	@SuppressWarnings("unchecked")
	private <T> Map<String, T> toMap(boolean convertToInt, String... params) {
		if (params.length % 2 != 0)
			throw new IllegalStateException();
		
		Map<String, T> map = new HashMap<>();
		for (int i = 0; i < params.length; i = i + 2)
			map.put(params[i], (T) (convertToInt ? parseInt(params[i + 1]) : params[i + 1]));
		return map;
	}
	
	private String[] firstNonEmpty(String[]... params) {
		for (String[] param : params) {
			if (param.length != 0)
				return param;
		}
		return new String[0];
	}
	
	private class LoggingAgendaEventListener extends DefaultAgendaEventListener {
		@Override
		public void beforeMatchFired(BeforeMatchFiredEvent event) {
			String ruleName = event.getMatch().getRule().getName();
			activations.put(ruleName, firstNonNull(activations.get(ruleName), INTEGER_ZERO) + 1);
			out.println(format("<-- '%s' has been activated by the tuple %s", ruleName, tupleToString(event.getMatch().getObjects())));
		}
	}
	
	private class LoggingWorkingMemoryEventListener extends DefaultRuleRuntimeEventListener {
		@Override
		public void objectInserted(ObjectInsertedEvent event) {
			Object fact = event.getObject();
			if (!facts.containsKey(fact))
				facts.put(fact, facts.size());
			out.println("--> inserted: " + (droolsSessionMeta.logFacts() ? factToString(fact) : fact.getClass().getSimpleName()));
		}
		
		@Override
		public void objectDeleted(ObjectDeletedEvent event) {
			Object fact = event.getOldObject();
			out.println("--> retracted: " + (droolsSessionMeta.logFacts() ? factToString(fact) : fact.getClass().getSimpleName()));
		}
		
		@Override
		public void objectUpdated(ObjectUpdatedEvent event) {
			out.println("--> updated: " + (droolsSessionMeta.logFacts()
					? format("%s%nto: %s", factToString(event.getOldObject()), factToString(event.getObject()))
					: event.getOldObject().getClass().getSimpleName()));
		}
	}
}