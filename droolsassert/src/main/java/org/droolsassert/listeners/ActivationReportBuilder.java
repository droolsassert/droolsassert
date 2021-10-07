package org.droolsassert.listeners;

import static java.io.File.pathSeparator;
import static java.lang.Integer.parseInt;
import static java.lang.System.getProperty;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.io.FileUtils.forceMkdirParent;
import static org.apache.commons.io.IOUtils.readLines;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.droolsassert.DroolsAssertUtils.COUNT_OF_RULES;
import static org.droolsassert.DroolsAssertUtils.directory;
import static org.droolsassert.DroolsAssertUtils.getReentrantFileLockFactory;
import static org.droolsassert.util.AlphanumComparator.ALPHANUM_COMPARATOR;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;

import org.droolsassert.DroolsAssertException;
import org.droolsassert.util.ReentrantFileLock;
import org.kie.api.definition.rule.Query;
import org.kie.api.runtime.KieSession;

/**
 * Creates activation report (known rules in a session vs activated rules)<br>
 * First line of the report is percent of activated rules<br>
 * Other lines show absolute count of rules activation<br>
 * 
 * <pre>
 * 75.00 
 * 1       	after
 * 1       	atomic int rule
 * 0       	atomic long rule
 * 1       	before
 * </pre>
 * <p>
 * Define system property to enable activation reports
 * 
 * <pre>
 * -Ddroolsassert.activationReport[=&lt;directory_path&gt;[&lt;path_separator&gt;&lt;file_path&gt;]]
 * </pre>
 * 
 * <b>directory_path</b> - directory for reports per test, default
 * 
 * <pre>
 * target/droolsassert/activationReport
 * </pre>
 * 
 * <b>file_path</b> - consolidated report file path, default
 * 
 * <pre>
 * ${directory_path}/activationReport.txt
 * </pre>
 */
public class ActivationReportBuilder implements DroolsassertListener {
	
	private static String systemProperty = getProperty("droolsassert.activationReport");
	private static volatile ReentrantFileLock consolidatedReportLock;
	
	private KieSession session;
	private Map<String, Integer> activations;
	private File reportsDirectory;
	private File consolidatedReport;
	private String reportName;
	
	public ActivationReportBuilder(KieSession session, Map<String, Integer> activations) {
		if (systemProperty == null)
			return;
		this.session = session;
		this.activations = activations;
	}
	
	@Override
	public boolean enabled() {
		if (systemProperty == null)
			return false;
		if (reportsDirectory == null)
			initialize();
		return true;
	}
	
	@Override
	public void beforeScenario(String test, String scenario) {
		this.reportName = (test + "#" + scenario).replace('/', '.');
	}
	
	@Override
	public void afterScenario() {
		buildReport();
		buildConsolidatedReport();
	}
	
	private void initialize() {
		if ("true".equals(systemProperty))
			systemProperty = EMPTY;
		String[] params = systemProperty.split(pathSeparator);
		reportsDirectory = directory(new File(defaultIfEmpty(params[0], "target/droolsassert/activationReport")));
		consolidatedReport = new File(params.length > 1 ? params[1] : reportsDirectory + ".txt");
		
		getConsolidatedReportLock().lock();
		try {
			if (!consolidatedReport.exists()) {
				forceMkdirParent(consolidatedReport);
				consolidatedReport.createNewFile();
			}
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot initialize reports file system", e);
		} finally {
			getConsolidatedReportLock().unlock();
		}
	}
	
	private void buildReport() {
		if (reportsDirectory == null)
			return;
		
		TreeMap<String, Integer> reportData = new TreeMap<>(ALPHANUM_COMPARATOR);
		knownRules().forEach(rule -> reportData.put(rule, 0));
		reportData.putAll(activations);
		
		File reportFile = new File(reportsDirectory, reportName + ".txt");
		writeReport(reportFile, reportData);
	}
	
	private void buildConsolidatedReport() {
		if (consolidatedReport == null)
			return;
		
		TreeMap<String, Integer> consolidatedReportData = new TreeMap<>(ALPHANUM_COMPARATOR);
		knownRules().forEach(rule -> consolidatedReportData.put(rule, 0));
		
		getConsolidatedReportLock().lock();
		try {
			try (InputStream is = new FileInputStream(consolidatedReport)) {
				readLines(is, defaultCharset()).stream()
						.skip(1)
						.forEach(line -> {
							Matcher m = COUNT_OF_RULES.matcher(line);
							if (!m.matches())
								throw new IllegalStateException("Report broken, please delete manually " + consolidatedReport);
							consolidatedReportData.put(m.group("rule"), parseInt(m.group("count")));
						});
			} catch (IOException e) {
				throw new DroolsAssertException("Cannot read consolidated report", e);
			}
			
			activations.entrySet().forEach(e -> {
				consolidatedReportData.put(e.getKey(), consolidatedReportData.containsKey(e.getKey())
						? e.getValue() + consolidatedReportData.get(e.getKey())
						: e.getValue());
			});
			
			writeReport(consolidatedReport, consolidatedReportData);
		} finally {
			getConsolidatedReportLock().unlock();
		}
	}
	
	private void writeReport(File report, Map<String, Integer> activations) {
		if (activations.isEmpty())
			return;
		Set<String> triggeredRules = triggeredRules(activations);
		try (PrintWriter pw = new PrintWriter(report)) {
			pw.printf("%.2f%n", 100.0 * triggeredRules.size() / activations.size());
			for (Entry<String, Integer> e : activations.entrySet())
				pw.printf("%-7d \t%s%n", e.getValue(), e.getKey());
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot write activation report", e);
		}
	}
	
	private Set<String> knownRules() {
		return session.getKieBase().getKiePackages().stream()
				.flatMap(p -> p.getRules().stream())
				.filter(r -> !Query.class.isInstance(r))
				.map(rule -> rule.getName())
				.collect(toSet());
	}
	
	private Set<String> triggeredRules(Map<String, Integer> activations) {
		return activations.entrySet().stream()
				.filter(e -> e.getValue() > 0)
				.map(e -> e.getKey())
				.collect(toSet());
	}
	
	private ReentrantFileLock getConsolidatedReportLock() {
		if (consolidatedReportLock != null)
			return consolidatedReportLock;
		synchronized (getClass()) {
			if (consolidatedReportLock == null)
				consolidatedReportLock = getReentrantFileLockFactory().newLock(ActivationReportBuilder.class.getName());
			return consolidatedReportLock;
		}
	}
}
