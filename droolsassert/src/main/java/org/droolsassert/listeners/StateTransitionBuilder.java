package org.droolsassert.listeners;

import static java.awt.Color.white;
import static java.io.File.pathSeparator;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.System.setProperty;
import static java.nio.charset.Charset.defaultCharset;
import static javax.imageio.ImageIO.write;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;
import static org.droolsassert.DroolsAssertUtils.directory;
import static org.droolsassert.DroolsAssertUtils.getSimpleName;
import static org.droolsassert.ui.CellType.DeletedFact;
import static org.droolsassert.ui.CellType.InsertedFact;
import static org.droolsassert.ui.CellType.UpdatedFact;
import static org.droolsassert.util.JsonUtils.toYaml;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JPanel;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.core.common.InternalFactHandle;
import org.droolsassert.DroolsAssert;
import org.droolsassert.DroolsAssertException;
import org.droolsassert.DroolsSession;
import org.droolsassert.ui.AppFrame;
import org.droolsassert.ui.StateTransitionGraph;
import org.jgraph.JGraph;
import org.jgraph.graph.DefaultGraphModel;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.time.SessionPseudoClock;

/**
 * Creates state transition diagrams<br>
 * <p>
 * Define system property to enable the report
 * 
 * <pre>
 * -Ddroolsassert.stateTransitionReport[=&lt;image_format&gt;[&lt;path_separator&gt;&lt;directory_path&gt;]]
 * </pre>
 * 
 * <b>image_format</b> - graph output image format, default png<br>
 * <b>directory_path</b> - directory for reports per test, default
 * 
 * <pre>
 * target/droolsassert/stateTransitionReport
 * </pre>
 */
public class StateTransitionBuilder extends DefaultAgendaEventListener implements DroolsassertListener, RuleRuntimeEventListener {
	
	private static String systemProperty = getProperty("droolsassert.stateTransitionReport");
	
	static {
		setProperty("sun.java2d.uiScale", "1.0");
	}
	
	private DroolsSession droolsSessionMeta;
	private DroolsAssert droolsAssert;
	private SessionPseudoClock clock;
	private File reportsDirectory;
	private String format;
	private String test;
	private String scenario;
	
	private StateTransitionGraph graph;
	private AtomicInteger activatedCounter;
	private AtomicInteger insertedCounter;
	private AtomicInteger updatedCounter;
	private AtomicInteger deletedCounter;
	
	public StateTransitionBuilder(DroolsSession droolsSessionMeta, DroolsAssert droolsAssert, SessionPseudoClock clock) {
		this.droolsSessionMeta = droolsSessionMeta;
		this.droolsAssert = droolsAssert;
		this.clock = clock;
	}
	
	@Override
	public boolean enabled() {
		if (systemProperty == null && !droolsSessionMeta.showStateTransitionPopup())
			return false;
		if (reportsDirectory == null)
			initialize();
		return true;
	}
	
	@Override
	public void beforeScenario(String test, String scenario) {
		this.test = test;
		this.scenario = scenario;
		directory(new File(reportsDirectory, getReportName()));
		
		activatedCounter = new AtomicInteger();
		insertedCounter = new AtomicInteger();
		updatedCounter = new AtomicInteger();
		deletedCounter = new AtomicInteger();
		graph = new StateTransitionGraph(this, new DefaultGraphModel());
	}
	
	@Override
	public void afterScenario() {
		graph.highlightRetainedFacts();
		graph.layoutHierarchy();
		graph.layoutHierarchy();
		
		writeToFile(graph);
		
		if (droolsSessionMeta.showStateTransitionPopup())
			new AppFrame(this, scenario).showDialog();
	}
	
	private void initialize() {
		if ("true".equals(systemProperty))
			systemProperty = EMPTY;
		String[] params = trimToEmpty(systemProperty).split(pathSeparator);
		format = defaultIfEmpty(params[0], "png");
		reportsDirectory = directory(new File(params.length > 1 ? params[1] : "target/droolsassert/stateTransitionReport"));
	}
	
	@Override
	public void beforeMatchFired(BeforeMatchFiredEvent event) {
		activatedCounter.incrementAndGet();
		graph.ruleTriggered(event.getMatch());
	}
	
	@Override
	public void objectInserted(ObjectInsertedEvent event) {
		insertedCounter.incrementAndGet();
		graph.objectUpdated((InternalFactHandle) event.getFactHandle(), (RuleImpl) event.getRule(), InsertedFact);
	}
	
	@Override
	public void objectUpdated(ObjectUpdatedEvent event) {
		updatedCounter.incrementAndGet();
		graph.objectUpdated((InternalFactHandle) event.getFactHandle(), (RuleImpl) event.getRule(), UpdatedFact);
	}
	
	@Override
	public void objectDeleted(ObjectDeletedEvent event) {
		deletedCounter.incrementAndGet();
		graph.objectUpdated((InternalFactHandle) event.getFactHandle(), (RuleImpl) event.getRule(), DeletedFact);
	}
	
	private void writeToFile(JGraph graph) {
		try {
			JPanel panel = new JPanel();
			panel.setDoubleBuffered(false);
			panel.add(graph);
			panel.setVisible(true);
			panel.setEnabled(true);
			panel.addNotify();
			panel.validate();
			
			BufferedImage image = graph.getImage(white, 5);
			File f = new File(reportsDirectory, getReportName() + "/graph." + format);
			if (image != null && !write(image, format, f))
				throw new DroolsAssertException("No encoder for " + format);
			
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot write to file", e);
		}
	}
	
	public void writeToFile(Object fact, String stateId) {
		try {
			String fileName = format("%s/%s%s.txt", getReportName(), getSimpleName(fact.getClass()), stateId);
			writeStringToFile(new File(reportsDirectory, fileName), objectStateDump(fact), defaultCharset());
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot write object state to file", e);
		}
	}
	
	protected String objectStateDump(Object fact) {
		try {
			return toYaml(fact);
		} catch (Exception e) {
			return reflectionToString(fact, MULTI_LINE_STYLE);
		}
	}
	
	public DroolsAssert getDroolsAssert() {
		return droolsAssert;
	}

	public StateTransitionGraph getGraph() {
		return graph;
	}

	public AtomicInteger getActivatedCounter() {
		return activatedCounter;
	}

	public AtomicInteger getInsertedCounter() {
		return insertedCounter;
	}

	public AtomicInteger getUpdatedCounter() {
		return updatedCounter;
	}

	public AtomicInteger getDeletedCounter() {
		return deletedCounter;
	}

	public SessionPseudoClock getClock() {
		return clock;
	}

	protected String getReportName() {
		return (test + "#" + scenario).replace('/', '.');
	}
}