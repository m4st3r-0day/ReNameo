package net.renameo.ui.rename;

import static net.renameo.Logging.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;

import net.miginfocom.swing.MigLayout;
import net.renameo.WebServices;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.web.Artwork;
import net.renameo.web.ArtworkProvider;
import net.renameo.web.Episode;
import net.renameo.web.Movie;
import net.renameo.web.SeriesInfo;

/**
 * Posters of the titles whose artwork will be downloaded after the rename, so they can be checked (and left out) first.
 */
class ArtworkStrip extends JPanel {

	private static final int THUMB_WIDTH = 72;
	private static final int THUMB_HEIGHT = 108;

	private static class Title {

		final String name;
		final ArtworkProvider provider;
		final int id;
		final JCheckBox selected = new JCheckBox();

		Title(String name, ArtworkProvider provider, int id) {
			this.name = name;
			this.provider = provider;
			this.id = id;
		}
	}

	private final Map<String, Title> titles = new LinkedHashMap<String, Title>();

	ArtworkStrip(List<PreviewDialog.Row> rows) {
		super(new MigLayout("insets 0, fillx", "[fill, grow]", "[]6[]"));
		setOpaque(false);

		for (PreviewDialog.Row row : rows) {
			String key = key(row.info);
			if (key != null && !titles.containsKey(key)) {
				titles.put(key, title(row.info));
			}
		}
		if (titles.isEmpty()) {
			return;
		}

		add(Modern.label("Poster & fanart to download (untick to skip)", 12f, Font.BOLD, true), "wrap");

		JPanel strip = new JPanel(new MigLayout("insets 4 0 4 0, gap 14", "", "[top]"));
		strip.setOpaque(false);
		for (Title t : titles.values()) {
			strip.add(tile(t));
		}

		JScrollPane scroll = new JScrollPane(strip, JScrollPane.VERTICAL_SCROLLBAR_NEVER, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
		Modern.slim(scroll);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		scroll.setViewportBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		add(scroll, "growx, h 150!");
	}

	boolean isEmpty() {
		return titles.isEmpty();
	}

	boolean isSelected(PreviewDialog.Row row) {
		Title t = titles.get(key(row.info));
		return t != null && t.selected.isSelected();
	}

	private JComponent tile(Title t) {
		JPanel tile = new JPanel(new MigLayout("insets 0, gap 0, wrap 1", "[" + THUMB_WIDTH + "!]", "[]4[]"));
		tile.setOpaque(false);

		JLabel poster = new JLabel(new ImageIcon(placeholder()));
		tile.add(poster);

		t.selected.setSelected(true);
		t.selected.setOpaque(false);
		t.selected.setText(t.name);
		t.selected.setToolTipText(t.name);
		t.selected.setFont(Modern.font(11f, Font.PLAIN));
		t.selected.setForeground(NightTheme.getForeground());
		t.selected.setHorizontalAlignment(SwingConstants.LEFT);
		tile.add(t.selected, "w " + THUMB_WIDTH + "!");

		newSwingWorker(() -> loadPoster(t), image -> {
			if (image != null) {
				poster.setIcon(new ImageIcon(image));
			}
		}, error -> debug.fine("Poster preview: " + error)).execute();

		return tile;
	}

	private static Image loadPoster(Title t) throws Exception {
		List<Artwork> posters = t.provider.getArtwork(t.id, "posters", Locale.getDefault());
		if (posters.isEmpty() || posters.get(0).getUrl() == null) {
			return null;
		}

		// TheMovieDB serves smaller renditions of every image
		URL url = new URL(posters.get(0).getUrl().toString().replace("/original/", "/w154/"));
		BufferedImage image = ImageIO.read(url);
		return image == null ? null : scale(image);
	}

	private static Image scale(BufferedImage image) {
		BufferedImage thumb = new BufferedImage(THUMB_WIDTH, THUMB_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = thumb.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, THUMB_WIDTH, THUMB_HEIGHT, 10, 10));
		g.drawImage(image, 0, 0, THUMB_WIDTH, THUMB_HEIGHT, null);
		g.dispose();
		return thumb;
	}

	private static Image placeholder() {
		BufferedImage image = new BufferedImage(THUMB_WIDTH, THUMB_HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics g = image.createGraphics();
		((Graphics2D) g).setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(NightTheme.getCardBackground());
		g.fillRoundRect(0, 0, THUMB_WIDTH, THUMB_HEIGHT, 10, 10);
		g.setColor(new Color(128, 128, 128, 60));
		g.drawRoundRect(0, 0, THUMB_WIDTH - 1, THUMB_HEIGHT - 1, 10, 10);
		g.dispose();
		return image;
	}

	/**
	 * Artwork is downloaded from TheMovieDB, so only titles that have a TheMovieDB id can get it.
	 */
	private static String key(Object info) {
		if (info instanceof Movie && ((Movie) info).getTmdbId() > 0) {
			return "movie:" + ((Movie) info).getTmdbId();
		}
		if (info instanceof Episode) {
			SeriesInfo series = ((Episode) info).getSeriesInfo();
			if (series != null && series.getId() != null && series.getDatabase() != null && series.getDatabase().toLowerCase().contains("themoviedb")) {
				return "tv:" + series.getId();
			}
		}
		return null;
	}

	private static Title title(Object info) {
		if (info instanceof Movie) {
			Movie m = (Movie) info;
			return new Title(m.getNameWithYear(), WebServices.TheMovieDB, m.getTmdbId());
		}
		Episode e = (Episode) info;
		return new Title(e.getSeriesName(), WebServices.TheMovieDB_TV, e.getSeriesInfo().getId());
	}

	@Override
	public Dimension getMaximumSize() {
		return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
	}

}
