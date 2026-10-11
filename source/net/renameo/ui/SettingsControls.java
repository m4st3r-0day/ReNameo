package net.renameo.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

import javax.swing.JComponent;

import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;

/**
 * Small controls of the Settings page.
 */
final class SettingsControls {

	private SettingsControls() {
		throw new UnsupportedOperationException();
	}

	/**
	 * iOS style on / off switch.
	 */
	static class Switch extends JComponent {

		private boolean on;

		Switch(boolean on, Consumer<Boolean> listener) {
			this.on = on;
			setPreferredSize(new Dimension(38, 22));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {

				@Override
				public void mouseClicked(MouseEvent e) {
					if (!isEnabled()) {
						return;
					}
					Switch.this.on = !Switch.this.on;
					repaint();
					listener.accept(Switch.this.on);
				}
			});
		}

		boolean isOn() {
			return on;
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = Modern.smooth(g);
			int h = getHeight();
			Color track = on ? NightTheme.getAccent() : Modern.mix(NightTheme.getBorder(), NightTheme.getDimForeground(), 0.45f);
			g2d.setColor(isEnabled() ? track : Modern.alpha(track, 90));
			g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), h, h, h));
			g2d.setColor(Color.WHITE);
			int d = h - 4;
			g2d.fill(new Ellipse2D.Float(on ? getWidth() - d - 2 : 2, 2, d, d));
			g2d.dispose();
		}
	}

	/**
	 * Round color swatch with a ring when selected.
	 */
	static class Swatch extends JComponent {

		private final Color color;
		private final boolean selected;

		Swatch(Color color, String name, boolean selected, Consumer<Color> listener) {
			this.color = color;
			this.selected = selected;
			setPreferredSize(new Dimension(28, 28));
			setToolTipText(name);
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {

				@Override
				public void mouseClicked(MouseEvent e) {
					listener.accept(color);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = Modern.smooth(g);
			if (selected) {
				g2d.setColor(NightTheme.getForeground());
				g2d.setStroke(new java.awt.BasicStroke(2f));
				g2d.draw(new Ellipse2D.Float(1, 1, 26, 26));
			}
			g2d.setColor(color);
			g2d.fill(new Ellipse2D.Float(6, 6, 16, 16));
			g2d.dispose();
		}
	}

	/**
	 * Thin line between groups.
	 */
	static class Divider extends JComponent {

		@Override
		protected void paintComponent(Graphics g) {
			g.setColor(NightTheme.getBorder());
			g.fillRect(0, 0, getWidth(), 1);
		}
	}

}
