package org.droolsassert.util;

import static java.lang.String.format;
import static java.lang.System.currentTimeMillis;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.droolsassert.util.PerfStat.round;

import java.util.concurrent.atomic.AtomicLong;

public final class StatImpl implements Stat {
	private static final long serialVersionUID = 6025961415245995217L;
	private String type;
	private String name;
	long leapsCount;
	long leapsCountSample;
	long failedLeapsCount;
	long totalTimeNs;
	long totalTimeSampleNs;
	long avgTimeSampleNs;
	long leapTimeNs;
	long minTimeNs;
	long minTimeSampleNs;
	long minTimeThresholdNs;
	long maxTimeNs;
	long maxTimeSampleNs;
	long maxTimeThresholdNs;
	final AtomicLong peersCount = new AtomicLong();
	volatile long lastAggregationTimeNs = MILLISECONDS.toNanos(currentTimeMillis());
	
	public StatImpl() {
		// for deserialization
	}
	
	public StatImpl(String type, String name) {
		this.type = type;
		this.name = name;
	}
	
	@Override
	public synchronized void reset() {
		leapsCount = 0;
		leapsCountSample = 0;
		failedLeapsCount = 0;
		totalTimeNs = 0;
		totalTimeSampleNs = 0;
		avgTimeSampleNs = 0;
		leapTimeNs = 0;
		minTimeNs = 0;
		minTimeSampleNs = 0;
		minTimeThresholdNs = 0;
		maxTimeNs = 0;
		maxTimeSampleNs = 0;
		maxTimeThresholdNs = 0;
		lastAggregationTimeNs = MILLISECONDS.toNanos(currentTimeMillis());
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
		return EMPTY.equals(type) ? name : type + "/" + name;
	}
	
	@Override
	public synchronized long getLeapsCount() {
		return leapsCount;
	}
	
	@Override
	public synchronized double getLeapTimeMs() {
		return round(leapTimeNs);
	}
	
	@Override
	public synchronized double getMinTimeMs() {
		return round(minTimeNs);
	}
	
	@Override
	public synchronized double getMinTimeSampleMs() {
		return round(minTimeSampleNs);
	}
	
	@Override
	public synchronized double getMaxTimeMs() {
		return round(maxTimeNs);
	}
	
	@Override
	public synchronized double getMaxTimeSampleMs() {
		return round(maxTimeSampleNs);
	}
	
	@Override
	public synchronized double getAvgTimeMs() {
		if (leapsCount == 0)
			return 0;
		return round(totalTimeNs / leapsCount);
	}
	
	@Override
	public synchronized double getAvgTimeSampleMs() {
		return round(avgTimeSampleNs);
	}
	
	@Override
	public synchronized double getTotalTimeMs() {
		return round(totalTimeNs);
	}
	
	@Override
	public synchronized long getFailedLeapsCount() {
		return failedLeapsCount;
	}
	
	@Override
	public long getPeersCount() {
		return peersCount.get();
	}
	
	@Override
	public String toString() {
	    long min;
	    long total;
	    long count;
	    long max;
	    synchronized (this) {
	        min = minTimeNs;
	        total = totalTimeNs;
	        count = leapsCount;
	        max = maxTimeNs;
	    }
	    double avg = count == 0 ? 0 : round(total / count);
	    return format("%,.2f %,.2f %,.2f", round(min), avg, round(max));
	}
}