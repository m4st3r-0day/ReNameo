package net.renameo.ui;

import static net.renameo.Logging.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.Scrollable;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicScrollPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import net.miginfocom.swing.MigLayout;
import net.renameo.Settings;
import net.renameo.UserFiles;
import net.renameo.plugins.Plugins;
import net.renameo.plugins.Plugins.Action;
import net.renameo.plugins.Plugins.Activity;
import net.renameo.plugins.Plugins.CatalogEntry;
import net.renameo.plugins.Plugins.Plugin;
import net.renameo.plugins.Plugins.Setting;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;

/**
 * The Plugins page: installed plugins with their settings and actions, the plugins that come with the app, and what the plugins write to the log.
 */
public class PluginsPanel extends JPanel {

	private final JPanel content = new ScrollablePanel(new MigLayout("insets 2 2 16 2, fillx, wrap 1", "[fill, grow]"));
	private final PluginLogView logView = new PluginLogView();

	// the cards of the installed plugins, updated while plugins work
	private final List<CardStatus> cards = new ArrayList<CardStatus>();
	private final Timer ticker = new Timer(50, e -> tick());
	private int running = 0;

	public PluginsPanel() {
		super(new MigLayout("insets 0 0 20 0, fill", "[fill, grow]", "[fill, grow]"));
		setOpaque(false);

		content.setOpaque(false);
		JScrollPane scroll = new JScrollPane(content);
		// the Nimbus scroll pane paints a light frame of its own, the basic one draws nothing
		Modern.onThemeChange(scroll, () -> {
			scroll.setUI(new BasicScrollPaneUI());
			scroll.setBorder(null);
			scroll.setOpaque(false);
			scroll.getViewport().setOpaque(false);
		});
		Modern.slim(scroll);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setPreferredSize(new Dimension(200, 200)); // the log keeps its height, the cards get the rest

		// drag the divider to give the log more room
		JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, scroll, logView);
		split.setResizeWeight(1);
		logView.setPreferredSize(new Dimension(200, 240));
		Modern.onThemeChange(split, () -> {
			split.setUI(new BasicSplitPaneUI() {

				@Override
				public BasicSplitPaneDivider createDefaultDivider() {
					return new BasicSplitPaneDivider(this) {

						@Override
						public void paint(Graphics g) {
							Graphics2D g2d = Modern.smooth(g);
							g2d.setColor(NightTheme.getBorder());
							g2d.fillRoundRect((getWidth() - 40) / 2, getHeight() / 2 - 2, 40, 4, 4, 4);
							g2d.dispose();
						}
					};
				}
			});
			split.setBorder(null);
			split.setOpaque(false);
			split.setDividerSize(14);
		});
		add(split, "wmin 0, hmin 0");

		JButton folder = Modern.button(newAction("Open folder", e -> openURI(Plugins.getFolder().toURI().toString())), Glyph.of(Glyph.Shape.FOLDER, 16, NightTheme::getForeground), Modern.Style.SECONDARY);
		folder.setToolTipText(Plugins.getFolder().getPath());
		JButton reload = Modern.button(newAction("Reload", e -> {
			Plugins.load();
			refresh();
			SwingEventBus.getInstance().post(new AppEvents.Toast("Plugins reloaded", Plugins.list().size() + " installed", AppEvents.Status.Kind.READY));
		}), Glyph.of(Glyph.Shape.UNDO, 16, NightTheme::getForeground), Modern.Style.SECONDARY);
		reload.setToolTipText("Read the plugins folder again after you changed a plugin");

		JPanel pageActions = new JPanel(new MigLayout("insets 0, gap 10"));
		pageActions.setOpaque(false);
		pageActions.add(folder);
		pageActions.add(reload);
		putClientProperty(MainFrame.PAGE_ACTIONS, pageActions);
		putClientProperty(Modern.CUSTOM, true);

		Modern.onThemeChange(this, this::refresh);
	}

	@Override
	public void addNotify() {
		super.addNotify();
		ticker.start();
	}

	@Override
	public void removeNotify() {
		ticker.stop();
		super.removeNotify();
	}

	/**
	 * Show what the plugins are doing: progress bar, elapsed time, last run.
	 */
	private void tick() {
		if (!isVisible()) {
			return; // another page is shown
		}
		int now = 0;
		for (CardStatus card : cards) {
			card.update();
			if (card.plugin.activity != null) {
				now++;
			}
		}
		if (now != running && isShowing()) {
			running = now;
			SwingEventBus.getInstance().post(now > 0 ? new AppEvents.Status(now == 1 ? "1 plugin working…" : now + " plugins working…", AppEvents.Status.Kind.BUSY) : new AppEvents.Status("Ready", AppEvents.Status.Kind.READY));
		}
	}

	private void refresh() {
		content.removeAll();
		cards.clear();
		logView.updatePlugins();

		List<Plugin> installed = Plugins.list();
		content.add(Modern.caption("Installed"), "gapleft 4, gapbottom 2");
		if (installed.isEmpty()) {
			content.add(Modern.label("<html>No plugins installed yet. Install one below, or put your own <b>.groovy</b> file into the plugins folder and press Reload.</html>", 12.5f, Font.PLAIN, true), "gapleft 4, wmin 0, gapbottom 8");
		}
		for (Plugin plugin : installed) {
			content.add(installedCard(plugin), "wmin 0, gapbottom 8");
		}

		List<CatalogEntry> available = Plugins.catalog().stream().filter(c -> !c.isInstalled()).collect(Collectors.toList());
		if (!available.isEmpty()) {
			content.add(Modern.caption("Available"), "gapleft 4, gaptop 14, gapbottom 2");
			for (CatalogEntry entry : available) {
				content.add(catalogCard(entry), "wmin 0, gapbottom 8");
			}
		}

		content.add(Modern.label("<html>Plugins are Groovy scripts: they can react to every rename (in the app, the watch folder, the command line and Docker), add values to formats like <b>{plugin.name}</b> and add actions to this page. They run with your rights, so only install plugins you trust.</html>", 11.5f, Font.PLAIN, true), "gapleft 4, gaptop 10, wmin 0");
		JButton guide = Modern.button(newAction("How to write plugins and scripts", e -> openURI(Settings.getEmbeddedHelpURL(true))), Glyph.of(Glyph.Shape.HELP, 15, NightTheme::getForeground), Modern.Style.GHOST);
		content.add(guide, "gapleft 0, growx 0, alignx left");

		content.revalidate();
		content.repaint();
	}

	private JComponent installedCard(Plugin plugin) {
		Modern.Card card = new Modern.Card(new MigLayout("insets 14 16 14 16, fillx, wrap 1, hidemode 3", "[fill, grow]"));

		JLabel name = Modern.label(plugin.name, 14f, Font.BOLD, false);
		name.setIcon(Glyph.of(plugin.error != null ? Glyph.Shape.WARNING : Glyph.Shape.SLIDERS, 17, plugin.error != null ? NightTheme::getDanger : plugin.enabled ? NightTheme::getAccent : NightTheme::getDimForeground));
		name.setIconTextGap(8);

		JCheckBox enabled = new JCheckBox("On", plugin.enabled);
		enabled.setOpaque(false);
		enabled.setForeground(NightTheme.getForeground());
		enabled.setFont(Modern.font(12.5f, Font.PLAIN));
		enabled.setToolTipText("Turn this plugin on or off");
		enabled.addActionListener(e -> {
			Plugins.setEnabled(plugin, enabled.isSelected());
			refresh();
		});

		JButton remove = Modern.iconButton(newAction("Remove", e -> {
			try {
				UserFiles.trash(plugin.file);
				Plugins.load();
				refresh();
				SwingEventBus.getInstance().post(new AppEvents.Toast("Plugin removed", plugin.name + " was moved to the trash", AppEvents.Status.Kind.READY));
			} catch (Exception ex) {
				log.log(Level.WARNING, "Failed to remove plugin: " + ex.getMessage(), ex);
			}
		}), Glyph.Shape.TRASH, "Remove (moves the file to the trash)");

		JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[grow, fill][][]"));
		header.setOpaque(false);
		header.add(name, "wmin 0");
		header.add(enabled);
		header.add(remove);
		card.add(header);

		String text = plugin.error != null ? plugin.error : plugin.description.isEmpty() ? plugin.file.getName() : plugin.description;
		JLabel detail = Modern.label("<html>" + escapeHTML(text) + "</html>", 12f, Font.PLAIN, true);
		if (plugin.error != null) {
			detail.setForeground(NightTheme.getDanger());
		}
		card.add(detail, "wmin 0");

		if (!plugin.settings.isEmpty()) {
			JPanel settings = new JPanel(new MigLayout("insets 0, wrap 2, gap 10 6", "[right][fill]"));
			settings.setOpaque(false);
			for (Setting setting : plugin.settings.values()) {
				JTextField field = Modern.field(setting.secret ? new JPasswordField(20) : new JTextField(20));
				field.setText(plugin.getSetting(setting.key));
				field.setToolTipText((!setting.secret && !setting.defaultValue.isEmpty() ? "Default: " + setting.defaultValue + " · " : "") + "Environment variable: " + plugin.getEnvironmentName(setting.key));
				// saved as soon as the field is left or Enter is pressed, the plugin reads it the next time it runs
				Runnable save = () -> {
					// a value that comes from the environment stays there
					if (!field.getText().trim().equals(plugin.getSetting(setting.key))) {
						plugin.setSetting(setting.key, field.getText());
					}
				};
				field.addActionListener(e -> save.run());
				field.addFocusListener(new FocusAdapter() {

					@Override
					public void focusLost(FocusEvent e) {
						save.run();
					}
				});
				settings.add(Modern.label(setting.label, 12f, Font.BOLD, true));
				settings.add(field, "h 30!, wmin 0, w 200:420:520");
			}
			card.add(settings, "gaptop 8, growx 0, alignx left");
		}

		CardStatus status = new CardStatus(plugin);
		if (!plugin.actions.isEmpty()) {
			JPanel actions = new JPanel(new MigLayout("insets 0, gap 8"));
			actions.setOpaque(false);
			for (Action action : plugin.actions) {
				JButton button = Modern.button(newAction(action.name, evt -> run(plugin, action, evt)), Glyph.of(Glyph.Shape.PLAY, 14, NightTheme::getForeground), Modern.Style.SECONDARY);
				button.setToolTipText(action.description.isEmpty() ? null : action.description);
				button.setEnabled(plugin.enabled && plugin.error == null);
				status.buttons.add(button);
				actions.add(button);
			}
			card.add(actions, "gaptop 8");
		}
		card.add(status.panel, "gaptop 10");
		card.add(status.lastRun, "gaptop 6, wmin 0");
		cards.add(status);
		status.update();

		return card;
	}

	private void run(Plugin plugin, Action action, ActionEvent evt) {
		File folder = UserFiles.showOpenDialogSelectFolder(null, action.name, evt);
		if (folder == null) {
			return;
		}
		if (!Plugins.startAction(plugin, action, folder)) {
			SwingEventBus.getInstance().post(new AppEvents.Toast(plugin.name + " is busy", "Wait until it is done, or press Stop", AppEvents.Status.Kind.WARNING));
		}
	}

	/**
	 * The running and last run parts of a plugin card.
	 */
	private static class CardStatus {

		final Plugin plugin;
		final List<JButton> buttons = new ArrayList<JButton>();

		final JPanel panel = new JPanel(new MigLayout("insets 0, fillx, wrap 3, gap 8 6", "[grow, fill][][]"));
		final JLabel task = Modern.label("", 12.5f, Font.BOLD, false);
		final JLabel elapsed = Modern.label("", 12f, Font.PLAIN, true);
		final ProgressBar bar = new ProgressBar();
		final JButton stop;
		final JLabel lastRun = Modern.label("", 11.5f, Font.PLAIN, true);

		private Activity shown;
		private String shownLastRun;

		CardStatus(Plugin plugin) {
			this.plugin = plugin;
			panel.setOpaque(false);
			stop = Modern.button(newAction("Stop", e -> {
				Activity a = plugin.activity;
				if (a != null) {
					a.cancel();
				}
			}), null, Modern.Style.GHOST);
			stop.setToolTipText("Ask the plugin to stop");
			panel.add(task, "wmin 0");
			panel.add(elapsed);
			panel.add(stop, "h 26!");
			panel.add(bar, "span 3, h 6!, growx");
			panel.setVisible(false);
			lastRun.setVisible(false);
		}

		void update() {
			Activity a = plugin.activity;
			if (a != shown) {
				shown = a;
				panel.setVisible(a != null);
				buttons.forEach(b -> b.setEnabled(a == null && plugin.enabled && plugin.error == null));
			}
			if (a != null) {
				StringBuilder text = new StringBuilder(a.task);
				if (a.total > 0) {
					text.append(" · ").append(Math.max(0, a.done)).append(" of ").append(a.total);
				}
				if (a.message != null && !a.message.isEmpty()) {
					text.append(" · ").append(a.message);
				}
				task.setText(a.cancelled ? a.task + " · stopping…" : text.toString());
				task.setToolTipText(text.toString());
				long seconds = (System.currentTimeMillis() - a.started) / 1000;
				elapsed.setText(String.format("%d:%02d", seconds / 60, seconds % 60));
				stop.setEnabled(!a.cancelled);
				bar.setFraction(a.total > 0 ? Math.min(1f, Math.max(0, a.done) / (float) a.total) : -1);
				bar.repaint();
			}

			String last = plugin.lastRun;
			if (!Objects.equals(last, shownLastRun)) {
				shownLastRun = last;
				lastRun.setVisible(last != null && a == null);
				lastRun.setText(last == null ? "" : "Last run: " + last);
				lastRun.setForeground(plugin.lastRunFailed ? NightTheme.getDanger() : NightTheme.getDimForeground());
			} else if (last != null && lastRun.isVisible() == (a != null)) {
				lastRun.setVisible(a == null);
			}
		}
	}

	/**
	 * Thin rounded bar: filled to the fraction, or a sliding segment while the plugin does not say how far it is.
	 */
	private static class ProgressBar extends JComponent {

		private float fraction = -1;

		void setFraction(float fraction) {
			this.fraction = fraction;
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = Modern.smooth(g);
			int w = getWidth(), h = getHeight();
			g2d.setColor(NightTheme.getBorder());
			g2d.fill(new RoundRectangle2D.Float(0, 0, w, h, h, h));
			g2d.setColor(NightTheme.getAccent());
			if (fraction >= 0) {
				g2d.fill(new RoundRectangle2D.Float(0, 0, Math.max(h, w * fraction), h, h, h));
			} else {
				float segment = w * 0.28f;
				float t = (System.currentTimeMillis() % 1400) / 1400f;
				g2d.clip(new RoundRectangle2D.Float(0, 0, w, h, h, h));
				g2d.fill(new RoundRectangle2D.Float(-segment + (w + segment) * t, 0, segment, h, h, h));
			}
			g2d.dispose();
		}
	}

	private JComponent catalogCard(CatalogEntry entry) {
		Modern.Card card = new Modern.Card(new MigLayout("insets 14 16 14 16, fillx", "[grow, fill][]"));

		JLabel name = Modern.label(entry.name, 14f, Font.BOLD, false);
		JLabel detail = Modern.label("<html>" + escapeHTML(entry.description) + "</html>", 12f, Font.PLAIN, true);

		JPanel text = new JPanel(new MigLayout("insets 0, fillx, wrap 1, gap 0 3", "[fill, grow]"));
		text.setOpaque(false);
		text.add(name);
		text.add(detail, "wmin 0");
		card.add(text, "wmin 0");

		card.add(Modern.button(newAction("Install", e -> {
			try {
				Plugins.install(entry);
				refresh();
				SwingEventBus.getInstance().post(new AppEvents.Toast("Plugin installed", entry.name, AppEvents.Status.Kind.READY));
			} catch (Exception ex) {
				log.log(Level.WARNING, "Failed to install plugin: " + ex.getMessage(), ex);
			}
		}), Glyph.of(Glyph.Shape.DOWNLOAD, 15, NightTheme::getForeground), Modern.Style.SECONDARY), "aligny center");

		return card;
	}

	/**
	 * Follows the width of the viewport, so the cards wrap their text instead of scrolling sideways.
	 */
	private static class ScrollablePanel extends JPanel implements Scrollable {

		ScrollablePanel(LayoutManager layout) {
			super(layout);
		}

		@Override
		public Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
			return visible.height - 32;
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return false;
		}
	}

}
