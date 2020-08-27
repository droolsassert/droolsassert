package org.droolsassert.report;

import static java.io.File.pathSeparator;
import static java.lang.Integer.parseInt;
import static java.lang.String.CASE_INSENSITIVE_ORDER;
import static java.lang.System.getProperty;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.io.FileUtils.forceMkdir;
import static org.apache.commons.io.FileUtils.forceMkdirParent;
import static org.apache.commons.io.IOUtils.readLines;
import static org.droolsassert.DroolsAssertUtils.COUNT_OF_RULES;

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
import org.kie.api.runtime.KieSession;

/**
 * Creates activations coverage (known rules in a session vs activated rules)<br>
 * First line of the report is percent of activated rules<br>
 * Other lines shows absolute count of rule activations<br>
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
 * -Ddroolsassert.activationReport[=[&lt;directory_path&gt;][&lt;path_separator&gt;][&lt;file_path&gt;]]
 * </pre>
 * 
 * <b>directory_path</b> - directory for reports per test, default
 * 
 * <pre>
 * target/droolsassert/activationReports/
 * </pre>
 * 
 * <b>file_path</b> - consolidated report file path, default
 * 
 * <pre>
 * target/droolsassert/activationReport.txt
 * </pre>
 */
public class ActivationReportBuilder {
	
	private static String systemProperty = getProperty("droolsassert.activationReport");
	
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
		initializeReportFiles();
	}
	
	private void initializeReportFiles() {
		if (systemProperty.isEmpty()) {
			reportsDirectory = new File("target/droolsassert/activationReports/");
			consolidatedReport = new File("target/droolsassert/activationReport.txt");
		} else {
			for (String path : systemProperty.split(pathSeparator)) {
				File file = new File(path);
				if (file.isDirectory())
					reportsDirectory = file;
				else
					consolidatedReport = file;
			}
		}
		try {
			if (reportsDirectory != null)
				forceMkdir(reportsDirectory);
			if (consolidatedReport != null) {
				forceMkdirParent(consolidatedReport);
				consolidatedReport.createNewFile();
			}
		} catch (IOException e) {
			throw new RuntimeException("Cannot initialize reports file-system", e);
		}
	}
	
	public void buildReports() {
		if (systemProperty == null)
			return;
		
		buildReport();
		buildConsolidatedReport();
	}
	
	private void buildReport() {
		if (reportsDirectory == null)
			return;
		
		TreeMap<String, Integer> reportData = new TreeMap<>(CASE_INSENSITIVE_ORDER);
		knownRules().forEach(rule -> reportData.put(rule, 0));
		reportData.putAll(activations);
		
		File reportFile = new File(reportsDirectory, reportName + ".txt");
		writeReport(reportFile, reportData);
	}
	
	private void buildConsolidatedReport() {
		if (consolidatedReport == null)
			return;
		
		TreeMap<String, Integer> consolidatedReportData = new TreeMap<>(CASE_INSENSITIVE_ORDER);
		knownRules().forEach(rule -> consolidatedReportData.put(rule, 0));
		
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
	}
	
	private void writeReport(File report, Map<String, Integer> activations) {
		Set<String> triggeredRules = triggeredRules(activations);
		try (PrintWriter pw = new PrintWriter(report)) {
			pw.printf("%-6.2f%n", 100.0 * triggeredRules.size() / activations.size());
			for (Entry<String, Integer> e : activations.entrySet())
				pw.printf("%-7d \t%s%n", e.getValue(), e.getKey());
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot write consolidated report", e);
		}
	}
	
	private Set<String> knownRules() {
		return session.getKieBase().getKiePackages().stream()
				.flatMap(p -> p.getRules().stream())
				.map(rule -> rule.getName())
				.collect(toSet());
	}
	
	private Set<String> triggeredRules(Map<String, Integer> activations) {
		return activations.entrySet().stream()
				.filter(e -> e.getValue() > 0)
				.map(e -> e.getKey())
				.collect(toSet());
	}
	
	public void setReportName(String reportName) {
		this.reportName = reportName.replaceAll("[\\/]", ".");
	}
}
