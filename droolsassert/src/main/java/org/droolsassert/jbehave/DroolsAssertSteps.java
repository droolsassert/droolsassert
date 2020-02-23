package org.droolsassert.jbehave;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.collect.Sets.newHashSet;
import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static java.lang.reflect.Proxy.newProxyInstance;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;
import static java.util.stream.StreamSupport.stream;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.apache.commons.lang3.StringUtils.upperCase;
import static org.droolsassert.util.JsonUtils.fromJson;
import static org.droolsassert.util.JsonUtils.fromYaml;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;
import org.droolsassert.DroolsAssert;
import org.droolsassert.DroolsSession;
import org.droolsassert.TestRules;
import org.droolsassert.util.MvelProcessor;
import org.jbehave.core.annotations.Alias;
import org.jbehave.core.annotations.Aliases;
import org.jbehave.core.annotations.Given;
import org.jbehave.core.annotations.Then;
import org.jbehave.core.annotations.When;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import com.google.common.base.Splitter;
import com.google.common.io.Resources;

public class DroolsAssertSteps<A extends DroolsAssert> {
	
	protected static final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
	
	protected static final Pattern COUNT_OF_STRINGS = compile("(?<count>\\d+)\\s*[ ,:|-]?\\s*(?<rule>.+)");
	protected static final String STRINGS_DELIM = "(\r?\n|(?<=')\\s*,\\s*(?='[^']*'))";
	protected static final String VARIABLES_DELIM = "(\r?\n|\\s*,\\s*)";
	protected static final String LHS_DELIM = "\\s+((is|as)( an?)?|equals?( to)?)\\s+";
	protected static final String SPACE = "\\s+";
	protected static final String NL = "\r?\n";
	protected static final String Q = "'";
	
	protected final Set<String> knownMimeTypes = knownMimeTypes();
	protected DroolsSessionProxy droolsSessionMeta;
	protected TestRulesProxy testRulesMeta;
	protected MvelProcessor mvelProcessor = mvelProcessor();
	protected HashMap<String, Object> globals = new HashMap<>();
	protected A drools;
	
	/**
	 * Reset variables for each session definition
	 */
	protected void resetVariableDefinitions() {
		if (droolsSessionMeta == null)
			return;
		droolsSessionMeta = null;
		mvelProcessor = mvelProcessor();
		globals = new HashMap<>();
	}
	
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
		resetVariableDefinitions();
		for (String line : Splitter.onPattern(NL).trimResults().omitEmptyStrings().split(imports))
			mvelProcessor.importPackage(line);
	}
	
	/**
	 * Drools session definition, suitable for many scenarios
	 * 
	 * <pre>
	 * Given drools session classpath:/org/droolsassert/logicalEvents.drl
	 * </pre>
	 * 
	 * <pre>
	 * Given drools session 
	 * 	classpath*:/org/droolsassert/rules.drl
	 * 	classpath*:/org/droolsassert/rules2.drl
	 * ignore rules: 'before', 'after'
	 * log resources: true
	 * </pre>
	 */
	@Given("drools session $sessionMeta")
	public void givenDroolsSession(String sessionMeta) {
		resetVariableDefinitions();
		droolsSessionMeta = new DroolsSessionProxy();
		List<String> resources = new ArrayList<>();
		List<String> baseProperties = new ArrayList<>();
		List<String> sessionProperties = new ArrayList<>();
		List<String> ignoreRules = new ArrayList<>();
		List<String> current = resources;
		boolean properties = true;
		
		for (String line : sessionMeta.split(NL)) {
			if (line.matches("\\s*base properties.*")) {
				line = line.replaceFirst("\\s*base properties:?", "");
				current = baseProperties;
				properties = true;
			} else if (line.matches("\\s*session properties.*")) {
				line = line.replaceFirst("\\s*session properties:?", "");
				current = sessionProperties;
				properties = true;
			} else if (line.matches("\\s*ignore rules.*")) {
				line = line.replaceFirst("\\s*ignore rules:?", "");
				current = ignoreRules;
				properties = false;
			} else if (line.matches("\\s*log resources.*")) {
				droolsSessionMeta.logResources = parseBoolean(line.replaceFirst("\\s*log resources:?\\s+", ""));
				continue;
			} else if (line.matches("\\s*keep facts history.*")) {
				droolsSessionMeta.keepFactsHistory = parseBoolean(line.replaceFirst("\\s*keep facts history:?\\s+", ""));
				continue;
			} else if (line.matches("\\s*log facts.*")) {
				droolsSessionMeta.logFacts = parseBoolean(line.replaceFirst("\\s*log facts:?\\s+", ""));
				continue;
			}
			if (line.isEmpty())
				continue;
			if (properties)
				for (String property : splitStrings(line))
					current.addAll(asList(property.split("\\s*=\\s*")));
			else
				current.addAll(splitStrings(line));
		}
		if (!resources.isEmpty())
			droolsSessionMeta.resources = resources.toArray(new String[0]);
		if (!baseProperties.isEmpty())
			droolsSessionMeta.baseProperties = baseProperties.toArray(new String[0]);
		if (!sessionProperties.isEmpty())
			droolsSessionMeta.sessionProperties = sessionProperties.toArray(new String[0]);
		if (!ignoreRules.isEmpty())
			droolsSessionMeta.ignoreRules = ignoreRules.toArray(new String[0]);
	}
	
	/**
	 * Create empty drools session for a scenario.
	 * 
	 * <pre>
	 * Given new session for scenario
	 * 
	 * Given new session for scenario, ignore '* int rule', 'other rule'
	 * 
	 * Given new session for scenario
	 * 	ignore complex name * ${with}(and)[??]
	 * </pre>
	 */
	@Given("new session for scenario$sessionMeta")
	public void givenNewSessionForScenario(String sessionMeta) {
		testRulesMeta = new TestRulesProxy();
		List<String> ignore = new ArrayList<>();
		
		for (String line : sessionMeta.split(NL)) {
			if (line.matches("\\s*,?\\s*ignore:? .*"))
				line = line.replaceFirst("\\s*,?\\s*ignore:?\\s+", "");
			if (!line.isEmpty())
				ignore.addAll(splitStrings(line));
		}
		if (!ignore.isEmpty())
			testRulesMeta.ignore = ignore.toArray(new String[0]);
		
		if (drools != null)
			drools.destroy();
		drools = droolsAssert();
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
	
	@When("insert fact $variables")
	@Alias("insert facts $variables")
	public void whenInsert(String variables) {
		drools.insert(evalVariables(variables));
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
	
	@Then("retracted $variables")
	public void thenAssertRetracted(String variables) {
		if ("all facts".equals(variables))
			drools.assertAllRetracted();
		else
			drools.assertRetracted(evalVariables(variables));
	}
	
	@Then("activated $activated")
	public void thenAssertActivated(String activated) {
		if ("no rules".equals(activated))
			drools.assertActivated();
		else
			drools.assertActivated(splitStrings(activated).toArray(new String[0]));
	}
	
	@Then("all activations are$activations")
	@Alias("all activations is$activations")
	public void thenAssertAllActivations(String activations) {
		drools.assertAllActivations(splitStrings(activations).toArray(new String[0]));
	}
	
	@Then("all activations and scheduled are$activations")
	public void thenAssertAllActivationsAndScheduled(String activations) {
		drools.triggerAllScheduledActivations();
		drools.assertAllActivations(splitStrings(activations).toArray(new String[0]));
	}
	
	/**
	 * &lt;number&gt;&lt;delimiter&gt;[']&lt;value&gt;[']<br>
	 * <br>
	 * delimiter is one of ' ', ',', ':', '|', '-'
	 * 
	 * <pre>
	 * Then count of activated are
	 *     1 drop the call if caller is talking more than permitted time
	 *     1 call in progress dropped
	 * </pre>
	 */
	@Then("count of activated are$activated")
	public void thenAssertActivatedCount(String activated) {
		drools.assertActivated(splitCountOfStrings(activated));
	}
	
	/**
	 * @see #thenAssertActivatedCount(String)
	 */
	@Then("count of all activations are$activations")
	public void thenAssertAllActivationsCount(String activations) {
		drools.assertAllActivations(splitCountOfStrings(activations));
	}
	
	/**
	 * @see #thenAssertActivatedCount(String)
	 */
	@Then("count of all activations and scheduled are$activations")
	public void thenAssertAllActivationsAndScheduledCount(String activations) {
		drools.triggerAllScheduledActivations();
		drools.assertAllActivations(splitCountOfStrings(activations));
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
	@Then("assert $lhs equals $rhs")
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
	
	protected Map<String, Integer> splitCountOfStrings(String activations) {
		Map<String, Integer> evaluated = new HashMap<>();
		stream(activations.split(NL))
				.map(StringUtils::trim)
				.filter(StringUtils::isNotEmpty)
				.map(this::stripString)
				.forEach(line -> {
					Matcher m = COUNT_OF_STRINGS.matcher(line);
					if (!m.matches())
						throw new IllegalArgumentException("Cannot parse " + line);
					evaluated.put(stripString(m.group("rule")), parseInt(m.group("count")));
				});
		return evaluated;
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
		} catch (Exception e) {
			throw new RuntimeException(format("Cannot resolve %s from mime '%s', expression: '%s'", type, mime, expression), e);
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
		return (A) new DroolsAssert(
				(DroolsSession) newProxyInstance(getClass().getClassLoader(), new Class[] { DroolsSession.class }, droolsSessionMeta),
				(TestRules) newProxyInstance(getClass().getClassLoader(), new Class[] { TestRules.class }, testRulesMeta));
	}
	
	protected MvelProcessor mvelProcessor() {
		return new DefaultMvelProcessor();
	}
	
	protected final <T> Class<T> classOf(String className) {
		return mvelProcessor.evaluate(className + ".class");
	}
}
