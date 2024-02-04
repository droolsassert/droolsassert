package org.droolsassert;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Long.MAX_VALUE;
import static java.lang.String.format;
import static java.lang.System.out;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.Collections.sort;
import static java.util.Objects.nonNull;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.collections4.CollectionUtils.subtract;
import static org.apache.commons.io.FileUtils.forceMkdir;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.LF;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.join;
import static org.apache.commons.lang3.StringUtils.joinWith;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;
import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.apache.commons.lang3.math.NumberUtils.INTEGER_ZERO;
import static org.droolsassert.DroolsAssertUtils.firstNonEmpty;
import static org.droolsassert.DroolsAssertUtils.formatTime;
import static org.droolsassert.DroolsAssertUtils.getExpectedCount;
import static org.droolsassert.DroolsAssertUtils.getResources;
import static org.droolsassert.DroolsAssertUtils.getRulesCountFromSource;
import static org.droolsassert.DroolsAssertUtils.getRulesFromSource;
import static org.droolsassert.DroolsAssertUtils.getSimpleName;
import static org.droolsassert.jbehave.DroolsSessionProxy.newDroolsSessionProxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.kie.api.io.ResourceType.DRL;
import static org.kie.api.io.ResourceType.getResourceType;
import static org.kie.internal.io.ResourceFactory.newUrlResource;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Method;
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

import org.drools.core.common.InternalFactHandle;
import org.droolsassert.jbehave.DroolsAssertSteps;
import org.droolsassert.jbehave.DroolsSessionProxy;
import org.droolsassert.listeners.ActivationReportBuilder;
import org.droolsassert.listeners.DroolsassertListener;
import org.droolsassert.listeners.LoggingListener;
import org.droolsassert.listeners.StateTransitionBuilder;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestExecutionExceptionHandler;
import org.kie.api.KieBase;
import org.kie.api.KieBaseConfiguration;
import org.kie.api.KieServices;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.command.Command;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.DefaultRuleRuntimeEventListener;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
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
import org.opentest4j.AssertionFailedError;
import org.springframework.core.io.Resource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

/**
 * JUnit <a href="https://junit.org/junit5/docs/current/user-guide/#extensions">extension</a> for declarative drools tests.
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
 *     &#64;RegisterExtension
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
 * You can omit rule object references if you extend from {@code DroolsAssert}
 * 
 * <pre>
 * &#64;DroolsSession("org/droolsassert/complexEventProcessing.drl")
 * public class ComplexEventProcessingTest extends DroolsAssert {
 *     
 *     &#64;RegisterExtension
 *     public DroolsAssert droolsAssert = this;
 *     
 *     &#64;Before
 *     public void before() {
 *         setGlobal("stdout", System.out);
 *     }
 * 
 *     &#64;Test
 *     &#64;TestRules(expected = "input call")
 *     public void testAssertActivations() {
 *         insertAndFire(new Dialing("11111", "22222"));
 *     } 
 *     
 *     &#64;Test
 *     public void testCallsConnectAndDisconnectLogic() {
 *         Dialing caller1Dial = new Dialing("11111", "22222");
 *         insertAndFire(caller1Dial);
 *         assertDeleted(caller1Dial);
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
 *         assertDeleted(caller3Dial);
 *     
 *         advanceTime(1, HOURS);
 *         assertDeleted(call);
 *     
 *         assertAllDeleted();
 *     }
 * </pre>
 * 
 * @see DroolsAssertSteps
 * @see <a href=https://github.com/droolsassert>Documentation on GitHub</a>
 */
public class DroolsAssert implements BeforeEachCallback, AfterEachCallback, TestExecutionExceptionHandler {
	private static final String parameterizedScenarioNameRegex = ".*?\\[(\\d+).*";
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
	protected List<DroolsassertListener> listeners;
	
	private List<Throwable> errors = new ArrayList<>();
	
	/**
	 * Initializes new drools session based on meta data.<br>
	 * Must be paired with {@link #destroy()} to free-up resources.<br>
	 * Can be called multiple times paired with {@link #destroy()}
	 */
	public void init(DroolsSession droolsSessionMeta, TestRules testRulesMeta) {
		this.droolsSessionMeta = defaultIfNull(droolsSessionMeta, newDroolsSessionProxy(new DroolsSessionProxy()));
		this.testRulesMeta = testRulesMeta;
		this.session = newSession(this.droolsSessionMeta);
		
		agenda = session.getAgenda();
		clock = session.getSessionClock();
		session.addEventListener(new ActivationsTracker());
		if (this.droolsSessionMeta.keepFactsHistory())
			session.addEventListener(new FactsHistoryTracker());
		rulesChrono = rulesChrono();
		activations = new LinkedHashMap<>();
		activationsSnapshot = new LinkedHashMap<>();
		initializeIgnoredActivations();
		factsHistory = new IdentityHashMap<>();
		
		listeners = listeners();
		listeners.stream().filter(AgendaEventListener.class::isInstance).forEach(r -> session.addEventListener((AgendaEventListener) r));
		listeners.stream().filter(RuleRuntimeEventListener.class::isInstance).forEach(r -> session.addEventListener((RuleRuntimeEventListener) r));
		listeners.stream().filter(ProcessEventListener.class::isInstance).forEach(r -> session.addEventListener((ProcessEventListener) r));
		
		session.addEventListener(rulesChrono);
	}
	
	protected KieSession newSession(DroolsSession droolsSessionMeta) {
		try {
			return kieBase(droolsSessionMeta).newKieSession(sessionConfiguration(droolsSessionMeta), null);
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot create new session", e);
		}
	}
	
	protected KieBase kieBase(DroolsSession droolsSessionMeta) throws IOException {
		if (kieBases.containsKey(droolsSessionMeta))
			return kieBases.get(droolsSessionMeta);
		
		synchronized (DroolsAssert.class) {
			KieHelper kieHelper = new KieHelper();
			kieHelper.setKieModuleModel(kieModule(builderConfiguration(droolsSessionMeta)));
			
			String[] source = droolsSessionMeta.source();
			for (Resource resource : getResources(source.length == 0, droolsSessionMeta.logResources(), firstNonEmpty(droolsSessionMeta.value(), droolsSessionMeta.resources())))
				kieHelper.addResource(newUrlResource(resource.getURL()));
			
			if (source.length == 1) {
				kieHelper.addContent(source[0], DRL);
			} else {
				checkArgument(source.length % 2 == 0, "Unexpected number of arguments for @DroolsSession.source");
				for (int i = 0; i < source.length; i = i + 2)
					kieHelper.addContent(source[i + 1], getResourceType(source[i]));
			}
			
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
		assertFalse(objects.isEmpty(), format("No object of type %s found", getSimpleName(clazz)));
		assertFalse(objects.size() > 1, format("Non-unique object of type %s found", getSimpleName(clazz)));
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
		deleteExpiredEvents();
		return (List<T>) session.getEntryPoints().stream()
				.flatMap(e -> e.getObjects(obj -> clazz.isInstance(obj)).stream())
				.map(obj -> clazz.cast(obj))
				.filter(filter).collect(toList());
	}
	
	/**
	 * Returns all objects found
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> getObjects(ObjectFilter filter) {
		deleteExpiredEvents();
		return (List<T>) session.getEntryPoints().stream().flatMap(e -> e.getObjects(filter).stream()).collect(toList());
	}
	
	/**
	 * Returns all objects
	 */
	@SuppressWarnings("unchecked")
	public <T> List<T> getObjects() {
		deleteExpiredEvents();
		return (List<T>) session.getEntryPoints().stream().flatMap(e -> e.getObjects().stream()).collect(toList());
	}
	
	public InternalFactHandle getFactHandle(Object o) {
		return session.getEntryPoints().stream()
				.map(e -> e.getFactHandle(o))
				.filter(h -> nonNull(h))
				.map(InternalFactHandle.class::cast)
				.findFirst().orElse(null);
	}
	
	public InternalFactHandle getFactHandle(Class<?> clazz) {
		return getFactHandle(getObject(clazz));
	}
	
	public List<InternalFactHandle> getFactHandles(Class<?> clazz) {
		return getObjects(clazz).stream()
				.map(this::getFactHandle)
				.collect(toList());
	}
	
	public <T> List<InternalFactHandle> getFactHandles(Class<T> clazz, Predicate<T> filter) {
		return getObjects(clazz, filter).stream()
				.map(this::getFactHandle)
				.collect(toList());
	}
	
	public List<InternalFactHandle> getFactHandles(ObjectFilter filter) {
		return getObjects(filter).stream()
				.map(this::getFactHandle)
				.collect(toList());
	}
	
	/**
	 * Move clock forward and trigger any scheduled activations.<br>
	 * Use second as a smallest time tick.
	 */
	public void advanceTime(long amount, TimeUnit unit) {
		advanceTime(SECONDS, unit.toSeconds(amount));
	}
	
	/**
	 * Move clock forward and trigger any scheduled activations.<br>
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
			fail(formatUnexpectedCollection("Rule", "not activated", missing) + LF + formatUnexpectedCollection("\nRule", "activated", extra));
		else if (!missing.isEmpty())
			fail(formatUnexpectedCollection("Rule", "not activated", missing));
		else if (!extra.isEmpty())
			fail(formatUnexpectedCollection("Rule", "activated", extra));
		
		for (Entry<String, Integer> actual : actualActiavtions.entrySet()) {
			Integer expected = expectedActivations.get(actual.getKey());
			if (expected != null && !expected.equals(actual.getValue()))
				fail(format("'%s' should be activated %s time(s) but actually it was activated %s time(s)", actual.getKey(), expected, actual.getValue()));
		}
	}
	
	/**
	 * Move clock forward until all listed rules will be activated, fail if any of the rules was not activated before threshold.<br>
	 * Use second as a smallest time tick and a day as a threshold.<br>
	 * It is imperative that all other rules which are part of the same agenda will be also executed, see below.
	 * <p>
	 * <i>Drools Developer's Cookbook (c):</i><br>
	 * People quite often misunderstand how Drools works internally. So, let's try to clarify how rules are "executed" really. Each time an object is inserted/updated/deleted in
	 * the working memory, or the facts are update/deleted within the rules, the rules are re-evaluated with the new working memory state. If a rule matches, it generates an
	 * Activation object. This Activation object is stored inside the Agenda until the fireAllRules() method is invoked. These objects are also evaluated when the WorkingMemory
	 * state changes to be possibly cancelled. Finally, when the fireAllRules() method is invoked the Agenda is cleared, executing the associated rule consequence of each
	 * Activation object.
	 * 
	 * @see #awaitForAny()
	 * @see #awaitFor(TimeUnit, long, String...)
	 * @see #triggerAllScheduledActivations()
	 * @throws AssertionError
	 *             if expected rule was not activated within a day
	 */
	public void awaitFor(String... rulesToWait) {
		awaitFor(SECONDS, DAYS.toSeconds(1), rulesToWait);
	}
	
	/**
	 * Move clock forward until any rule will be activated, fail if no rule was activated before threshold.<br>
	 * Use second as a smallest time tick and a day as a threshold.
	 * 
	 * @see #awaitFor(String...)
	 * @throws AssertionError
	 *             if no rule was activated within a day
	 */
	public void awaitForAny() {
		awaitFor(SECONDS, DAYS.toSeconds(1));
	}
	
	/**
	 * Move clock forward until all listed rules will be activated, fail if any of the rules was not activated before threshold.<br>
	 * Use time unit as a smallest time tick, make specified amount of ticks at maximum.
	 * 
	 * @see #awaitFor(String...)
	 * @see #triggerAllScheduledActivations()
	 * @throws AssertionError
	 *             if expected rule was not activated within time period
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
				: formatUnexpectedCollection("Activation", "not scheduled", subtract(rules, getNewActivations(activationsSnapshot).keySet())));
	}
	
	/**
	 * Assert no rules will be activated in future assuming no new facts
	 * 
	 * @see #triggerAllScheduledActivations()
	 * @throws AssertionError
	 */
	public void assertNoScheduledActivations() {
		Map<String, Integer> activationsSnapshot = new HashMap<>(activations);
		triggerAllScheduledActivations();
		List<String> diff = getNewActivations(activationsSnapshot).keySet().stream().filter(this::isEligibleForAssertion).collect(toList());
		assertTrue(diff.isEmpty(), formatUnexpectedCollection("Activation", "scheduled", diff));
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
	
	protected final void deleteExpiredEvents() {
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
			assertTrue(unknown.isEmpty(), formatUnexpectedCollection("Fact", "never inserted into the session", unknown));
		}
		
		deleteExpiredEvents();
		session.getEntryPoints().stream().flatMap(e -> e.getObjects().stream()).forEach(obj -> identityMap.remove(obj));
		List<String> deleted = identityMap.keySet().stream().map(this::factToString).collect(toList());
		assertTrue(deleted.isEmpty(), formatUnexpectedCollection("Fact", "removed from the session", deleted));
	}
	
	/**
	 * Asserts object(s) deleted from knowledge base in all partitions.
	 * 
	 * @throws AssertionError
	 */
	public void assertDeleted(Object... objects) {
		Map<Object, Void> identityMap = new IdentityHashMap<>();
		stream(objects).forEach(obj -> identityMap.put(obj, null));
		
		if (droolsSessionMeta.keepFactsHistory()) {
			List<String> unknown = stream(objects).filter(obj -> !factsHistory.containsKey(obj)).map(this::factToString).collect(toList());
			assertTrue(unknown.isEmpty(), formatUnexpectedCollection("Fact", "never inserted into the session", unknown));
		}
		
		deleteExpiredEvents();
		List<String> notDeleted = session.getEntryPoints().stream().flatMap(e -> e.getObjects().stream())
				.filter(obj -> identityMap.containsKey(obj)).map(this::factToString).collect(toList());
		assertTrue(notDeleted.isEmpty(), formatUnexpectedCollection("Fact", "not deleted from the session", notDeleted));
	}
	
	/**
	 * Asserts all objects were deleted from knowledge base in all partitions.
	 * 
	 * @throws AssertionError
	 */
	public void assertAllDeleted() {
		deleteExpiredEvents();
		List<String> facts = session.getEntryPoints().stream().flatMap(e -> e.getObjects().stream()).map(this::factToString).collect(toList());
		assertTrue(facts.isEmpty(), formatUnexpectedCollection("Fact", "not deleted from the session", facts));
	}
	
	/**
	 * Asserts exact count of facts in knowledge base in all partitions.
	 * 
	 * @throws AssertionError
	 */
	public void assertFactsCount(long factsCount) {
		deleteExpiredEvents();
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
		checkArgument(objects != null, "You cannot insert null object into drools knowledge base");
		List<FactHandle> factHandles = new LinkedList<>();
		for (int i = 0; i < objects.length; i++) {
			Object object = objects[i];
			checkArgument(object != null, "You cannot insert null object into drools knowledge base. Parameter " + (i + 1));
			factHandles.add(entryPoint.insert(object));
		}
		return factHandles;
	}
	
	/**
	 * Update all objects by their handles
	 * 
	 * @see EntryPoint#update(FactHandle, Object)
	 */
	public void update(FactHandle... handles) {
		update(asList(handles));
	}
	
	/**
	 * Update all objects by their handles
	 * 
	 * @see EntryPoint#update(FactHandle, Object)
	 */
	public void update(Collection<FactHandle> handles) {
		for (EntryPoint entryPoint : session.getEntryPoints()) {
			for (FactHandle factHandle : handles) {
				if (entryPoint.getObject(factHandle) != null)
					entryPoint.update(factHandle, ((InternalFactHandle) factHandle).getObject());
			}
		}
	}
	
	/**
	 * Update all objects by their handles
	 * 
	 * @see EntryPoint#update(FactHandle, Object)
	 */
	public void update(Object... objects) {
		for (EntryPoint entryPoint : session.getEntryPoints()) {
			for (Object object : objects) {
				FactHandle factHandle = entryPoint.getFactHandle(object);
				if (factHandle != null)
					entryPoint.update(factHandle, object);
			}
		}
	}
	
	/**
	 * Delete all objects by their handles
	 * 
	 * @see EntryPoint#delete(FactHandle)
	 */
	public void delete(FactHandle... handles) {
		delete(asList(handles));
	}
	
	/**
	 * Delete all objects by their handles
	 * 
	 * @see EntryPoint#delete(FactHandle)
	 */
	public void delete(Collection<FactHandle> handles) {
		for (EntryPoint entryPoint : session.getEntryPoints()) {
			for (FactHandle factHandle : handles) {
				if (entryPoint.getObject(factHandle) != null)
					entryPoint.delete(factHandle);
			}
		}
	}
	
	/**
	 * Delete all objects by their handles
	 * 
	 * @see EntryPoint#delete(FactHandle)
	 */
	public void delete(Object... objects) {
		for (EntryPoint entryPoint : session.getEntryPoints()) {
			for (Object object : objects) {
				FactHandle factHandle = entryPoint.getFactHandle(object);
				if (factHandle != null)
					entryPoint.delete(factHandle);
			}
		}
	}
	
	/**
	 * @see KieSession#fireAllRules()
	 */
	public int fireAllRules() {
		if (droolsSessionMeta.log())
			log("--> fireAllRules");
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
		checkArgument(objects != null, "You cannot insert null object into drools knowledge base");
		List<FactHandle> factHandles = new LinkedList<>();
		for (int i = 0; i < objects.length; i++) {
			Object object = objects[i];
			checkArgument(object != null, "You cannot insert null object into drools knowledge base. Parameter " + (i + 1));
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
		deleteExpiredEvents();
		List<Object> sortedFacts = session.getEntryPoints().stream().flatMap(e -> e.getObjects().stream()).collect(toList());
		if (droolsSessionMeta.keepFactsHistory())
			sort(sortedFacts, (o1, o2) -> factsHistory.get(o1).compareTo(factsHistory.get(o2)));
		
		StringBuilder sb = new StringBuilder(format("Facts (%s):", sortedFacts.size()));
		for (Object fact : sortedFacts) {
			sb.append("\n");
			sb.append(factToString(fact));
		}
		log(sb.toString());
	}
	
	public void printPerformanceStatistic() {
		StringBuilder sb = new StringBuilder(format("Performance Statistic, total activations %s:", activations.values().stream().mapToInt(Integer::intValue).sum()));
		rulesChrono.getPerfStat().values()
				.forEach(s -> sb.append(format("%n%s - min: %.2f avg: %.2f max: %.2f activations: %d", s.getFullName(), s.getMinTimeMs(), s.getAvgTimeMs(), s.getMaxTimeMs(), s.getLeapsCount())));
		log(sb.toString());
	}
	
	@Override
	public void beforeEach(ExtensionContext context) throws Exception {
		Class<?> clazz = context.getTestClass().get();
		Method method = context.getTestMethod().get();
		StringBuilder scenario = new StringBuilder(method.getName());
		if (context.getDisplayName().matches(parameterizedScenarioNameRegex))
			scenario.append(context.getDisplayName().replaceAll(parameterizedScenarioNameRegex, "[$1]"));
		init(clazz.getAnnotation(DroolsSession.class), method.getAnnotation(TestRules.class));
		listeners.forEach(l -> l.beforeScenario(getSimpleName(clazz), scenario.toString()));
	}
	
	@Override
	public void afterEach(ExtensionContext context) throws Exception {
		if (session == null)
			return;
		listeners.forEach(DroolsassertListener::afterScenario);
		
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
		rethrowMultiple(errors);
	}
	
	@Override
	public void handleTestExecutionException(ExtensionContext context, Throwable throwable) throws Throwable {
		errors.add(throwable);
	}
	
	public void rethrowMultiple(List<Throwable> errors) throws Exception {
		if (errors.size() == 1 && errors.get(0) instanceof Exception) {
			throw (Exception) errors.get(0);
		} else if (errors.size() == 1 && errors.get(0) instanceof Error) {
			throw (Error) errors.get(0);
		} else if (!errors.isEmpty()) {
			StringBuilder sb = new StringBuilder("Failures detected:\n");
			for (Throwable e : errors)
				sb.append(format("\n%s", getStackTrace(e)));
			throw new AssertionFailedError(sb.toString());
		}
	}
	
	private void initializeIgnoredActivations() {
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
	
	/**
	 * Set rules chrono listener last to exclude other listeners (if any) processing time in performance results
	 */
	public void setRulesChrono(RulesChronoAgendaEventListener rulesChrono) {
		session.removeEventListener(this.rulesChrono);
		session.addEventListener(rulesChrono);
		this.rulesChrono = rulesChrono;
	}
	
	/**
	 * Another integration point to {@link DroolsAssert}. Override to specify set of listeners being registered.<br>
	 * Listener can optionally implement one or few of {@link AgendaEventListener}, {@link RuleRuntimeEventListener}, {@link ProcessEventListener} which will cause the listener to
	 * be subscribed for respective session notifications<br>
	 */
	protected List<DroolsassertListener> listeners() {
		return asList(
				new LoggingListener(droolsSessionMeta, this),
				new ActivationReportBuilder(session, activations),
				new StateTransitionBuilder(droolsSessionMeta, this, clock))
						.stream().filter(DroolsassertListener::enabled).collect(toList());
	}
	
	public List<DroolsassertListener> getListeners() {
		return listeners;
	}
	
	protected boolean isEligibleForAssertion(String rule) {
		return !ignored.stream().filter(pattern -> nameMatcher.match(pattern, rule)).findFirst().isPresent();
	}
	
	public String factToString(Object fact) {
		return fact instanceof String ? (String) fact : reflectionToString(fact, SHORT_PREFIX_STYLE);
	}
	
	protected final String formatUnexpectedCollection(String entityName, String message, Collection<String> entities) {
		return format("%s%s %s:%n%s", entityName, entities.size() == 1 ? " was" : "s were", message, join(entities, LF));
	}
	
	public void log(String message) {
		out.println(formatTime(clock) + SPACE + message);
	}
	
	private class ActivationsTracker extends DefaultAgendaEventListener {
		@Override
		public void beforeMatchFired(BeforeMatchFiredEvent event) {
			String ruleName = event.getMatch().getRule().getName();
			activations.put(ruleName, firstNonNull(activations.get(ruleName), INTEGER_ZERO) + 1);
		}
	}
	
	private class FactsHistoryTracker extends DefaultRuleRuntimeEventListener {
		@Override
		public void objectInserted(ObjectInsertedEvent event) {
			factsHistory.putIfAbsent(event.getObject(), factsHistory.size());
		}
	}
}