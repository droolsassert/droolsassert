package org.droolsassert.util;

import static java.lang.Long.parseLong;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static javax.management.ObjectName.quote;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.containsAny;
import static org.apache.commons.lang3.StringUtils.isNoneEmpty;
import static org.droolsassert.util.AlphanumComparator.ALPHANUM_COMPARATOR;
import static org.droolsassert.util.JmxUtils.registerMBean;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.time.StopWatch;

/**
 * Performance statistic per type (optional) and name. Exposed via MBean server to monitor in real-time with default 4s aggregation time (jvisualvm mbean charts refresh interval).
 * Output statistic from this VM or deliver serializable and merge from several VMs.
 * 
 * <pre>
 * private PerfStat methodPerf = new PerfStat("type", "name") 
 * ...
 * 
 *     public void myMethod() {
 *         methodPerf.start();
 *         ...
 *         methodPerf.stop();
 *     }
 * }
 * </pre>
 * 
 * @see #start()
 * @see #stop()
 * @see PerfStat#getPerfStat()
 * @see StopWatch
 */
public class PerfStat {
	
	private static String jmxDomain = getProperty("perfstat.domain", "perfstat");
	private static long defaultAggregationPeriodMs = parseLong(getProperty("perfstat.aggregationPeriodMs", "4000"));
	private static final ConcurrentHashMap<String, Map<String, StatImpl>> stats = new ConcurrentHashMap<>();
	
	public static String getJmxDomain() {
		return jmxDomain;
	}
	
	public static void setJmxDomain(String jmxDomain) {
		PerfStat.jmxDomain = jmxDomain;
	}
	
	public static long getDefaultAggregationPeriodMs() {
		return defaultAggregationPeriodMs;
	}
	
	public static void setDefaultAggregationPeriodMs(long defaultAggregationPeriodMs) {
		PerfStat.defaultAggregationPeriodMs = defaultAggregationPeriodMs;
	}
	
	/**
	 * Performance statistic for name (if type was not used)
	 * 
	 * @param name
	 */
	public static Stat getPerfStat(String name) {
		return getPerfStat(EMPTY, name);
	}
	
	/**
	 * Performance statistic for type and name
	 * 
	 * @param type
	 * @param name
	 */
	public static Stat getPerfStat(String type, String name) {
		return stats.get(type).get(name);
	}
	
	/**
	 * Remove performance statistic for type and name
	 * 
	 * @param type
	 * @param name
	 */
	public static void removePerfStat(String type, String name) {
		stats.get(type).remove(name);
	}
	
	/**
	 * Remove performance statistic for type
	 * 
	 * @param type
	 */
	public static void removePerfStat(String type) {
		stats.remove(type);
	}
	
	/**
	 * Performance statistic for all types and names
	 */
	public static TreeMap<String, TreeMap<String, StatImpl>> getPerfStat() {
		TreeMap<String, TreeMap<String, StatImpl>> sorted = new TreeMap<>(ALPHANUM_COMPARATOR);
		stats.entrySet().forEach(e -> {
			TreeMap<String, StatImpl> sortedNames = new TreeMap<>(ALPHANUM_COMPARATOR);
			sortedNames.putAll(e.getValue());
			sorted.put(e.getKey(), sortedNames);
		});
		return sorted;
	}
	
	/**
	 * You may want to merge performance statistic from other JVMs
	 */
	public static void merge(Map<String, Map<String, StatImpl>> rhsStatsByType) {
		synchronized (stats) {
			for (Entry<String, Map<String, StatImpl>> rhsStatsByName : rhsStatsByType.entrySet()) {
				Map<String, StatImpl> lhsStatsByName = stats.get(rhsStatsByName.getKey());
				if (lhsStatsByName == null) {
					lhsStatsByName = new ConcurrentHashMap<>();
					stats.put(rhsStatsByName.getKey(), lhsStatsByName);
				}
				for (Entry<String, StatImpl> rhsStat : rhsStatsByName.getValue().entrySet()) {
					StatImpl lhs = lhsStatsByName.get(rhsStat.getKey());
					StatImpl rhs = rhsStat.getValue();
					if (lhs == null) {
						lhsStatsByName.put(rhsStat.getKey(), rhs);
						continue;
					}
					lhs.leapsCount += rhs.leapsCount;
					lhs.failedLeapsCount += rhs.failedLeapsCount;
					lhs.totalTimeNs += rhs.totalTimeNs;
					if (rhs.minTimeNs < lhs.minTimeNs)
						lhs.minTimeNs = rhs.minTimeNs;
					if (rhs.maxTimeNs > lhs.maxTimeNs)
						lhs.maxTimeNs = rhs.maxTimeNs;
				}
			}
		}
	}
	
	/**
	 * Reset statistic for all types and names
	 */
	public static void resetAll() {
		stats.values().stream()
				.flatMap(m -> m.values().stream())
				.forEach(StatImpl::reset);
	}
	
	private ThreadLocal<StopWatch> stopWatch = ThreadLocal.withInitial(() -> new StopWatch());
	private StatImpl stat;
	private long aggregationPeriodNs;
	
	public PerfStat(String name) {
		this(EMPTY, name, defaultAggregationPeriodMs);
	}
	
	public PerfStat(String type, String name) {
		this(type, name, defaultAggregationPeriodMs);
	}
	
	public PerfStat(String name, long aggregationPeriodMs) {
		this(EMPTY, name, aggregationPeriodMs);
	}
	
	public PerfStat(String type, String name, long aggregationPeriodMs) {
		aggregationPeriodNs = MILLISECONDS.toNanos(aggregationPeriodMs);
		if (type == null)
			type = EMPTY;
		
		Map<String, StatImpl> statsByName = stats.get(type);
		if (statsByName == null) {
			statsByName = new ConcurrentHashMap<>();
			stats.put(type, statsByName);
		}
		
		stat = statsByName.get(name);
		if (stat == null)
			initStat(type, name);
		stat.peersCount.incrementAndGet();
	}
	
	@Override
	protected void finalize() throws Throwable {
		stat.peersCount.decrementAndGet();
		super.finalize();
	}
	
	private void initStat(String type, String name) {
		synchronized (stats) {
			Map<String, StatImpl> statsByName = stats.get(type);
			if (statsByName == null) {
				statsByName = new ConcurrentHashMap<>();
				stats.put(type, statsByName);
			}
			
			stat = statsByName.get(name);
			if (stat == null) {
				stat = new StatImpl(type, name);
				statsByName.put(name, stat);
				
				StringBuilder objName = new StringBuilder(jmxDomain);
				objName.append(":");
				if (isNoneEmpty(type)) {
					objName.append("type=");
					objName.append(quoteIfNeeded(type));
					objName.append(",");
				}
				objName.append("name=");
				objName.append(quoteIfNeeded(name));
				registerMBean(objName.toString(), stat, Stat.class);
			}
		}
	}
	
	private String quoteIfNeeded(String name) {
		return containsAny(name, '\n', '\\', '\"', '*', '?', ':') ? quote(name) : name;
	}
	
	/**
	 * Start to measure execution time for current thread.<br>
	 * Reset sample (period) values if aggregation time threshold passed over.
	 */
	public PerfStat start() {
		if (stopWatch.get().isStarted()) {
			synchronized (stat) {
				stat.failedLeapsCount += 1;
			}
		}
		stopWatch.get().reset();
		stopWatch.get().start();
		return this;
	}
	
	/**
	 * Stop to measure execution time for current thread, update performance statistic for the name.<br>
	 * If stop was not executed for some reason and then start will be called again <i>by the same thread</i> then current leap will be counted as failed.
	 */
	public long stop() {
		stopWatch.get().stop();
		long timeNs = stopWatch.get().getNanoTime();
		synchronized (stat) {
			stat.leapTimeNs = timeNs;
			stat.totalTimeNs += timeNs;
			stat.totalTimeSampleNs += timeNs;
			if (timeNs > stat.maxTimeNs)
				stat.maxTimeNs = timeNs;
			if (timeNs > stat.maxTimeThresholdNs)
				stat.maxTimeThresholdNs = timeNs;
			if (timeNs < stat.minTimeNs || stat.minTimeNs == 0)
				stat.minTimeNs = timeNs;
			if (timeNs < stat.minTimeThresholdNs || stat.minTimeThresholdNs == 0)
				stat.minTimeThresholdNs = timeNs;
			stat.leapsCount += 1;
			stat.leapsCountSample += 1;
		}
		long currentTimeNs = MILLISECONDS.toNanos(currentTimeMillis());
		if (currentTimeNs > stat.lastAggregationTimeNs + aggregationPeriodNs && stat.leapsCountSample > 0) {
			synchronized (stat) {
				if (currentTimeNs > stat.lastAggregationTimeNs + aggregationPeriodNs && stat.leapsCountSample > 0) {
					stat.avgTimeSampleNs = stat.totalTimeSampleNs / stat.leapsCountSample;
					stat.leapsCountSample = 0;
					stat.totalTimeSampleNs = 0;
					stat.maxTimeSampleNs = stat.maxTimeThresholdNs;
					stat.maxTimeThresholdNs = 0;
					stat.minTimeSampleNs = stat.minTimeThresholdNs;
					stat.minTimeThresholdNs = 0;
					stat.lastAggregationTimeNs = currentTimeNs;
				}
			}
		}
		return timeNs;
	}
	
	/**
	 * Reset statistic
	 */
	public void reset() {
		stat.reset();
	}
	
	public StopWatch getStopWatch() {
		return stopWatch.get();
	}
	
	public Stat getStat() {
		return stat;
	}
	
	public String getType() {
		return stat.getType();
	}
	
	public String getName() {
		return stat.getName();
	}
	
	public String getFullName() {
		return stat.getFullName();
	}
	
	static double round(double nanos) {
		double scale = 1000.0;
		double nsInMs = 1000_000.0;
		return Math.round(nanos * scale / nsInMs) / scale;
	}
	
	@Override
	public String toString() {
		return stat.toString();
	}
}
