package org.droolsassert;

import static java.awt.Color.black;
import static java.awt.Color.lightGray;
import static java.lang.String.format;
import static org.droolsassert.util.ChartUtils.pngChart;

import java.util.Random;
import java.util.TreeMap;

import org.jfree.data.time.TimeSeries;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@DroolsSession("classpath:/org/droolsassert/chrono.drl")
public class RulesChronoTest {
	
	@Rule
	public DroolsAssert drools = new DroolsAssert();
	
	@Test
	@Ignore
	@TestRules(expected = { "sleep method", "more than 200" })
	public void testRulesChronoListener() {
		for (int i = 1; i <= 50; i++)
			drools.insertAndFire(randomFunction(i));
		drools.printPerformanceStatistic();
	}
	
	@Test
	@Ignore
	@TestRules(expected = { "sleep method", "more than 200" })
	public void testRulesChronoChartRecorder() {
		drools.setRulesChrono(new RulesChronoChartRecorder(5000));
		
		for (int i = 1; i <= 110; i++)
			drools.insertAndFire(randomFunction(i));
		drools.printPerformanceStatistic();
		
		RulesChronoChartRecorder rulesChrono = drools.getRulesChrono();
		TreeMap<String, TimeSeries> rulesMaxChart = rulesChrono.getRulesMaxChart();
		rulesChrono.getRulesAvgChart().entrySet()
				.forEach(e -> pngChart(format("charts/%s.png", e.getKey()), 1024, 500, e.getValue(), black, rulesMaxChart.get(e.getKey()), lightGray));
	}
	
	public int randomFunction(int i) {
		return new Random().nextInt(i) * 10;
	}
}
