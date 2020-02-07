package org.droolsassert.util;

import static java.lang.Long.parseLong;
import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.lang.System.getProperty;
import static org.droolsassert.util.JmxUtils.registerMBean;

import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.time.StopWatch;

/**
 * Performance statistic per domain. Exposed via MBean server to monitor in real-time.
 * 
 * <pre>
 * private PerfStat methodPerf = new PerfStat("business.domain.method") 
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
	
	public static final long AGGREGATION_TIME_MS = parseLong(getProperty("org.droolsassert.perfStatAggregationTimeMs", "4000"));
	private static final ConcurrentHashMap<String, StatImpl> stats = new ConcurrentHashMap<>();
	
	/**
	 * Performance statistic for domain
	 * 
	 * @param domain
	 */
	public static Stat getPerfStat(String domain) {
		return stats.get(domain);
	}
	
	/**
	 * Performance statistic for all domains
	 */
	public static TreeMap<String, StatImpl> getPerfStat() {
		return new TreeMap<>(stats);
	}
	
	/**
	 * You may want to merge performance statistic from other JVMs
	 */
	public static void merge(Map<String, StatImpl> rhsStats) {
		synchronized (stats) {
			for (Entry<String, StatImpl> rhsStat : rhsStats.entrySet()) {
				StatImpl lhs = stats.get(rhsStat.getKey());
				StatImpl rhs = rhsStat.getValue();
				if (lhs == null) {
					stats.put(rhsStat.getKey(), rhs);
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
	
	/**
	 * Reset statistic for all domains
	 */
	public static void resetAll() {
		stats.values().forEach(StatImpl::reset);
	}
	
	private ThreadLocal<StopWatch> stopWatch = ThreadLocal.withInitial(() -> new StopWatch());
	private StatImpl stat;
	private long lastAggregationTimeMs;
	private long aggregationTimeMs;
	
	public PerfStat(String domain) {
		this(domain, AGGREGATION_TIME_MS);
	}
	
	public PerfStat(String domain, long aggregationTimeMs) {
		this.aggregationTimeMs = aggregationTimeMs;
		stat = stats.get(domain);
		if (stat == null)
			initStat(domain);
		stat.peersCount.incrementAndGet();
	}
	
	@Override
	protected void finalize() throws Throwable {
		stat.peersCount.decrementAndGet();
		super.finalize();
	}
	
	private void initStat(String domain) {
		synchronized (stats) {
			stat = stats.get(domain);
			if (stat == null) {
				stat = new StatImpl(domain);
				stats.put(domain, stat);
				registerMBean(format("%s:type=%s", getClass().getName(), domain), stat, Stat.class);
			}
		}
	}
	
	/**
	 * Start to measure execution time for current thread.<br>
	 * Reset sample (period) values if aggregation time threshold was passed over.
	 */
	public PerfStat start() {
		if (stopWatch.get().isStarted()) {
			synchronized (stat) {
				stat.failedLeapsCount += 1;
			}
		}
		long currentTimeMillis = currentTimeMillis();
		if (stat.leapsCountSample > 0 && currentTimeMillis > lastAggregationTimeMs + aggregationTimeMs) {
			synchronized (stat) {
				if (stat.leapsCountSample > 0 && currentTimeMillis > lastAggregationTimeMs + aggregationTimeMs) {
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
	 * Stop to measure execution time for current thread, update performance statistic for the domain.<br>
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
}
