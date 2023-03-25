package org.droolsassert.ui;

import static java.awt.Color.white;
import static java.awt.Font.PLAIN;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static java.awt.GridBagConstraints.LINE_END;
import static java.awt.GridBagConstraints.LINE_START;
import static java.awt.GridBagConstraints.NORTH;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.regex.Pattern.quote;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createLineBorder;
import static org.apache.commons.lang3.StringUtils.SPACE;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;
import static org.droolsassert.ui.UIUtils.segoiUi;
import static org.droolsassert.ui.UIUtils.withComponentTree;

import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Matcher;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JToolTip;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.droolsassert.listeners.StateTransitionBuilder;
import org.droolsassert.util.PatternProcessor;
import org.jgraph.graph.DefaultGraphCell;

public class LeftPanel extends JPanel {

	private final StateTransitionBuilder builder;
	private final SearchProcessor searchProcessor;

	public LeftPanel(StateTransitionBuilder builder) {
		super(new GridBagLayout());
		this.builder = builder;
		this.searchProcessor = new SearchProcessor(builder.getGraph());
		setBackground(white);

		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(0, 0, 0, 0);
		c.anchor = NORTH;
		c.gridx = 0;
		c.gridy = 0;
		add(statsPanel(), c);

		c.anchor = NORTH;
		c.gridx = 0;
		c.gridy = 1;
		c.weighty = 1;
		c.fill = HORIZONTAL;
		add(searchField(), c);

		Font font = segoiUi.deriveFont(PLAIN, 15f);
		withComponentTree(this, p -> p.setFont(font));
	}

	private JPanel statsPanel() {
		JPanel pane = new JPanel(new GridBagLayout());
		pane.setBackground(white);
		pane.setBorder(createLineBorder(white, 5));
		GridBagConstraints c = new GridBagConstraints();
		c.anchor = LINE_END;
		c.gridx = 0;
		c.gridy = 0;
		pane.add(new JLabel("activated"), c);

		c.anchor = LINE_START;
		c.gridx = 1;
		c.gridy = 0;
		pane.add(new JLabel(SPACE + builder.getActivatedCounter().get()), c);

		c.anchor = LINE_END;
		c.gridx = 0;
		c.gridy = 1;
		pane.add(new JLabel("inserted"), c);

		c.anchor = LINE_START;
		c.gridx = 1;
		c.gridy = 1;
		pane.add(new JLabel(SPACE + builder.getInsertedCounter().get()), c);

		c.anchor = LINE_END;
		c.gridx = 0;
		c.gridy = 2;
		pane.add(new JLabel("updated"), c);

		c.anchor = LINE_START;
		c.gridx = 1;
		c.gridy = 2;
		pane.add(new JLabel(SPACE + builder.getUpdatedCounter().get()), c);

		c.anchor = LINE_END;
		c.gridx = 0;
		c.gridy = 3;
		pane.add(new JLabel("deleted"), c);

		c.anchor = LINE_START;
		c.gridx = 1;
		c.gridy = 3;
		pane.add(new JLabel(SPACE + builder.getDeletedCounter().get()), c);

		c.anchor = LINE_END;
		c.gridx = 0;
		c.gridy = 4;
		pane.add(new JLabel("retained"), c);

		c.anchor = LINE_START;
		c.gridx = 1;
		c.gridy = 4;
		pane.add(new JLabel(SPACE + builder.getDroolsAssert().getObjects().size()), c);

		return pane;
	}

	private JTextField searchField() {
		JTextField jtf = new JTextField(8) {
			@Override
			public JToolTip createToolTip() {
				return new Tooltip(this);
			}
		};
		jtf.setBorder(createEmptyBorder());
		jtf.setToolTipText("Search");
		jtf.setHorizontalAlignment(JTextField.CENTER);
		jtf.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void changedUpdate(DocumentEvent e) {
				LeftPanel.this.onSearch(e);
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				LeftPanel.this.onSearch(e);
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				LeftPanel.this.onSearch(e);
			}
		});
		return jtf;
	}

	private void onSearch(DocumentEvent event) {
		try {
			Document document = event.getDocument();
			searchProcessor.search(document.getText(0, document.getLength()));
		} catch (BadLocationException e) {
			throw new RuntimeException(e);
		}
	}

	private static class SearchProcessor implements Runnable {
		private static String tableDataRegex = "(?s)(>)([^<>]*?)(</td)";
		private static String highlightSpan = "<span style=\"background-color: yellow\">";
		private static String highlightedRegex = highlightSpan + "(.*?)</span>";
		private static ExecutorService searchProcessor = newFixedThreadPool(1, new BasicThreadFactory.Builder().namingPattern("searchProcessor").daemon(true).build());
		private LinkedBlockingQueue<String> searchStrings = new LinkedBlockingQueue<>();
		private StateTransitionGraph stateTransitionGraph;

		public SearchProcessor(StateTransitionGraph stateTransitionGraph) {
			this.stateTransitionGraph = stateTransitionGraph;
			searchProcessor.submit(this);
		}

		public void search(String searchString) {
			searchStrings.clear();
			searchStrings.offer(searchString);
		}

		@Override
		public void run() {
			while (true) {
				try {
					String searchString = escapeHtml4(searchStrings.take());
					PatternProcessor dataProcessor = new PatternProcessor(tableDataRegex) {
						@Override
						protected String resolve(Matcher matcher) {
							return matcher.group(1) + matcher.group(2).replaceAll("(?s)" + quote(searchString), highlightSpan + searchString + "</span>") + matcher.group(3);
						}
					};
					for (DefaultGraphCell cell : stateTransitionGraph.getCells()) {
						String html = (String) cell.getUserObject();
						cell.setUserObject(html.replaceAll(highlightedRegex, "$1"));
						if (isEmpty(searchString))
							continue;
						html = (String) cell.getUserObject();
						cell.setUserObject(dataProcessor.process(html));
					}
					stateTransitionGraph.refresh();
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}
}