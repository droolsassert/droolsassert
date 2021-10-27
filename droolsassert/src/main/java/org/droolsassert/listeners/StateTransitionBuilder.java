package org.droolsassert.listeners;

import static java.awt.Color.darkGray;
import static java.awt.Color.white;
import static java.awt.Toolkit.getDefaultToolkit;
import static java.awt.event.KeyEvent.VK_ESCAPE;
import static java.io.File.pathSeparator;
import static java.lang.String.format;
import static java.lang.System.getProperty;
import static java.lang.Thread.currentThread;
import static java.nio.charset.Charset.defaultCharset;
import static java.util.stream.Collectors.toList;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import static javax.swing.KeyStroke.getKeyStroke;
import static javax.swing.SwingUtilities.invokeLater;
import static javax.swing.WindowConstants.DISPOSE_ON_CLOSE;
import static org.apache.commons.io.FileUtils.writeStringToFile;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.apache.commons.lang3.builder.ToStringBuilder.reflectionToString;
import static org.apache.commons.lang3.builder.ToStringStyle.MULTI_LINE_STYLE;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;
import static org.droolsassert.DroolsAssertUtils.directory;
import static org.droolsassert.DroolsAssertUtils.formatTime;
import static org.droolsassert.DroolsAssertUtils.getRuleActivatedBy;
import static org.droolsassert.DroolsAssertUtils.getRuleLogicialDependencies;
import static org.droolsassert.DroolsAssertUtils.getSimpleName;
import static org.droolsassert.DroolsAssertUtils.isJustified;
import static org.droolsassert.listeners.StateTransitionBuilder.CellType.DeletedFact;
import static org.droolsassert.listeners.StateTransitionBuilder.CellType.InsertedFact;
import static org.droolsassert.listeners.StateTransitionBuilder.CellType.Rule;
import static org.droolsassert.listeners.StateTransitionBuilder.CellType.Statistic;
import static org.droolsassert.listeners.StateTransitionBuilder.CellType.UpdatedFact;
import static org.droolsassert.util.JsonUtils.toYaml;
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
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.drools.core.common.InternalFactHandle;
import org.drools.core.definitions.rule.impl.RuleImpl;
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
import org.kie.api.event.rule.BeforeMatchFiredEvent;
import org.kie.api.event.rule.DefaultAgendaEventListener;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.KieSession;
import org.kie.api.time.SessionPseudoClock;

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
 * target/droolsassert/stateTransitionReport
 * </pre>
 */
public class StateTransitionBuilder extends DefaultAgendaEventListener implements DroolsassertListener, RuleRuntimeEventListener {
	
	public enum CellType {
		InsertedFact("#c8edc2", "#42b52f"),
		UpdatedFact("#c8edc2", "#42b52f"),
		DeletedFact("#ebebeb", "#9e9e9e"),
		Rule("#c7e7ff", "#4eb3fc"),
		Statistic("#ffffff", "#ffffff");
		
		private String background;
		private String borderColor;
		
		private CellType(String background, String borderColor) {
			this.background = background;
			this.borderColor = borderColor;
		}
	}
	
	private static String systemProperty = getProperty("droolsassert.stateTransitionReport");
	
	private DroolsSession droolsSessionMeta;
	private KieSession session;
	private SessionPseudoClock clock;
	private File reportsDirectory;
	private String format;
	private String test;
	private String scenario;
	private JGraph graph;
	private DefaultGraphCell statisticCell;
	private volatile Thread eventDispatchThread;
	
	private IdentityHashMap<Object, AtomicInteger> lastObjectState;
	private IdentityHashMap<Object, DefaultGraphCell> lastObjectCell;
	private IdentityHashMap<Object, DefaultGraphCell> lastRemovedCell;
	private IdentityHashMap<Object, AtomicInteger> lastRuleActivationCount;
	private AtomicInteger adgeCounter;
	private AtomicInteger activatedCounter;
	private AtomicInteger insertedCounter;
	private AtomicInteger updatedCounter;
	private AtomicInteger deletedCounter;
	
	public StateTransitionBuilder(DroolsSession droolsSessionMeta, KieSession session, SessionPseudoClock clock) {
		this.droolsSessionMeta = droolsSessionMeta;
		this.session = session;
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
		
		lastObjectState = new IdentityHashMap<>();
		lastObjectCell = new IdentityHashMap<>();
		lastRemovedCell = new IdentityHashMap<>();
		lastRuleActivationCount = new IdentityHashMap<>();
		adgeCounter = new AtomicInteger();
		activatedCounter = new AtomicInteger();
		insertedCounter = new AtomicInteger();
		updatedCounter = new AtomicInteger();
		deletedCounter = new AtomicInteger();
		graph = newGraph();
		statisticCell = newStatisticCell();
	}
	
	@Override
	public void afterScenario() {
		Map<String, Integer> statistic = new LinkedHashMap<>();
		statistic.put("activated", activatedCounter.get());
		statistic.put("inserted", insertedCounter.get());
		statistic.put("updated", updatedCounter.get());
		statistic.put("deleted", deletedCounter.get());
		statistic.put("retained", (int) session.getObjects().stream().count());
		statisticCell.setUserObject(newStatsLabel(statistic));
		
		// JGraph instances require class synchronization otherwise NPEs appear deep in AWT stuff
		synchronized (StateTransitionBuilder.class) {
			layout(graph);
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
		reportsDirectory = directory(new File(params.length > 1 ? params[1] : "target/droolsassert/stateTransitionReport"));
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
		activatedCounter.incrementAndGet();
		RuleImpl rule = (RuleImpl) event.getMatch().getRule();
		if (!lastRuleActivationCount.containsKey(rule))
			lastRuleActivationCount.putIfAbsent(rule, new AtomicInteger());
		int activationCount = lastRuleActivationCount.get(rule).incrementAndGet();
		
		StringBuilder ruleMeta = new StringBuilder(rule.getAgendaGroup());
		if (rule.getActivationGroup() != null)
			ruleMeta.append("|").append(rule.getActivationGroup());
		ruleMeta.append("|").append(rule.getSalienceValue());
		if (rule.isSalienceDynamic())
			ruleMeta.append("D");
		if (rule.isNoLoop())
			ruleMeta.append("|NL");
		if (rule.isLockOnActive())
			ruleMeta.append("|LOA");
		ruleMeta.append("|").append(activationCount);
		
		String flags = rule.getTimer() == null ? "" : "T";
		DefaultGraphCell ruleCell = newCell(newLabel(Rule, rule.getName(), ruleMeta.toString(), formatTime(clock), flags), Rule);
		lastObjectCell.put(rule, ruleCell);
		
		synchronized (StateTransitionBuilder.class) {
			getView().insert(ruleCell);
			getView().setVisible(ruleCell, false);
			
			getRuleActivatedBy(event.getMatch()).stream()
					.map(this::getLastKnownObjectCell)
					.filter(Objects::nonNull)
					.forEach(objectCell -> getView().insert(newEdge(objectCell, ruleCell)));
			getRuleLogicialDependencies(event.getMatch()).stream()
					.flatMap(o -> lastObjectCell.entrySet().stream().filter(e -> e.getKey().equals(o)).map(e -> e.getValue()))
					.forEach(objectCell -> getView().insert(newEdge(ruleCell, objectCell)));
		}
	}
	
	@Override
	public void objectInserted(ObjectInsertedEvent event) {
		insertedCounter.incrementAndGet();
		objectUpdated((InternalFactHandle) event.getFactHandle(), (RuleImpl) event.getRule(), InsertedFact);
	}
	
	@Override
	public void objectUpdated(ObjectUpdatedEvent event) {
		updatedCounter.incrementAndGet();
		objectUpdated((InternalFactHandle) event.getFactHandle(), (RuleImpl) event.getRule(), UpdatedFact);
	}
	
	@Override
	public void objectDeleted(ObjectDeletedEvent event) {
		deletedCounter.incrementAndGet();
		objectUpdated((InternalFactHandle) event.getFactHandle(), (RuleImpl) event.getRule(), DeletedFact);
	}
	
	private void objectUpdated(InternalFactHandle fh, RuleImpl rule, CellType cellType) {
		Object fact = fh.getObject();
		if (!lastObjectState.containsKey(fact))
			lastObjectState.putIfAbsent(fact, new AtomicInteger());
		AtomicInteger state = lastObjectState.get(fact);
		String flags = fh.isEvent() ? "E" : "";
		if (isJustified(fh))
			flags += "J";
		
		String stateId = format("#%s-%s", fh.getIdentityHashCode(), cellType == DeletedFact ? state : state.incrementAndGet());
		writeToFile(fact, stateId);
		DefaultGraphCell cell = newCell(newLabel(cellType, getSimpleName(fact.getClass()), stateId, formatTime(clock), flags), cellType);
		DefaultGraphCell previousStateCell;
		if (cellType == DeletedFact) {
			previousStateCell = lastObjectCell.remove(fact);
			lastRemovedCell.put(fact, cell);
			if (isJustified(fh))
				lastObjectCell.keySet().stream().filter(e -> e.equals(fact)).collect(toList()).forEach(lastObjectCell::remove);
		} else {
			previousStateCell = lastObjectCell.put(fact, cell);
		}
		
		synchronized (StateTransitionBuilder.class) {
			getView().insert(cell);
			getView().setVisible(cell, false);
			
			if (rule != null)
				getView().insert(newEdge(lastObjectCell.get(rule), cell));
			else if (previousStateCell != null)
				getView().insert(newEdge(previousStateCell, cell));
		}
	}
	
	private GraphLayoutCache getView() {
		return graph.getGraphLayoutCache();
	}
	
	private DefaultGraphCell getLastKnownObjectCell(Object object) {
		return firstNonNull(lastObjectCell.get(object), lastRemovedCell.get(object));
	}
	
	private void writeToFile(Object fact, String stateId) {
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
	
	private DefaultGraphCell newStatisticCell() {
		DefaultGraphCell statistic = newCell(null, Statistic);
		DefaultGraphCell statistic2 = newCell(null, Statistic);
		getView().insert(statistic);
		getView().insert(statistic2);
		
		DefaultEdge edge = new DefaultEdge();
		edge.setSource(statistic.getChildAt(0));
		edge.setTarget(statistic2.getChildAt(0));
		setLineColor(edge.getAttributes(), white);
		getView().insert(edge);
		
		return statistic;
	}
	
	private String newLabel(CellType cellType, String line1, String line2, String line3, String flags) {
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<table style='width:100%'>");
		sb.append("<tr>");
		sb.append("<td style='padding: 0; text-align: center; font-family:tahoma,serif; font-size:11px; font-weight: normal'>");
		sb.append(escapeHtml4(line1));
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("<tr>");
		sb.append("<td style='padding: 0; text-align: center; font-family:verdana; font-size:8px; font-weight: lighter'>");
		sb.append(escapeHtml4(line2));
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("<tr style=''>");
		sb.append("<td style='padding: 0px;'>");
		
		sb.append("<table style='margin-top: -7; margin-bottom: -7; margin-left: -4; margin-right: -4; width:100%;'>");
		sb.append("<tr>");
		sb.append(format("<td style='padding: 0; font-family:verdana; font-size:8px; font-weight: normal; color: %s'>", cellType.background));
		sb.append(flags);
		sb.append("</td>");
		sb.append("<td style='padding: 0; text-align: center; font-family:verdana; font-size:8px; font-weight: lighter; width: 100%;'>");
		sb.append(line3);
		sb.append("</td>");
		sb.append("<td style='padding: 0; font-family:verdana; font-size:8px; font-weight: normal; color: red'>");
		sb.append(flags);
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("</table>");
		
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("</table>");
		sb.append("</html>");
		return sb.toString();
	}
	
	private String newStatsLabel(Map<String, Integer> map) {
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append("<table style='width:100%'>");
		for (Entry<String, Integer> entry : map.entrySet()) {
			sb.append("<tr>");
			sb.append("<td style='padding: 0; text-align: right; font-family:verdana; font-size:9px; font-weight: lighter'>");
			sb.append(entry.getKey());
			sb.append("</td>");
			sb.append("<td style='padding: 0 0 0 3px; text-align: left; font-family:verdana; font-size:9px; font-weight: lighter'>");
			sb.append(entry.getValue());
			sb.append("</td>");
			sb.append("</tr>");
		}
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