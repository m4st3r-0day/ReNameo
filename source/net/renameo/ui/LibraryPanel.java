package net.renameo.ui;

import static net.renameo.Logging.*;
import static net.renameo.util.FileUtilities.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicScrollPaneUI;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;

import net.miginfocom.swing.MigLayout;
import net.renameo.ApiKeys;
import net.renameo.Settings;
import net.renameo.UserFiles;
import net.renameo.media.LibraryStats;
import net.renameo.media.LibraryStats.Kind;
import net.renameo.media.LibraryStats.Result;
import net.renameo.media.LibraryStats.Title;
import net.renameo.util.PreferencesMap.PreferencesEntry;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;
import net.renameo.util.ui.ThinProgressBar;

/**
 * The Library page: what a folder of movies and series holds, how big it is, in which quality, what is there twice and which episodes are missing.
 */
public class LibraryPanel extends JPanel {

	private static final PreferencesEntry<String> persistentFolder = Settings.forPackage(LibraryPanel.class).entry("library.folder").defaultValue("");

	private Result result;

	// work in the background: scan or missing episodes
	private volatile Thread worker;
	private volatile boolean cancelled;
	private volatile String progressText = "";
	private volatile float progressFraction = -1;

	private final JLabel folderLabel = Modern.label("", 12.5f, Font.PLAIN, true);
	private final JPanel progressRow = new JPanel(new MigLayout("insets 0, fillx, gap 10", "[grow, fill][]", "[][]"));
	private final JLabel progressLabel = Modern.label("", 12.5f, Font.PLAIN, false);
	private final ThinProgressBar progressBar = new ThinProgressBar();
	private final Timer ticker = new Timer(50, e -> tick());

	private final JPanel tiles = new JPanel(new MigLayout("insets 0, fillx, gap 10", "[fill, grow, sg]"));
	private final ResolutionChart chart = new ResolutionChart();
	private final TitleModel model = new TitleModel();
	private final JTable table = new JTable(model);
	private final TableRowSorter<TitleModel> sorter = new TableRowSorter<TitleModel>(model);
	private final JComboBox<String> kind = new JComboBox<String>(new String[] { "Movies and series", "Series", "Movies" });
	private final JCheckBox problems = new JCheckBox("Only with something to fix");
	private final Modern.SearchField search = new Modern.SearchField("Filter titles", null);

	private final JPanel body = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[fill, grow]", "[][fill, grow]"));
	private final JPanel empty = new JPanel(new MigLayout("insets 0, fill", "[center]", "[center]"));

	private final JButton missingButton;

	public LibraryPanel() {
		super(new MigLayout("insets 0 0 20 0, fill, wrap 1, hidemode 3", "[fill, grow]", "[][][fill, grow]"));
		setOpaque(false);

		JButton choose = Modern.button(newAction("Choose folder…", this::choose), Glyph.of(Glyph.Shape.FOLDER, 16, NightTheme::getForeground), Modern.Style.SECONDARY);
		JButton rescan = Modern.button(newAction("Rescan", e -> scan()), Glyph.of(Glyph.Shape.UNDO, 16, NightTheme::getForeground), Modern.Style.SECONDARY);
		missingButton = Modern.button(newAction("Check missing episodes", e -> checkMissing()), Glyph.of(Glyph.Shape.TV, 16, NightTheme::getForeground), Modern.Style.SECONDARY);
		missingButton.setToolTipText("Ask TheMovieDB which aired episodes of each series are not in the library");

		JPanel pageActions = new JPanel(new MigLayout("insets 0, gap 10"));
		pageActions.setOpaque(false);
		pageActions.add(choose);
		pageActions.add(rescan);
		pageActions.add(missingButton);
		putClientProperty(MainFrame.PAGE_ACTIONS, pageActions);
		putClientProperty(Modern.CUSTOM, true);

		// folder and progress
		add(folderLabel, "gapleft 4");
		progressRow.setOpaque(false);
		JButton stop = Modern.button(newAction("Stop", e -> cancel()), null, Modern.Style.GHOST);
		progressRow.add(progressLabel, "wmin 0");
		progressRow.add(stop, "h 26!, wrap");
		progressRow.add(progressBar, "span 2, h 6!, growx");
		progressRow.setVisible(false);
		add(progressRow, "gaptop 4");

		// tiles, chart and the list of titles
		tiles.setOpaque(false);
		body.setOpaque(false);
		body.add(tiles, "gaptop 6");

		Modern.Card chartCard = new Modern.Card(new MigLayout("insets 14 16 14 16, fill, wrap 1", "[fill, grow]", "[][fill, grow]"));
		chartCard.add(Modern.caption("Resolution"));
		chartCard.add(chart, "grow");

		Modern.Card tableCard = new Modern.Card(new MigLayout("insets 10 12 10 12, fill, wrap 1", "[fill, grow]", "[][fill, grow]"));
		JPanel filters = new JPanel(new MigLayout("insets 0, gap 10", "[260!][][]push"));
		filters.setOpaque(false);
		filters.add(search, "h 30!, growx");
		filters.add(kind, "h 30!");
		problems.setOpaque(false);
		problems.setFont(Modern.font(12.5f, Font.PLAIN));
		Modern.onThemeChange(problems, () -> problems.setForeground(NightTheme.getForeground()));
		problems.setToolTipText("Missing episodes, duplicates or videos without subtitles");
		filters.add(problems);
		tableCard.add(filters);
		tableCard.add(createTable(), "grow, hmin 0");

		JPanel lower = new JPanel(new MigLayout("insets 0, fill, gap 10", "[300!][fill, grow]", "[fill, grow]"));
		lower.setOpaque(false);
		lower.add(chartCard, "growy, aligny top, hmax 320");
		lower.add(tableCard, "grow, hmin 0");
		body.add(lower, "grow, hmin 0, gaptop 4");

		empty.setOpaque(false);
		JPanel emptyText = new JPanel(new MigLayout("insets 0, wrap 1", "[center]"));
		emptyText.setOpaque(false);
		JLabel emptyIcon = new JLabel(Glyph.of(Glyph.Shape.CHART, 44, NightTheme::getDimForeground));
		emptyText.add(emptyIcon);
		emptyText.add(Modern.label("Choose the folder with your movies and series", 16f, Font.BOLD, false), "gaptop 8");
		emptyText.add(Modern.label("ReNameo counts titles, episodes, size and quality, and finds duplicates and missing episodes. Nothing is changed.", 12.5f, Font.PLAIN, true));
		emptyText.add(Modern.button(newAction("Choose folder…", this::choose), Glyph.of(Glyph.Shape.FOLDER, 16, NightTheme::getForeground), Modern.Style.PRIMARY), "gaptop 12");
		empty.add(emptyText);

		add(body, "grow, hmin 0");
		add(empty, "grow");

		search.getDocument().addDocumentListener(new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				applyFilter();
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				applyFilter();
			}
		});
		kind.addActionListener(e -> applyFilter());
		problems.addActionListener(e -> applyFilter());

		showResult();
	}

	@Override
	public void addNotify() {
		super.addNotify();
		// the first time the page is shown, read the folder from last time
		if (result == null && worker == null && !persistentFolder.getValue().isEmpty() && new File(persistentFolder.getValue()).isDirectory()) {
			SwingUtilities.invokeLater(this::scan);
		}
	}

	@Override
	public void removeNotify() {
		ticker.stop();
		super.removeNotify();
	}

	private File getFolder() {
		String path = persistentFolder.getValue();
		return path.isEmpty() ? null : new File(path);
	}

	private void choose(java.awt.event.ActionEvent evt) {
		File folder = UserFiles.showOpenDialogSelectFolder(getFolder(), "Library folder", evt);
		if (folder != null) {
			persistentFolder.setValue(folder.getPath());
			scan();
		}
	}

	private void scan() {
		File folder = getFolder();
		if (folder == null || !folder.isDirectory() || worker != null) {
			return;
		}
		run("Reading " + folder.getName(), () -> {
			Result r = LibraryStats.scan(folder, () -> cancelled, (count, current) -> {
				progressText = String.format("Reading %s · %d videos", current, count);
			});
			SwingUtilities.invokeLater(() -> {
				result = r;
				showResult();
			});
		});
	}

	private void checkMissing() {
		if (result == null || worker != null) {
			return;
		}
		if (ApiKeys.isMissing(ApiKeys.Service.TMDB)) {
			ApiKeysDialog.show(SwingUtilities.getWindowAncestor(this), false);
			return;
		}
		List<Title> series = result.titles.stream().filter(t -> t.kind == Kind.SERIES).collect(Collectors.toList());
		Locale locale = Locale.forLanguageTag(Settings.forPackage(net.renameo.ui.rename.RenamePanel.class).entry("rename.language").defaultValue("en").getValue());
		run("Checking missing episodes", () -> {
			LibraryStats.findMissing(series, locale, () -> cancelled, (i, name) -> {
				progressText = String.format("Checking %s · %d of %d", name, i + 1, series.size());
				progressFraction = series.isEmpty() ? -1 : (i + 1) / (float) series.size();
				SwingUtilities.invokeLater(this::showResult); // fill in the table as it goes
			});
			SwingUtilities.invokeLater(() -> {
				showResult();
				int missing = Math.max(0, result.getMissing());
				SwingEventBus.getInstance().post(new AppEvents.Toast(missing == 0 ? "No missing episodes" : missing + " missing episodes", "Hover over a number in the Missing column to see them", AppEvents.Status.Kind.READY));
			});
		});
	}

	/**
	 * Run in the background, with the progress bar and Stop.
	 */
	private void run(String title, BackgroundRunnable work) {
		cancelled = false;
		progressText = title;
		progressFraction = -1;
		progressRow.setVisible(true);
		missingButton.setEnabled(false);
		ticker.start();
		SwingEventBus.getInstance().post(new AppEvents.Status(title + " …", AppEvents.Status.Kind.BUSY));

		Thread thread = new Thread(() -> {
			try {
				work.run();
			} catch (CancellationException e) {
				debug.fine("Library: stopped");
			} catch (Exception e) {
				log.log(Level.WARNING, "Library: " + e.getMessage(), e);
			} finally {
				SwingUtilities.invokeLater(() -> {
					worker = null;
					ticker.stop();
					progressRow.setVisible(false);
					missingButton.setEnabled(result != null && result.count(Kind.SERIES) > 0);
					SwingEventBus.getInstance().post(new AppEvents.Status("Ready", AppEvents.Status.Kind.READY));
				});
			}
		}, "Library");
		thread.setDaemon(true);
		worker = thread;
		thread.start();
	}

	private void cancel() {
		cancelled = true;
		progressText = "Stopping…";
	}

	private void tick() {
		progressLabel.setText(progressText);
		progressBar.setFraction(progressFraction);
		progressBar.repaint();
	}

	private void showResult() {
		File folder = getFolder();
		folderLabel.setText(folder == null ? "" : folder.getPath().replace(System.getProperty("user.home"), "~"));
		boolean has = result != null;
		body.setVisible(has);
		empty.setVisible(!has && worker == null);
		missingButton.setEnabled(has && worker == null && result.count(Kind.SERIES) > 0);
		if (!has) {
			return;
		}

		tiles.removeAll();
		int missing = result.getMissing();
		tiles.add(tile(String.valueOf(result.count(Kind.MOVIE)), "Movies", Glyph.Shape.FILM, null), "wmin 0");
		tiles.add(tile(String.valueOf(result.count(Kind.SERIES)), "Series", Glyph.Shape.TV, null), "wmin 0");
		tiles.add(tile(String.valueOf(result.getEpisodes()), "Episodes", Glyph.Shape.PLAY, null), "wmin 0");
		tiles.add(tile(formatSize(result.size), "Size", Glyph.Shape.FOLDER, result.extras > 0 ? "including " + result.extras + (result.extras == 1 ? " extra" : " extras") : null), "wmin 0");
		tiles.add(tile(missing < 0 ? "–" : String.valueOf(missing), "Missing episodes", Glyph.Shape.WARNING, missing < 0 ? "not checked yet" : null), "wmin 0");
		tiles.add(tile(String.valueOf(result.getDuplicates()), "Duplicates", Glyph.Shape.MATCH, null), "wmin 0");
		tiles.add(tile(String.valueOf(result.getWithoutSubtitles()), "Without subtitles", Glyph.Shape.MINUS_CIRCLE, null), "wmin 0");
		tiles.revalidate();
		tiles.repaint();

		chart.setResult(result);
		model.setTitles(result.titles);
		revalidate();
		repaint();
	}

	private static JComponent tile(String value, String label, Glyph.Shape icon, String hint) {
		Modern.Card card = new Modern.Card(new MigLayout("insets 12 14 12 14, wrap 1, gap 0 2", "[]"));
		JLabel caption = Modern.label(label, 12f, Font.PLAIN, true);
		caption.setIcon(Glyph.of(icon, 14, NightTheme::getDimForeground));
		caption.setIconTextGap(6);
		card.add(caption);
		card.add(Modern.label(value, 24f, Font.BOLD, false));
		// every tile has the hint line, so they are all as high
		card.add(Modern.label(hint == null ? " " : hint, 11f, Font.PLAIN, true));
		return card;
	}

	private JComponent createTable() {
		table.setRowSorter(sorter);
		table.setRowHeight(30);
		table.setShowVerticalLines(false);
		table.setIntercellSpacing(new Dimension(0, 1));
		table.setFillsViewportHeight(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getTableHeader().setReorderingAllowed(false);

		int[] widths = { 280, 70, 60, 70, 70, 80, 80, 80, 80 };
		for (int i = 0; i < widths.length; i++) {
			table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
		}
		// numbers sort as numbers, "–" (not checked) last
		for (int i = 2; i < model.getColumnCount(); i++) {
			if (i != 6) {
				sorter.setComparator(i, Comparator.comparingLong(o -> o instanceof Number ? ((Number) o).longValue() : Long.MIN_VALUE));
			}
		}

		// double click: show the folder in the Finder
		table.addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				int row = table.rowAtPoint(e.getPoint());
				if (e.getClickCount() == 2 && row >= 0) {
					Title t = model.titles.get(table.convertRowIndexToModel(row));
					if (t.folder != null) {
						UserFiles.revealFiles(Collections.singletonList(t.folder));
					}
				}
			}
		});
		table.setToolTipText("");

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

	private void applyFilter() {
		String text = search.getText().trim().toLowerCase(Locale.ROOT);
		int k = kind.getSelectedIndex();
		boolean onlyProblems = problems.isSelected();
		sorter.setRowFilter(new RowFilter<TitleModel, Integer>() {

			@Override
			public boolean include(Entry<? extends TitleModel, ? extends Integer> entry) {
				Title t = model.titles.get(entry.getIdentifier());
				if (k == 1 && t.kind != Kind.SERIES || k == 2 && t.kind != Kind.MOVIE) {
					return false;
				}
				if (onlyProblems && t.duplicates == 0 && t.withoutSubtitles == 0 && (t.missing == null || t.missing.isEmpty())) {
					return false;
				}
				return text.isEmpty() || t.name.toLowerCase(Locale.ROOT).contains(text);
			}
		});
	}

	private static class TitleModel extends AbstractTableModel {

		private static final String[] COLUMNS = { "Title", "Type", "Seasons", "Episodes", "Missing", "Size", "Quality", "No subtitles", "Duplicates" };

		List<Title> titles = new ArrayList<Title>();

		void setTitles(List<Title> titles) {
			this.titles = new ArrayList<Title>(titles);
			fireTableDataChanged();
		}

		@Override
		public int getRowCount() {
			return titles.size();
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
			Title t = titles.get(row);
			switch (column) {
			case 0:
				return t.name;
			case 1:
				return t.kind == Kind.SERIES ? "Series" : "Movie";
			case 2:
				return t.kind == Kind.SERIES ? (Object) t.seasons.size() : "";
			case 3:
				return t.kind == Kind.SERIES ? (Object) t.episodes.size() : "";
			case 4:
				return t.kind != Kind.SERIES ? "" : t.missing == null ? "–" : (Object) t.missing.size();
			case 5:
				return t.size;
			case 6:
				return t.getResolution();
			case 7:
				return t.withoutSubtitles;
			default:
				return t.duplicates;
			}
		}
	}

	private class CellRenderer extends DefaultTableCellRenderer {

		@Override
		public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
			int c = table.convertColumnIndexToModel(column);
			Object shown = c == 5 && value instanceof Long ? formatSize((Long) value) : value;
			super.getTableCellRendererComponent(table, shown, isSelected, false, row, column);
			setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
			setFont(Modern.font(12.5f, c == 0 ? Font.BOLD : Font.PLAIN));
			setHorizontalAlignment(c >= 2 && c != 6 ? RIGHT : LEFT);
			setOpaque(isSelected);

			// something to fix stands out, zeros step back
			boolean problem = (c == 4 || c == 7 || c == 8) && value instanceof Integer && (Integer) value > 0;
			boolean zero = value instanceof Integer && (Integer) value == 0;
			setForeground(problem ? NightTheme.getWarning() : zero ? NightTheme.getDimForeground() : NightTheme.getForeground());

			Title t = model.titles.get(table.convertRowIndexToModel(row));
			setToolTipText(c == 4 && t.missing != null && !t.missing.isEmpty() ? missingTooltip(t.missing) : c == 0 && t.folder != null ? t.folder.getPath() + " · double-click to show it" : null);
			return this;
		}
	}

	private static String missingTooltip(List<String> missing) {
		StringBuilder html = new StringBuilder("<html>");
		missing.stream().limit(15).forEach(m -> html.append(escapeHTML(m)).append("<br>"));
		if (missing.size() > 15) {
			html.append("… and ").append(missing.size() - 15).append(" more");
		}
		return html.append("</html>").toString();
	}

	/**
	 * One bar per resolution, length by number of videos: one hue for the qualities, gray for "Unknown". Labels and numbers in text colors.
	 */
	private static class ResolutionChart extends JComponent {

		private Result result;
		private int hover = -1;

		ResolutionChart() {
			setToolTipText("");
			addMouseMotionListener(new MouseAdapter() {

				@Override
				public void mouseMoved(MouseEvent e) {
					int row = rowAt(e.getY());
					if (row != hover) {
						hover = row;
						repaint();
					}
				}
			});
			addMouseListener(new MouseAdapter() {

				@Override
				public void mouseExited(MouseEvent e) {
					hover = -1;
					repaint();
				}
			});
		}

		void setResult(Result result) {
			this.result = result;
			repaint();
		}

		private static final int ROW = 40;

		private int rowAt(int y) {
			int row = y / ROW;
			return row >= 0 && row < LibraryStats.RESOLUTIONS.length ? row : -1;
		}

		@Override
		public String getToolTipText(MouseEvent e) {
			int row = rowAt(e.getY());
			if (result == null || row < 0) {
				return null;
			}
			String r = LibraryStats.RESOLUTIONS[row];
			int count = result.resolutions.get(r);
			return String.format("%s: %d %s, %s", r, count, count == 1 ? "video" : "videos", formatSize(result.resolutionSizes.get(r)));
		}

		@Override
		public Dimension getPreferredSize() {
			return new Dimension(260, ROW * LibraryStats.RESOLUTIONS.length);
		}

		@Override
		protected void paintComponent(Graphics g) {
			if (result == null) {
				return;
			}
			Graphics2D g2d = Modern.smooth(g);
			int max = result.resolutions.values().stream().mapToInt(Integer::intValue).max().orElse(0);
			int total = result.resolutions.values().stream().mapToInt(Integer::intValue).sum();
			Font labelFont = Modern.font(12.5f, Font.PLAIN);
			Font valueFont = Modern.font(12f, Font.PLAIN);
			FontMetrics fm = g2d.getFontMetrics(labelFont);
			int labelWidth = 70, valueWidth = 64, barX = labelWidth, barW = Math.max(20, getWidth() - labelWidth - valueWidth - 8);

			for (int i = 0; i < LibraryStats.RESOLUTIONS.length; i++) {
				String r = LibraryStats.RESOLUTIONS[i];
				int count = result.resolutions.get(r);
				int y = i * ROW;
				int baseline = y + (ROW + fm.getAscent() - fm.getDescent()) / 2;

				if (i == hover) {
					g2d.setColor(NightTheme.getCardHover());
					g2d.fill(new RoundRectangle2D.Float(0, y + 3, getWidth(), ROW - 6, 8, 8));
				}

				g2d.setFont(labelFont);
				g2d.setColor(NightTheme.getForeground());
				g2d.drawString(r, 4, baseline);

				// recessive track, then the bar with rounded ends
				int h = 10, by = y + (ROW - h) / 2;
				g2d.setColor(Modern.alpha(NightTheme.getBorder(), 120));
				g2d.fill(new RoundRectangle2D.Float(barX, by, barW, h, 4, 4));
				if (count > 0) {
					Color color = r.equals("Unknown") ? NightTheme.getDimForeground() : NightTheme.getAccent();
					g2d.setColor(color);
					g2d.fill(new RoundRectangle2D.Float(barX, by, Math.max(h, barW * count / (float) max), h, 4, 4));
				}

				g2d.setFont(valueFont);
				g2d.setColor(count == 0 ? NightTheme.getDimForeground() : NightTheme.getForeground());
				String value = total == 0 ? "0" : String.format("%d · %d%%", count, Math.round(100f * count / total));
				g2d.drawString(value, getWidth() - g2d.getFontMetrics().stringWidth(value) - 2, baseline);
			}
			g2d.dispose();
		}
	}

}
