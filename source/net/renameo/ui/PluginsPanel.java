package net.renameo.ui;

import static net.renameo.Logging.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollPaneUI;

import net.miginfocom.swing.MigLayout;
import net.renameo.Settings;
import net.renameo.UserFiles;
import net.renameo.plugins.Plugins;
import net.renameo.plugins.Plugins.Action;
import net.renameo.plugins.Plugins.Activity;
import net.renameo.plugins.Plugins.CatalogEntry;
import net.renameo.plugins.Plugins.LogEntry;
import net.renameo.plugins.Plugins.Plugin;
import net.renameo.plugins.Plugins.Setting;
import net.renameo.ui.SettingsControls.Switch;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.ScrollablePanel;
import net.renameo.util.ui.SwingEventBus;
import net.renameo.util.ui.ThinProgressBar;

/**
 * The Plugins page: installed plugins (list and details), the catalog of plugins that come with the app, and the plugin log.
 */
public class PluginsPanel extends JPanel {

	enum Tab {
		INSTALLED("Installed"), CATALOG("Catalog"), LOG("Log");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	private Tab tab = Tab.INSTALLED;

	private final JPanel tabs = new JPanel(new MigLayout("insets 0, gap 6"));
	private final Modern.SearchField search = new Modern.SearchField("Search plugins", null);
	private final CardLayout cards = new CardLayout();
	private final JPanel stack = new JPanel(cards);

	private final DefaultListModel<Plugin> listModel = new DefaultListModel<Plugin>();
	private final JList<Plugin> list = new JList<Plugin>(listModel);
	private final JPanel detail = new ScrollablePanel(new MigLayout("insets 18 20 18 20, fillx, wrap 1, hidemode 3", "[fill, grow]"));
	private final JPanel catalog = new ScrollablePanel(new MigLayout("insets 2 2 16 2, fillx, wrap 2, gap 10 10", "[fill, grow, sg][fill, grow, sg]"));
	private final PluginLogView logView = new PluginLogView();

	private CardStatus status;
	private Plugin shown;
	private final JPanel activity = new JPanel(new MigLayout("insets 0, fillx, wrap 1, gap 0 4", "[fill, grow]"));
	private int logSize = -1;
	private final Timer ticker = new Timer(60, e -> tick());
	private int running = 0;

	public PluginsPanel() {
		super(new MigLayout("insets 0 0 20 0, fill, wrap 1", "[fill, grow]", "[][fill, grow]"));
		setOpaque(false);

		JPanel top = new JPanel(new MigLayout("insets 0, fillx", "[]push[260!]"));
		top.setOpaque(false);
		tabs.setOpaque(false);
		top.add(tabs);
		top.add(search, "h 30!");
		add(top, "gapbottom 6");

		search.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				refresh();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				refresh();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				refresh();
			}
		});

		// installed: list on the left, the selected plugin on the right
		list.setCellRenderer(new PluginRenderer());
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFixedCellHeight(58);
		list.setOpaque(false);
		list.addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				showDetail(list.getSelectedValue());
			}
		});
		Modern.Card listCard = new Modern.Card(new MigLayout("insets 6, fill", "[fill, grow]", "[fill, grow]"));
		listCard.add(scroll(list), "grow");

		detail.setOpaque(false);
		Modern.Card detailCard = new Modern.Card(new MigLayout("insets 0, fill", "[fill, grow]", "[fill, grow]"));
		detailCard.add(scroll(detail), "grow");

		JPanel installed = new JPanel(new MigLayout("insets 0, fill, gap 12", "[300!][fill, grow]", "[fill, grow]"));
		installed.setOpaque(false);
		installed.add(listCard, "grow");
		installed.add(detailCard, "grow, wmin 0");

		catalog.setOpaque(false);
		JPanel log = new JPanel(new MigLayout("insets 0, fill", "[fill, grow]", "[fill, grow]"));
		log.setOpaque(false);
		log.add(logView, "grow");

		stack.setOpaque(false);
		stack.add(installed, Tab.INSTALLED.name());
		stack.add(scroll(catalog), Tab.CATALOG.name());
		stack.add(log, Tab.LOG.name());
		add(stack, "grow, hmin 0");

		JButton folder = Modern.button(newAction("Open folder", e -> openURI(Plugins.getFolder().toURI().toString())), Glyph.of(Glyph.Shape.FOLDER, 16, NightTheme::getForeground), Modern.Style.SECONDARY);
		folder.setToolTipText(Plugins.getFolder().getPath());
		JButton reload = Modern.button(newAction("Reload", e -> {
			Plugins.load();
			refresh();
			SwingEventBus.getInstance().post(new AppEvents.Toast("Plugins reloaded", Plugins.list().size() + " installed", AppEvents.Status.Kind.READY));
		}), Glyph.of(Glyph.Shape.UNDO, 16, NightTheme::getForeground), Modern.Style.SECONDARY);
		reload.setToolTipText("Read the plugins folder again after you changed a plugin");
		JButton guide = Modern.button(newAction("Write your own", e -> openURI(Settings.getEmbeddedHelpURL(true))), Glyph.of(Glyph.Shape.HELP, 16, NightTheme::getForeground), Modern.Style.SECONDARY);
		guide.setToolTipText("How to write plugins and scripts");

		JPanel pageActions = new JPanel(new MigLayout("insets 0, gap 10"));
		pageActions.setOpaque(false);
		pageActions.add(guide);
		pageActions.add(folder);
		pageActions.add(reload);
		putClientProperty(MainFrame.PAGE_ACTIONS, pageActions);
		putClientProperty(Modern.CUSTOM, true);

		Modern.onThemeChange(this, this::refresh);
	}

	private static JScrollPane scroll(JComponent view) {
		JScrollPane scroll = new JScrollPane(view);
		Modern.onThemeChange(scroll, () -> {
			scroll.setUI(new BasicScrollPaneUI());
			scroll.setBorder(null);
			scroll.setOpaque(false);
			scroll.getViewport().setOpaque(false);
		});
		Modern.slim(scroll);
		scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.getVerticalScrollBar().setUnitIncrement(16);
		return scroll;
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

	private void tick() {
		if (!isVisible()) {
			return;
		}
		if (status != null) {
			status.update();
		}
		// new log lines: refresh the activity of the plugin on the right
		int size = Plugins.getLog().size();
		if (size != logSize && shown != null) {
			logSize = size;
			fillActivity(shown);
		}
		int now = (int) Plugins.list().stream().filter(p -> p.activity != null).count();
		if (now > 0 || now != running) {
			list.repaint(); // working dots
		}
		if (now != running && isShowing()) {
			running = now;
			SwingEventBus.getInstance().post(now > 0 ? new AppEvents.Status(now == 1 ? "1 plugin working…" : now + " plugins working…", AppEvents.Status.Kind.BUSY) : new AppEvents.Status("Ready", AppEvents.Status.Kind.READY));
		}
	}

	private boolean matches(String... texts) {
		String q = search.getText().trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) {
			return true;
		}
		for (String t : texts) {
			if (t != null && t.toLowerCase(Locale.ROOT).contains(q)) {
				return true;
			}
		}
		return false;
	}

	private void refresh() {
		List<Plugin> installed = Plugins.list();
		List<CatalogEntry> entries = Plugins.catalog();
		long updates = entries.stream().filter(CatalogEntry::hasUpdate).count();

		tabs.removeAll();
		for (Tab t : Tab.values()) {
			String label = t == Tab.INSTALLED ? t.label + " (" + installed.size() + ")" : t == Tab.CATALOG ? t.label + (updates > 0 ? " · " + updates + (updates == 1 ? " update" : " updates") : " (" + entries.size() + ")") : t.label;
			tabs.add(Modern.button(newAction(label, e -> {
				tab = t;
				refresh();
			}), null, t == tab ? Modern.Style.SECONDARY : Modern.Style.GHOST), "h 30!");
		}
		search.setVisible(tab != Tab.LOG);
		cards.show(stack, tab.name());

		// installed
		Plugin selected = list.getSelectedValue();
		String selectedName = selected != null ? selected.name : null;
		listModel.clear();
		for (Plugin p : installed) {
			if (matches(p.name, p.description, p.category)) {
				listModel.addElement(p);
			}
		}
		int index = 0;
		for (int i = 0; i < listModel.size(); i++) {
			if (listModel.get(i).name.equals(selectedName)) {
				index = i;
			}
		}
		if (listModel.isEmpty()) {
			showDetail(null);
		} else {
			list.setSelectedIndex(index);
			showDetail(list.getSelectedValue());
		}

		// catalog, by category
		catalog.removeAll();
		Map<String, List<CatalogEntry>> byCategory = new LinkedHashMap<String, List<CatalogEntry>>();
		for (CatalogEntry e : entries) {
			if (matches(e.name, e.description, e.category)) {
				byCategory.computeIfAbsent(e.category, k -> new ArrayList<CatalogEntry>()).add(e);
			}
		}
		byCategory.forEach((category, list) -> {
			catalog.add(Modern.caption(category), "newline, span 2, gapleft 4, gaptop 10");
			list.forEach(e -> catalog.add(catalogCard(e), "wmin 0, growy"));
		});
		if (byCategory.isEmpty()) {
			catalog.add(Modern.label("No plugin matches the search.", 12.5f, Font.PLAIN, true), "span 2, gapleft 4");
		}

		logView.updatePlugins();
		revalidate();
		repaint();
	}

	private void showDetail(Plugin plugin) {
		detail.removeAll();
		status = null;
		shown = plugin;

		if (plugin == null) {
			detail.add(Modern.label(Plugins.list().isEmpty() ? "No plugins installed yet." : "No plugin matches the search.", 15f, Font.BOLD, false), "gaptop 20");
			detail.add(Modern.label("<html>Install ready-made plugins from the <b>Catalog</b>, or put your own <b>.groovy</b> file into the plugins folder and press <b>Reload</b>.</html>", 12.5f, Font.PLAIN, true), "wmin 0");
			JButton browse = Modern.button(newAction("Open the catalog", e -> {
				tab = Tab.CATALOG;
				refresh();
			}), null, Modern.Style.PRIMARY);
			detail.add(browse, "gaptop 12, growx 0");
			detail.revalidate();
			detail.repaint();
			return;
		}

		// name, on / off, remove
		JLabel name = Modern.label(plugin.name, 18f, Font.BOLD, false);
		Switch enabled = new Switch(plugin.enabled, on -> {
			Plugins.setEnabled(plugin, on);
			list.repaint();
			showDetail(plugin);
		});
		enabled.setToolTipText("Turn this plugin on or off");
		JButton remove = Modern.iconButton(newAction("Remove", e -> remove(plugin)), Glyph.Shape.TRASH, "Remove (moves the file to the trash)");
		JPanel header = new JPanel(new MigLayout("insets 0, fillx", "[grow, fill][][]"));
		header.setOpaque(false);
		header.add(name, "wmin 0");
		header.add(enabled);
		header.add(remove, "gapleft 8");
		detail.add(header);

		detail.add(Modern.label(plugin.category, 11.5f, Font.BOLD, true));
		String text = plugin.error != null ? plugin.error : plugin.description.isEmpty() ? plugin.file.getName() : plugin.description;
		JLabel description = Modern.label("<html>" + escapeHTML(text) + "</html>", 13f, Font.PLAIN, plugin.error == null);
		if (plugin.error != null) {
			description.setForeground(NightTheme.getDanger());
		}
		detail.add(description, "wmin 0, gaptop 4");

		if (!plugin.settings.isEmpty()) {
			detail.add(Modern.caption("Settings"), "gaptop 18, gapbottom 2");
			JPanel settings = new JPanel(new MigLayout("insets 0, wrap 2, gap 12 8", "[right][fill]"));
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
				settings.add(Modern.label(setting.label, 12.5f, Font.BOLD, true));
				settings.add(field, "h 32!, wmin 0, w 200:420:520");
			}
			detail.add(settings, "growx 0, alignx left");
		}

		CardStatus status = new CardStatus(plugin);
		if (!plugin.actions.isEmpty()) {
			detail.add(Modern.caption("Actions"), "gaptop 18, gapbottom 2");
			JPanel actions = new JPanel(new MigLayout("insets 0, gap 8, wrap 3"));
			actions.setOpaque(false);
			for (Action action : plugin.actions) {
				JButton button = Modern.button(newAction(action.name, evt -> run(plugin, action, evt)), Glyph.of(Glyph.Shape.PLAY, 14, NightTheme::getForeground), Modern.Style.SECONDARY);
				button.setToolTipText(action.description.isEmpty() ? null : action.description);
				button.setEnabled(plugin.enabled && plugin.error == null);
				status.buttons.add(button);
				actions.add(button);
			}
			detail.add(actions);
		}
		detail.add(status.panel, "gaptop 10");
		detail.add(status.lastRun, "gaptop 6, wmin 0");
		this.status = status;
		status.update();

		// what this plugin did lately
		detail.add(Modern.caption("Activity"), "gaptop 18, gapbottom 2");
		activity.setOpaque(false);
		detail.add(activity);
		fillActivity(plugin);
		JButton all = Modern.button(newAction("Show the whole log", e -> {
			tab = Tab.LOG;
			refresh();
		}), null, Modern.Style.GHOST);
		detail.add(all, "growx 0, gaptop 4");

		detail.revalidate();
		detail.repaint();
	}

	private void fillActivity(Plugin plugin) {
		activity.removeAll();
		List<LogEntry> recent = Plugins.getLog().stream().filter(e -> e.plugin.equals(plugin.name)).collect(Collectors.toList());
		if (recent.isEmpty()) {
			activity.add(Modern.label("Nothing yet.", 12.5f, Font.PLAIN, true));
		}
		SimpleDateFormat time = new SimpleDateFormat("dd/MM HH:mm");
		for (LogEntry e : recent.subList(Math.max(0, recent.size() - 12), recent.size())) {
			String mark = e.kind == LogEntry.Kind.START ? "▶" : e.kind == LogEntry.Kind.DONE ? "✓" : e.kind == LogEntry.Kind.ERROR ? "✕" : e.kind == LogEntry.Kind.STOPPED ? "■" : "·";
			JLabel line = Modern.label(time.format(new Date(e.time)) + "   " + mark + " " + e.message, 12f, Font.PLAIN, e.kind == LogEntry.Kind.INFO);
			if (e.kind == LogEntry.Kind.ERROR) {
				line.setForeground(NightTheme.getDanger());
			} else if (e.kind == LogEntry.Kind.DONE) {
				line.setForeground(NightTheme.getSuccess());
			}
			line.setToolTipText(e.message);
			activity.add(line, "wmin 0");
		}
		activity.revalidate();
		activity.repaint();
	}

	private void remove(Plugin plugin) {
		try {
			UserFiles.trash(plugin.file);
			Plugins.load();
			refresh();
			SwingEventBus.getInstance().post(new AppEvents.Toast("Plugin removed", plugin.name + " was moved to the trash", AppEvents.Status.Kind.READY));
		} catch (Exception ex) {
			log.log(Level.WARNING, "Failed to remove plugin: " + ex.getMessage(), ex);
		}
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

	private JComponent catalogCard(CatalogEntry entry) {
		Modern.Card card = new Modern.Card(new MigLayout("insets 14 16 14 16, fillx, wrap 1", "[grow, fill]"));
		card.add(Modern.label(entry.name, 14f, Font.BOLD, false));
		card.add(Modern.label("<html>" + escapeHTML(entry.description) + "</html>", 12f, Font.PLAIN, true), "wmin 0, growy, aligny top");

		JButton button;
		if (!entry.isInstalled()) {
			button = Modern.button(newAction("Install", e -> {
				try {
					Plugins.install(entry);
					refresh();
					SwingEventBus.getInstance().post(new AppEvents.Toast("Plugin installed", entry.name, AppEvents.Status.Kind.READY));
				} catch (Exception ex) {
					log.log(Level.WARNING, "Failed to install plugin: " + ex.getMessage(), ex);
				}
			}), Glyph.of(Glyph.Shape.DOWNLOAD, 15, NightTheme::getForeground), Modern.Style.SECONDARY);
		} else if (entry.hasUpdate()) {
			button = Modern.button(newAction("Update", e -> {
				try {
					Plugins.update(entry);
					refresh();
					SwingEventBus.getInstance().post(new AppEvents.Toast("Plugin updated", entry.name + " · its settings are kept", AppEvents.Status.Kind.READY));
				} catch (Exception ex) {
					log.log(Level.WARNING, "Failed to update plugin: " + ex.getMessage(), ex);
				}
			}), Glyph.of(Glyph.Shape.ARROW_DOWN, 15, NightTheme::getForeground), Modern.Style.PRIMARY);
			button.setToolTipText("Replace your copy with the one that comes with this version of ReNameo (changes you made to the file are lost, the settings are kept)");
		} else {
			button = Modern.button(newAction("Installed", e -> {
				tab = Tab.INSTALLED;
				search.setText("");
				refresh();
				for (int i = 0; i < listModel.size(); i++) {
					if (listModel.get(i).name.equals(entry.name)) {
						list.setSelectedIndex(i);
					}
				}
			}), Glyph.of(Glyph.Shape.CHECK_CIRCLE, 15, NightTheme::getSuccess), Modern.Style.GHOST);
		}
		card.add(button, "growx 0, alignx left, gaptop 6");
		return card;
	}

	/**
	 * A plugin in the list: a dot (on, off, error, working), the name and what it last did.
	 */
	private static class PluginRenderer extends JPanel implements ListCellRenderer<Plugin> {

		private final JLabel name = new JLabel();
		private final JLabel line = new JLabel();
		private Color dot;
		private boolean selected;

		PluginRenderer() {
			super(new MigLayout("insets 8 30 8 10, wrap 1, gap 0 2", "[fill, grow]"));
			setOpaque(false);
			add(name, "wmin 0");
			add(line, "wmin 0");
		}

		@Override
		public Component getListCellRendererComponent(JList<? extends Plugin> list, Plugin p, int index, boolean isSelected, boolean cellHasFocus) {
			selected = isSelected;
			name.setText(p.name);
			name.setFont(Modern.font(13.5f, Font.BOLD));
			name.setForeground(NightTheme.getForeground());
			Activity a = p.activity;
			String text = p.error != null ? "Error: " + p.error : a != null ? "Working: " + a.task : !p.enabled ? "Off" : p.lastRun != null ? p.lastRun : p.category;
			line.setText(text);
			line.setFont(Modern.font(11.5f, Font.PLAIN));
			line.setForeground(p.error != null ? NightTheme.getDanger() : NightTheme.getDimForeground());
			dot = p.error != null ? NightTheme.getDanger() : a != null ? NightTheme.getAccent() : p.enabled ? NightTheme.getSuccess() : NightTheme.getDimForeground();
			return this;
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = Modern.smooth(g);
			if (selected) {
				g2d.setColor(Modern.alpha(NightTheme.getAccent(), 55));
				g2d.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
			}
			g2d.setColor(dot);
			g2d.fillOval(12, 16, 9, 9);
			g2d.dispose();
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
		final ThinProgressBar bar = new ThinProgressBar();
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

}
