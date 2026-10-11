package net.renameo.ui;

import static net.renameo.Logging.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.plaf.basic.BasicScrollPaneUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import net.miginfocom.swing.MigLayout;
import net.renameo.UserFiles;
import net.renameo.guardian.Guardian;
import net.renameo.guardian.Guardian.Entry;
import net.renameo.guardian.Guardian.Status;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;
import net.renameo.util.ui.ThinProgressBar;

/**
 * The Guardian page: what the guardian suggests, everything it did (with undo), and the files it leaves alone.
 */
public class GuardianPanel extends JPanel {

	enum View {
		REVIEW("To review"), JOURNAL("Journal"), IGNORED("Ignored");

		final String label;

		View(String label) {
			this.label = label;
		}
	}

	private View view = View.REVIEW;

	private final JLabel status = Modern.label("", 12.5f, Font.PLAIN, true);
	private final JPanel progressRow = new JPanel(new MigLayout("insets 0, fillx, gap 10", "[grow, fill][]", "[][]"));
	private final JLabel progressLabel = Modern.label("", 12.5f, Font.PLAIN, false);
	private final ThinProgressBar progressBar = new ThinProgressBar();
	private final Timer ticker = new Timer(60, e -> tick());

	private final JPanel tabs = new JPanel(new MigLayout("insets 0, gap 6"));
	private final JPanel actions = new JPanel(new MigLayout("insets 0, gap 8", "[][][]push[]"));
	private final EntryModel model = new EntryModel();
	private final JTable table = new JTable(model);
	private final JLabel empty = Modern.label("", 13f, Font.PLAIN, true);
	private final JPanel center = new JPanel(new MigLayout("insets 0, fill, hidemode 3", "[fill, grow]", "[fill, grow]"));

	private final JButton scan;
	private final Runnable listener = () -> SwingUtilities.invokeLater(this::refresh);
	private boolean wasRunning;

	public GuardianPanel() {
		super(new MigLayout("insets 0 0 20 0, fill, wrap 1, hidemode 3", "[fill, grow]", "[][][][fill, grow][]"));
		setOpaque(false);

		scan = Modern.button(newAction("Check now", e -> GuardianRunner.runNow()), Glyph.of(Glyph.Shape.PLAY, 14, () -> java.awt.Color.WHITE), Modern.Style.PRIMARY);
		scan.setToolTipText("Check the library now");
		JButton settings = Modern.button(newAction("Settings", e -> SettingsPanel.open(SettingsPanel.Section.GUARDIAN)), Glyph.of(Glyph.Shape.SETTINGS, 16, NightTheme::getForeground), Modern.Style.SECONDARY);
		JPanel pageActions = new JPanel(new MigLayout("insets 0, gap 10"));
		pageActions.setOpaque(false);
		pageActions.add(settings);
		pageActions.add(scan);
		putClientProperty(MainFrame.PAGE_ACTIONS, pageActions);
		putClientProperty(Modern.CUSTOM, true);

		add(status, "gapleft 4, wmin 0");

		progressRow.setOpaque(false);
		JButton stop = Modern.button(newAction("Stop", e -> GuardianRunner.cancel()), null, Modern.Style.GHOST);
		progressRow.add(progressLabel, "wmin 0");
		progressRow.add(stop, "h 26!, wrap");
		progressRow.add(progressBar, "span 2, h 6!, growx");
		progressRow.setVisible(false);
		add(progressRow, "gaptop 6");

		tabs.setOpaque(false);
		add(tabs, "gaptop 10");

		Modern.Card card = new Modern.Card(new MigLayout("insets 8, fill", "[fill, grow]", "[fill, grow]"));
		center.setOpaque(false);
		center.add(createTable(), "grow, hmin 0");
		empty.setHorizontalAlignment(JLabel.CENTER);
		center.add(empty, "cell 0 0, grow");
		card.add(center, "grow");
		add(card, "grow, hmin 0");

		actions.setOpaque(false);
		add(actions, "gaptop 8");

		refresh();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		Guardian.addListener(listener);
		ticker.start();
		refresh();
	}

	@Override
	public void removeNotify() {
		Guardian.removeListener(listener);
		ticker.stop();
		super.removeNotify();
	}

	private void tick() {
		boolean running = GuardianRunner.isRunning();
		if (running != wasRunning) {
			wasRunning = running;
			progressRow.setVisible(running);
			scan.setEnabled(!running);
			refresh();
		}
		if (running) {
			progressLabel.setText(GuardianRunner.getProgress());
			progressBar.repaint();
		}
	}

	private File getRoot() {
		return Guardian.getFolder();
	}

	private void refresh() {
		File root = getRoot();
		String last = Guardian.getLastRun();
		Guardian.Mode mode = Guardian.getMode();
		boolean scheduled = Boolean.parseBoolean(Guardian.enabled.getValue());
		if (root == null) {
			status.setText("No library folder yet: choose it in Settings.");
		} else {
			status.setText(String.format("<html>%s · <b>%s</b> · %s%s</html>", escapeHTML(root.getPath().replace(System.getProperty("user.home"), "~")), escapeHTML(mode.label), scheduled ? "every day at " + Guardian.hour.getValue() + ":00" : "only when you check", last != null ? "<br>Last check: " + escapeHTML(last) : ""));
		}

		List<Entry> all = Guardian.getEntries();
		long review = all.stream().filter(e -> e.status == Status.PENDING).count();
		int ignored = Guardian.getIgnored().size();

		tabs.removeAll();
		for (View v : View.values()) {
			long count = v == View.REVIEW ? review : v == View.IGNORED ? ignored : all.stream().filter(e -> e.status != Status.PENDING).count();
			JButton tab = Modern.button(newAction(v.label + " (" + count + ")", e -> {
				view = v;
				refresh();
			}), null, v == view ? Modern.Style.SECONDARY : Modern.Style.GHOST);
			tabs.add(tab, "h 30!");
		}

		List<Row> rows = new ArrayList<Row>();
		if (view == View.IGNORED) {
			for (String path : Guardian.getIgnored()) {
				rows.add(new Row(null, path));
			}
		} else {
			for (Entry e : all) {
				if ((view == View.REVIEW) == (e.status == Status.PENDING)) {
					rows.add(new Row(e, null));
				}
			}
		}
		model.setRows(rows, root);

		empty.setText(view == View.REVIEW ? (root == null ? "Choose your library folder in Settings, then press Check now." : "Nothing to review. Changes the guardian suggests show up here.") : view == View.JOURNAL ? "Nothing changed yet. Every change the guardian makes is written down here and can be undone." : "No ignored files. Files you undo or ignore are listed here and left alone.");
		empty.setVisible(rows.isEmpty());
		table.getParent().getParent().setVisible(!rows.isEmpty());

		actions.removeAll();
		if (view == View.REVIEW) {
			actions.add(action("Apply", Glyph.Shape.CHECK_CIRCLE, "Make the selected changes", this::apply));
			actions.add(action("Ignore", Glyph.Shape.MINUS_CIRCLE, "Leave the selected files as they are, now and later", this::ignore));
			actions.add(action("Select all", null, null, e -> table.selectAll()));
		} else if (view == View.JOURNAL) {
			actions.add(action("Undo", Glyph.Shape.UNDO, "Put the selected files back where they were; they are left alone from now on", this::undo));
			actions.add(action("Wrong title…", Glyph.Shape.PENCIL, "Undo, and tell the guardian which series or movie it is", this::wrongTitle));
			actions.add(new JLabel());
			actions.add(action("Export CSV…", Glyph.Shape.DOWNLOAD, "Save the journal as a table", this::export));
		} else {
			actions.add(action("Check again", Glyph.Shape.UNDO, "The guardian may look at the selected files again", this::unignore));
		}
		revalidate();
		repaint();
	}

	private JButton action(String name, Glyph.Shape icon, String tooltip, java.util.function.Consumer<ActionEvent> handler) {
		JButton b = Modern.button(newAction(name, handler), icon == null ? null : Glyph.of(icon, 15, NightTheme::getForeground), Modern.Style.SECONDARY);
		b.setToolTipText(tooltip);
		return b;
	}

	private List<Entry> selectedEntries() {
		List<Entry> list = new ArrayList<Entry>();
		for (int i : table.getSelectedRows()) {
			Row r = model.rows.get(table.convertRowIndexToModel(i));
			if (r.entry != null) {
				list.add(r.entry);
			}
		}
		return list;
	}

	private void background(String title, Runnable work) {
		SwingEventBus.getInstance().post(new AppEvents.Status(title + " …", AppEvents.Status.Kind.BUSY));
		newSwingWorker(() -> {
			work.run();
			return null;
		}, ok -> {
			SwingEventBus.getInstance().post(new AppEvents.Status("Ready", AppEvents.Status.Kind.READY));
			refresh();
		}).execute();
	}

	private void apply(ActionEvent evt) {
		List<Entry> list = selectedEntries();
		if (!list.isEmpty()) {
			background("Applying " + list.size() + " changes", () -> Guardian.apply(list));
		}
	}

	private void ignore(ActionEvent evt) {
		Guardian.ignore(selectedEntries());
		refresh();
	}

	private void undo(ActionEvent evt) {
		List<Entry> list = selectedEntries().stream().filter(e -> e.status == Status.APPLIED).collect(Collectors.toList());
		if (!list.isEmpty()) {
			List<String> problems = Collections.synchronizedList(new ArrayList<String>());
			background("Undoing " + list.size() + " changes", () -> {
				Guardian.undo(list, problems::add);
				if (!problems.isEmpty()) {
					SwingUtilities.invokeLater(() -> SwingEventBus.getInstance().post(new AppEvents.Toast("Some files could not be put back", String.join(", ", problems), AppEvents.Status.Kind.WARNING)));
				}
			});
		}
	}

	private void wrongTitle(ActionEvent evt) {
		List<Entry> list = selectedEntries().stream().filter(e -> e.status == Status.APPLIED).collect(Collectors.toList());
		if (list.size() != 1) {
			SwingEventBus.getInstance().post(new AppEvents.Toast("Select one change", "Pick the change that has the wrong series or movie.", AppEvents.Status.Kind.WARNING));
			return;
		}
		Entry e = list.get(0);
		String title = showInputDialog("<html>Which series or movie is <b>" + escapeHTML(e.getFrom().getName()) + "</b>?<br>The change is undone and the guardian looks for this title from now on.</html>", "", "Wrong title", this);
		if (title != null && !title.trim().isEmpty()) {
			File root = getRoot();
			background("Undoing", () -> Guardian.wrongTitle(e, title, root));
		}
	}

	private void unignore(ActionEvent evt) {
		List<String> paths = new ArrayList<String>();
		for (int i : table.getSelectedRows()) {
			paths.add(model.rows.get(table.convertRowIndexToModel(i)).path);
		}
		Guardian.unignore(paths);
		refresh();
	}

	private void export(ActionEvent evt) {
		File file = UserFiles.showSaveDialogSelectFile(false, new File("renameo-guardian-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".csv"), "Export journal", evt);
		if (file != null) {
			try {
				Files.write(file.toPath(), Guardian.toCsv(Guardian.getEntries()).getBytes("UTF-8"));
				SwingEventBus.getInstance().post(new AppEvents.Toast("Journal exported", file.getName(), AppEvents.Status.Kind.READY));
			} catch (Exception x) {
				log.log(Level.WARNING, "Failed to export the journal: " + x.getMessage(), x);
			}
		}
	}

	private JComponent createTable() {
		table.setRowHeight(46);
		table.setShowVerticalLines(false);
		table.setIntercellSpacing(new Dimension(0, 1));
		table.setFillsViewportHeight(true);
		table.getTableHeader().setReorderingAllowed(false);
		table.setAutoCreateRowSorter(true);
		int[][] widths = { { 95, 110 }, { 115, 130 }, { 640, -1 }, { 380, -1 } };
		for (int i = 0; i < widths.length; i++) {
			table.getColumnModel().getColumn(i).setPreferredWidth(widths[i][0]);
			if (widths[i][1] > 0) {
				table.getColumnModel().getColumn(i).setMaxWidth(widths[i][1]);
			}
		}
		table.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				int row = table.rowAtPoint(e.getPoint());
				if (e.getClickCount() == 2 && row >= 0) {
					Row r = model.rows.get(table.convertRowIndexToModel(row));
					File f = r.entry == null ? new File(r.path) : r.entry.status == Status.APPLIED ? r.entry.getTo() : r.entry.getFrom();
					if (f.exists()) {
						UserFiles.revealFiles(Collections.singletonList(f));
					}
				}
			}
		});

		JScrollPane scroll = new JScrollPane(table);
		Modern.onThemeChange(scroll, () -> {
			// a new look and feel replaces the renderers of the table
			table.setDefaultRenderer(Object.class, new CellRenderer());
			scroll.setUI(new BasicScrollPaneUI());
			scroll.setBorder(BorderFactory.createEmptyBorder());
			table.setBackground(NightTheme.getCardBackground());
			table.setForeground(NightTheme.getForeground());
			table.setGridColor(NightTheme.getBorder());
			table.setSelectionBackground(Modern.alpha(NightTheme.getAccent(), 60));
			table.setSelectionForeground(NightTheme.getForeground());
			scroll.getViewport().setBackground(NightTheme.getCardBackground());
			table.getTableHeader().setBackground(NightTheme.getCardBackground());
			table.getTableHeader().setForeground(NightTheme.getDimForeground());
			table.getTableHeader().setFont(Modern.font(11.5f, Font.BOLD));
		});
		Modern.slim(scroll);
		return scroll;
	}

	private static class Row {

		final Entry entry;
		final String path;

		Row(Entry entry, String path) {
			this.entry = entry;
			this.path = path;
		}
	}

	private static class EntryModel extends AbstractTableModel {

		private static final String[] COLUMNS = { "When", "Status", "Change", "Why" };
		private static final SimpleDateFormat TIME = new SimpleDateFormat("dd/MM HH:mm");

		List<Row> rows = new ArrayList<Row>();
		File root;

		void setRows(List<Row> rows, File root) {
			this.rows = rows;
			this.root = root;
			fireTableDataChanged();
		}

		String relative(String path) {
			return root != null && path.startsWith(root.getPath() + File.separator) ? path.substring(root.getPath().length() + 1) : path;
		}

		@Override
		public int getRowCount() {
			return rows.size();
		}

		@Override
		public int getColumnCount() {
			return COLUMNS.length;
		}

		@Override
		public String getColumnName(int column) {
			return COLUMNS[column];
		}

		@Override
		public Object getValueAt(int row, int column) {
			Row r = rows.get(row);
			if (r.entry == null) {
				return column == 2 ? relative(r.path) : column == 1 ? "Ignored" : "";
			}
			Entry e = r.entry;
			switch (column) {
			case 0:
				synchronized (TIME) {
					return TIME.format(new Date(e.time));
				}
			case 1:
				return e.status;
			case 2:
				// two lines that don't wrap: before, and after
				return "<html><nobr>" + escapeHTML(relative(e.from)) + "</nobr><br><nobr>→ " + escapeHTML(relative(e.to)) + "</nobr></html>";
			default:
				String second = e.note != null ? e.note : e.extra.isEmpty() ? (e.exact ? "certain" : "likely") : (e.exact ? "certain" : "likely") + " · " + e.extra.size() + (e.extra.size() == 1 ? " file followed the folder" : " files followed the folder");
				return "<html><nobr>" + escapeHTML(e.reason) + "</nobr><br><nobr>" + escapeHTML(second) + "</nobr></html>";
			}
		}
	}

	private class CellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			Object shown = value instanceof Status ? label((Status) value) : value;
			super.getTableCellRendererComponent(table, shown, isSelected, false, row, column);
			setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
			setOpaque(isSelected);
			setFont(Modern.font(12.5f, column == 1 ? Font.BOLD : Font.PLAIN));
			setForeground(value instanceof Status ? color((Status) value) : column == 3 || column == 0 ? NightTheme.getDimForeground() : NightTheme.getForeground());
			setToolTipText(value instanceof String && ((String) value).startsWith("<html>") ? (String) value : null);
			return this;
		}

		private String label(Status s) {
			switch (s) {
			case PENDING:
				return "To review";
			case APPLIED:
				return "✓ Corrected";
			case UNDONE:
				return "↶ Undone";
			case IGNORED:
				return "Ignored";
			default:
				return "✕ Failed";
			}
		}

		private java.awt.Color color(Status s) {
			switch (s) {
			case PENDING:
				return NightTheme.getAccent();
			case APPLIED:
				return NightTheme.getSuccess();
			case FAILED:
				return NightTheme.getDanger();
			default:
				return NightTheme.getDimForeground();
			}
		}
	}

}
