package org.droolsassert.ui;

import static java.awt.Color.blue;
import static javax.swing.BorderFactory.createEmptyBorder;
import static javax.swing.BorderFactory.createLineBorder;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JToolTip;

class Button extends JButton implements MouseListener {

	private Insets insets = new Insets(5, 5, 5, 5);
	private boolean mouseWithin;
	private Color background;
	private Color backgroundHover;
	private Color backgroundClicked;

	public Button(String text, Icon icon) {
		super(text, icon);
		setLayout(new GridBagLayout());
		setBorder(createEmptyBorder());
		setBorder(createLineBorder(blue));
		setBackground(null);
		setFocusPainted(false);
		setContentAreaFilled(false);
		addMouseListener(this);
	}

	@Override
	public JToolTip createToolTip() {
		return new Tooltip(this);
	}

	@Override
	protected void paintComponent(Graphics g) {
		if (getModel().isPressed() && mouseWithin) {
			g.setColor(backgroundClicked);
		} else if (getModel().isRollover()) {
			g.setColor(backgroundHover);
		} else {
			g.setColor(background);
		}
		g.fillRect(0, 0, getWidth(), getHeight());
		super.paintComponent(g);
	}

	@Override
	public void mouseClicked(MouseEvent e) {
	}

	@Override
	public void mousePressed(MouseEvent e) {
	}

	@Override
	public void mouseReleased(MouseEvent e) {
	}

	@Override
	public void mouseEntered(MouseEvent e) {
		mouseWithin = true;
	}

	@Override
	public void mouseExited(MouseEvent e) {
		mouseWithin = false;
	}

	public void setInsets(int top, int left, int bottom, int right) {
		insets = new Insets(top, left, bottom, right);
	}

	@Override
	public Insets getInsets() {
		return insets;
	}

	public void setBackgrounds(Color normal, Color hover, Color clicked) {
		this.background = normal;
		this.backgroundHover = hover;
		this.backgroundClicked = clicked;
	}
}