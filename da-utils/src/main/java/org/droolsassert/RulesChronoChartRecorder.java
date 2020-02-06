package org.droolsassert;

import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;
import static java.util.concurrent.TimeUnit.MINUTES;
import static org.droolsassert.util.PerfStat.AGGREGATION_TIME_MS;

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
	 * Creates {@link RulesChronoChartRecorder} with no session prefix and default aggregation time
	 */
	public RulesChronoChartRecorder() {
		this(null, AGGREGATION_TIME_MS);
	}
	
	/**
	 * Creates {@link RulesChronoChartRecorder} with no session prefix and provided aggregation time
	 * 
	 * @param aggregationTimeMs
	 */
	public RulesChronoChartRecorder(long aggregationTimeMs) {
		this(null, aggregationTimeMs);
	}
	
	/**
	 * Creates {@link RulesChronoChartRecorder} with provided session prefix and default aggregation time
	 * 
	 * @param sessionPreffix
	 */
	public RulesChronoChartRecorder(String sessionPreffix) {
		this(sessionPreffix, AGGREGATION_TIME_MS);
	}
	
	/**
	 * Creates {@link RulesChronoChartRecorder} with provided session prefix and aggregation time
	 * 
	 * @param sessionPreffix
	 * @param aggregationTimeMs
	 */
	public RulesChronoChartRecorder(String sessionPreffix, long aggregationTimeMs) {
		super(sessionPreffix, aggregationTimeMs);
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				recordTimeSeries();
			}
		}, 0, aggregationTimeMs);
		
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
		return new TreeMap<>(rulesMaxChart);
	}
	
	public TreeMap<String, TimeSeries> getRulesAvgChart() {
		return new TreeMap<>(rulesAvgChart);
	}
}
