package org.droolsassert.ui;

import static java.awt.Color.black;
import static java.awt.Color.lightGray;
import static java.awt.Color.white;
import static javax.swing.BorderFactory.createLineBorder;
import static org.droolsassert.ui.UIUtils.segoiUi;

import java.awt.Font;
import java.awt.Insets;
import javax.swing.JComponent;
import javax.swing.JToolTip;

class Tooltip extends JToolTip {
	public Tooltip(JComponent component) {
		setComponent(component);
		setBackground(white);
		setForeground(black);
		setBorder(createLineBorder(lightGray));
		Font font = segoiUi.deriveFont(14f);
		setFont(font);
	}

	@Override
	public Insets getInsets() {
		return new Insets(5, 5, 5, 5);
	}
}