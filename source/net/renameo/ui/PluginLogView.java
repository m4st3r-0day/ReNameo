package net.renameo.ui;

import static net.renameo.Logging.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicScrollPaneUI;

import net.miginfocom.swing.MigLayout;
import net.renameo.UserFiles;
import net.renameo.plugins.Plugins;
import net.renameo.plugins.Plugins.LogEntry;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.RoundBorder;
import net.renameo.util.ui.SwingEventBus;

/**
 * What the plugins wrote: kept while the app runs, filtered by plugin or errors, and easy to copy or save.
 */
class PluginLogView extends JPanel {

	private static final String ALL = "All plugins";

	private final DefaultListModel<LogEntry> model = new DefaultListModel<LogEntry>();
	private final JList<LogEntry> list = new JList<LogEntry>(model) {

		@Override
		protected void paintComponent(Graphics g) {
			super.paintComponent(g);
			if (model.isEmpty()) {
				Graphics2D g2d = Modern.smooth(g);
				g2d.setFont(Modern.font(12.5f, Font.PLAIN));
				g2d.setColor(NightTheme.getDimForeground());
				g2d.drawString(Plugins.getLog().isEmpty() ? "Nothing yet. What the plugins do shows up here." : "No messages match the filter.", 12, 24);
				g2d.dispose();
			}
		}

		@Override
		public String getToolTipText(MouseEvent e) {
			int index = locationToIndex(e.getPoint());
			return index >= 0 && getCellBounds(index, index).contains(e.getPoint()) ? getModel().getElementAt(index).message : null;
		}
	};

	private final JComboBox<String> filter = new JComboBox<String>();
	private final JCheckBox errorsOnly = new JCheckBox("Errors only");
	private final JLabel count = Modern.label("", 11.5f, Font.PLAIN, true);

	private final Consumer<LogEntry> listener = entry -> SwingUtilities.invokeLater(() -> add(entry));

	PluginLogView() {
		super(new MigLayout("insets 0, fill, wrap 1", "[fill, grow]", "[][fill, grow]"));
		setOpaque(false);

		list.setCellRenderer(new EntryRenderer());
		list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
		list.setFixedCellHeight(22);
		list.setToolTipText("");
		Modern.onThemeChange(list, () -> {
			list.setBackground(NightTheme.getFieldBackground());
			list.setSelectionBackground(Modern.alpha(NightTheme.getAccent(), 90));
		});

		// ⌘C on the Mac, Ctrl+C elsewhere; select all with ⌘A / Ctrl+A
		int menu = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
		installAction(list, KeyStroke.getKeyStroke(KeyEvent.VK_C, menu), newAction("Copy", e -> copy(list.getSelectedValuesList())));
		installAction(list, KeyStroke.getKeyStroke(KeyEvent.VK_A, menu), newAction("Select all", e -> selectAll()));

		JPopupMenu popup = new JPopupMenu();
		popup.add(newAction("Copy", e -> copy(list.getSelectedValuesList())));
		popup.add(newAction("Copy all", e -> copy(visible())));
		popup.add(newAction("Select all", e -> selectAll()));
		popup.addSeparator();
		popup.add(newAction("Show only this plugin", e -> {
			LogEntry entry = list.getSelectedValue();
			if (entry != null) {
				filter.setSelectedItem(entry.plugin);
			}
		}));
		popup.add(newAction("Clear log", e -> clear()));
		list.addMouseListener(new MouseAdapter() {

			@Override
			public void mousePressed(MouseEvent e) {
				showPopup(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				showPopup(e);
			}

			private void showPopup(MouseEvent e) {
				if (e.isPopupTrigger()) {
					int index = list.locationToIndex(e.getPoint());
					if (index >= 0 && !list.isSelectedIndex(index)) {
						list.setSelectedIndex(index);
					}
					popup.show(list, e.getX(), e.getY());
				}
			}
		});

		JScrollPane scroll = new JScrollPane(list);
		Modern.onThemeChange(scroll, () -> {
			scroll.setUI(new BasicScrollPaneUI());
			scroll.setBorder(new RoundBorder(NightTheme.getBorder(), 12, new Insets(4, 2, 4, 2)));
			scroll.getViewport().setBackground(NightTheme.getFieldBackground());
		});
		Modern.slim(scroll);

		filter.addActionListener(e -> rebuild());
		filter.setToolTipText("Show the messages of one plugin");
		errorsOnly.setOpaque(false);
		errorsOnly.setFont(Modern.font(12f, Font.PLAIN));
		Modern.onThemeChange(errorsOnly, () -> errorsOnly.setForeground(NightTheme.getForeground()));
		errorsOnly.addActionListener(e -> rebuild());

		JButton copy = Modern.button(newAction("Copy", e -> copy(list.isSelectionEmpty() ? visible() : list.getSelectedValuesList())), null, Modern.Style.GHOST);
		copy.setToolTipText("Copy the selected lines, or all shown lines if none is selected (⌘C)");
		JButton save = Modern.button(newAction("Save…", this::save), null, Modern.Style.GHOST);
		save.setToolTipText("Save the shown lines to a text file");
		JButton clear = Modern.button(newAction("Clear", e -> clear()), null, Modern.Style.GHOST);

		JPanel bar = new JPanel(new MigLayout("insets 0, gap 8, aligny center", "[][]push[][][][][]"));
		bar.setOpaque(false);
		bar.add(Modern.caption("Plugin log"), "gapleft 4");
		bar.add(count);
		bar.add(filter, "h 28!, wmin 140");
		bar.add(errorsOnly);
		bar.add(copy, "h 28!");
		bar.add(save, "h 28!");
		bar.add(clear, "h 28!");
		add(bar, "gaptop 4");
		add(scroll, "hmin 60");

		rebuild();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		Plugins.addLogListener(listener);
		rebuild(); // what happened while the page was not shown
	}

	@Override
	public void removeNotify() {
		Plugins.removeLogListener(listener);
		super.removeNotify();
	}

	/**
	 * Update the plugin filter after plugins were installed or removed.
	 */
	void updatePlugins() {
		Object selected = filter.getSelectedItem();
		TreeSet<String> names = new TreeSet<String>();
		Plugins.list().forEach(p -> names.add(p.name));
		Plugins.getLog().forEach(e -> names.add(e.plugin));

		DefaultComboBoxModel<String> items = new DefaultComboBoxModel<String>();
		items.addElement(ALL);
		names.forEach(items::addElement);
		items.setSelectedItem(selected != null && names.contains(selected) ? selected : ALL);
		filter.setModel(items);
	}

	private boolean accept(LogEntry entry) {
		Object plugin = filter.getSelectedItem();
		return (plugin == null || ALL.equals(plugin) || plugin.equals(entry.plugin)) && (!errorsOnly.isSelected() || entry.kind == LogEntry.Kind.ERROR);
	}

	private void rebuild() {
		model.clear();
		List<LogEntry> entries = Plugins.getLog().stream().filter(this::accept).collect(Collectors.toList());
		entries.forEach(model::addElement);
		if (!model.isEmpty()) {
			list.ensureIndexIsVisible(model.size() - 1);
		}
		updateCount();
		list.repaint();
	}

	private void add(LogEntry entry) {
		if (filter.getItemCount() > 0 && ((DefaultComboBoxModel<String>) filter.getModel()).getIndexOf(entry.plugin) < 0) {
			filter.addItem(entry.plugin);
		}
		if (!accept(entry)) {
			updateCount();
			return;
		}

		// follow new lines only when the user is looking at the end
		boolean atEnd = model.isEmpty() || list.getLastVisibleIndex() >= model.size() - 1;
		model.addElement(entry);
		while (model.size() > 2000) {
			model.remove(0);
		}
		if (atEnd) {
			list.ensureIndexIsVisible(model.size() - 1);
		}
		updateCount();
	}

	private void updateCount() {
		long errors = Plugins.getLog().stream().filter(e -> e.kind == LogEntry.Kind.ERROR).count();
		count.setText(model.size() + (model.size() == 1 ? " line" : " lines") + (errors > 0 ? " · " + errors + (errors == 1 ? " error" : " errors") : ""));
		count.setForeground(errors > 0 ? NightTheme.getDanger() : NightTheme.getDimForeground());
	}

	private void selectAll() {
		if (!model.isEmpty()) {
			list.setSelectionInterval(0, model.size() - 1);
		}
	}

	private List<LogEntry> visible() {
		List<LogEntry> entries = new ArrayList<LogEntry>();
		for (int i = 0; i < model.size(); i++) {
			entries.add(model.get(i));
		}
		return entries;
	}

	private static String text(List<LogEntry> entries) {
		return entries.stream().map(Objects::toString).collect(Collectors.joining("\n"));
	}

	private void copy(List<LogEntry> entries) {
		if (entries.isEmpty()) {
			return;
		}
		copyToClipboard(text(entries));
		SwingEventBus.getInstance().post(new AppEvents.Toast("Copied", entries.size() + (entries.size() == 1 ? " line" : " lines"), AppEvents.Status.Kind.READY));
	}

	private void save(ActionEvent evt) {
		List<LogEntry> entries = visible();
		String name = "renameo-plugins-" + new SimpleDateFormat("yyyy-MM-dd-HHmm").format(new Date()) + ".log";
		File file = UserFiles.showSaveDialogSelectFile(false, new File(name), "Save plugin log", evt);
		if (file == null) {
			return;
		}
		try {
			Files.write(file.toPath(), (text(entries) + "\n").getBytes("UTF-8"));
			SwingEventBus.getInstance().post(new AppEvents.Toast("Log saved", file.getName(), AppEvents.Status.Kind.READY));
		} catch (Exception e) {
			log.log(Level.WARNING, "Failed to save the plugin log: " + e.getMessage(), e);
		}
	}

	private void clear() {
		Plugins.clearLog();
		rebuild();
	}

	/**
	 * One line: time, a colored chip with the plugin name, a mark for start, end and errors, the message.
	 */
	private static class EntryRenderer extends JComponent implements ListCellRenderer<LogEntry> {

		private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");

		private LogEntry entry;
		private boolean selected;

		@Override
		public Component getListCellRendererComponent(JList<? extends LogEntry> list, LogEntry value, int index, boolean isSelected, boolean cellHasFocus) {
			this.entry = value;
			this.selected = isSelected;
			setFont(list.getFont());
			return this;
		}

		@Override
		public Dimension getPreferredSize() {
			FontMetrics fm = getFontMetrics(Modern.font(12.5f, Font.PLAIN));
			return new Dimension(entry == null ? 100 : 250 + fm.stringWidth(entry.message), 22);
		}

		@Override
		protected void paintComponent(Graphics g) {
			if (entry == null) {
				return;
			}
			Graphics2D g2d = Modern.smooth(g);
			int h = getHeight();
			if (selected) {
				g2d.setColor(Modern.alpha(NightTheme.getAccent(), 70));
				g2d.fillRect(0, 0, getWidth(), h);
			}

			Font mono = new Font(Font.MONOSPACED, Font.PLAIN, 12);
			g2d.setFont(mono);
			FontMetrics fm = g2d.getFontMetrics();
			int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;
			g2d.setColor(NightTheme.getDimForeground());
			String time;
			synchronized (TIME) {
				time = TIME.format(new Date(entry.time));
			}
			g2d.drawString(time, 10, baseline);
			int x = 10 + fm.stringWidth(time) + 10;

			// the same plugin always gets the same color
			Font chipFont = Modern.font(11f, Font.BOLD);
			Color tint = Color.getHSBColor((entry.plugin.hashCode() & 0xFFFF) / (float) 0xFFFF, 0.55f, 0.95f);
			int chip = Modern.paintChip(g2d, entry.plugin, x, 3, h - 6, chipFont, Modern.alpha(tint, 45), tint);
			x += Math.max(chip, 110) + 8;

			String mark;
			Color markColor;
			Color text = NightTheme.getForeground();
			Font textFont = Modern.font(12.5f, Font.PLAIN);
			switch (entry.kind) {
			case START:
				mark = "▶";
				markColor = NightTheme.getAccent();
				textFont = Modern.font(12.5f, Font.BOLD);
				break;
			case DONE:
				mark = "✓";
				markColor = NightTheme.getSuccess();
				text = NightTheme.getSuccess();
				break;
			case STOPPED:
				mark = "■";
				markColor = NightTheme.getWarning();
				text = NightTheme.getWarning();
				break;
			case ERROR:
				mark = "✕";
				markColor = NightTheme.getDanger();
				text = NightTheme.getDanger();
				break;
			default:
				mark = " ";
				markColor = text;
			}
			g2d.setFont(Modern.font(11.5f, Font.BOLD));
			g2d.setColor(markColor);
			g2d.drawString(mark, x, baseline);
			x += 18;

			g2d.setFont(textFont);
			g2d.setColor(text);
			g2d.drawString(entry.message, x, baseline);
			g2d.dispose();
		}
	}

}
