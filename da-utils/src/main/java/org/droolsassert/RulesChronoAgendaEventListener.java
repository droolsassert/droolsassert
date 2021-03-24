package org.droolsassert;

import static org.droolsassert.util.AlphanumComparator.ALPHANUM_COMPARATOR;
import static org.droolsassert.util.PerfStat.AGGREGATION_PERIOD_MS;

import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.droolsassert.util.PerfStat;
import org.droolsassert.util.Stat;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;

/**
 * Collect live performance statistic for rules (then block) as aggregated {@code Serializable} result.<br>
 * Statistic domains are JVM global, you can use unique session prefix as a namespace if needed.<br>
 * 
 * @see RulesChronoChartRecorder
 * @see PerfStat
 */
public class RulesChronoAgendaEventListener extends DefaultAgendaEventListener {
	
	protected final ConcurrentHashMap<String, PerfStat> rulesStat = new ConcurrentHashMap<>();
	protected final long aggregationPeriodMs;
	protected final String sessionPreffix;
	
	/**
	 * Creates {@link RulesChronoAgendaEventListener} with no session prefix and default aggregation period
	 */
	public RulesChronoAgendaEventListener() {
		this(null, AGGREGATION_PERIOD_MS);
	}
	
	/**
	 * Creates {@link RulesChronoAgendaEventListener} with no session prefix and provided aggregation period
	 * 
	 * @param aggregationPeriodMs
	 */
	public RulesChronoAgendaEventListener(long aggregationPeriodMs) {
		this(null, aggregationPeriodMs);
	}
	
	/**
	 * Creates {@link RulesChronoAgendaEventListener} with provided session prefix and default aggregation period
	 * 
	 * @param sessionPreffix
	 */
	public RulesChronoAgendaEventListener(String sessionPreffix) {
		this(sessionPreffix, AGGREGATION_PERIOD_MS);
	}
	
	/**
	 * Creates {@link RulesChronoAgendaEventListener} with provided session prefix and aggregation period
	 * 
	 * @param sessionPreffix
	 * @param aggregationPeriodMs
	 */
	public RulesChronoAgendaEventListener(String sessionPreffix, long aggregationPeriodMs) {
		this.sessionPreffix = sessionPreffix;
		this.aggregationPeriodMs = aggregationPeriodMs;
	}
	
	public TreeMap<String, Stat> getPerfStat() {
		TreeMap<String, Stat> result = new TreeMap<>(ALPHANUM_COMPARATOR);
		for (Entry<String, PerfStat> e : rulesStat.entrySet())
			result.put(e.getKey(), e.getValue().getStat());
		return result;
	}
	
	@Override
	public void beforeMatchFired(BeforeMatchFiredEvent event) {
		String ruleName = event.getMatch().getRule().getName();
		PerfStat ruleStat = rulesStat.get(ruleName);
		if (ruleStat == null) {
			synchronized (rulesStat) {
				if (ruleStat == null) {
					ruleStat = new PerfStat(sessionPreffix == null ? ruleName : sessionPreffix + ruleName, aggregationPeriodMs);
					rulesStat.put(ruleName, ruleStat);
				}
			}
		}
		ruleStat.start();
	}
	
	@Override
	public void afterMatchFired(AfterMatchFiredEvent event) {
		rulesStat.get(event.getMatch().getRule().getName()).stop();
	}
	
	public void reset() {
		rulesStat.values().forEach(PerfStat::reset);
	}
}
