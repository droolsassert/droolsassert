package org.droolsassert.ui;

import static java.awt.BorderLayout.CENTER;
import static java.awt.BorderLayout.WEST;
import static java.awt.Color.white;
import static java.awt.GridBagConstraints.BOTH;
import static java.awt.Toolkit.getDefaultToolkit;
import static java.awt.event.KeyEvent.VK_ESCAPE;
import static java.lang.Thread.currentThread;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.JComponent.WHEN_IN_FOCUSED_WINDOW;
import static javax.swing.KeyStroke.getKeyStroke;
import static javax.swing.SwingUtilities.invokeLater;
import static org.droolsassert.ui.UIUtils.scale;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.concurrent.CountDownLatch;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.droolsassert.listeners.StateTransitionBuilder;

public class AppFrame extends JFrame {

	private StateTransitionBuilder builder;
	private volatile Thread eventDispatchThread;

	public AppFrame(StateTransitionBuilder builder, String title) {
		super(title);
		this.builder = builder;

		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		getRootPane().registerKeyboardAction(e -> dispose(), getKeyStroke(VK_ESCAPE, 0), WHEN_IN_FOCUSED_WINDOW);

		Container contentPane = getContentPane();
		contentPane.add(new LeftPanel(builder), WEST);
		contentPane.add(graphPanel(), CENTER);
	}

	public void showDialog() {
		setMinimumSize(new Dimension((int) scale(800), (int) scale(600)));
		setMaximumSize(getDefaultToolkit().getScreenSize());
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		awaitForClose();
	}

	private JScrollPane graphPanel() {
		JPanel pane = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.fill = BOTH;
		c.weightx = 1;
		c.weighty = 1;
		c.insets = new Insets((int) scale(5), (int) scale(5), (int) scale(5), (int) scale(5));
		pane.add(builder.getGraph(), c);
		pane.setBackground(white);

		JScrollPane sp = new JScrollPane(pane);
		sp.setBorder(createEmptyBorder());
		sp.getVerticalScrollBar().setUnitIncrement(10);
		sp.getHorizontalScrollBar().setUnitIncrement(10);
		return sp;
	}

	private void awaitForClose() {
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
}