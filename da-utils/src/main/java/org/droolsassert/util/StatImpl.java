package org.droolsassert.util;

import static java.lang.String.format;
import static org.apache.commons.lang3.StringUtils.EMPTY;

import java.util.concurrent.atomic.AtomicLong;

public final class StatImpl implements Stat {
	private static final long serialVersionUID = 6025961415245995217L;
	private String type;
	private String name;
	volatile long leapsCount;
	volatile long leapsCountSample;
	volatile long failedLeapsCount;
	volatile double totalTimeNs;
	volatile double totalTimeSampleNs;
	volatile double avgTimeSampleMs;
	volatile double leapTimeMs;
	volatile double minTimeMs;
	volatile double minTimeSampleMs;
	volatile double minTimeThresholdMs;
	volatile double maxTimeMs;
	volatile double maxTimeSampleMs;
	volatile double maxTimeThresholdMs;
	final AtomicLong peersCount = new AtomicLong();
	
	public StatImpl() {
		// for deserialization
	}
	
	public StatImpl(String type, String name) {
		this.type = type;
		this.name = name;
	}
	
	@Override
	public synchronized void reset() {
		totalTimeNs = 0;
		totalTimeSampleNs = 0;
		maxTimeMs = 0;
		maxTimeSampleMs = 0;
		maxTimeThresholdMs = 0;
		minTimeMs = 0;
		minTimeSampleMs = 0;
		minTimeThresholdMs = 0;
		leapsCount = 0;
		leapsCountSample = 0;
	}
	
	@Override
	public String getType() {
		return type;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	@Override
	public String getFullName() {
		return type == EMPTY ? name : type + "/" + name;
	}
	
	@Override
	public long getLeapsCount() {
		return leapsCount;
	}
	
	@Override
	public double getLeapTimeMs() {
		return leapTimeMs;
	}
	
	@Override
	public double getMinTimeMs() {
		return minTimeMs;
	}
	
	@Override
	public double getMinTimeSampleMs() {
		return minTimeSampleMs;
	}
	
	@Override
	public double getMaxTimeMs() {
		return maxTimeMs;
	}
	
	@Override
	public double getMaxTimeSampleMs() {
		return maxTimeSampleMs;
	}
	
	@Override
	public double getAvgTimeMs() {
		return PerfStat.round(totalTimeNs / leapsCount);
	}
	
	@Override
	public double getAvgTimeSampleMs() {
		return avgTimeSampleMs;
	}
	
	@Override
	public double getTotalTimeMs() {
		return PerfStat.round(totalTimeNs);
	}
	
	@Override
	public long getFailedLeapsCount() {
		return failedLeapsCount;
	}
	
	@Override
	public long getPeersCount() {
		return peersCount.get();
	}
	
	@Override
	public String toString() {
		return format("%,.2f %,.2f %,.2f", minTimeMs, getAvgTimeMs(), maxTimeMs);
	}
}