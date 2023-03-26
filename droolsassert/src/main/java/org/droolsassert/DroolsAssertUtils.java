package org.droolsassert;

import static com.google.common.base.Preconditions.checkArgument;
import static java.lang.Integer.parseInt;
import static java.lang.Long.MAX_VALUE;
import static java.lang.System.out;
import static java.nio.charset.Charset.defaultCharset;
import static java.time.ZoneOffset.UTC;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.io.FileUtils.forceMkdir;
import static org.apache.commons.io.IOUtils.readLines;
import static org.apache.commons.lang3.ClassUtils.getShortCanonicalName;
import static org.apache.commons.lang3.StringUtils.LF;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.join;
import static org.drools.core.common.EqualityKey.JUSTIFIED;
import static org.droolsassert.util.ReentrantFileLock.newReentrantFileLockFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.LogicalDependency;
import org.drools.core.spi.Activation;
import org.drools.core.spi.Tuple;
import org.drools.core.time.SessionPseudoClock;
import org.drools.core.util.LinkedList;
import org.droolsassert.util.ReentrantFileLock.ReentrantFileLockFactory;
import org.kie.api.runtime.rule.Match;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

public final class DroolsAssertUtils {
	protected static final DateTimeFormatter HH_MM_SS = DateTimeFormatter.ofPattern("HH:mm:ss");
	protected static final DateTimeFormatter HH_MM_SS_SSS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
	protected static final DateTimeFormatter DDD_HH_MM_SS = DateTimeFormatter.ofPattern("DDD HH:mm:ss");
	protected static final DateTimeFormatter DDD_HH_MM_SS_SSS = DateTimeFormatter.ofPattern("DDD HH:mm:ss.SSS");
	protected static final long DAY_MILLISECONDS = DAYS.toMillis(1);
	public static final Pattern COUNT_OF_RULES = compile("(?<count>\\d+)\\s+(?<rule>.+)");
	public static final PathMatchingResourcePatternResolver resourceResolver = new PathMatchingResourcePatternResolver();
	public static class LazyReentrantFileLockFactory {
		public static final ReentrantFileLockFactory instance = newReentrantFileLockFactory("target/droolsassert/lock");
	}
	
	private DroolsAssertUtils() {
	}
	
	public static List<Object> getRuleActivatedBy(Match match) {
		Object activatorObject = ((InternalFactHandle) ((Activation<?>) match).getPropagationContext().getFactHandle()).getObject();
		List<Object> result = new ArrayList<>(match.getObjects());
		if (!result.contains(activatorObject))
			result.add(0, activatorObject);
		return result;
	}
	
	/**
	 * When the Drools engine logically inserts an object during a rule execution, the Drools engine justifies the object by executing the rule. For each logical insertion, only
	 * one equal object can exist, and each subsequent equal logical insertion increases the justification counter for that logical insertion. A justification is removed when the
	 * conditions of the rule become untrue. When no more justifications exist, the logical object is automatically deleted.
	 * 
	 * @param fh
	 * @return
	 */
	// https://issues.redhat.com/browse/DROOLS-6072
	public static boolean isJustified(InternalFactHandle fh) {
		return fh.getEqualityKey() != null && fh.getEqualityKey().getStatus() == JUSTIFIED;
	}
	
	public static Set<Object> getRuleLogicialDependencies(Match match) {
		Set<Object> logicalDependencies = new HashSet<>();
		Activation<?> activation = (Activation<?>) match;
		collectLogicalDependencies(activation, logicalDependencies);
		
		Tuple tuple = activation.getTuple();
		while (tuple != null) {
			if (tuple instanceof Activation)
				collectLogicalDependencies((Activation<?>) tuple, logicalDependencies);
			tuple = tuple.getHandlePrevious();
		}
		return logicalDependencies;
	}
	
	public static void collectLogicalDependencies(Activation<?> activation, Set<Object> logicalDependencies) {
		LinkedList<?> list = activation.getLogicalDependencies();
		if (list != null) {
			for (LogicalDependency<?> node = (LogicalDependency<?>) list.getFirst(); node != null; node = node.getNext())
				logicalDependencies.add(node.getObject());
		}
	}
	
	public static List<Resource> getResources(boolean mandatory, boolean logResources, String... locations) {
		List<Resource> resources = new ArrayList<>();
		
		try {
			for (String resourceNameFilter : locations)
				resources.addAll(asList(DroolsAssertUtils.resourceResolver.getResources(resourceNameFilter)));
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot get resources", e);
		}
		
		if (mandatory)
			checkArgument(resources.size() > 0, "No resources found");
		if (logResources)
			resources.forEach(resource -> out.println(resource));
		return resources;
	}
	
	/**
	 * Pins relative directory
	 */
	public static File directory(File file) {
		try {
			File absoluteFile = file.getAbsoluteFile();
			forceMkdir(absoluteFile);
			return absoluteFile;
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot create directory", e);
		}
	}
	
	public static Map<String, Integer> getExpectedCount(Object[] params) {
		checkArgument(params.length % 2 == 0, "Cannot create expected count out of odd number of parameters");
		Map<String, Integer> map = new LinkedHashMap<>();
		for (int i = 0; i < params.length; i = i + 2)
			map.put("" + params[i + 1], new Integer("" + params[i]));
		return map;
	}
	
	public static String[] getRulesFromSource(List<Resource> resources) {
		checkArgument(resources.size() == 1, "Non-unique rules source " + join(resources, LF));
		try {
			List<String> params = readLines(resources.get(0).getInputStream(), defaultCharset()).stream()
					.map(String::trim)
					.filter(line -> !line.isEmpty() && !line.startsWith("#"))
					.collect(toList());
			return params.toArray(new String[0]);
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot get rules from " + resources.get(0), e);
		}
	}
	
	public static Map<String, Integer> getRulesCountFromSource(List<Resource> resources) {
		checkArgument(resources.size() == 1, "Non-unique rules count source " + join(resources, LF));
		try {
			return parseCountOfRules(IOUtils.toString(resources.get(0).getInputStream(), defaultCharset()));
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot get rules count from " + resources.get(0), e);
		}
	}
	
	public static Map<String, Integer> parseCountOfRules(String activations) {
		Map<String, Integer> parsed = new HashMap<>();
		stream(activations.split("\r?\n"))
				.map(String::trim)
				.filter(line -> !line.isEmpty() && !line.startsWith("#"))
				.forEach(line -> {
					Matcher m = COUNT_OF_RULES.matcher(line);
					if (!m.matches())
						throw new DroolsAssertException("Expected <number><space><rule name>, but found: " + line);
					parsed.put(m.group("rule"), parseInt(m.group("count")));
				});
		return parsed;
	}
	
	public static String[] firstNonEmpty(String[]... params) {
		for (String[] param : params) {
			if (param.length != 0)
				return param;
		}
		return new String[0];
	}
	
	public static String formatTime(SessionPseudoClock clock) {
		synchronized (clock) {
			return LocalDateTime.ofInstant(Instant.ofEpochMilli(clock.getCurrentTime() == MAX_VALUE ? -1 : clock.getCurrentTime()), UTC)
					.format(clock.getCurrentTime() == MAX_VALUE ? DDD_HH_MM_SS_SSS : clock.getCurrentTime() % 1000 == 0
							? (clock.getCurrentTime() < DAY_MILLISECONDS ? HH_MM_SS : DDD_HH_MM_SS)
							: (clock.getCurrentTime() < DAY_MILLISECONDS ? HH_MM_SS_SSS : DDD_HH_MM_SS_SSS));
		}
	}
	
	public static String getSimpleName(Class<?> clazz) {
		return defaultIfEmpty(clazz.getSimpleName(), getShortCanonicalName(clazz));
	}
}
