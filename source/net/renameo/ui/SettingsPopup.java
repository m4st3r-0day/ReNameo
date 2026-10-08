package net.renameo.ui;

import static net.renameo.Settings.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Window;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.util.function.Consumer;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.border.LineBorder;

import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;
import net.miginfocom.swing.MigLayout;

/**
 * Settings popover: theme switches and the accent color, drawn with the app theme.
 */
class SettingsPopup extends JPopupMenu {

	private static final int WIDTH = 300;

	SettingsPopup() {
		setLayout(new MigLayout("insets 16 18 14 18, fillx, wrap 1", "[fill, grow]"));
		setBorder(new LineBorder(NightTheme.getBorder()));
		setOpaque(true);

		add(Modern.label("Settings", 15f, Font.BOLD, false), "gapbottom 10");

		add(Modern.caption("Appearance"), "gapbottom 4");
		add(row("Night mode", "Dark theme", NightTheme.isNightMode(), NightTheme::setNightMode));
		add(row("Compact rows", "Denser lists for large libraries", NightTheme.isCompact(), NightTheme::setCompact));

		add(Modern.caption("Accent color"), "gaptop 14, gapbottom 6");
		JPanel swatches = new JPanel(new MigLayout("insets 0, gap 10"));
		swatches.setOpaque(false);
		Color custom = NightTheme.getCustomAccent();
		for (int i = 0; i < NightTheme.ACCENT_PRESETS.length; i++) {
			Color color = NightTheme.ACCENT_PRESETS[i];
			boolean selected = custom == null ? i == 0 : color.equals(custom);
			swatches.add(new Swatch(color, NightTheme.ACCENT_PRESET_NAMES[i], selected, c -> apply(() -> NightTheme.setAccent(c))));
		}
		add(swatches);

		add(Modern.caption("Tools"), "gaptop 14, gapbottom 4");
		add(tool("API keys", Glyph.Shape.LINK, owner -> ApiKeysDialog.show(owner, false)));
		add(tool("Watch folder", Glyph.Shape.FOLDER, WatchFolderDialog::show));
		add(tool("Update offline index", Glyph.Shape.DOWNLOAD, OfflineIndexAction::run));
		add(tool("Plugins", Glyph.Shape.SLIDERS, owner -> SwingEventBus.getInstance().post(new PluginsPanelBuilder())));

		JPanel footer = new JPanel(new MigLayout("insets 0, fillx", "[]push[]"));
		footer.setOpaque(false);
		JLabel guide = Modern.label("User guide", 12.5f, Font.PLAIN, false);
		guide.setForeground(NightTheme.getAccent());
		guide.setIcon(Glyph.of(Glyph.Shape.HELP, 14, NightTheme::getAccent));
		guide.setIconTextGap(6);
		guide.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		guide.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				setVisible(false);
				openURI(getEmbeddedHelpURL());
			}
		});
		footer.add(guide);
		footer.add(Modern.label(getApplicationName() + " " + getApplicationVersion(), 11.5f, Font.PLAIN, true));
		add(new Divider(), "gaptop 16, h 1!");
		add(footer, "gaptop 8");
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(WIDTH, super.getPreferredSize().height);
	}

	@Override
	protected void paintComponent(Graphics g) {
		g.setColor(NightTheme.getCardHover());
		g.fillRect(0, 0, getWidth(), getHeight());
	}

	/**
	 * Theme changes rebuild every window, so close the popover first and apply afterwards.
	 */
	private void apply(Runnable change) {
		setVisible(false);
		SwingUtilities.invokeLater(change);
	}

	private JComponent tool(String title, Glyph.Shape icon, Consumer<Window> action) {
		JLabel link = Modern.label(title, 13f, Font.PLAIN, false);
		link.setIcon(Glyph.of(icon, 15, NightTheme::getAccent));
		link.setIconTextGap(8);
		link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		link.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				Window owner = SwingUtilities.getWindowAncestor(link);
				setVisible(false);
				SwingUtilities.invokeLater(() -> action.accept(owner));
			}
		});
		return link;
	}

	private JComponent row(String title, String description, boolean value, Consumer<Boolean> setter) {
		JPanel row = new JPanel(new MigLayout("insets 6 0 6 0, fillx", "[grow][]", "[]0[]"));
		row.setOpaque(false);
		row.add(Modern.label(title, 13f, Font.PLAIN, false), "cell 0 0");
		row.add(Modern.label(description, 11.5f, Font.PLAIN, true), "cell 0 1");
		row.add(new Switch(value, on -> apply(() -> setter.accept(on))), "cell 1 0 1 2, aligny center");
		return row;
	}

	private static class Divider extends JComponent {

		@Override
		protected void paintComponent(Graphics g) {
			g.setColor(NightTheme.getBorder());
			g.fillRect(0, 0, getWidth(), 1);
		}
	}

	/**
	 * iOS style on / off switch.
	 */
	private static class Switch extends JComponent {

		private boolean on;

		Switch(boolean on, Consumer<Boolean> listener) {
			this.on = on;
			setPreferredSize(new Dimension(38, 22));
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter() {

				@Override
				public void mouseClicked(MouseEvent e) {
					Switch.this.on = !Switch.this.on;
					repaint();
					listener.accept(Switch.this.on);
				}
			});
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = Modern.smooth(g);
			int h = getHeight();
			g2d.setColor(on ? NightTheme.getAccent() : Modern.mix(NightTheme.getBorder(), NightTheme.getDimForeground(), 0.45f));
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
	private static class Swatch extends JComponent {

		private final Color color;
		private final boolean selected;

		Swatch(Color color, String name, boolean selected, Consumer<Color> listener) {
			this.color = color;
			this.selected = selected;
			setPreferredSize(new Dimension(24, 24));
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
				g2d.draw(new Ellipse2D.Float(1, 1, 22, 22));
			}
			g2d.setColor(color);
			g2d.fill(new Ellipse2D.Float(5, 5, 14, 14));
			g2d.dispose();
		}
	}

}
