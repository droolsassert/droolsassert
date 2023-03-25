package org.droolsassert.ui;

import static com.google.common.io.Resources.getResource;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.util.function.Consumer;

class UIUtils {

	public static final Font segoiUi;

	static {
		try {
			segoiUi = Font.createFont(Font.PLAIN, getResource("org/droolsassert/font/segoe-ui.ttf").openStream());
		} catch (IOException | FontFormatException e) {
			throw new IllegalStateException("Cannot create font", e);
		}
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