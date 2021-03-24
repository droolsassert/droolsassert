package org.droolsassert;

import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.droolsassert.util.AlphanumComparator.ALPHANUM_COMPARATOR;

import java.util.Map.Entry;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.droolsassert.util.PerfStat;
import org.droolsassert.util.Stat;
import org.jfree.data.time.Second;
import org.jfree.data.time.TimeSeries;

/**
 * Collect live performance statistic for rules (then block) as aggregated result and jfree chart {@code TimeSeries}.<br>
 * Suitable for real environment and statistic delivery at the end of the flow or exposed by rest API etc.<br>
 * Statistic domains are JVM global, you can use unique session prefix as a namespace if needed.<br>
 * <i>Note:</i> This class creates single background thread (for all instances) which will stop gracefully when last instance will be garbage collected.
 * 
 * @see RulesChronoAgendaEventListener
 * @see PerfStat
 */
public class RulesChronoChartRecorder extends RulesChronoAgendaEventListener {
	
	public static final int RETENTION_PERIOD_MIN = parseInt(getProperty("org.droolsassert.RulesChronoChartRecorderRetentionPeriodMin", "180"));
	protected final ConcurrentHashMap<String, TimeSeries> rulesMaxChart = new ConcurrentHashMap<>();
	protected final ConcurrentHashMap<String, TimeSeries> rulesAvgChart = new ConcurrentHashMap<>();
	protected final Timer timer = new Timer(getClass().getSimpleName(), true);
	protected long retentionPeriodSec = MINUTES.toSeconds(RETENTION_PERIOD_MIN);
	
	/**
	 * Creates {@link RulesChronoChartRecorder} with no session prefix and default aggregation period
	 */
	public RulesChronoChartRecorder() {
		this(null, RETENTION_PERIOD_MIN);
	}
	
	/**
	 * Creates {@link RulesChronoChartRecorder} with no session prefix and provided aggregation period
	 * 
	 * @param aggregationPeriodMs
	 */
	public RulesChronoChartRecorder(long aggregationPeriodMs) {
		this(null, aggregationPeriodMs);
	}
	
	/**
	 * Creates {@link RulesChronoChartRecorder} with provided session prefix and default aggregation period
	 * 
	 * @param sessionPreffix
	 */
	public RulesChronoChartRecorder(String sessionPreffix) {
		this(sessionPreffix, RETENTION_PERIOD_MIN);
	}
	
	/**
	 * Creates {@link RulesChronoChartRecorder} with provided session prefix and aggregation period
	 * 
	 * @param sessionPreffix
	 * @param aggregationPeriodMs
	 */
	public RulesChronoChartRecorder(String sessionPreffix, long aggregationPeriodMs) {
		super(sessionPreffix, aggregationPeriodMs);
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				recordTimeSeries();
			}
		}, 0, aggregationPeriodMs);
	}
	
	public void recordTimeSeries() {
		for (Entry<String, PerfStat> entry : rulesStat.entrySet()) {
			String rule = entry.getKey();
			Stat stat = entry.getValue().getStat();
			if (!rulesMaxChart.containsKey(rule))
				initTimeSeries(rule, stat);
			Second period = new Second();
			rulesMaxChart.get(rule).addOrUpdate(period, stat.getMaxTimeSampleMs());
			rulesAvgChart.get(rule).addOrUpdate(period, stat.getAvgTimeSampleMs());
		}
	}
	
	public void initTimeSeries(String rule, Stat stat) {
		TimeSeries series = new TimeSeries(stat.getDomain());
		series.setMaximumItemAge(retentionPeriodSec);
		rulesMaxChart.put(rule, series);
		
		series = new TimeSeries(stat.getDomain());
		series.setMaximumItemAge(retentionPeriodSec);
		rulesAvgChart.put(rule, series);
	}
	
	public void setRetentionPeriod(long time, TimeUnit units) {
		retentionPeriodSec = units.toSeconds(time);
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
}
