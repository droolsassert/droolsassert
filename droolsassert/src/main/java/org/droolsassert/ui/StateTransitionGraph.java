package org.droolsassert.ui;

import static java.awt.Color.darkGray;
import static java.awt.Color.decode;
import static java.awt.Color.red;
import static java.lang.Math.sqrt;
import static java.lang.String.format;
import static java.util.Locale.US;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.ObjectUtils.firstNonNull;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;
import static org.droolsassert.DroolsAssertUtils.formatTime;
import static org.droolsassert.DroolsAssertUtils.getRuleActivatedBy;
import static org.droolsassert.DroolsAssertUtils.getRuleLogicialDependencies;
import static org.droolsassert.DroolsAssertUtils.getSimpleName;
import static org.droolsassert.DroolsAssertUtils.isJustified;
import static org.droolsassert.ui.CellType.DeletedFact;
import static org.droolsassert.ui.CellType.Rule;
import static org.jgraph.graph.GraphConstants.ARROW_CLASSIC;
import static org.jgraph.graph.GraphConstants.setAutoSize;
import static org.jgraph.graph.GraphConstants.setBorderColor;
import static org.jgraph.graph.GraphConstants.setEndFill;
import static org.jgraph.graph.GraphConstants.setLineColor;
import static org.jgraph.graph.GraphConstants.setLineEnd;
import static org.jgraph.graph.GraphConstants.setLineWidth;
import static org.droolsassert.ui.UIUtils.scale;
import static org.droolsassert.ui.UIUtils.scaling;

import com.jgraph.layout.JGraphFacade;
import com.jgraph.layout.hierarchical.JGraphHierarchicalLayout;
import java.awt.geom.Rectangle2D;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.definitions.rule.impl.RuleImpl;
import org.droolsassert.listeners.StateTransitionBuilder;
import org.jgraph.JGraph;
import org.jgraph.graph.DefaultCellViewFactory;
import org.jgraph.graph.DefaultEdge;
import org.jgraph.graph.DefaultGraphCell;
import org.jgraph.graph.DefaultPort;
import org.jgraph.graph.GraphConstants;
import org.jgraph.graph.GraphLayoutCache;
import org.jgraph.graph.GraphModel;
import org.kie.api.runtime.rule.Match;

public class StateTransitionGraph extends JGraph {
	private StateTransitionBuilder builder;

	private HashSet<DefaultGraphCell> cells = new HashSet<>();
	private IdentityHashMap<Object, AtomicInteger> lastObjectState;
	private IdentityHashMap<Object, DefaultGraphCell> lastObjectCell;
	private IdentityHashMap<Object, DefaultGraphCell> lastRemovedCell;
	private IdentityHashMap<Object, AtomicInteger> lastRuleActivationCount;
	private AtomicInteger adgeCounter;

	public StateTransitionGraph(StateTransitionBuilder builder, GraphModel model) {
		super(model, new GraphLayoutCache(model, new DefaultCellViewFactory(), true));
		this.builder = builder;

		lastObjectState = new IdentityHashMap<>();
		lastObjectCell = new IdentityHashMap<>();
		lastRemovedCell = new IdentityHashMap<>();
		lastRuleActivationCount = new IdentityHashMap<>();
		adgeCounter = new AtomicInteger();

		setAutoResizeGraph(true);
	}

	public void layoutHierarchy() {
		JGraphFacade facade = new JGraphFacade(this);
		JGraphHierarchicalLayout layout = new JGraphHierarchicalLayout();
		layout.setFineTuning(true);
		layout.setCompactLayout(true);
		layout.setParallelEdgeSpacing(scale(5));
		layout.setInterHierarchySpacing(scale(20));
		layout.setIntraCellSpacing(scale(20));
		layout.setInterRankCellSpacing(scale(40));
		layout.run(facade);
		getGraphLayoutCache().edit(facade.createNestedMap(true, true));
	}

	public void highlightRetainedFacts() {
		builder.getDroolsAssert().getObjects().stream()
				.map(lastObjectCell::get)
				.filter(Objects::nonNull)
				.forEach(cell -> setBorderColor(cell.getAttributes(), red));
		getGraphLayoutCache().reload();
	}

	public void ruleTriggered(Match match) {
		RuleImpl rule = (RuleImpl) match.getRule();
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
		DefaultGraphCell ruleCell = cell(label(Rule, rule.getName(), ruleMeta.toString(), formatTime(builder.getClock()), flags), Rule);
		lastObjectCell.put(rule, ruleCell);

		synchronized (StateTransitionBuilder.class) {
			getGraphLayoutCache().insert(ruleCell);
			getGraphLayoutCache().setVisible(ruleCell, false);

			getRuleActivatedBy(match).stream()
					.map(this::getLastKnownObjectCell)
					.filter(Objects::nonNull)
					.forEach(objectCell -> getGraphLayoutCache().insert(edge(objectCell, ruleCell)));
			getRuleLogicialDependencies(match).stream()
					.flatMap(o -> lastObjectCell.entrySet().stream().filter(e -> e.getKey().equals(o)).map(e -> e.getValue()))
					.forEach(objectCell -> getGraphLayoutCache().insert(edge(ruleCell, objectCell)));
		}

	}

	public void objectUpdated(InternalFactHandle fh, RuleImpl rule, CellType cellType) {
		Object fact = fh.getObject();
		if (!lastObjectState.containsKey(fact))
			lastObjectState.putIfAbsent(fact, new AtomicInteger());
		AtomicInteger state = lastObjectState.get(fact);
		String flags = fh.isEvent() ? "E" : "";
		if (isJustified(fh))
			flags += "J";

		String stateId = format("#%s-%s", fh.getIdentityHashCode(), cellType == DeletedFact ? state : state.incrementAndGet());
		builder.writeToFile(fact, stateId);
		DefaultGraphCell cell = cell(label(cellType, getSimpleName(fact.getClass()), stateId, formatTime(builder.getClock()), flags), cellType);
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
			getGraphLayoutCache().insert(cell);
			getGraphLayoutCache().setVisible(cell, false);

			if (rule != null)
				getGraphLayoutCache().insert(edge(lastObjectCell.get(rule), cell));
			else if (previousStateCell != null)
				getGraphLayoutCache().insert(edge(previousStateCell, cell));
		}
	}

	private DefaultGraphCell cell(String label, CellType cellType) {
		DefaultGraphCell cell = new DefaultGraphCell(label);
		cell.add(new DefaultPort());
		cells.add(cell);

		GraphConstants.setBounds(cell.getAttributes(), new Rectangle2D.Double(0, 0, scale(100), scale(50)));
		GraphConstants.setBackground(cell.getAttributes(), decode(cellType.background));
		setBorderColor(cell.getAttributes(), decode(cellType.borderColor));
		setAutoSize(cell.getAttributes(), true);
		setLineWidth(cell.getAttributes(), scale(1));
		GraphConstants.setOpaque(cell.getAttributes(), true);
		return cell;
	}

	private DefaultEdge edge(DefaultGraphCell cell1, DefaultGraphCell cell2) {
		DefaultEdge edge = new DefaultEdge(adgeCounter.incrementAndGet());
		edge.setSource(cell1.getChildAt(0));
		edge.setTarget(cell2.getChildAt(0));

		setLineColor(edge.getAttributes(), darkGray);
		setLineEnd(edge.getAttributes(), ARROW_CLASSIC);
		setEndFill(edge.getAttributes(), true);
		setLineWidth(edge.getAttributes(), scale(1));
		GraphConstants.setFont(edge.getAttributes(), getFont().deriveFont(scale(10)));
		GraphConstants.setOpaque(edge.getAttributes(), true);
		return edge;
	}

	private String label(CellType cellType, String line1, String line2, String line3, String flags) {
		StringBuilder sb = new StringBuilder();
		sb.append("<html>");
		sb.append(format(US, "<table style='border: %.1fpx; border-spacing: 0; width:100%%'>", scale(2)));
		sb.append("<tr>");
		sb.append(format(US, "<td style='padding: %.1fpx; text-align: center; font-family:tahoma,serif; font-size:%.1fpx; font-weight: normal'>", scale(3), scale(9)));
		sb.append(escapeHtml4(line1));
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("<tr>");
		sb.append(format(US, "<td style='padding: 0; text-align: center; font-family:verdana; font-size:%.1fpx; font-weight: lighter'>", scale(7)));
		sb.append(escapeHtml4(line2));
		sb.append("</td>");
		sb.append("</tr>");
		sb.append("<tr>");
		sb.append("<td style='padding: 0;'>");

		sb.append(format(US, "<table style='border: 0; border-spacing: 0; margin-top: -%1$.1f; margin-bottom: -%1$.1f; width:100%%;'>", 3 * sqrt(scaling)));
		sb.append("<tr>");
		sb.append(format(US, "<td style='padding: 0; font-family:verdana; font-size:%.1fpx; font-weight: normal;'>", scale(7)));
		sb.append(format("<span style='color: %s'>", cellType.background));
		sb.append(flags);
		sb.append("</span>");
		sb.append("</td>");
		sb.append(format(US, "<td style='padding: 0; text-align: center; font-family:verdana; font-size:%.1fpx; font-weight: lighter; width: 100%%;'>", scale(7)));
		sb.append(line3);
		sb.append("</td>");
		sb.append(format(US, "<td style='padding: 0; font-family:verdana; font-size:%.1fpx; font-weight: normal; color: red'>", scale(7)));
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

	private DefaultGraphCell getLastKnownObjectCell(Object object) {
		return firstNonNull(lastObjectCell.get(object), lastRemovedCell.get(object));
	}

	public Set<DefaultGraphCell> getCells() {
		return cells;
	}
}