package net.renameo.ui;

import static net.renameo.Settings.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

import net.renameo.util.ui.NightTheme;

public class ReNameoMenuBar {

	public static JMenuBar createMenuBar() {
		JMenuBar menuBar = new JMenuBar();
		menuBar.add(createViewMenu());
		menuBar.add(createHelp());
		return menuBar;
	}

	public static JMenu createViewMenu() {
		JMenu view = new JMenu("View");

		JCheckBoxMenuItem nightMode = new JCheckBoxMenuItem("Night Mode", NightTheme.isNightMode());
		nightMode.setToolTipText("Toggle dark theme");
		nightMode.addActionListener(evt -> NightTheme.setNightMode(nightMode.isSelected()));
		view.add(nightMode);

		JCheckBoxMenuItem compact = new JCheckBoxMenuItem("Compact Rows", NightTheme.isCompact());
		compact.setToolTipText("Denser list rows to fit more results on screen");
		compact.addActionListener(evt -> NightTheme.setCompact(compact.isSelected()));
		view.add(compact);

		JMenu accent = new JMenu("Accent Color");
		accent.setToolTipText("Highlight color used for buttons, links and selected rows");

		for (int i = 0; i < NightTheme.ACCENT_PRESETS.length; i++) {
			final Color color = NightTheme.ACCENT_PRESETS[i];
			JCheckBoxMenuItem preset = new JCheckBoxMenuItem(NightTheme.ACCENT_PRESET_NAMES[i]);
			preset.setIcon(new ColorDotIcon(color));
			preset.setSelected(color.equals(NightTheme.getCustomAccent()));
			preset.addActionListener(evt -> NightTheme.setAccent(color));
			accent.add(preset);
		}

		Action reset = newAction("Reset to Default", null, evt -> NightTheme.setAccent(null));
		accent.addSeparator();
		accent.add(reset);
		view.add(accent);

		return view;
	}

	/**
	 * Small circular color preview for the accent presets.
	 */
	private static class ColorDotIcon implements Icon {

		private final Color color;
		private final int size = 14;

		ColorDotIcon(Color color) {
			this.color = color;
		}

		@Override
		public void paintIcon(Component c, Graphics g, int x, int y) {
			Graphics2D g2d = (Graphics2D) g.create();
			g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2d.setColor(color);
			g2d.fillOval(x, y, size, size);
			g2d.setColor(NightTheme.getBorder());
			g2d.drawOval(x, y, size - 1, size - 1);
			g2d.dispose();
		}

		@Override
		public int getIconWidth() {
			return size;
		}

		@Override
		public int getIconHeight() {
			return size;
		}

	}

	public static JMenuBar createHelp() {
		JMenu help = new JMenu("Help");
		help.add(newAction("User Guide", null, evt -> GettingStartedStage.start()));

		JMenuBar menuBar = new JMenuBar();
		menuBar.add(help);
		return menuBar;
	}

}