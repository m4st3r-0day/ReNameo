package net.renameo.ui.rename;

import static javax.swing.BorderFactory.*;
import static net.renameo.Logging.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Window;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

import net.renameo.WebServices;
import net.renameo.media.FileNameInfo;
import net.renameo.similarity.EpisodeMatcher;
import net.renameo.similarity.Match;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.web.Episode;
import net.renameo.web.Movie;
import net.renameo.web.SearchResult;
import net.renameo.web.SortOrder;
import net.miginfocom.swing.MigLayout;

/**
 * Fix a wrong match in a few clicks: search a title, pick the result and the selected files are matched again (episodes are aligned by their season / episode numbers).
 */
class ManualMatchDialog extends JDialog {

	private final List<File> files;
	private final Locale locale;

	private final Modern.SearchField query = new Modern.SearchField("Search title…", null);
	private final JToggleButton movieMode = new JToggleButton("Movie");
	private final JToggleButton seriesMode = new JToggleButton("TV Show");
	private final DefaultListModel<Object> results = new DefaultListModel<Object>();
	private final JList<Object> list = new JList<Object>(results);
	private final JLabel status = Modern.label(" ", 12f, Font.PLAIN, true);

	private Map<File, Object> result;
	private SwingWorker<?, ?> search;

	ManualMatchDialog(Window owner, List<File> files, Locale locale) {
		super(owner, "Change Match", ModalityType.DOCUMENT_MODAL);
		this.files = files;
		this.locale = locale;

		FileNameInfo info = FileNameInfo.parse(files.get(0));
		query.setText(info.title == null ? "" : info.title + (info.year != null && info.season == null ? " " + info.year : ""));
		(info.season != null || info.episode != null ? seriesMode : movieMode).setSelected(true);

		ButtonGroup group = new ButtonGroup();
		group.add(movieMode);
		group.add(seriesMode);
		Modern.style(movieMode, Modern.Style.SECONDARY);
		Modern.style(seriesMode, Modern.Style.SECONDARY);
		movieMode.addActionListener(evt -> search());
		seriesMode.addActionListener(evt -> search());

		query.addActionListener(evt -> search());

		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setFixedCellHeight(34);
		list.setCellRenderer(new DefaultListCellRenderer() {

			@Override
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean isSelected, boolean cellHasFocus) {
				super.getListCellRendererComponent(l, describe(value), index, isSelected, false);
				setBorder(createEmptyBorder(0, 12, 0, 12));
				setFont(Modern.font(13f, Font.PLAIN));
				setOpaque(isSelected);
				setBackground(Modern.alpha(NightTheme.getAccent(), 70));
				setForeground(NightTheme.getForeground());
				return this;
			}
		});
		list.addMouseListener(mouseClicked(evt -> {
			if (evt.getClickCount() == 2) {
				confirm();
			}
		}));

		JScrollPane scroll = new JScrollPane(list);
		scroll.setBorder(createEmptyBorder());
		Modern.onThemeChange(list, () -> {
			list.setBackground(NightTheme.getCardBackground());
			scroll.getViewport().setBackground(NightTheme.getCardBackground());
		});
		Modern.Card card = new Modern.Card(new MigLayout("insets 6, fill", "[fill, grow]", "[fill, grow]"));
		card.add(scroll, "grow");

		JButton cancel = Modern.button(newAction("Cancel", evt -> dispose()), null, Modern.Style.SECONDARY);
		JButton use = Modern.button(newAction("Use Match", evt -> confirm()), Glyph.of(Glyph.Shape.CHECK_CIRCLE, 15, Color.WHITE), Modern.Style.PRIMARY);

		JPanel content = new JPanel(new MigLayout("insets 20, fill", "[grow, fill][][]", "[]4[]14[]10[fill, grow]10[]"));
		content.setBackground(NightTheme.getBackground());
		content.add(Modern.label("Change Match", 20f, Font.BOLD, false), "span 3, wrap");
		content.add(Modern.label(files.size() == 1 ? files.get(0).getName() : files.size() + " selected files", 12.5f, Font.PLAIN, true), "span 3, wrap");
		content.add(query, "h 34!");
		content.add(movieMode, "h 34!");
		content.add(seriesMode, "h 34!, wrap");
		content.add(card, "span 3, grow, wrap");
		content.add(status, "growx");
		content.add(cancel);
		content.add(use);

		setContentPane(content);
		installAction(content, KeyStroke.getKeyStroke("ESCAPE"), newAction("Cancel", evt -> dispose()));
		setSize(640, 520);
		setLocationRelativeTo(owner);

		search();
	}

	Map<File, Object> showDialog() {
		setVisible(true);
		return result;
	}

	private static String describe(Object value) {
		if (value instanceof Movie) {
			Movie m = (Movie) value;
			return m.getYear() > 0 ? String.format("%s (%d)", m.getName(), m.getYear()) : m.getName();
		}
		if (value instanceof SearchResult) {
			return ((SearchResult) value).getName();
		}
		return String.valueOf(value);
	}

	private void search() {
		String q = query.getText().trim();
		if (q.isEmpty()) {
			return;
		}
		if (search != null) {
			search.cancel(true);
		}

		boolean movies = movieMode.isSelected();
		status.setText("Searching TheMovieDB …");
		results.clear();

		search = newSwingWorker(() -> {
			return movies ? new ArrayList<Object>(WebServices.TheMovieDB.searchMovie(q, locale)) : new ArrayList<Object>(WebServices.TheMovieDB_TV.search(q, locale));
		}, found -> {
			found.forEach(results::addElement);
			status.setText(found.isEmpty() ? "No results. Try a shorter title or the original title." : found.size() + " results from " + (movies ? "TheMovieDB" : "TheMovieDB TV"));
			if (!found.isEmpty()) {
				list.setSelectedIndex(0);
			}
		}, error -> {
			status.setText("Search failed: " + error.getMessage());
		});
		search.execute();
	}

	private void confirm() {
		Object selected = list.getSelectedValue();
		if (selected == null) {
			return;
		}

		if (selected instanceof Movie) {
			Movie movie = (Movie) selected;
			result = new LinkedHashMap<File, Object>();
			for (File f : files) {
				result.put(f, movie);
			}
			dispose();
			return;
		}

		status.setText("Fetching episodes …");
		SearchResult series = (SearchResult) selected;
		newSwingWorker(() -> {
			List<Episode> episodes = WebServices.TheMovieDB_TV.getEpisodeList(series, SortOrder.Airdate, locale);
			return matchEpisodes(files, episodes);
		}, matched -> {
			if (matched.isEmpty()) {
				status.setText("None of the selected files could be aligned with an episode of " + series.getName());
				return;
			}
			result = matched;
			dispose();
		}, error -> {
			status.setText("Failed to fetch episodes: " + error.getMessage());
		}).execute();
	}

	/**
	 * Align files with episodes by their numbers, the same way automatic matching does.
	 */
	private static Map<File, Object> matchEpisodes(Collection<File> files, List<Episode> episodes) throws Exception {
		Map<File, Object> matched = new LinkedHashMap<File, Object>();
		for (Match<File, Object> m : new EpisodeMatcher(files, episodes, false).match()) {
			matched.put(m.getValue(), m.getCandidate());
		}
		debug.fine("Manual episode match: " + matched.size() + " of " + files.size());
		return matched;
	}

}
