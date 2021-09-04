package org.droolsassert.jbehave;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.String.format;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.apache.commons.lang3.StringUtils.upperCase;
import static org.droolsassert.DroolsAssertUtils.getResources;
import static org.droolsassert.DroolsAssertUtils.getRulesCountFromSource;
import static org.droolsassert.DroolsAssertUtils.getRulesFromSource;
import static org.droolsassert.DroolsAssertUtils.parseCountOfRules;
import static org.droolsassert.jbehave.DroolsSessionProxy.newDroolsSessionProxy;
import static org.droolsassert.jbehave.TestRulesProxy.newTestRulesProxy;
import static org.droolsassert.util.JsonUtils.fromJson;
import static org.droolsassert.util.JsonUtils.fromYaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.droolsassert.DroolsAssert;
import org.droolsassert.DroolsAssertException;
import org.droolsassert.listeners.DroolsassertListener;
import org.droolsassert.util.MvelProcessor;
import org.jbehave.core.annotations.Alias;
import org.jbehave.core.annotations.Aliases;
import org.jbehave.core.annotations.Given;
import org.jbehave.core.annotations.Then;
import org.jbehave.core.annotations.When;
import org.jbehave.core.model.Scenario;
import org.jbehave.core.model.Story;
import org.jbehave.core.reporters.NullStoryReporter;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.google.common.base.Splitter;
import com.google.common.io.Resources;

/**
 * Example <a href="https://jbehave.org/">jbehave</a> test
 * 
 * <pre>
 * Drools assert story
 * 
 * Scenario: definitions
 * Given import java.util.concurrent.atomic
 * 
 * Given drools session 
 *     classpath&#42;:/org/droolsassert/rules.drl
 *     classpath&#42;:/com/company/project/&#42;/{regex:.&#42;.(drl|dsl|xlsx|gdst)}
 *     classpath&#42;:/com/company/project/&#42;/ruleUnderTest.rdslr
 * ignore rules: 'before', 'after'
 * log resources: true
 * 
 * 
 * Scenario: test int
 * Given new session for scenario
 * Given variable atomicInteger is new AtomicInteger()
 * When insert and fire atomicInteger
 * Then assert atomicInteger.get() is 1
 * Then there was single activation atomic int rule
 * 
 * 
 * Scenario: test long
 * Given new session for scenario
 * Given variable a1 is new AtomicInteger()
 * Given variable a2 is new AtomicLong()
 * Given variable a3 is new AtomicLong()
 * When insert facts a1, a2, a3
 * When fire all rules
 * Then count of facts is 3
 * Given variable listOfLong as AtomicLong objects from the session
 * Then assert listOfLong.size() is 2
 * Then all activations are 
 *     atomic int rule
 *     atomic long rule
 * </pre>
 */
public class DroolsAssertSteps<A extends DroolsAssert> extends NullStoryReporter {
	
	protected static final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
	
	protected static final String STRINGS_DELIM = "(\r?\n|(?<=')\\s*,\\s*(?='[^']*'))";
	protected static final String VARIABLES_DELIM = "(\r?\n|\\s*,\\s*)";
	protected static final String LHS_DELIM = "\\s+((is|as)( an?)?|equals?( to)?)\\s+";
	protected static final String SPACE = "\\s+";
	protected static final String NL = "\r?\n";
	protected static final String Q = "'";
	
	protected final Set<String> knownMimeTypes = knownMimeTypes();
	protected DroolsSessionProxy droolsSessionMeta;
	protected TestRulesProxy testRulesMeta;
	protected volatile MvelProcessor mvelProcessor;
	protected volatile Set<String> imports;
	protected volatile HashMap<String, Object> globals;
	protected volatile Story story;
	protected volatile Scenario scenario;
	protected volatile A drools;
	
	/**
	 * <pre>
	 * Given import java.util.concurrent.atomic
	 * </pre>
	 * 
	 * <pre>
	 * Given imports 
	 * 	java.util.concurrent.atomic
	 * 	org.droolsassert.SpringIntegrationTest
	 * </pre>
	 */
	@Given("import $imports")
	@Alias("imports $imports")
	public void givenImports(String imports) {
		Splitter.onPattern(NL).trimResults().omitEmptyStrings().split(imports)
				.forEach(line -> mvelProcessor.importPackage(line));
	}
	
	/**
	 * Drools session definition, suitable for many scenarios
	 * 
	 * <pre>
	 * Given drools session classpath:/org/droolsassert/complexEventProcessing.drl
	 * </pre>
	 * 
	 * <pre>
	 * Given drools session 
	 * 	classpath*:/org/droolsassert/rules.drl
	 * 	classpath*:/org/droolsassert/rules2.drl
	 * base property source: classpath:/kie.properties
	 * session property source: classpath:/session.properties
	 * ignore rules: 'before', 'after'
	 * log resources: true
	 * </pre>
	 */
	@Given("drools session $sessionMeta")
	public void givenDroolsSession(String sessionMeta) {
		droolsSessionMeta = new DroolsSessionProxy();
		List<String> resources = new ArrayList<>();
		List<String> sessionProperties = new ArrayList<>();
		List<String> sessionPropertySource = new ArrayList<>();
		List<String> baseProperties = new ArrayList<>();
		List<String> basePropertySource = new ArrayList<>();
		List<String> builderProperties = new ArrayList<>();
		List<String> builderPropertySource = new ArrayList<>();
		List<String> ignoreRules = new ArrayList<>();
		List<String> current = resources;
		
		for (String line : sessionMeta.split(NL)) {
			if (line.matches("\\s*session properties.*")) {
				line = line.replaceFirst("\\s*session properties:?", "");
				current = sessionProperties;
			} else if (line.matches("\\s*session property source.*")) {
				line = line.replaceFirst("\\s*session property source:?", "");
				current = sessionPropertySource;
			} else if (line.matches("\\s*base properties.*")) {
				line = line.replaceFirst("\\s*base properties:?", "");
				current = baseProperties;
			} else if (line.matches("\\s*base property source.*")) {
				line = line.replaceFirst("\\s*base property source:?", "");
				current = basePropertySource;
			} else if (line.matches("\\s*builder properties.*")) {
				line = line.replaceFirst("\\s*builder properties:?", "");
				current = builderProperties;
			} else if (line.matches("\\s*builder property source.*")) {
				line = line.replaceFirst("\\s*builder property source:?", "");
				current = builderPropertySource;
			} else if (line.matches("\\s*ignore rules source.*")) {
				droolsSessionMeta.ignoreRulesSource = line.replaceFirst("\\s*ignore rules source:?\\s+", "");
				continue;
			} else if (line.matches("\\s*ignore rules.*")) {
				line = line.replaceFirst("\\s*ignore rules:?", "");
				current = ignoreRules;
			} else if (line.matches("\\s*log resources:?(\\s|$).*")) {
				droolsSessionMeta.logResources = parseBoolean(line.replaceFirst("\\s*log resources:?(\\s|$)", ""));
				continue;
			} else if (line.matches("\\s*keep facts history:?(\\s|$).*")) {
				droolsSessionMeta.keepFactsHistory = parseBoolean(line.replaceFirst("\\s*keep facts history:?(\\s|$)", ""));
				continue;
			} else if (line.matches("\\s*log facts:?(\\s|$).*")) {
				droolsSessionMeta.logFacts = parseBoolean(line.replaceFirst("\\s*log facts:?(\\s|$)", ""));
				continue;
			} else if (line.matches("\\s*log:?(\\s|$).*")) {
				droolsSessionMeta.log = parseBoolean(line.replaceFirst("\\s*log:?(\\s|$)", ""));
				continue;
			} else if (line.matches("\\s*show state transition popup:?(\\s|$).*")) {
				droolsSessionMeta.showStateTransitionPopup = parseBoolean(line.replaceFirst("\\s*show state transition popup:?(\\s|$)", ""));
				continue;
			}
			if (line.isEmpty())
				continue;
			current.addAll(splitStrings(line));
		}
		if (!resources.isEmpty())
			droolsSessionMeta.resources = resources.toArray(new String[0]);
		if (!sessionProperties.isEmpty())
			droolsSessionMeta.sessionProperties = sessionProperties.toArray(new String[0]);
		if (!sessionPropertySource.isEmpty())
			droolsSessionMeta.sessionPropertySource = sessionPropertySource.toArray(new String[0]);
		if (!baseProperties.isEmpty())
			droolsSessionMeta.baseProperties = baseProperties.toArray(new String[0]);
		if (!basePropertySource.isEmpty())
			droolsSessionMeta.basePropertySource = basePropertySource.toArray(new String[0]);
		if (!builderProperties.isEmpty())
			droolsSessionMeta.builderProperties = builderProperties.toArray(new String[0]);
		if (!builderPropertySource.isEmpty())
			droolsSessionMeta.builderPropertySource = builderPropertySource.toArray(new String[0]);
		if (!ignoreRules.isEmpty())
			droolsSessionMeta.ignoreRules = ignoreRules.toArray(new String[0]);
	}
	
	/**
	 * Create new drools session for a scenario.
	 * 
	 * <pre>
	 * Scenario:  test 1
	 * Given new session for scenario
	 * 
	 * Scenario:  test 2
	 * Given new session for scenario, check scheduled, ignore '* int rule', 'other rule'
	 * 
	 * Scenario:  test 3
	 * Given new session for scenario, check scheduled, ignore source: &#42;&#42;/ignoreDroolsAssertTest.txt
	 * 
	 * Scenario:  test 4
	 * Given new session for scenario
	 * 	check scheduled
	 * 	ignore * ${with}(and)[??]
	 * </pre>
	 */
	@Given("new session for scenario$sessionMeta")
	public void givenNewSessionForScenario(String sessionMeta) {
		testRulesMeta = new TestRulesProxy();
		List<String> ignore = new ArrayList<>();
		
		for (String line : sessionMeta.split(NL)) {
			if (line.matches("\\s*,?\\s*check scheduled.*")) {
				line = line.replaceFirst("\\s*,?\\s*check scheduled", "");
				testRulesMeta.checkScheduled = true;
			}
			if (line.matches("\\s*,?\\s*ignore source.*")) {
				testRulesMeta.ignoreSource = line.replaceFirst("\\s*,?\\s*ignore source:?\\s+", "");
				continue;
			}
			if (line.matches("\\s*,?\\s*ignore:? .*"))
				line = line.replaceFirst("\\s*,?\\s*ignore:?\\s+", "");
			if (!line.isEmpty())
				ignore.addAll(splitStrings(line));
		}
		if (!ignore.isEmpty())
			testRulesMeta.ignore = ignore.toArray(new String[0]);
		
		drools.init(newDroolsSessionProxy(droolsSessionMeta), newTestRulesProxy(testRulesMeta));
		drools.getListeners().forEach(builder -> builder.beforeScenario(story.getPath(), scenario.getTitle()));
		globals.entrySet().forEach(e -> drools.setGlobal(e.getKey(), e.getValue()));
	}
	
	/**
	 * Defines variable in both mvel and drools global context
	 * 
	 * @see #givenVariable(String)
	 */
	@Given("global $expression")
	public void givenGlobal(String expression) {
		defineVariable(expression, true);
	}
	
	/**
	 * Defines variable that can be used in mvel expressions later<br>
	 * <br>
	 * &lt;name&gt; [is|as[ a][ an]][equal[s] [to]] [&lt;type&gt; from] [mime] [expression]
	 * 
	 * <pre>
	 * Given variable stdout is System.out
	 * Given variable dial as Dialing from yaml {...}
	 * Given variable dial as Dialing from json {...}
	 * Given variable dial as Dialing from yaml resource classpath:org/droolsassert/yaml
	 * Given variable call as CallInProgres object from the session
	 * Given variable listOfLong as AtomicLong objects from the session
	 * Given variable restTemplate is a spring service restTemplate
	 * </pre>
	 * 
	 * @see #knownMimeTypes()
	 */
	@Given("variable $expression")
	public void givenVariable(String expression) {
		defineVariable(expression, false);
	}
	
	@Given("facts printed")
	public void givenFactsPrinted() {
		drools.printFacts();
	}
	
	@Given("performance statistic printed")
	public void givenPerformanceStatisticPrinted() {
		drools.printPerformanceStatistic();
	}
	
	@When("insert and fire $variables")
	public void whenInsertAndFire(String variables) {
		drools.insertAndFire(evalVariables(variables));
	}
	
	@When("insert into $entryPoint and fire $variables")
	public void whenInsertAndFire(String entryPoint, String variables) {
		drools.insertAndFireAt(entryPoint, evalVariables(variables));
	}
	
	@When("insert fact $variables")
	@Alias("insert facts $variables")
	public void whenInsert(String variables) {
		drools.insert(evalVariables(variables));
	}
	
	@When("insert into $entryPoint fact $variables")
	@Alias("insert $entryPoint facts $variables")
	public void whenInsert(String entryPoint, String variables) {
		drools.insertTo(entryPoint, evalVariables(variables));
	}
	
	@When("fire all rules")
	public void whenFireAllRules() {
		drools.fireAllRules();
	}
	
	@When("advance time for $count $unit")
	public void whenAdvanceTime(int count, String unit) {
		drools.advanceTime(count, TimeUnit.valueOf(upperCase(unit.endsWith("s") ? unit : unit + "s")));
	}
	
	@When("await for $rules")
	public void whenAwaitFor(String rules) {
		drools.awaitFor(splitStrings(rules).toArray(new String[0]));
	}
	
	@Then("exist $variables")
	public void thenAssertExist(String variables) {
		drools.assertExist(evalVariables(variables));
	}
	
	@Then("count of facts is $count")
	public void thenAssertFactsCount(int count) {
		drools.assertFactsCount(count);
	}
	
	@Then("deleted $variables")
	public void thenAssertDeleted(String variables) {
		if ("all facts".equals(variables))
			drools.assertAllDeleted();
		else
			drools.assertDeleted(evalVariables(variables));
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less <i>since previous check</i>.
	 * 
	 * <pre>
	 * Then activated no rules
	 * 
	 * Then activated 'rule 1', 'rule 2'
	 * 
	 * Then activated
	 *     drop the call if caller is talking more than permitted time
	 *     call in progress dropped
	 * </pre>
	 */
	@Then("activated $activated")
	public void thenAssertActivated(String activated) {
		if ("no rules".equals(activated))
			drools.assertActivated();
		else
			drools.assertActivated(splitStrings(activated).toArray(new String[0]));
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less.
	 * 
	 * @see #thenAssertActivated(String)
	 */
	@Then("all activations are$activations")
	@Aliases(values = { "there was single activation$activations", "there were no activations$activations" })
	public void thenAssertAllActivations(String activations) {
		if (testRulesMeta.checkScheduled)
			drools.triggerAllScheduledActivations();
		drools.assertAllActivations(splitStrings(activations).toArray(new String[0]));
	}
	
	@Then("all activations defined by$source")
	public void thenAssertAllActivationsFromSource(String source) throws IOException {
		if (testRulesMeta.checkScheduled)
			drools.triggerAllScheduledActivations();
		drools.assertAllActivations(getRulesFromSource(getResources(true, false, source.trim())));
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less <i>since previous check</i>.<br>
	 * Accepts the number of activations to assert.
	 * 
	 * <pre>
	 * Then count of activated is 1 drop the call if caller is talking more than permitted time
	 *     
	 * Then count of activated are
	 *     1 drop the call if caller is talking more than permitted time
	 *     1 call in progress dropped
	 * </pre>
	 */
	@Then("count of activated are$activated")
	@Alias("count of activated is$activated")
	public void thenAssertActivatedCount(String activated) {
		drools.assertActivated(parseCountOfRules(activated));
	}
	
	/**
	 * Asserts the only rules listed have been activated no more no less.<br>
	 * Accepts the number of activations to assert.
	 * 
	 * @see #thenAssertActivatedCount(String)
	 */
	@Then("count of all activations are$activations")
	@Alias("count of all activations is$activations")
	public void thenAssertAllActivationsCount(String activations) {
		if (testRulesMeta.checkScheduled)
			drools.triggerAllScheduledActivations();
		drools.assertAllActivations(parseCountOfRules(activations));
	}
	
	/**
	 * @see #thenAssertActivatedCount(String)
	 */
	@Then("count of all activations defined by$source")
	public void thenAssertAllActivationsCountFromSource(String source) throws IOException {
		if (testRulesMeta.checkScheduled)
			drools.triggerAllScheduledActivations();
		drools.assertAllActivations(getRulesCountFromSource(getResources(true, false, source.trim())));
	}
	
	@Then("there are no scheduled activations")
	public void thenAssertNoScheduledActivations() {
		drools.assertNoScheduledActivations();
	}
	
	@Then("assert $message statement $expression")
	public void thenAssertStatement(String message, String expression) {
		if (isBlank(message))
			assertTrue((boolean) mvelProcessor.evaluate(expression));
		else
			assertTrue(message, (boolean) mvelProcessor.evaluate(expression));
	}
	
	/**
	 * <pre>
	 * Then assert call.callerNumber equals '11111'
	 * </pre>
	 */
	@Then("assert $actual equals $expected")
	@Aliases(values = { "assert $actual equal $expected", "assert $actual is $expected" })
	public void thenAssertEquals(String actual, String expected) {
		assertEquals((Object) mvelProcessor.evaluate(expected), mvelProcessor.evaluate(actual));
	}
	
	protected List<String> splitStrings(String lines) {
		return stream(lines.split(STRINGS_DELIM))
				.map(StringUtils::trim)
				.filter(StringUtils::isNotEmpty)
				.map(this::stripString)
				.collect(toList());
	}
	
	protected String stripString(String quoted) {
		return quoted.startsWith(Q) && quoted.endsWith(Q) ? removeStart(removeEnd(quoted, Q), Q) : quoted;
	}
	
	protected Object[] evalVariables(String variables) {
		return stream(Splitter.onPattern(VARIABLES_DELIM).trimResults().omitEmptyStrings().split(variables).spliterator(), false)
				.map(var -> mvelProcessor.evaluate(var)).toArray();
	}
	
	protected void defineVariable(String expression, boolean droolsGlobal) {
		String[] rhsArr = expression.split(LHS_DELIM, 2);
		String name = rhsArr[0];
		String type = null;
		if (rhsArr[1].split(NL, 2)[0].contains(" from ")) {
			rhsArr = rhsArr[1].split(" from ");
			type = rhsArr[0];
		}
		
		String mime = null;
		String rhs = rhsArr[1];
		for (String mt : knownMimeTypes) {
			if (rhs.startsWith(mt)) {
				mime = mt;
				rhs = rhs.replaceFirst(mt, "").trim();
				break;
			}
		}
		
		defineVariable(name, type, mime, rhs, droolsGlobal);
	}
	
	protected void defineVariable(String name, String type, String mime, String expression, boolean droolsGlobal) {
		Object resolved = resolveVariable(type, mime, expression);
		if (droolsGlobal)
			globals.put(name, resolved);
		mvelProcessor.define(name, resolved);
	}
	
	/**
	 * "json", "json resource", "yaml", "yaml resource", "spring service", "the session"
	 */
	protected Set<String> knownMimeTypes() {
		return newHashSet("json", "json resource", "yaml", "yaml resource", "spring service", "the session");
	}
	
	protected Object resolveVariable(String type, String mime, String expression) {
		try {
			if (mime == null)
				return mvelProcessor.evaluate(expression);
			
			switch (mime) {
			case "json":
				return resolveVariableFromJson(type, expression);
			case "json resource":
				return resolveVariableFromJsonResource(type, expression);
			case "yaml":
				return resolveValriableFromYaml(type, expression);
			case "yaml resource":
				return resolveVariableFromYamlResource(type, expression);
			case "the session":
				return resolveVariableFromSession(type);
			case "spring service":
				return resolveSpringService(expression);
			default:
				throw new IllegalArgumentException("Not supported mime type " + mime);
			}
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new DroolsAssertException(format("Cannot resolve %s from mime '%s', expression: '%s'", type, mime, expression), e);
		}
	}
	
	protected Object resolveVariableFromJson(String type, String expression) {
		return fromJson(expression, classOf(type));
	}
	
	protected Object resolveVariableFromJsonResource(String type, String expression) throws IOException {
		return fromJson(Resources.toString(resourceResolver.getResource(expression).getURL(), UTF_8), classOf(type));
	}
	
	protected Object resolveValriableFromYaml(String type, String expression) {
		return fromYaml(expression, classOf(type));
	}
	
	protected Object resolveVariableFromYamlResource(String type, String expression) throws IOException {
		return fromYaml(Resources.toString(resourceResolver.getResource(expression).getURL(), UTF_8), classOf(type));
	}
	
	@SuppressWarnings("unchecked")
	protected <T> T resolveVariableFromSession(String type) {
		String[] args = type.split(SPACE);
		if (args.length == 2 && args[1].equals("object"))
			return drools.getObject(classOf(args[0]));
		if (args.length == 2 && args[1].equals("objects"))
			return (T) drools.getObjects(classOf(args[0]));
		throw new IllegalArgumentException("Cannot resolve variable from the session using " + type);
	}
	
	protected Object resolveSpringService(String name) {
		throw new NotImplementedException("Override ApplicationContextAware and implement");
	}
	
	@SuppressWarnings("unchecked")
	protected A droolsAssert() {
		return (A) new DroolsAssert();
	}
	
	protected MvelProcessor mvelProcessor() {
		return new DefaultMvelProcessor();
	}
	
	protected final <T> Class<T> classOf(String className) {
		return mvelProcessor.evaluate(className + ".class");
	}
	
	private boolean parseBoolean(String value) {
		return isBlank(value) || Boolean.parseBoolean(value);
	}
	
	@Override
	public void beforeStory(Story story, boolean givenStory) {
		this.story = story;
		
		drools = droolsAssert();
		mvelProcessor = mvelProcessor();
		imports = new HashSet<>();
		globals = new HashMap<>();
	}
	
	@Override
	public void beforeScenario(Scenario scenario) {
		this.scenario = scenario;
	}
	
	@Override
	public void afterScenario() {
		if (drools.getSession() != null) {
			drools.getListeners().forEach(DroolsassertListener::afterScenario);
			drools.destroy();
		}
	}
}
