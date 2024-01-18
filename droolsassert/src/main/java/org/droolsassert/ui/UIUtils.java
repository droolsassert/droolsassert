package org.droolsassert.ui;

import static com.google.common.io.Resources.getResource;
import static java.awt.Toolkit.getDefaultToolkit;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.HeadlessException;
import java.io.IOException;
import java.util.function.Consumer;

class UIUtils {

	public static final Font segoiUi = initSegoiUI();
	public static final float scaling = initScaling();

	private static Font initSegoiUI() {
		try {
			return Font.createFont(Font.PLAIN, getResource("org/droolsassert/font/segoe-ui.ttf").openStream());
		} catch (IOException | FontFormatException e) {
			throw new IllegalStateException("Cannot create font", e);
		}
	}

	private static float initScaling() {
		try {
			return (float) (getDefaultToolkit().getScreenResolution() / 96.0);
		} catch (HeadlessException e) {
			return 1;
		}
	}

	public static float scale(float num) {
		return num * scaling;
	}

	public static Color darker(Color c, double factor) {
		return new Color((int) (c.getRed() * factor), (int) (c.getGreen() * factor), (int) (c.getBlue() * factor), c.getAlpha());
	}

	public static void withComponentTree(Component component, Consumer<Component> consumer) {
		consumer.accept(component);
		if (component instanceof Container) {
			for (Component child : ((Container) component).getComponents())
				withComponentTree(child, consumer);
		}
	}
}