package org.droolsassert;

import static java.lang.Double.MAX_VALUE;
import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;
import static java.util.Arrays.asList;
import static java.util.concurrent.Executors.newScheduledThreadPool;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.droolsassert.RulesChronoChartRecorder.DataType.GlobalAvg;
import static org.droolsassert.RulesChronoChartRecorder.DataType.GlobalMax;
import static org.droolsassert.RulesChronoChartRecorder.DataType.GlobalMin;
import static org.droolsassert.RulesChronoChartRecorder.DataType.RulesAvg;
import static org.droolsassert.RulesChronoChartRecorder.DataType.RulesMax;
import static org.droolsassert.RulesChronoChartRecorder.DataType.RulesMin;
import static org.droolsassert.RulesChronoChartRecorder.ThresholdType.Avg;
import static org.droolsassert.RulesChronoChartRecorder.ThresholdType.Max;
import static org.droolsassert.RulesChronoChartRecorder.ThresholdType.Min;
import static org.droolsassert.util.AlphanumComparator.ALPHANUM_COMPARATOR;
import static org.droolsassert.util.PerfStat.getDefaultAggregationPeriodMs;

import java.lang.ref.WeakReference;
import java.util.EnumSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.droolsassert.util.PerfStat;
import org.droolsassert.util.Stat;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

/**
 * Collect live performance statistic for rules (then block) as aggregated result and jfree chart {@code TimeSeries}.<br>
 * Suitable for prod environment and statistic delivery at the end of the flow or exposing via rest API etc.<br>
 * <br>
 * <i>Note:</i> This class creates thread pool executor with single background thread (for all instances) with core pool size 0 (thread will stop if no statistic is gathered).<br>
 * Executor holds week reference to the recorder and scheduled periodic statistic gathering will be automatically cancelled when recorder is not in use any more.
 * 
 * @see RulesChronoAgendaEventListener
 * @see PerfStat
 */
public class RulesChronoChartRecorder extends RulesChronoAgendaEventListener {
	
	public static enum DataType {
		RulesMax, RulesAvg, RulesMin, GlobalMax, GlobalAvg, GlobalMin
	}
	
	enum ThresholdType {
		Max, Avg, Min;
		
		private volatile double threshold;
		private double localThreshold;
		
		private boolean check(double value) {
			if (localThreshold == 0)
				localThreshold = threshold;
			return value > localThreshold;
		}
	}
	
	public static final int RETENTION_PERIOD_MIN = parseInt(getProperty("org.droolsassert.RulesChronoChartRecorder.retentionPeriodMin", "180"));
	private static final ScheduledExecutorService EXECUTOR = newScheduledThreadPool(0, new ThreadFactoryBuilder().setNameFormat("RulesChronoChartRecorder%s").setDaemon(true).build());
	protected final ConcurrentHashMap<String, TimeSeries> rulesMaxChart = new ConcurrentHashMap<>();
	protected final ConcurrentHashMap<String, TimeSeries> rulesAvgChart = new ConcurrentHashMap<>();
	protected final ConcurrentHashMap<String, TimeSeries> rulesMinChart = new ConcurrentHashMap<>();
	protected TimeSeries globalMaxChart = new TimeSeries("globalMax");
	protected TimeSeries globalAvgChart = new TimeSeries("globalAvg");
	protected TimeSeries globalMinChart = new TimeSeries("globalMin");
	protected long retentionPeriodSec = MINUTES.toSeconds(RETENTION_PERIOD_MIN);
	private EnumSet<DataType> dataTypes = EnumSet.allOf(DataType.class);
	private EnumSet<ThresholdType> thresholdTypes = EnumSet.noneOf(ThresholdType.class);
	private volatile boolean recordingStarted;
	
	/**
	 * Creates {@link RulesChronoChartRecorder} with no session prefix and default aggregation period
	 */
	public RulesChronoChartRecorder() {
		this(getDefaultAggregationPeriodMs());
	}
	
	/**
	 * Creates {@link RulesChronoChartRecorder} with no session prefix and provided aggregation period
	 * 
	 * @param aggregationPeriodMs
	 */
	public RulesChronoChartRecorder(long aggregationPeriodMs) {
		super(aggregationPeriodMs);
		scheduleRecording(aggregationPeriodMs);
	}
	
	/**
	 * Retain last 3h chart data by default
	 */
	public RulesChronoChartRecorder withRetentionPeriod(long time, TimeUnit units) {
		retentionPeriodSec = units.toSeconds(time);
		globalMaxChart.setMaximumItemAge(retentionPeriodSec);
		globalAvgChart.setMaximumItemAge(retentionPeriodSec);
		globalMinChart.setMaximumItemAge(retentionPeriodSec);
		return this;
	}
	
	/**
	 * Per rule and global (generalized) chart data is gathered by default
	 */
	public RulesChronoChartRecorder withDataTypes(DataType... dataTypes) {
		this.dataTypes = EnumSet.copyOf(asList(dataTypes));
		return this;
	}
	
	/**
	 * Start gather chart data only if threshold value reached
	 */
	public RulesChronoChartRecorder withMaxThreshold(double threshold) {
		Max.threshold = threshold;
		thresholdTypes.add(Max);
		return this;
	}
	
	/**
	 * Start gather chart data only if threshold value reached
	 */
	public RulesChronoChartRecorder withAvgThreshold(double threshold) {
		Avg.threshold = threshold;
		thresholdTypes.add(Avg);
		return this;
	}
	
	/**
	 * Start gather chart data only if threshold value reached
	 */
	public RulesChronoChartRecorder withMinThreshold(double threshold) {
		Min.threshold = threshold;
		thresholdTypes.add(Min);
		return this;
	}
	
	@Override
	public RulesChronoChartRecorder withPackageName(boolean usePackageName) {
		super.withPackageName(usePackageName);
		return this;
	}
	
	@Override
	public RulesChronoChartRecorder withSessionPrefix(String sessionPrefix) {
		super.withSessionPrefix(sessionPrefix);
		return this;
	}
	
	protected void recordTimeSeries() {
		Second period = new Second();
		double globalMax = 0;
		double globalTotal = 0;
		double globalMin = MAX_VALUE;
		
		Set<Entry<String, PerfStat>> es = rulesStat.entrySet();
		for (Entry<String, PerfStat> entry : es) {
			String rule = entry.getKey();
			Stat stat = entry.getValue().getStat();
			if (!rulesMaxChart.containsKey(rule)) {
				if (!thresholdTypes.isEmpty() && !(thresholdTypes.contains(Max) && Max.check(stat.getMaxTimeSampleMs())
						|| thresholdTypes.contains(Avg) && Avg.check(stat.getAvgTimeSampleMs())
						|| thresholdTypes.contains(Min) && Min.check(stat.getMinTimeSampleMs()))) {
					continue;
				}
				initTimeSeries(rule, stat);
				recordingStarted = true;
			}
			if (dataTypes.contains(RulesMax))
				rulesMaxChart.get(rule).addOrUpdate(period, stat.getMaxTimeSampleMs());
			if (dataTypes.contains(RulesAvg))
				rulesAvgChart.get(rule).addOrUpdate(period, stat.getAvgTimeSampleMs());
			if (dataTypes.contains(RulesMin))
				rulesMinChart.get(rule).addOrUpdate(period, stat.getMinTimeSampleMs());
			
			if (dataTypes.contains(GlobalMax) && globalMax < stat.getMaxTimeSampleMs())
				globalMax = stat.getMaxTimeSampleMs();
			if (dataTypes.contains(GlobalAvg))
				globalTotal += stat.getAvgTimeSampleMs();
			if (dataTypes.contains(GlobalMin) && globalMin > stat.getMinTimeSampleMs())
				globalMin = stat.getMinTimeSampleMs();
		}
		
		if (!recordingStarted)
			return;
		
		if (dataTypes.contains(GlobalMax))
			globalMaxChart.addOrUpdate(period, globalMax);
		if (dataTypes.contains(GlobalAvg))
			globalAvgChart.addOrUpdate(period, globalTotal / es.size());
		if (dataTypes.contains(GlobalMin))
			globalMinChart.addOrUpdate(period, globalMin);
	}
	
	private void initTimeSeries(String rule, Stat stat) {
		TimeSeries series = new TimeSeries(stat.getDomain());
		series.setMaximumItemAge(retentionPeriodSec);
		rulesMaxChart.put(rule, series);
		
		series = new TimeSeries(stat.getDomain());
		series.setMaximumItemAge(retentionPeriodSec);
		rulesAvgChart.put(rule, series);
		
		series = new TimeSeries(stat.getDomain());
		series.setMaximumItemAge(retentionPeriodSec);
		rulesMinChart.put(rule, series);
	}
	
	public TreeMap<String, TimeSeries> getRulesMaxChart() {
		TreeMap<String, TimeSeries> sorted = new TreeMap<>(ALPHANUM_COMPARATOR);
		sorted.putAll(rulesMaxChart);
		return sorted;
	}
	
	public TreeMap<String, TimeSeries> getRulesAvgChart() {
		TreeMap<String, TimeSeries> sorted = new TreeMap<>(ALPHANUM_COMPARATOR);
		sorted.putAll(rulesAvgChart);
		return sorted;
	}
	
	public TreeMap<String, TimeSeries> getRulesMinChart() {
		TreeMap<String, TimeSeries> sorted = new TreeMap<>(ALPHANUM_COMPARATOR);
		sorted.putAll(rulesMinChart);
		return sorted;
	}
	
	public TimeSeries getGlobalMaxChart() {
		return globalMaxChart;
	}
	
	public TimeSeries getGlobalAvgChart() {
		return globalAvgChart;
	}
	
	public TimeSeries getGlobalMinChart() {
		return globalMinChart;
	}
	
	public boolean isRecordingStarted() {
		return recordingStarted;
	}
	
	@Override
	public void reset() {
		rulesMaxChart.clear();
		rulesAvgChart.clear();
		rulesMinChart.clear();
		globalMaxChart = new TimeSeries("globalMax");
		globalMaxChart.setMaximumItemAge(retentionPeriodSec);
		globalAvgChart = new TimeSeries("globalAvg");
		globalAvgChart.setMaximumItemAge(retentionPeriodSec);
		globalMinChart = new TimeSeries("globalMin");
		globalMinChart.setMaximumItemAge(retentionPeriodSec);
		recordingStarted = false;
		super.reset();
	}
	
	private void scheduleRecording(long aggregationPeriodMs) {
		SelfDiscardWrapper wrapper = new SelfDiscardWrapper(this);
		ScheduledFuture<?> scheduled = EXECUTOR.scheduleAtFixedRate(wrapper, 0, aggregationPeriodMs, MILLISECONDS);
		wrapper.setScheduled(scheduled);
	}
	
	private static class SelfDiscardWrapper implements Runnable {
		private final WeakReference<RulesChronoChartRecorder> ref;
		private volatile ScheduledFuture<?> scheduled;
		
		private SelfDiscardWrapper(RulesChronoChartRecorder referent) {
			this.ref = new WeakReference<RulesChronoChartRecorder>(referent);
		}
		
		@Override
		public void run() {
			RulesChronoChartRecorder referent = ref.get();
			if (referent == null)
				scheduled.cancel(false);
			else
				referent.recordTimeSeries();
		}
		
		private void setScheduled(ScheduledFuture<?> scheduled) {
			this.scheduled = scheduled;
		}
	}
}
