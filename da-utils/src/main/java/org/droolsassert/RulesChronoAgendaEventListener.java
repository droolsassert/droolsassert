package org.droolsassert;

import static org.droolsassert.util.AlphanumComparator.ALPHANUM_COMPARATOR;
import static org.droolsassert.util.PerfStat.getDefaultAggregationPeriodMs;

import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import org.droolsassert.util.PerfStat;
import org.droolsassert.util.Stat;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.AfterMatchFiredEvent;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;

/**
 * Collect live performance statistic for rules (then block) as aggregated {@code Serializable} result.<br>
 * 
 * @see RulesChronoChartRecorder
 * @see PerfStat
 */
public class RulesChronoAgendaEventListener extends DefaultAgendaEventListener {
	
	protected final ConcurrentHashMap<String, PerfStat> rulesStat = new ConcurrentHashMap<>();
	protected final long aggregationPeriodMs;
	protected String sessionPrefix;
	protected boolean usePackageName;
	
	/**
	 * Creates {@link RulesChronoAgendaEventListener} with default aggregation period
	 */
	public RulesChronoAgendaEventListener() {
		this(getDefaultAggregationPeriodMs());
	}
	
	/**
	 * Creates {@link RulesChronoAgendaEventListener} with aggregation period
	 * 
	 * @param aggregationPeriodMs
	 */
	public RulesChronoAgendaEventListener(long aggregationPeriodMs) {
		this.aggregationPeriodMs = aggregationPeriodMs;
	}
	
	/**
	 * Include rule package name to qualify rule name, false by default
	 */
	public RulesChronoAgendaEventListener withPackageName(boolean usePackageName) {
		this.usePackageName = usePackageName;
		return this;
	}
	
	/**
	 * Include unique session prefix to segregate statistic.<br>
	 * Optional unless you want to use different aggregation periods for the same rule names.<br>
	 * If several listeners gather statistic under the same name statistic will be merged and 'peer' counter increased.
	 */
	public RulesChronoAgendaEventListener withSessionPrefix(String sessionPrefix) {
		this.sessionPrefix = sessionPrefix;
		return this;
	}
	
	public TreeMap<String, Stat> getPerfStat() {
		TreeMap<String, Stat> result = new TreeMap<>(ALPHANUM_COMPARATOR);
		for (Entry<String, PerfStat> e : rulesStat.entrySet())
			result.put(e.getKey(), e.getValue().getStat());
		return result;
	}
	
	@Override
	public void beforeMatchFired(BeforeMatchFiredEvent event) {
		String ruleName = uniqueRuleName(event.getMatch().getRule());
		PerfStat ruleStat = rulesStat.get(ruleName);
		if (ruleStat == null) {
			synchronized (rulesStat) {
				if (ruleStat == null) {
					ruleStat = new PerfStat(sessionPrefix, ruleName, aggregationPeriodMs);
					rulesStat.put(ruleName, ruleStat);
				}
			}
		}
		ruleStat.start();
	}
	
	private String uniqueRuleName(Rule rule) {
		StringBuilder sb = new StringBuilder();
		if (usePackageName) {
			sb.append(rule.getPackageName());
			sb.append(".");
		}
		sb.append(rule.getName());
		return sb.toString();
	}
	
	@Override
	public void afterMatchFired(AfterMatchFiredEvent event) {
		rulesStat.get(uniqueRuleName(event.getMatch().getRule())).stop();
	}
	
	public void reset() {
		rulesStat.values().forEach(PerfStat::reset);
	}
}
