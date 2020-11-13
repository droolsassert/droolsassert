package org.droolsassert;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Long.MAX_VALUE;
import static java.lang.String.format;
import static java.lang.System.out;
import static java.time.LocalTime.MIDNIGHT;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.sort;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.subtract;
import static org.apache.commons.io.FileUtils.forceMkdir;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.LF;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.commons.lang3.StringUtils.joinWith;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ZERO;
import static org.droolsassert.DroolsAssertUtils.firstNonEmpty;
import static org.droolsassert.DroolsAssertUtils.getExpectedCount;
import static org.droolsassert.DroolsAssertUtils.getResources;
import static org.droolsassert.DroolsAssertUtils.getRulesCountFromSource;
import static org.droolsassert.DroolsAssertUtils.getRulesFromSource;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.runners.model.MultipleFailureException.assertEmpty;
import static org.kie.internal.io.ResourceFactory.newUrlResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.droolsassert.jbehave.DroolsAssertSteps;
import org.droolsassert.report.ActivationReportBuilder;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.command.Command;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.DefaultRuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.rule.Agenda;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.builder.KnowledgeBuilderConfiguration;
import org.kie.internal.builder.conf.DumpDirOption;
import org.kie.internal.utils.KieHelper;
import org.springframework.core.io.Resource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

/**
 * JUnit {@link TestRule} for declarative drools tests.
 * 
 * <pre>
 * &#64;DroolsSession(resources = {
 *     "classpath&#42;:/org/droolsassert/rules.drl",
 *     "classpath&#42;:/com/company/project/&#42;/&#123;regex:.&#42;.(drl|dsl|xlsx|gdst)&#125;",
 *     "classpath&#42;:/com/company/project/&#42;/ruleUnderTest.rdslr" },
 *     ignoreRules = &#123; "before", "after" &#125;,
 *     logResources = true)
 * public class DroolsAssertTest {
 *     
 *     &#64;Rule
 *     public DroolsAssert drools = new DroolsAssert();
 *     
 *     &#64;Test
 *     &#64;TestRules(expected = "atomic int rule")
 *     public void testInt() {
 *        drools.insertAndFire(new AtomicInteger());
 *        assertEquals(1, drools.getObject(AtomicInteger.class).get());
 *     }
 * </pre>
 * 
 * You can omit rule object reference snippet if you can extend from {@code DroolsAssert}
 * 
 * <pre>
 * &#64;DroolsSession("org/droolsassert/complexEventProcessing.drl")
 * public class ComplexEventProcessingTest extends DroolsAssert {
 *     
 *     &#64;Rule
 *     public DroolsAssert droolsAssert = this;
 *     
 *     &#64;Before
 *         public void before() {
 *         setGlobal("stdout", System.out);
 *     }
 * 
 *     &#64;Test
 *     &#64;TestRules(expected = "input call")
 *         public void testAssertActivations() {
 *         insertAndFire(new Dialing("11111", "22222"));
 *     } 
 *     
 *     &#64;Test
 *     public void testCallsConnectAndDisconnectLogic() {
 *         Dialing caller1Dial = new Dialing("11111", "22222");
 *         insertAndFire(caller1Dial);
 *         assertRetracted(caller1Dial);
 *         CallInProgress call = getObject(CallInProgress.class);
 *         assertEquals("11111", call.callerNumber);
 *     
 *         advanceTime(5, MINUTES);
 *         Dialing caller3Dial = new Dialing("33333", "22222");
 *         insertAndFire(caller3Dial);
 *         assertExist(caller3Dial);
 *     
 *         advanceTime(5, SECONDS);
 *         assertExist(call, caller3Dial);
 *     
 *         advanceTime(5, SECONDS);
 *         assertExist(call);
 *         assertRetracted(caller3Dial);
 *     
 *         advanceTime(1, HOURS);
 *         assertRetracted(call);
 *     
 *         assertAllRetracted();
 *     }
 * </pre>
 * 
 * @see DroolsAssertSteps
 * @see <a href=https://github.com/droolsassert/droolsassert>Documentation on GitHub</a>
 */
public class DroolsAssert implements TestRule {
	protected static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
	protected static final DateTimeFormatter HH_MM_SS_SSS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	protected static final PathMatcher nameMatcher = new AntPathMatcher("\n");
	protected static Map<DroolsSession, KieBase> kieBases = new WeakHashMap<>();
	
	protected DroolsSession droolsSessionMeta;
	protected TestRules testRulesMeta;
	
	protected KieSession session;
	protected Agenda agenda;
	protected SessionPseudoClock clock;
	protected Map<String, Integer> activations;
	protected Map<String, Integer> activationsSnapshot;
	protected Set<String> ignored;
	protected Map<Object, Integer> factsHistory;
	protected RulesChronoAgendaEventListener rulesChrono;
	protected ActivationReportBuilder activationReportBuilder;
	
	/**
	 * Initializes new drools session based on meta data.<br>
	 * Must be paired with {@link #destroy()} to free-up resources.<br>
	 * Can be called multiple times paired with {@link #destroy()}
	 */
	public void init(DroolsSession droolsSessionMeta, TestRules testRulesMeta) {
		this.droolsSessionMeta = droolsSessionMeta;
		this.testRulesMeta = testRulesMeta;
		this.session = newSession(droolsSessionMeta);
		
		agenda = session.getAgenda();
		clock = session.getSessionClock();
		session.addEventListener(new LoggingAgendaEventListener());
		if (droolsSessionMeta.log())
			session.addEventListener(new LoggingWorkingMemoryEventListener());
		rulesChrono = rulesChrono();
		session.addEventListener(rulesChrono);
		
		activations = new LinkedHashMap<>();
		activationsSnapshot = new LinkedHashMap<>();
		initializeIgnoredActivations();
		factsHistory = new IdentityHashMap<>();
		activationReportBuilder = new ActivationReportBuilder(session, activations);
	}
	
	protected KieSession newSession(DroolsSession droolsSessionMeta) {
		try {
			return kieBase(droolsSessionMeta).newKieSession(sessionConfiguration(droolsSessionMeta), null);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot create new session", e);
		}
	}
	
	protected KieBase kieBase(DroolsSession droolsSessionMeta) throws IOException {
		if (kieBases.containsKey(droolsSessionMeta))
			return kieBases.get(droolsSessionMeta);
		
		synchronized (DroolsAssert.class) {
			KieHelper kieHelper = new KieHelper();
			kieHelper.setKieModuleModel(kieModule(builderConfiguration(droolsSessionMeta)));
			for (Resource resource : getResources(true, droolsSessionMeta.logResources(), firstNonEmpty(droolsSessionMeta.value(), droolsSessionMeta.resources())))
				kieHelper.addResource(newUrlResource(resource.getURL()));
			KieBase kieBase = kieHelper.build(baseConfiguration(droolsSessionMeta));
			
			kieBases.put(droolsSessionMeta, kieBase);
			return kieBase;
		}
	}
	
	protected KieModuleModel kieModule(Properties properties) throws IOException {
		KieModuleModel kmm = KieServices.Factory.get().newKieModuleModel();
		for (Entry<Object, Object> property : properties.entrySet()) {
			if (DumpDirOption.PROPERTY_NAME.equals(property.getKey()))
				forceMkdir(new File((String) property.getValue()));
			kmm.setConfigurationProperty((String) property.getKey(), (String) property.getValue());
		}
		return kmm;
	}
	
	public KieSession getSession() {
		return session;
	}
	
	public EntryPoint getEntryPoint(String entryPoint) {
		return checkNotNull(session.getEntryPoint(entryPoint), "No entry point instance associated with %s", entryPoint);
	}
	
	/**
	 * Returns an object of the specified class.
	 *
	 * @throws AssertionError
	 *             if object was not found or there are more than one instance of the class
	 */
	@SuppressWarnings("unchecked")
	public <T> T getObject(Class<T> clazz) {
		Collection<T> objects = getObjects(clazz);
		assertFalse(format("No object of type %s found", clazz.getSimpleName()), objects.isEmpty());
		assertFalse(format("Non-unique object of type %s found", clazz.getSimpleName()), objects.size() > 1);
		return (T) objects.toArray()[0];
	}
	
	/**
	 * Returns all objects of the class if found
	 */
	public <T> List<T> getObjects(Class<T> clazz) {
		return getObjects(obj -> clazz.isInstance(obj));
	}
	
	/**
	 * Returns all objects of the class if found
	 */
	public <T> List<T> getObjects(Class<T> clazz, Predicate<T> filter) {
		retractExpiredEvents();
		return (List<T>) session.getEntryPoints().stream()
				.flatMap(e -> e.getObjects(obj -> clazz.isInstance(obj)).stream())
				.map(obj -> clazz.cast(obj))
				.filter(filter).collect(toList());
	}
	
	/**
	 * Returns all objects of the class if found
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> getObjects(ObjectFilter filter) {
		retractExpiredEvents();
		return (List<T>) session.getEntryPoints().stream().flatMap(e -> e.getObjects(filter).stream()).collect(toList());
	}
	
	/**
	 * Move clock forward and trigger any scheduled rules.<br>
	 * Use second as a smallest time tick.
	 */
	public void advanceTime(long amount, TimeUnit unit) {
		advanceTime(SECONDS, unit.toSeconds(amount));
	}
	
	/**
	 * Move clock forward and trigger any scheduled rules.<br>
	 * Use time unit as a smallest time tick, make specified amount of ticks.
	 */
	public void advanceTime(TimeUnit unit, long amount) {
		for (int i = 0; i < amount; i++)
			tickTime(1, unit);
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less.
	 *
	 * @see #assertAllActivationsCount(Object...)
	 * @throws AssertionError
	 */
	public void assertAllActivations(String... expected) {
		Map<String, Integer> expectedMap = new LinkedHashMap<>();
		for (String rule : expected)
			expectedMap.put(rule, null);
		assertAllActivations(expectedMap);
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less.<br>
	 * Accepts the number of activations to assert.
	 * 
	 * @see #assertAllActivations(String...)
	 * @see #assertActivatedCount(Object...)
	 * @throws AssertionError
	 */
	public void assertAllActivationsCount(Object... expectedCount) {
		assertAllActivations(getExpectedCount(expectedCount));
	}
	
	public void assertAllActivations(Map<String, Integer> expectedCount) {
		assertActivations(expectedCount, activations);
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less <i>since previous check</i>.
	 *
	 * @see #assertActivated(Map)
	 * @see #awaitFor(String...)
	 * @throws AssertionError
	 */
	public void assertActivated(String... expected) {
		Map<String, Integer> expectedMap = new HashMap<>();
		for (String rule : expected)
			expectedMap.put(rule, null);
		assertActivated(expectedMap);
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less <i>since previous check</i>.<br>
	 * Accepts the number of activations to assert.
	 * 
	 * <pre>
	 * drools.assertActivatedCount(
	 * 		2, "input call",
	 * 		1, "drop the call if caller is talking more than permitted time",
	 * 		1, "call in progress dropped");
	 * </pre>
	 *
	 * @see #assertActivated(String...)
	 * @see #awaitFor(String...)
	 * @throws AssertionError
	 */
	public void assertActivatedCount(Object... expectedCount) {
		assertActivated(getExpectedCount(expectedCount));
	}
	
	public void assertActivated(Map<String, Integer> expectedCount) {
		Map<String, Integer> delta = getNewActivations(activationsSnapshot);
		activationsSnapshot = new LinkedHashMap<>(activations);
		assertActivations(expectedCount, delta);
	}
	
	protected final void assertActivations(Map<String, Integer> expectedActivations, Map<String, Integer> actualActiavtions) {
		List<String> missing = subtract(expectedActivations.keySet(), actualActiavtions.keySet()).stream()
				.filter(this::isEligibleForAssertion).collect(toList());
		List<String> extra = subtract(actualActiavtions.keySet(), expectedActivations.keySet()).stream()
				.filter(this::isEligibleForAssertion).collect(toList());
		
		if (!missing.isEmpty() && !extra.isEmpty())
			fail(formatUnexpectedCollection("Activation", "not triggered", missing) + LF + formatUnexpectedCollection("Activation", "triggered", extra));
		else if (!missing.isEmpty())
			fail(formatUnexpectedCollection("Activation", "not triggered", missing));
		else if (!extra.isEmpty())
			fail(formatUnexpectedCollection("Activation", "triggered", extra));
		
		for (Entry<String, Integer> actual : actualActiavtions.entrySet()) {
			Integer expected = expectedActivations.get(actual.getKey());
			if (expected != null && !expected.equals(actual.getValue()))
				fail(format("'%s' should be activated %s time(s) but actually it was activated %s time(s)", actual.getKey(), expected, actual.getValue()));
		}
	}
	
	/**
	 * Move clock forward until all listed rules will be triggered, fail if any was not triggered before threshold.<br>
	 * Use second as a smallest time tick and a day as a threshold.<br>
	 * It is imperative that all other activations which were part of the same agenda were also triggered, see below.
	 * <p>
	 * <i>Drools Developer's Cookbook (c):</i><br>
	 * People quite often misunderstand how Drools works internally. So, let's try to clarify how rules are "executed" really. Each time an object is inserted/updated/retracted in the working memory, or the facts are update/retracted within the rules, the rules are re-evaluated with the new working
	 * memory state. If a rule matches, it generates an Activation object. This Activation object is stored inside the Agenda until the fireAllRules() method is invoked. These objects are also evaluated when the WorkingMemory state changes to be possibly cancelled. Finally, when the fireAllRules()
	 * method is invoked the Agenda is cleared, executing the associated rule consequence of each Activation object.
	 * 
	 * @see #awaitForAny()
	 * @see #awaitFor(TimeUnit, long, String...)
	 * @see #triggerAllScheduledActivations()
	 * @throws AssertionError
	 *             if expected activation(s) was not be triggered within a day
	 */
	public void awaitFor(String... rulesToWait) {
		awaitFor(SECONDS, DAYS.toSeconds(1), rulesToWait);
	}
	
	/**
	 * Move clock forward until any upcoming scheduled activation will be triggered, fail if no rule was triggered before threshold.<br>
	 * Use second as a smallest time tick and a day as a threshold.
	 * 
	 * @see #awaitFor(String...)
	 * @throws AssertionError
	 *             if no rule was triggered within a day
	 */
	public void awaitForAny() {
		awaitFor(SECONDS, DAYS.toSeconds(1));
	}
	
	/**
	 * Move clock forward until all listed rules will be triggered, fail if any of the rule was not triggered before threshold.<br>
	 * Use time unit as a smallest time tick, make specified amount of ticks at maximum.
	 * 
	 * @see #awaitFor(String...)
	 * @see #triggerAllScheduledActivations()
	 * @throws AssertionError
	 *             if expected activation(s) will not be triggered within time period
	 */
	public void awaitFor(TimeUnit unit, long maxCount, String... rulesToWait) {
		Map<String, Integer> activationsSnapshot = new HashMap<>(activations);
		List<String> rules = asList(rulesToWait);
		for (int i = 0; i < maxCount; i++) {
			tickTime(1, unit);
			if (rules.isEmpty() && !getNewActivations(activationsSnapshot).isEmpty()
					|| !rules.isEmpty() && getNewActivations(activationsSnapshot).keySet().containsAll(rules))
				return;
		}
		
		fail(rules.isEmpty()
				? "Expected at least one scheduled activation"
				: formatUnexpectedCollection("Activations", "not scheduled", subtract(rules, getNewActivations(activationsSnapshot).keySet())));
	}
	
	/**
	 * Assert no activations will be triggered in future assuming no new facts
	 * 
	 * @see #triggerAllScheduledActivations()
	 * @throws AssertionError
	 */
	public void assertNoScheduledActivations() {
		Map<String, Integer> activationsSnapshot = new HashMap<>(activations);
		triggerAllScheduledActivations();
		List<String> diff = getNewActivations(activationsSnapshot).keySet().stream().filter(this::isEligibleForAssertion).collect(toList());
		assertTrue(formatUnexpectedCollection("Activation", "scheduled", diff), diff.isEmpty());
	}
	
	protected final void tickTime(long amount, TimeUnit unit) {
		clock.advanceTime(amount, unit);
		// https://issues.jboss.org/browse/DROOLS-2240
		session.fireAllRules();
	}
	
	/**
	 * Trigger all scheduled activations if any
	 * 
	 * @see #awaitFor(String...)
	 * @see #awaitFor(TimeUnit, long, String...)
	 * @see #assertNoScheduledActivations()
	 */
	public final void triggerAllScheduledActivations() {
		clock.advanceTime(MAX_VALUE, MILLISECONDS);
		session.fireAllRules();
		clock.advanceTime(-MAX_VALUE, MILLISECONDS);
	}
	
	protected final void retractExpiredEvents() {
		clock.advanceTime(1, MILLISECONDS);
		session.fireAllRules();
		clock.advanceTime(-1, MILLISECONDS);
	}
	
	/**
	 * New activations (delta) since previous check.
	 */
	protected final Map<String, Integer> getNewActivations(Map<String, Integer> activationsSnapshot) {
		Map<String, Integer> newActivations = new LinkedHashMap<>();
		for (Entry<String, Integer> activation : activations.entrySet()) {
			if (!activationsSnapshot.containsKey(activation.getKey()))
				newActivations.put(activation.getKey(), activation.getValue());
			else if (activation.getValue() > activationsSnapshot.get(activation.getKey()))
				newActivations.put(activation.getKey(), activation.getValue() - activationsSnapshot.get(activation.getKey()));
		}
		return newActivations;
	}
	
	/**
	 * Asserts object(s) presence in drools knowledge base.
	 * 
	 * @throws AssertionError
	 */
	public void assertExist(Object... objects) {
		Map<Object, Void> identityMap = new IdentityHashMap<>();
		stream(objects).forEach(obj -> identityMap.put(obj, null));
		
		if (droolsSessionMeta.keepFactsHistory()) {
			List<String> unknown = stream(objects).filter(obj -> !factsHistory.containsKey(obj)).map(this::factToString).collect(toList());
			assertTrue(formatUnexpectedCollection("Fact", "never inserted into the session", unknown), unknown.isEmpty());
		}
		
		retractExpiredEvents();
		session.getEntryPoints().stream().flatMap(e -> e.getObjects().stream()).forEach(obj -> identityMap.remove(obj));
		List<String> retracted = identityMap.keySet().stream().map(this::factToString).collect(toList());
		assertTrue(formatUnexpectedCollection("Fact", "removed from the session", retracted), retracted.isEmpty());
	}
	
	/**
	 * Asserts object(s) retracted from knowledge base in all partitions.
	 * 
	 * @throws AssertionError
	 */
	public void assertRetracted(Object... objects) {
		Map<Object, Void> identityMap = new IdentityHashMap<>();
		stream(objects).forEach(obj -> identityMap.put(obj, null));
		
		if (droolsSessionMeta.keepFactsHistory()) {
			List<String> unknown = stream(objects).filter(obj -> !factsHistory.containsKey(obj)).map(this::factToString).collect(toList());
			assertTrue(formatUnexpectedCollection("Fact", "never inserted into the session", unknown), unknown.isEmpty());
		}
		
		retractExpiredEvents();
		List<String> notRetracted = session.getEntryPoints().stream().flatMap(e -> e.getObjects().stream())
				.filter(obj -> identityMap.containsKey(obj)).map(this::factToString).collect(toList());
		assertTrue(formatUnexpectedCollection("Fact", "not retracted from the session", notRetracted), notRetracted.isEmpty());
	}
	
	/**
	 * Asserts all objects were retracted from knowledge base in all partitions.
	 * 
	 * @throws AssertionError
	 */
	public void assertAllRetracted() {
		retractExpiredEvents();
		List<String> facts = session.getEntryPoints().stream().flatMap(e -> e.getObjects().stream()).map(this::factToString).collect(toList());
		assertTrue(formatUnexpectedCollection("Fact", "not retracted from the session", facts), facts.isEmpty());
	}
	
	/**
	 * Asserts exact count of facts in knowledge base in all partitions.
	 * 
	 * @throws AssertionError
	 */
	public void assertFactsCount(long factsCount) {
		retractExpiredEvents();
		assertEquals(factsCount, session.getEntryPoints().stream().mapToLong(e -> e.getFactCount()).sum());
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
	
	/**
	 * @see KieSession#execute(Command)
	 */
	public <T> T execute(Command<T> command) {
		return session.execute(command);
	}
	
	/**
	 * Insert all objects listed
	 * 
	 * @see KieSession#insert(Object)
	 */
	public List<FactHandle> insert(Object... objects) {
		return insert(session, objects);
	}
	
	/**
	 * Insert all objects listed into entry point
	 * 
	 * @see EntryPoint#insert(Object)
	 */
	public List<FactHandle> insertTo(String entryPoint, Object... objects) {
		return insert(getEntryPoint(entryPoint), objects);
	}
	
	/**
	 * Insert all objects listed into entry point
	 * 
	 * @see EntryPoint#insert(Object)
	 */
	public List<FactHandle> insert(EntryPoint entryPoint, Object... objects) {
		List<FactHandle> factHandles = new LinkedList<>();
		for (Object object : objects)
			factHandles.add(entryPoint.insert(object));
		return factHandles;
	}
	
	/**
	 * @see KieSession#fireAllRules()
	 */
	public int fireAllRules() {
		if (droolsSessionMeta.log())
			out.println(formatTime() + " --> fireAllRules");
		return session.fireAllRules();
	}
	
	/**
	 * Insert all objects listed and fire all rules after each
	 * 
	 * @see KieSession#insert(Object)
	 * @see KieSession#fireAllRules()
	 */
	public List<FactHandle> insertAndFire(Object... objects) {
		return insertAndFire(session, objects);
	}
	
	/**
	 * Insert all objects listed into entry point and fire all rules after each
	 * 
	 * @see EntryPoint#insert(Object)
	 * @see KieSession#fireAllRules()
	 */
	public List<FactHandle> insertAndFireAt(String entryPoint, Object... objects) {
		return insertAndFire(getEntryPoint(entryPoint), objects);
	}
	
	/**
	 * Insert all objects listed into entry point and fire all rules after each
	 * 
	 * @see EntryPoint#insert(Object)
	 * @see KieSession#fireAllRules()
	 */
	public List<FactHandle> insertAndFire(EntryPoint entryPoint, Object... objects) {
		List<FactHandle> factHandles = new LinkedList<>();
		for (Object object : objects) {
			factHandles.add(entryPoint.insert(object));
			fireAllRules();
		}
		return factHandles;
	}
	
	/**
	 * Print retained facts in insertion order
	 * 
	 * @see DroolsSession#keepFactsHistory()
	 */
	public void printFacts() {
		retractExpiredEvents();
		List<Object> sortedFacts = session.getEntryPoints().stream().flatMap(e -> e.getObjects().stream()).collect(toList());
		if (droolsSessionMeta.keepFactsHistory())
			sort(sortedFacts, (o1, o2) -> factsHistory.get(o1).compareTo(factsHistory.get(o2)));
		out.println(format("%s Facts (%s):", formatTime(), sortedFacts.size()));
		for (Object fact : sortedFacts)
			out.println(factToString(fact));
	}
	
	public void printPerformanceStatistic() {
		out.println(format("%s Performance Statistic, total activations %s:", formatTime(), activations.values().stream().mapToInt(Integer::intValue).sum()));
		rulesChrono.getPerfStat().values()
				.forEach(s -> out.printf("%s - min: %.2f avg: %.2f max: %.2f activations: %d%n", s.getDomain(), s.getMinTimeMs(), s.getAvgTimeMs(), s.getMaxTimeMs(), s.getLeapsCount()));
	}
	
	@Override
	public Statement apply(Statement base, Description description) {
		init(
				checkNotNull(description.getTestClass().getAnnotation(DroolsSession.class), "Missing @DroolsSession definition"),
				description.getAnnotation(TestRules.class));
		
		activationReportBuilder.setReportName(description.getClassName() + "." + description.getMethodName());
		
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
		} finally {
			activationReportBuilder.buildReports();
		}
		if (testRulesMeta != null) {
			try {
				if (testRulesMeta.checkScheduled())
					triggerAllScheduledActivations();
				if (testRulesMeta.expectedCount().length != 0)
					assertAllActivations(getExpectedCount(testRulesMeta.expectedCount()));
				else if (isExpectedSet(testRulesMeta.expected()))
					assertAllActivations(testRulesMeta.expected());
				else if (!testRulesMeta.expectedCountSource().isEmpty())
					assertAllActivations(getRulesCountFromSource(getResources(true, false, testRulesMeta.expectedCountSource())));
				else if (!testRulesMeta.expectedSource().isEmpty())
					assertAllActivations(getRulesFromSource(getResources(true, false, testRulesMeta.expectedSource())));
			} catch (Throwable th) {
				errors.add(0, th);
			}
		}
		
		destroy();
		assertEmpty(errors);
	}
	
	public final void initializeIgnoredActivations() {
		ignored = new HashSet<>();
		ignoreActivations(droolsSessionMeta.ignoreRules());
		if (!droolsSessionMeta.ignoreRulesSource().isEmpty())
			ignoreActivations(getRulesFromSource(getResources(true, false, droolsSessionMeta.ignoreRulesSource())));
		if (testRulesMeta != null) {
			ignoreActivations(testRulesMeta.ignore());
			if (!testRulesMeta.ignoreSource().isEmpty())
				ignoreActivations(getRulesFromSource(getResources(true, false, testRulesMeta.ignoreSource())));
		}
	}
	
	protected final boolean isExpectedSet(String[] expected) {
		return expected.length != 1 || !EMPTY.equals(expected[0]);
	}
	
	public void destroy() {
		rulesChrono.reset();
		session.dispose();
	}
	
	protected KieSessionConfiguration sessionConfiguration(DroolsSession droolsSessionMeta) throws IOException {
		return KieServices.Factory.get().newKieSessionConfiguration(
				loadProperties(() -> this.defaultSessionProperties(), () -> droolsSessionMeta.sessionPropertySource(), () -> droolsSessionMeta.sessionProperties()));
	}
	
	protected KieBaseConfiguration baseConfiguration(DroolsSession droolsSessionMeta) throws IOException {
		return KieServices.Factory.get().newKieBaseConfiguration(
				loadProperties(() -> this.defaultBaseProperties(), () -> droolsSessionMeta.basePropertySource(), () -> droolsSessionMeta.baseProperties()));
	}
	
	/**
	 * @see KnowledgeBuilderConfiguration
	 */
	protected Properties builderConfiguration(DroolsSession droolsSessionMeta) throws IOException {
		return loadProperties(() -> this.defaultBuilderProperties(), () -> droolsSessionMeta.builderPropertySource(), () -> droolsSessionMeta.builderProperties());
	}
	
	protected Properties loadProperties(Supplier<String[]> defaultProperties, Supplier<String[]> propertySource, Supplier<String[]> propertyOverrides) throws IOException {
		Properties properties = new Properties();
		properties.load(new StringReader(joinWith(LF, defaultProperties.get())));
		for (Resource resource : getResources(false, droolsSessionMeta.logResources(), propertySource.get())) {
			try (Reader reader = new InputStreamReader(resource.getInputStream())) {
				properties.load(reader);
			}
		}
		properties.load(new StringReader(joinWith(LF, propertyOverrides.get())));
		return properties;
	}
	
	protected String[] defaultSessionProperties() {
		return new String[] { "drools.clockType = pseudo" };
	}
	
	protected String[] defaultBaseProperties() {
		return new String[] { "drools.eventProcessingMode = stream" };
	}
	
	protected String[] defaultBuilderProperties() {
		return new String[0]; // { "drools.dump.dir = target/drools-dump" };
	}
	
	protected RulesChronoAgendaEventListener rulesChrono() {
		return rulesChrono = new RulesChronoAgendaEventListener();
	}
	
	@SuppressWarnings("unchecked")
	public <T extends RulesChronoAgendaEventListener> T getRulesChrono() {
		return (T) rulesChrono;
	}
	
	public void setRulesChrono(RulesChronoAgendaEventListener rulesChrono) {
		session.removeEventListener(this.rulesChrono);
		session.addEventListener(rulesChrono);
		this.rulesChrono = rulesChrono;
	}
	
	public ActivationReportBuilder getActivationReportBuilder() {
		return activationReportBuilder;
	}
	
	protected boolean isEligibleForAssertion(String rule) {
		return !ignored.stream().filter(pattern -> nameMatcher.match(pattern, rule)).findFirst().isPresent();
	}
	
	protected String factToString(Object fact) {
		return fact instanceof String ? (String) fact : reflectionToString(fact, SHORT_PREFIX_STYLE);
	}
	
	protected String tupleToString(List<Object> tuple) {
		return "" + tuple.stream().map(o -> o.getClass().getSimpleName()).collect(toList());
	}
	
	protected final String formatUnexpectedCollection(String entityName, String message, Collection<String> entities) {
		return format("%s%s %s:%n%s", entityName, entities.size() == 1 ? " was" : "s were", message, join(entities, LF));
	}
	
	protected final String formatTime() {
		return MIDNIGHT.plus(Duration.ofMillis(clock.getCurrentTime() == MAX_VALUE ? -1 : clock.getCurrentTime()))
				.format(clock.getCurrentTime() == MAX_VALUE || clock.getCurrentTime() % 1000 == 0 ? HH_MM_SS : HH_MM_SS_SSS);
	}
	
	private class LoggingAgendaEventListener extends DefaultAgendaEventListener {
		@Override
		public void beforeMatchFired(BeforeMatchFiredEvent event) {
			String ruleName = event.getMatch().getRule().getName();
			activations.put(ruleName, firstNonNull(activations.get(ruleName), INTEGER_ZERO) + 1);
			if (droolsSessionMeta.log())
				out.printf("%s <-- '%s' has been activated by the tuple %s%n", formatTime(), ruleName, tupleToString(event.getMatch().getObjects()));
		}
	}
	
	private class LoggingWorkingMemoryEventListener extends DefaultRuleRuntimeEventListener {
		@Override
		public void objectInserted(ObjectInsertedEvent event) {
			Object fact = event.getObject();
			if (droolsSessionMeta.keepFactsHistory() && !factsHistory.containsKey(fact))
				factsHistory.put(fact, factsHistory.size());
			
			out.println(formatTime() + " --> inserted: " + (droolsSessionMeta.logFacts() ? factToString(fact) : fact.getClass().getSimpleName()));
		}
		
		@Override
		public void objectDeleted(ObjectDeletedEvent event) {
			Object fact = event.getOldObject();
			out.println(formatTime() + " --> retracted: " + (droolsSessionMeta.logFacts() ? factToString(fact) : fact.getClass().getSimpleName()));
		}
		
		@Override
		public void objectUpdated(ObjectUpdatedEvent event) {
			out.println(formatTime() + " --> updated: " + (droolsSessionMeta.logFacts()
					? format("%s%nto: %s", factToString(event.getOldObject()), factToString(event.getObject()))
					: event.getOldObject().getClass().getSimpleName()));
		}
	}
}