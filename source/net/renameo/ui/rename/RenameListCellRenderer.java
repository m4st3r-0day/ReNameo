package net.renameo.ui.rename;

import static net.renameo.MediaTypes.*;
import static net.renameo.similarity.EpisodeMetrics.*;
import static net.renameo.util.FileUtilities.*;
import static net.renameo.util.ui.SwingUI.*;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import net.renameo.media.FileNameInfo;
import net.renameo.similarity.Match;
import net.renameo.similarity.MetricCascade;
import net.renameo.similarity.MetricMin;
import net.renameo.similarity.SimilarityMetric;
import net.renameo.ui.rename.RenameModel.FormattedFuture;
import net.renameo.util.TextDiff;
import net.renameo.util.ui.Glyph;
import net.renameo.util.ui.Modern;
import net.renameo.util.ui.NightTheme;
import net.renameo.web.AudioTrack;
import net.renameo.web.Episode;
import net.renameo.web.EpisodeFormat;
import net.renameo.web.Movie;

/**
 * Two line media row: type tile, primary name, secondary details and a trailing status chip (match confidence, conflicts, progress).
 */
class RenameListCellRenderer extends JComponent implements ListCellRenderer<Object> {

	private static final Color TV = new Color(0x14B8A6);
	private static final Color MUSIC = new Color(0xEC4899);

	private final RenameModel renameModel;
	private final String home;

	// row state
	private int index;
	private boolean selected;
	private boolean matched;
	private String title;
	private String detail;
	private List<String> chips = new ArrayList<String>();
	private Glyph.Shape shape;
	private Color tint;
	private String status;
	private Color statusColor;
	private boolean busy;
	private List<TextDiff.Segment> diff;
	private List<String> badges = new ArrayList<String>();

	private final Map<File, FileNameInfo> parsed = new WeakHashMap<File, FileNameInfo>();

	/**
	 * Confidence thresholds shared with the preview: below LIKELY a match needs a manual confirmation.
	 */
	static final float EXACT = 0.95f;
	static final float LIKELY = 0.7f;

	public RenameListCellRenderer(RenameModel renameModel, File home) {
		this.renameModel = renameModel;
		this.home = home.getPath();
		setOpaque(false);
	}

	@Override
	public Dimension getPreferredSize() {
		return new Dimension(200, NightTheme.isCompact() ? 40 : 56);
	}

	@Override
	public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
		this.index = index;
		this.selected = isSelected;
		this.matched = renameModel.hasComplement(index);
		this.chips = new ArrayList<String>(2);
		this.status = null;
		this.statusColor = null;
		this.busy = false;
		this.detail = null;
		this.diff = null;
		this.badges = new ArrayList<String>(4);

		Match<Object, File> match = index < renameModel.size() ? renameModel.getMatch(index) : null;
		Object info = match != null ? match.getValue() : null;
		File file = match != null ? match.getCandidate() : null;

		if (value instanceof File) {
			configureFile((File) value, info);
		} else if (value instanceof FormattedFuture) {
			configureName((FormattedFuture) value, info, file);
		} else {
			title = String.valueOf(value);
			shape = Glyph.Shape.FILE;
			tint = NightTheme.getDimForeground();
		}

		if (diff == null) {
			setToolTipText(file != null ? file.getPath() : null);
		}
		return this;
	}

	private void configureFile(File file, Object info) {
		title = renameModel.preserveExtension() ? file.getName() : formatPath(file);

		List<String> parts = new ArrayList<String>(3);
		if (file.isFile()) {
			parts.add(formatSize(file.length()));
		}
		String ext = getExtension(file);
		if (ext != null) {
			parts.add(ext.toUpperCase());
		}
		File parent = file.getParentFile();
		if (parent != null) {
			parts.add(formatPath(parent) + "/");
		}
		detail = String.join("  •  ", parts);
		badges = parsed.computeIfAbsent(file, FileNameInfo::parse).getBadges();

		setType(info, file);
	}

	private void configureName(FormattedFuture future, Object info, File file) {
		if (future.isDone() && !future.isCancelled()) {
			String name = future.toString();
			if (!renameModel.preserveExtension() && matched && file != null) {
				File path = resolveAbsolutePath(file.getParentFile(), name, null);
				title = path.getName();
				detail = formatPath(path.getParentFile()) + "/";
			} else {
				File path = new File(name);
				title = path.getName();
				detail = path.getParent() != null ? path.getParent() + "/" : null;
			}
		} else {
			title = future.preview();
			busy = true;
			status = future.getState() == javax.swing.SwingWorker.StateValue.STARTED ? "Formatting" : "Queued";
			statusColor = NightTheme.getDimForeground();
		}

		if (info instanceof Episode) {
			Episode e = (Episode) info;
			chips.add("TV Show");
			String sxe = EpisodeFormat.SeasonEpisode.formatS00E00(e);
			if (sxe != null && sxe.length() > 0) {
				chips.add(sxe);
			}
		} else if (info instanceof Movie) {
			chips.add("Movie");
			if (((Movie) info).getYear() > 0) {
				chips.add(String.valueOf(((Movie) info).getYear()));
			}
		} else if (info instanceof AudioTrack) {
			chips.add("Music");
		} else if (info instanceof File) {
			chips.add("File");
		}

		String source = getSource(info);
		if (source != null) {
			chips.add(source);
		}

		// what changes compared to the current file name
		if (!busy && matched && file != null) {
			diff = TextDiff.diff(getNameWithoutExtension(file.getName()), title);
			setToolTipText(toDiffHtml(diff));
		}

		setType(info, file);

		if (!busy && matched && file != null) {
			File to = resolveAbsolutePath(file.getParentFile(), future.toString(), renameModel.preserveExtension() ? getExtension(file) : null);
			if (equalsCaseSensitive(file, to)) {
				status = "Unchanged";
				statusColor = NightTheme.getDimForeground();
			} else if (to.exists() && !to.equals(file)) {
				status = "Exists";
				statusColor = NightTheme.getDanger();
			} else {
				float p = getMatchProbablity(future.getMatch());
				status = getConfidenceLabel(p);
				statusColor = confidenceColor(p);
			}
		}
	}

	private void setType(Object info, File file) {
		if (info instanceof Episode) {
			shape = Glyph.Shape.TV;
			tint = TV;
		} else if (info instanceof Movie) {
			shape = Glyph.Shape.FILM;
			tint = NightTheme.getAccent();
		} else if (info instanceof AudioTrack || (file != null && AUDIO_FILES.accept(file))) {
			shape = Glyph.Shape.MUSIC;
			tint = MUSIC;
		} else if (file != null && file.isDirectory()) {
			shape = Glyph.Shape.FOLDER;
			tint = NightTheme.getWarning();
		} else if (file != null && VIDEO_FILES.accept(file)) {
			shape = Glyph.Shape.FILM;
			tint = NightTheme.getSecondaryAccent();
		} else {
			shape = Glyph.Shape.FILE;
			tint = NightTheme.getDimForeground();
		}
	}

	static Color confidenceColor(float p) {
		if (p >= EXACT) {
			return NightTheme.getSuccess();
		}
		if (p >= LIKELY) {
			return NightTheme.getAccent();
		}
		return NightTheme.getWarning();
	}

	/**
	 * "Exact", "Likely · 82%" or "Review · 45%".
	 */
	static String getConfidenceLabel(float p) {
		if (p >= EXACT) {
			return "Exact";
		}
		return String.format("%s · %d%%", p >= LIKELY ? "Likely" : "Review", Math.round(p * 100));
	}

	/**
	 * Where the match came from, e.g. "TMDB", "TMDB TV", "TVmaze", "AniDB".
	 */
	static String getSource(Object info) {
		if (info instanceof Movie) {
			return ((Movie) info).getTmdbId() > 0 ? "TMDB" : ((Movie) info).getImdbId() > 0 ? "IMDb" : null;
		}
		if (info instanceof Episode && ((Episode) info).getSeriesInfo() != null) {
			String db = ((Episode) info).getSeriesInfo().getDatabase();
			if (db == null) {
				return null;
			}
			return db.equals("TheMovieDB::TV") ? "TMDB TV" : db.equals("TheMovieDB") ? "TMDB" : db;
		}
		if (info instanceof AudioTrack) {
			return "Music tags";
		}
		return null;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = Modern.smooth(g);
		int w = getWidth();
		int h = getHeight();
		boolean compact = h < 48;

		if (selected) {
			g2d.setColor(Modern.alpha(NightTheme.getAccent(), NightTheme.isNightMode() ? 48 : 34));
			g2d.fill(new RoundRectangle2D.Float(4, 2, w - 8, h - 4, 10, 10));
		} else {
			g2d.setColor(NightTheme.getBorder());
			g2d.fillRect(12, h - 1, w - 24, 1);
		}

		if (!matched && !selected) {
			g2d.setComposite(AlphaComposite.SrcOver.derive(0.55f));
		}

		// index column
		Font small = Modern.font(11.5f, Font.PLAIN);
		g2d.setFont(small);
		g2d.setColor(NightTheme.getDimForeground());
		String number = String.valueOf(index + 1);
		FontMetrics sfm = g2d.getFontMetrics();
		g2d.drawString(number, 30 - sfm.stringWidth(number), (h - sfm.getHeight()) / 2 + sfm.getAscent());

		// type tile
		int tile = compact ? 26 : 36;
		int tx = 40;
		int ty = (h - tile) / 2;
		g2d.setColor(Modern.alpha(tint, 38));
		g2d.fill(new RoundRectangle2D.Float(tx, ty, tile, tile, 10, 10));
		int glyph = compact ? 15 : 18;
		Glyph.of(shape, glyph, tint).paintIcon(this, g2d, tx + (tile - glyph) / 2, ty + (tile - glyph) / 2);

		// trailing status chip
		int right = w - 12;
		Font chipFont = Modern.font(11f, Font.BOLD);
		if (status != null) {
			int cw = Modern.chipWidth(g2d, status, chipFont);
			int chipH = 22;
			right -= cw;
			Modern.paintChip(g2d, status, right, (h - chipH) / 2, chipH, chipFont, Modern.alpha(statusColor, 40), statusColor);
			right -= 10;
		}

		// text block
		int textX = tx + tile + 12;
		Font titleFont = Modern.font(13f, Font.BOLD);
		FontMetrics tfm = g2d.getFontMetrics(titleFont);
		boolean twoLines = !compact && (detail != null || !chips.isEmpty());
		int titleY = twoLines ? h / 2 - 3 : (h - tfm.getHeight()) / 2 + tfm.getAscent();

		g2d.setFont(titleFont);
		if (diff != null && !selected) {
			paintDiff(g2d, titleFont, textX, titleY, right - textX);
		} else {
			g2d.setColor(NightTheme.getForeground());
			g2d.drawString(ellipsize(title, tfm, right - textX), textX, titleY);
		}

		if (twoLines) {
			int y = h / 2 + 4;
			int x = textX;
			Font chipSmall = Modern.font(10.5f, Font.PLAIN);
			for (String chip : chips) {
				int cw = Modern.chipWidth(g2d, chip, chipSmall);
				if (x + cw > right) {
					break;
				}
				Modern.paintChip(g2d, chip, x, y, 18, chipSmall, NightTheme.getControlColor(), NightTheme.getDimForeground());
				x += cw + 6;
			}
			Font badgeFont = Modern.font(10f, Font.BOLD);
			for (String badge : badges) {
				int cw = Modern.chipWidth(g2d, badge, badgeFont);
				if (x + cw > right - 60) {
					break;
				}
				Color tone = getBadgeColor(badge);
				Modern.paintChip(g2d, badge, x, y, 18, badgeFont, Modern.alpha(tone, 36), tone);
				x += cw + 5;
			}
			if (!badges.isEmpty()) {
				x += 4;
			}
			if (detail != null && x < right) {
				g2d.setFont(small);
				g2d.setColor(NightTheme.getDimForeground());
				g2d.drawString(ellipsize(detail, sfm, right - x - (chips.isEmpty() ? 0 : 4)), x + (chips.isEmpty() ? 0 : 4), y + 9 + sfm.getAscent() / 2 - 1);
			}
		}

		g2d.dispose();
	}

	/**
	 * New name with unchanged words in grey, added words in green and removed words struck through in red.
	 */
	private void paintDiff(Graphics2D g2d, Font font, int x, int baseline, int width) {
		FontMetrics fm = g2d.getFontMetrics(font);
		int limit = x + width;
		Color same = Modern.mix(NightTheme.getForeground(), NightTheme.getDimForeground(), 0.55f);

		// removed words are only shown when the whole diff fits, long release names would otherwise bury the new name
		int full = diff.stream().mapToInt(it -> fm.stringWidth(it.text)).sum();
		boolean showDeleted = full <= width;

		for (TextDiff.Segment segment : diff) {
			if (segment.kind == TextDiff.Kind.DELETE && !showDeleted) {
				continue;
			}
			if (x >= limit) {
				break;
			}
			String text = ellipsize(segment.text, fm, limit - x);
			int w = fm.stringWidth(text);
			switch (segment.kind) {
			case INSERT:
				g2d.setColor(NightTheme.getSuccess());
				break;
			case DELETE:
				g2d.setColor(Modern.alpha(NightTheme.getDanger(), 200));
				break;
			default:
				g2d.setColor(same);
			}
			g2d.drawString(text, x, baseline);
			if (segment.kind == TextDiff.Kind.DELETE) {
				int mid = baseline - fm.getAscent() / 3;
				g2d.fillRect(x, mid, w, 1);
			}
			x += w;
		}
	}

	private static String toDiffHtml(List<TextDiff.Segment> diff) {
		StringBuilder html = new StringBuilder("<html><nobr>");
		for (TextDiff.Segment s : diff) {
			String text = escapeHTML(s.text);
			switch (s.kind) {
			case INSERT:
				html.append("<b style='color:#22C55E'>").append(text).append("</b>");
				break;
			case DELETE:
				html.append("<s style='color:#EF4444'>").append(text).append("</s>");
				break;
			default:
				html.append("<span style='color:#8B93A7'>").append(text).append("</span>");
			}
		}
		return html.append("</nobr></html>").toString();
	}

	static Color getBadgeColor(String badge) {
		switch (badge) {
		case "4K":
		case "DV":
		case "DV HDR":
		case "HDR":
		case "HDR10+":
			return new Color(0xF59E0B);
		case "HEVC":
		case "AVC":
		case "AV1":
		case "1080p":
		case "720p":
			return NightTheme.getAccent();
		case "SRT":
		case "ASS":
		case "SUB":
			return new Color(0x14B8A6);
		default:
			return badge.matches("[A-Z]{3}|MULTI") ? NightTheme.getSecondaryAccent() : new Color(0xEC4899);
		}
	}

	private static String ellipsize(String text, FontMetrics fm, int width) {
		if (text == null) {
			return "";
		}
		if (fm.stringWidth(text) <= width) {
			return text;
		}
		String dots = "…";
		int end = text.length();
		while (end > 0 && fm.stringWidth(text.substring(0, end)) + fm.stringWidth(dots) > width) {
			end--;
		}
		return text.substring(0, end) + dots;
	}

	protected String formatPath(File file) {
		if (file == null) {
			return "";
		}
		if (file.getPath().startsWith(home)) {
			return "~" + normalizePathSeparators(file.getPath().substring(home.length()));
		}
		return normalizePathSeparators(file.getPath());
	}

	protected File resolveAbsolutePath(File targetDir, String path, String extension) {
		File f = new File(extension == null || extension.isEmpty() ? path : String.format("%s.%s", path, extension));
		if (!f.isAbsolute()) {
			f = new File(targetDir, f.getPath()); // resolve path against target folder
		}
		return f.getAbsoluteFile();
	}

	static float getMatchProbablity(Match<Object, File> match) {
		if (match.getValue() == null || match.getCandidate() == null) {
			return 1; // assume match is ok
		}

		if (match.getValue() instanceof Episode) {
			float f = verificationMetric().getSimilarity(match.getValue(), match.getCandidate());
			return (f + 1) / 2; // normalize -1..1 to 0..1
		}

		SimilarityMetric fsm = new MetricCascade(new MetricMin(FileSize, 0), FileName, EpisodeIdentifier);
		float f = fsm.getSimilarity(match.getValue(), match.getCandidate());
		if (f != 0) {
			return (Math.max(f, 0)); // normalize -1..1 and boost by 0.25 (because file <-> file matches are not necessarily about Episodes)
		}

		return 1; // assume match is OK
	}

}
