package net.renameo.ui.rename;

import static net.renameo.Logging.*;
import static net.renameo.util.FileUtilities.*;

import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

import javax.imageio.ImageIO;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

import net.renameo.WebServices;
import net.renameo.media.FileNameInfo;
import net.renameo.similarity.Match;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.web.AudioTrack;
import net.renameo.web.Episode;
import net.renameo.web.EpisodeFormat;
import net.renameo.web.Movie;
import net.renameo.web.MovieInfo;
import net.renameo.web.SearchResult;
import net.renameo.web.SeriesInfo;
import net.miginfocom.swing.MigLayout;

/**
 * Details of the selected match: cover, title, tags, rating, overview and key facts.
 */
class MetadataPanel extends Modern.Card {

	private static final Map<URL, Image> posters = Collections.synchronizedMap(new LinkedHashMap<URL, Image>(32, 0.75f, true) {

		@Override
		protected boolean removeEldestEntry(Map.Entry<URL, Image> eldest) {
			return size() > 64;
		}
	});

	private final RenameModel model;
	private final ListSelectionModel selection;

	private final Cover cover = new Cover();
	private final JLabel title = Modern.label("", 17f, Font.BOLD, false);
	private final JPanel chips = new JPanel(new MigLayout("insets 0, gap 6"));
	private final JLabel rating = Modern.label("", 13f, Font.PLAIN, false);
	private final JTextArea overview = new JTextArea();
	private final JPanel facts = new JPanel(new MigLayout("insets 0, wrap 2, gapy 8", "[]14[0:0:n, grow, fill]"));
	private final JButton edit;
	private final JButton change;
	private final JPanel badges = new JPanel(new MigLayout("insets 0, gap 5"));
	private final JLabel parsedTitle = Modern.caption("From file name");
	private final JPanel parsed = new JPanel(new MigLayout("insets 0, wrap 2, gapy 6", "[]14[0:0:n, grow, fill]"));
	private final JComponent empty;
	private int currentIndex = -1;
	private final JPanel content = new JPanel(new MigLayout("insets 0, wrap 1, fillx", "[fill, grow]"));

	private Object current;
	private SwingWorker<?, ?> worker;

	MetadataPanel(RenameModel model, ListSelectionModel selection, Action editAction, Action changeAction) {
		super(new MigLayout("insets 16, fill", "[fill, grow]", "[]12[grow, fill]"));
		this.model = model;
		this.selection = selection;

		add(Modern.label("Metadata", 15f, Font.BOLD, false), "wrap");

		chips.setOpaque(false);
		facts.setOpaque(false);
		badges.setOpaque(false);
		parsed.setOpaque(false);
		content.setOpaque(false);

		overview.setEditable(false);
		overview.setFocusable(false);
		overview.setLineWrap(true);
		overview.setWrapStyleWord(true);
		overview.setOpaque(false);
		overview.setBorder(null);
		overview.setFont(Modern.font(12.5f, Font.PLAIN));
		overview.setRows(5);
		Modern.onThemeChange(overview, () -> {
			overview.setUI(new javax.swing.plaf.basic.BasicTextAreaUI());
			overview.setFont(Modern.font(12.5f, Font.PLAIN));
			overview.setForeground(NightTheme.getDimForeground());
			overview.setOpaque(false);
			overview.setBorder(null);
		});

		edit = Modern.button(editAction, Glyph.of(Glyph.Shape.PENCIL, 15, NightTheme::getForeground), Modern.Style.SECONDARY);
		change = Modern.button(changeAction, Glyph.of(Glyph.Shape.SEARCH, 15, NightTheme::getForeground), Modern.Style.SECONDARY);
		rating.setIcon(Glyph.of(Glyph.Shape.STAR, 15, new Color(0xFBBF24)));
		rating.setIconTextGap(6);

		content.add(cover, "h 180!, gapbottom 10");
		content.add(title);
		content.add(chips, "gaptop 2");
		content.add(rating, "gaptop 4, hidemode 3");
		content.add(overview, "gaptop 6, wmin 0, w 0:0:n, hidemode 3");
		content.add(badges, "gaptop 8, hidemode 3");
		content.add(facts, "gaptop 10");
		content.add(parsedTitle, "gaptop 16, hidemode 3");
		content.add(parsed, "gaptop 6, hidemode 3");
		content.add(change, "gaptop 14, h 36!");
		content.add(edit, "gaptop 6, h 36!");

		empty = createEmptyState();

		JPanel stack = new WidthTrackingPanel(new MigLayout("insets 0, fill, hidemode 3", "[0:0:n, fill, grow]", "[fill, grow]"));
		stack.setOpaque(false);
		stack.add(content, "cell 0 0, aligny top");
		stack.add(empty, "cell 0 0");
		javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(stack, javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		Modern.onThemeChange(scroll, () -> {
			scroll.setUI(new javax.swing.plaf.basic.BasicScrollPaneUI());
			scroll.setBorder(null);
			scroll.setOpaque(false);
			scroll.getViewport().setOpaque(false);
		});
		Modern.slim(scroll);
		add(scroll, "grow, hmin 0");

		selection.addListSelectionListener(evt -> {
			if (!evt.getValueIsAdjusting()) {
				update();
			}
		});
		model.matches().addListEventListener(evt -> javax.swing.SwingUtilities.invokeLater(this::update));

		update();
	}

	private JComponent createEmptyState() {
		JPanel panel = new JPanel(new MigLayout("insets 0, fill, wrap 1", "[center, grow]", "push[]10[]4[]push"));
		panel.setOpaque(false);
		JLabel icon = new JLabel(Glyph.of(Glyph.Shape.INFO, 30, NightTheme::getDimForeground));
		panel.add(icon);
		panel.add(Modern.label("No item selected", 13.5f, Font.BOLD, false));
		panel.add(Modern.label("Select a match to see its details.", 12f, Font.PLAIN, true));
		return panel;
	}

	private void update() {
		int index = selection.getMinSelectionIndex();
		Match<Object, File> match = index >= 0 && index < model.size() && model.hasComplement(index) ? model.getMatch(index) : null;
		Object info = match != null ? match.getValue() : null;
		File file = match != null ? match.getCandidate() : index >= 0 && index < model.size() ? model.getMatch(index).getCandidate() : null;

		if (info == current && info != null && index == currentIndex) {
			return;
		}
		current = info;
		currentIndex = index;

		if (worker != null) {
			worker.cancel(true);
			worker = null;
		}

		boolean hasInfo = info != null;
		content.setVisible(hasInfo || file != null);
		empty.setVisible(!hasInfo && file == null);
		edit.setVisible(hasInfo);
		if (!hasInfo && file == null) {
			revalidate();
			repaint();
			return;
		}

		cover.set(null, info);
		chips.removeAll();
		facts.removeAll();
		rating.setVisible(false);
		overview.setVisible(false);
		showParsed(file);

		if (!hasInfo) {
			title.setText("Not matched yet");
			chip("Unmatched");
			addFileFacts(file);
			revalidate();
			repaint();
			return;
		}

		if (info instanceof Episode) {
			Episode e = (Episode) info;
			title.setText(e.getSeriesName());
			chip("TV Show");
			chip(EpisodeFormat.SeasonEpisode.formatS00E00(e));
			if (e.getTitle() != null) {
				showOverview("“" + e.getTitle() + "”");
			}
			SeriesInfo series = e.getSeriesInfo();
			if (series != null) {
				showRating(series.getRating(), series.getRatingCount());
				if (series.getGenres() != null && !series.getGenres().isEmpty()) {
					fact(Glyph.Shape.TAG, "Genres", String.join(", ", series.getGenres()));
				}
				if (series.getNetwork() != null) {
					fact(Glyph.Shape.TV, "Network", series.getNetwork());
				}
				if (series.getRuntime() != null && series.getRuntime() > 0) {
					fact(Glyph.Shape.CLOCK, "Runtime", series.getRuntime() + " min");
				}
			}
			if (e.getAirdate() != null) {
				fact(Glyph.Shape.CLOCK, "Aired", e.getAirdate().toString());
			}
			loadSeriesPoster(e);
		} else if (info instanceof Movie) {
			Movie m = (Movie) info;
			title.setText(m.getName());
			chip("Movie");
			if (m.getYear() > 0) {
				chip(String.valueOf(m.getYear()));
			}
			loadMovieInfo(m, file);
		} else if (info instanceof AudioTrack) {
			AudioTrack t = (AudioTrack) info;
			title.setText(t.getTrackTitle() != null ? t.getTrackTitle() : t.toString());
			chip("Music");
			if (t.getArtist() != null) {
				fact(Glyph.Shape.USER, "Artist", t.getArtist());
			}
			if (t.getAlbum() != null) {
				fact(Glyph.Shape.MUSIC, "Album", t.getAlbum());
			}
		} else {
			title.setText(info instanceof File ? ((File) info).getName() : info.toString());
			chip("File");
		}

		String source = RenameListCellRenderer.getSource(info);
		if (source != null) {
			chip(source);
		}

		addFileFacts(file);

		revalidate();
		repaint();
	}

	/**
	 * Technical badges and how the file name was read (title, season, episode, quality, release group, ...).
	 */
	private void showParsed(File file) {
		badges.removeAll();
		parsed.removeAll();
		if (file == null) {
			badges.setVisible(false);
			parsedTitle.setVisible(false);
			parsed.setVisible(false);
			return;
		}
		FileNameInfo info = FileNameInfo.parse(file);
		for (String badge : info.getBadges()) {
			badges.add(new TechBadge(badge));
		}
		info.getFields().forEach((key, value) -> {
			JLabel k = Modern.label(key, 12f, Font.PLAIN, true);
			JLabel v = Modern.label(value, 12f, Font.PLAIN, false);
			v.setMinimumSize(new java.awt.Dimension(0, 0));
			parsed.add(k);
			parsed.add(v, "wmin 0");
		});
		badges.setVisible(badges.getComponentCount() > 0);
		parsedTitle.setVisible(parsed.getComponentCount() > 0);
		parsed.setVisible(parsed.getComponentCount() > 0);
	}

	/**
	 * New absolute path of the selected file once renamed, if its new name is ready.
	 */
	private File getDestination(int index, File file) {
		try {
			if (index < 0 || index >= model.names().size() || !model.hasComplement(index)) {
				return null;
			}
			RenameModel.FormattedFuture future = model.names().get(index);
			if (!future.isDone() || future.isCancelled()) {
				return null;
			}
			String name = future.get();
			String ext = getExtension(file);
			if (model.preserveExtension() && ext != null) {
				name = name + "." + ext.toLowerCase();
			}
			File destination = new File(name);
			return destination.isAbsolute() ? destination : new File(file.getParentFile(), name);
		} catch (Exception e) {
			return null;
		}
	}

	private static class TechBadge extends JLabel {

		private final Color tone;

		TechBadge(String text) {
			super(text);
			this.tone = RenameListCellRenderer.getBadgeColor(text);
			setFont(Modern.font(10.5f, Font.BOLD));
			setForeground(tone);
			setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8));
		}

		@Override
		protected void paintComponent(java.awt.Graphics g) {
			java.awt.Graphics2D g2d = Modern.smooth(g);
			g2d.setColor(Modern.alpha(tone, 36));
			g2d.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 9, 9));
			g2d.dispose();
			super.paintComponent(g);
		}
	}

	/**
	 * Episodes show the poster of their series (episodes have no poster of their own). Series matched with another database are looked up on TheMovieDB by name.
	 */
	private void loadSeriesPoster(Episode episode) {
		SwingWorker<Image, Void> task = new SwingWorker<Image, Void>() {

			@Override
			protected Image doInBackground() throws Exception {
				SeriesInfo info = episode.getSeriesInfo();
				Integer id = info != null && "TheMovieDB::TV".equals(info.getDatabase()) ? info.getId() : null;
				if (id == null) {
					List<SearchResult> results = WebServices.TheMovieDB_TV.search(episode.getSeriesName(), Locale.ENGLISH);
					if (results.isEmpty()) {
						return null;
					}
					id = results.get(0).getId();
				}
				URL url = WebServices.TheMovieDB_TV.getPoster(id);
				if (url == null || isCancelled()) {
					return null;
				}
				Image poster = posters.get(url);
				if (poster == null) {
					poster = ImageIO.read(url);
					if (poster != null) {
						posters.put(url, poster);
					}
				}
				return poster;
			}

			@Override
			protected void done() {
				if (isCancelled() || current != episode) {
					return;
				}
				try {
					Image poster = get();
					if (poster != null) {
						cover.set(poster, episode);
					}
				} catch (Exception e) {
					debug.log(Level.FINE, "Failed to load series poster: " + e.getMessage());
				}
			}
		};
		worker = task;
		task.execute();
	}

	private void loadMovieInfo(Movie movie, File file) {
		if (movie.getTmdbId() <= 0 && movie.getImdbId() <= 0) {
			return;
		}

		SwingWorker<Object[], Void> task = new SwingWorker<Object[], Void>() {

			@Override
			protected Object[] doInBackground() throws Exception {
				MovieInfo info = WebServices.TheMovieDB.getMovieInfo(movie, movie.getLanguage() != null ? movie.getLanguage() : Locale.ENGLISH, true);
				Image poster = null;
				URL url = info.getPoster();
				if (url != null) {
					poster = posters.get(url);
					if (poster == null && !isCancelled()) {
						poster = ImageIO.read(url);
						if (poster != null) {
							posters.put(url, poster);
						}
					}
				}
				return new Object[] { info, poster };
			}

			@Override
			protected void done() {
				if (isCancelled() || current != movie) {
					return;
				}
				try {
					Object[] result = get();
					MovieInfo info = (MovieInfo) result[0];
					cover.set((Image) result[1], movie);
					showRating(info.getRating(), info.getVotes());
					showOverview(info.getOverview());

					facts.removeAll();
					if (info.getDirector() != null) {
						fact(Glyph.Shape.USER, "Director", info.getDirector());
					}
					if (info.getGenres() != null && !info.getGenres().isEmpty()) {
						fact(Glyph.Shape.TAG, "Genres", String.join(", ", info.getGenres().subList(0, Math.min(3, info.getGenres().size()))));
					}
					if (info.getRuntime() != null && info.getRuntime() > 0) {
						fact(Glyph.Shape.CLOCK, "Runtime", String.format("%dh %02dm", info.getRuntime() / 60, info.getRuntime() % 60));
					}
					addFileFacts(file);
					revalidate();
					repaint();
				} catch (Exception e) {
					debug.log(Level.FINE, "Failed to load movie info: " + e.getMessage());
				}
			}
		};
		worker = task;
		task.execute();
	}

	private void addFileFacts(File file) {
		if (current instanceof Movie) {
			Movie m = (Movie) current;
			if (m.getTmdbId() > 0) {
				fact(Glyph.Shape.TAG, "TMDB ID", String.valueOf(m.getTmdbId()));
			}
			if (m.getImdbId() > 0) {
				fact(Glyph.Shape.TAG, "IMDb ID", String.format("tt%07d", m.getImdbId()));
			}
		} else if (current instanceof Episode) {
			Episode e = (Episode) current;
			if (e.getSeason() != null) {
				fact(Glyph.Shape.TV, "Season", String.valueOf(e.getSeason()));
			}
			if (e.getEpisode() != null) {
				fact(Glyph.Shape.TV, "Episode", String.valueOf(e.getEpisode()));
			}
			if (e.getSeriesInfo() != null && e.getSeriesInfo().getId() != null) {
				fact(Glyph.Shape.TAG, RenameListCellRenderer.getSource(e) + " ID", String.valueOf(e.getSeriesInfo().getId()));
			}
		}
		if (file != null) {
			if (file.isFile()) {
				fact(Glyph.Shape.FILE, "Size", formatSize(file.length()));
			}
			fact(Glyph.Shape.FOLDER, "Path", file.getParent());
			File destination = getDestination(currentIndex, file);
			if (destination != null) {
				fact(Glyph.Shape.ARROW_RIGHT, "New path", destination.getPath());
			}
		}
	}

	private void showRating(Double value, Integer votes) {
		if (value == null || value <= 0) {
			return;
		}
		String text = String.format(Locale.ROOT, "%.1f", value);
		if (votes != null && votes > 0) {
			text += votes >= 1000 ? String.format(Locale.ROOT, "  (%dK)", votes / 1000) : String.format("  (%d)", votes);
		}
		rating.setText(text);
		rating.setVisible(true);
	}

	private void showOverview(String text) {
		if (text == null || text.trim().isEmpty()) {
			return;
		}
		overview.setText(text.length() > 240 ? text.substring(0, 237).trim() + "…" : text);
		overview.setCaretPosition(0);
		overview.setVisible(true);
	}

	private void chip(String text) {
		if (text != null && text.length() > 0) {
			chips.add(new Modern.Badge(text));
		}
	}

	private void fact(Glyph.Shape shape, String key, String value) {
		JLabel k = Modern.label(key, 12f, Font.PLAIN, true);
		k.setIcon(Glyph.of(shape, 14, NightTheme::getDimForeground));
		k.setIconTextGap(8);
		JLabel v = Modern.label(value, 12f, Font.PLAIN, false);
		v.setMinimumSize(new java.awt.Dimension(0, 0));
		v.setToolTipText(value);
		facts.add(k);
		facts.add(v, "wmin 0");
	}

	/**
	 * Scrolls vertically only, so wrapped text and ellipsized labels follow the panel width.
	 */
	private static class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {

		WidthTrackingPanel(java.awt.LayoutManager layout) {
			super(layout);
		}

		@Override
		public java.awt.Dimension getPreferredScrollableViewportSize() {
			return getPreferredSize();
		}

		@Override
		public int getScrollableUnitIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
			return 16;
		}

		@Override
		public int getScrollableBlockIncrement(java.awt.Rectangle visibleRect, int orientation, int direction) {
			return visibleRect.height;
		}

		@Override
		public boolean getScrollableTracksViewportWidth() {
			return true;
		}

		@Override
		public boolean getScrollableTracksViewportHeight() {
			return getParent() != null && getParent().getHeight() > getPreferredSize().height;
		}
	}

	/**
	 * Poster, or a tinted placeholder with the media type glyph.
	 */
	private static class Cover extends JComponent {

		private Image image;
		private Object info;

		void set(Image image, Object info) {
			this.image = image;
			this.info = info;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = Modern.smooth(g);
			g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
			int w = getWidth();
			int h = getHeight();
			RoundRectangle2D shape = new RoundRectangle2D.Float(0, 0, w, h, 14, 14);

			if (image != null) {
				// center the poster on a blurred-looking backdrop of its own dominant tone
				int iw = image.getWidth(null);
				int ih = image.getHeight(null);
				double scale = Math.min((double) h / ih, (double) w / iw);
				int dw = (int) (iw * scale);
				int dh = (int) (ih * scale);
				g2d.setClip(shape);
				g2d.setColor(averageColor(image));
				g2d.fill(shape);
				g2d.drawImage(image, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
			} else {
				Color tint = info instanceof Episode ? new Color(0x14B8A6) : info instanceof AudioTrack ? new Color(0xEC4899) : NightTheme.getAccent();
				g2d.setPaint(new GradientPaint(0, 0, Modern.alpha(tint, 70), w, h, Modern.alpha(NightTheme.getSecondaryAccent(), 40)));
				g2d.fill(shape);
				Glyph.Shape glyph = info instanceof Episode ? Glyph.Shape.TV : info instanceof AudioTrack ? Glyph.Shape.MUSIC : info instanceof Movie ? Glyph.Shape.FILM : Glyph.Shape.FILE;
				Glyph.of(glyph, 44, Modern.alpha(Color.WHITE, 200)).paintIcon(this, g2d, (w - 44) / 2, (h - 44) / 2);
			}
			g2d.dispose();
		}

		private static Color averageColor(Image image) {
			BufferedImage one = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = one.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(image, 0, 0, 1, 1, null);
			g.dispose();
			return Modern.mix(new Color(one.getRGB(0, 0)), Color.BLACK, 0.45f);
		}
	}

}
