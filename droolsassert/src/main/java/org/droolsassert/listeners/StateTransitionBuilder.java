package org.droolsassert.listeners;

import static java.awt.Color.darkGray;
import static java.awt.Color.white;
import static java.awt.Toolkit.getDefaultToolkit;
import static java.awt.event.KeyEvent.VK_ESCAPE;
import static java.io.File.pathSeparator;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.System.identityHashCode;
import static java.lang.Thread.currentThread;
import static java.nio.charset.Charset.defaultCharset;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import static javax.swing.KeyStroke.getKeyStroke;
import static javax.swing.SwingUtilities.invokeLater;
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;
import static org.droolsassert.DroolsAssertUtils.directory;
import static org.droolsassert.DroolsAssertUtils.formatTime;
import static org.droolsassert.util.JsonUtils.toJson;
import static org.jgraph.graph.GraphConstants.ARROW_CLASSIC;
import static org.jgraph.graph.GraphConstants.setAutoSize;
import static org.jgraph.graph.GraphConstants.setBackground;
import static org.jgraph.graph.GraphConstants.setBorderColor;
import static org.jgraph.graph.GraphConstants.setBounds;
import static org.jgraph.graph.GraphConstants.setEndFill;
import static org.jgraph.graph.GraphConstants.setInset;
import static org.jgraph.graph.GraphConstants.setLineColor;
import static org.jgraph.graph.GraphConstants.setLineEnd;
import static org.jgraph.graph.GraphConstants.setOpaque;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.droolsassert.DroolsAssertException;
import org.droolsassert.DroolsSession;
import org.jgraph.JGraph;
import org.jgraph.graph.DefaultCellViewFactory;
import org.jgraph.graph.DefaultEdge;
import org.jgraph.graph.DefaultGraphCell;
import org.jgraph.graph.DefaultGraphModel;
import org.jgraph.graph.DefaultPort;
import org.jgraph.graph.GraphLayoutCache;
import org.jgraph.graph.GraphModel;
import org.kie.api.definition.rule.Rule;
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.time.SessionPseudoClock;
import org.kie.internal.definition.rule.InternalRule;

import com.jgraph.layout.JGraphFacade;
import com.jgraph.layout.hierarchical.JGraphHierarchicalLayout;

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
 * target/droolsassert/stateTransitionReports
 * </pre>
 */
public class StateTransitionBuilder extends DefaultAgendaEventListener implements DroolsassertListener, RuleRuntimeEventListener {
	
	private enum CellType {
		UpdatedFact("#c8edc2", "#42b52f"),
		DeletedFact("#ebebeb", "#9e9e9e"),
		Rule("#c7e7ff", "#4eb3fc");
		
		private String background;
		private String borderColor;
		
		private CellType(String background, String borderColor) {
			this.background = background;
			this.borderColor = borderColor;
		}
	}
	
	private static String systemProperty = getProperty("droolsassert.stateTransitionReport");
	
	private DroolsSession droolsSessionMeta;
	private SessionPseudoClock clock;
	private File reportsDirectory;
	private String format;
	private String test;
	private String scenario;
	private JGraph graph;
	private Thread eventDispatchThread;
	
	private Map<Integer, AtomicInteger> lastObjectState;
	private Map<Integer, DefaultGraphCell> lastObjectCell;
	private Map<Integer, AtomicInteger> lastRuleTriggerCount;
	private AtomicInteger adgeCounter;
	
	public StateTransitionBuilder(DroolsSession droolsSessionMeta, SessionPseudoClock clock) {
		this.droolsSessionMeta = droolsSessionMeta;
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
		
		lastObjectState = new HashMap<>();
		lastObjectCell = new HashMap<>();
		lastRuleTriggerCount = new HashMap<>();
		adgeCounter = new AtomicInteger();
		graph = newGraph();
	}
	
	@Override
	public void afterScenario() {
		// JGraph instances require class synchronization otherwise NPEs appear deep in AWT stuff
		synchronized (StateTransitionBuilder.class) {
			layout(graph);
			writeToFile(graph);
			if (droolsSessionMeta.showStateTransitionPopup())
				showDialog(graph);
		}
	}
	
	private void initialize() {
		if ("true".equals(systemProperty))
			systemProperty = EMPTY;
		String[] params = trimToEmpty(systemProperty).split(pathSeparator);
		format = defaultIfEmpty(params[0], "png");
		reportsDirectory = directory(new File(params.length > 1 ? params[1] : "target/droolsassert/stateTransitionReports"));
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
			if (image != null && !ImageIO.write(image, format, f))
				throw new DroolsAssertException("No encoder for " + format);
			
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot write to file", e);
		}
	}
	
	private void showDialog(JGraph graph) {
		JFrame dialog = new JFrame(scenario);
		dialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		dialog.getRootPane().registerKeyboardAction(e -> dialog.dispose(), getKeyStroke(VK_ESCAPE, 0), WHEN_IN_FOCUSED_WINDOW);
		dialog.getContentPane().add(new JScrollPane(graph));
		dialog.setMinimumSize(new Dimension(800, 600));
		dialog.setMaximumSize(getDefaultToolkit().getScreenSize());
		dialog.pack();
		dialog.setLocationRelativeTo(null);
		dialog.setVisible(true);
		awaitForDialogClose(dialog);
	}
	
	@Override
	public void beforeMatchFired(BeforeMatchFiredEvent event) {
		InternalRule rule = (InternalRule) event.getMatch().getRule();
		int ruleId = identityHashCode(rule);
		if (!lastRuleTriggerCount.containsKey(ruleId))
			lastRuleTriggerCount.putIfAbsent(ruleId, new AtomicInteger());
		int triggerCount = lastRuleTriggerCount.get(ruleId).incrementAndGet();
		
		String ruleMeta = format("%s/%s/%s", rule.getAgendaGroup(), rule.getSalienceValue(), triggerCount);
		DefaultGraphCell ruleCell = newCell(newCellLabel(rule.getName(), ruleMeta, formatTime(clock)), CellType.Rule);
		lastObjectCell.put(ruleId, ruleCell);
		
		synchronized (StateTransitionBuilder.class) {
			graph.getGraphLayoutCache().insert(ruleCell);
			event.getMatch().getObjects().stream()
					.map(o -> lastObjectCell.get(identityHashCode(o)))
					.filter(Objects::nonNull).forEach(objectCell -> {
						graph.getGraphLayoutCache().setVisible(objectCell, true);
						graph.getGraphLayoutCache().insert(newEdge(objectCell, ruleCell));
					});
		}
	}
	
	@Override
	public void objectInserted(ObjectInsertedEvent event) {
		objectUpdated(event.getObject(), event.getRule(), CellType.UpdatedFact);
	}
	
	@Override
	public void objectUpdated(ObjectUpdatedEvent event) {
		objectUpdated(event.getObject(), event.getRule(), CellType.UpdatedFact);
	}
	
	@Override
	public void objectDeleted(ObjectDeletedEvent event) {
		objectUpdated(event.getOldObject(), event.getRule(), CellType.DeletedFact);
	}
	
	private void objectUpdated(Object fact, Rule rule, CellType cellType) {
		int factId = identityHashCode(fact);
		if (!lastObjectState.containsKey(factId))
			lastObjectState.putIfAbsent(factId, new AtomicInteger());
		AtomicInteger state = lastObjectState.get(factId);
		String stateId = format("#%s-%s", factId, cellType == CellType.DeletedFact ? state : state.incrementAndGet());
		
		try {
			writeStringToFile(new File(reportsDirectory, getReportName() + "/" + stateId), objectStateDump(fact), defaultCharset());
		} catch (IOException e) {
			throw new DroolsAssertException("Cannot write object state to file", e);
		}
		
		DefaultGraphCell cell = newCell(newCellLabel(fact.getClass().getSimpleName(), stateId, formatTime(clock)), cellType);
		lastObjectCell.put(factId, cell);
		
		synchronized (StateTransitionBuilder.class) {
			graph.getGraphLayoutCache().insert(cell);
			graph.getGraphLayoutCache().setVisible(cell, false);
			
			if (rule != null) {
				graph.getGraphLayoutCache().setVisible(cell, true);
				graph.getGraphLayoutCache().insert(newEdge(lastObjectCell.get(identityHashCode(rule)), cell));
			}
		}
	}
	
	protected String objectStateDump(Object fact) {
		return toJson(fact, true);
	}
	
	private JGraph newGraph() {
		GraphModel model = new DefaultGraphModel();
		GraphLayoutCache view = new GraphLayoutCache(model, new DefaultCellViewFactory(), true);
		return new JGraph(model, view);
	}
	
	private DefaultGraphCell newCell(String label, CellType cellType) {
		DefaultGraphCell cell = new DefaultGraphCell(label);
		cell.add(new DefaultPort());
		
		setBounds(cell.getAttributes(), new Rectangle2D.Double(0, 0, 100, 50));
		setInset(cell.getAttributes(), 3);
		setBackground(cell.getAttributes(), Color.decode(cellType.background));
		setBorderColor(cell.getAttributes(), Color.decode(cellType.borderColor));
		setAutoSize(cell.getAttributes(), true);
		setOpaque(cell.getAttributes(), true);
		return cell;
	}
	
	private DefaultEdge newEdge(DefaultGraphCell cell1, DefaultGraphCell cell2) {
		DefaultEdge edge = new DefaultEdge(adgeCounter.incrementAndGet());
		edge.setSource(cell1.getChildAt(0));
		edge.setTarget(cell2.getChildAt(0));
		
		setLineColor(edge.getAttributes(), darkGray);
		setLineEnd(edge.getAttributes(), ARROW_CLASSIC);
		setEndFill(edge.getAttributes(), true);
		setOpaque(edge.getAttributes(), true);
		return edge;
	}
	
	private String newCellLabel(String line1, String line2, String line3) {
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<table style='padding: 0px; width:100%'>");
		sb.append("<tr style='padding: 0px'>");
		sb.append("<td style='padding: 0px; text-align: center; font-family:tahoma,serif; font-size:11px; font-weight: normal'>");
		sb.append(escapeHtml4(line1));
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("<tr style='padding: 0px'>");
		sb.append("<td style='padding: 0px; text-align: center; font-family:verdana; font-size:8px; font-weight: normal'>");
		sb.append(escapeHtml4(line2));
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("<tr style='padding: 0px'>");
		sb.append("<td style='padding: 0px; margin: -5; text-align: center; font-family:verdana; font-size:8px; font-weight: normal'>");
		sb.append(escapeHtml4(line3));
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("</table>");
		sb.append("</html>");
		return sb.toString();
	}
	
	private void layout(JGraph graph) {
		JGraphFacade facade = new JGraphFacade(graph);
		JGraphHierarchicalLayout layout = new JGraphHierarchicalLayout();
		layout.run(facade);
		graph.getGraphLayoutCache().edit(facade.createNestedMap(true, true));
	}
	
	private void awaitForDialogClose(JFrame frame) {
		CountDownLatch initEventDispatchThreadLatch = new CountDownLatch(1);
		invokeLater(() -> {
			eventDispatchThread = currentThread();
			initEventDispatchThreadLatch.countDown();
		});
		try {
			initEventDispatchThreadLatch.await();
		} catch (InterruptedException e) {
			// continue normally
		}
		try {
			eventDispatchThread.join();
		} catch (InterruptedException e) {
			// continue normally
		}
	}
	
	protected String getReportName() {
		return (test + "#" + scenario).replace('/', '.');
	}
}