package org.droolsassert.util;

import static java.lang.Long.parseLong;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isNoneEmpty;
import static org.apache.commons.lang3.StringUtils.replaceChars;
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
	 * @param name
	 */
	public static Stat getPerfStat(String type, String name) {
		return stats.get(type).get(name);
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
					lhs.totalTimeNs += rhs.totalTimeNs;
					if (rhs.minTimeMs < lhs.minTimeMs)
						lhs.minTimeMs = rhs.minTimeMs;
					if (rhs.maxTimeMs > lhs.maxTimeMs)
						lhs.maxTimeMs = rhs.maxTimeMs;
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
	private long lastAggregationTimeMs = currentTimeMillis();
	private long aggregationPeriodMs;
	
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
		this.aggregationPeriodMs = aggregationPeriodMs;
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
				stat = new StatImpl(name);
				statsByName.put(name, stat);
				
				StringBuilder objName = new StringBuilder(jmxDomain);
				objName.append(":");
				if (isNoneEmpty(type)) {
					objName.append("type=");
					objName.append(replaceChars(type, "*?\\\n", ""));
					objName.append(",");
				}
				objName.append("name=");
				objName.append(replaceChars(name, "*?\\\n", ""));
				registerMBean(objName.toString(), stat, Stat.class);
			}
		}
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
		long currentTimeMillis = currentTimeMillis();
		if (stat.leapsCountSample > 0 && currentTimeMillis > lastAggregationTimeMs + aggregationPeriodMs) {
			synchronized (stat) {
				if (stat.leapsCountSample > 0 && currentTimeMillis > lastAggregationTimeMs + aggregationPeriodMs) {
					lastAggregationTimeMs = currentTimeMillis;
					stat.avgTimeSampleMs = round(stat.totalTimeSampleNs / stat.leapsCountSample);
					stat.leapsCountSample = 0;
					stat.totalTimeSampleNs = 0;
					stat.maxTimeSampleMs = stat.maxTimeThresholdMs;
					stat.maxTimeThresholdMs = 0;
					stat.minTimeSampleMs = stat.minTimeThresholdMs;
					stat.minTimeThresholdMs = 0;
				}
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
		double timeMs = round(timeNs);
		synchronized (stat) {
			stat.leapTimeMs = timeMs;
			stat.totalTimeNs += timeNs;
			stat.totalTimeSampleNs += timeNs;
			if (timeMs > stat.maxTimeMs)
				stat.maxTimeMs = timeMs;
			if (timeMs > stat.maxTimeThresholdMs)
				stat.maxTimeThresholdMs = timeMs;
			if (timeMs < stat.minTimeMs || stat.minTimeMs == 0)
				stat.minTimeMs = timeMs;
			if (timeMs < stat.minTimeThresholdMs || stat.minTimeThresholdMs == 0)
				stat.minTimeThresholdMs = timeMs;
			stat.leapsCount += 1;
			stat.leapsCountSample += 1;
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
