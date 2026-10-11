package net.renameo.media;

import static net.renameo.MediaTypes.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.renameo.WebServices;
import net.renameo.similarity.SeasonEpisodeMatcher.SxE;
import net.renameo.web.Episode;
import net.renameo.web.SearchResult;
import net.renameo.web.SimpleDate;
import net.renameo.web.SortOrder;

/**
 * What a media library holds: movies and series with their episodes, size, resolution, subtitles and duplicates. Read from file and folder names only, so it is fast
 * even on a NAS; missing episodes are looked up on TheMovieDB on request.
 */
public final class LibraryStats {

	public enum Kind {
		MOVIE, SERIES
	}

	public static final String[] RESOLUTIONS = { "4K", "1080p", "720p", "SD", "Unknown" };

	public static class Title {

		public final Kind kind;
		public final String name;
		public final File folder;

		public long size;
		public int files;
		public int withoutSubtitles;
		public int duplicates;
		public final Map<String, Integer> resolutions = new HashMap<String, Integer>();
		public final Set<String> episodes = new TreeSet<String>();
		public final Set<Integer> seasons = new TreeSet<Integer>();

		/** aired episodes that are not there, e.g. "S01E03 Title", or null if not checked yet */
		public List<String> missing;

		Title(Kind kind, String name, File folder) {
			this.kind = kind;
			this.name = name;
			this.folder = folder;
		}

		/**
		 * The resolution most of the files have.
		 */
		public String getResolution() {
			return resolutions.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("Unknown");
		}
	}

	public static class Result {

		public final File root;
		public final List<Title> titles = new ArrayList<Title>();
		public final Map<String, Integer> resolutions = new LinkedHashMap<String, Integer>();
		public final Map<String, Long> resolutionSizes = new LinkedHashMap<String, Long>();
		public long size;
		public int extras;

		Result(File root) {
			this.root = root;
			for (String r : RESOLUTIONS) {
				resolutions.put(r, 0);
				resolutionSizes.put(r, 0L);
			}
		}

		public long count(Kind kind) {
			return titles.stream().filter(t -> t.kind == kind).count();
		}

		public int getEpisodes() {
			return titles.stream().filter(t -> t.kind == Kind.SERIES).mapToInt(t -> t.episodes.size()).sum();
		}

		public int getWithoutSubtitles() {
			return titles.stream().mapToInt(t -> t.withoutSubtitles).sum();
		}

		public int getDuplicates() {
			return titles.stream().mapToInt(t -> t.duplicates).sum();
		}

		/**
		 * Missing episodes of the series checked so far, or -1 if none was checked.
		 */
		public int getMissing() {
			return titles.stream().anyMatch(t -> t.missing != null) ? titles.stream().filter(t -> t.missing != null).mapToInt(t -> t.missing.size()).sum() : -1;
		}
	}

	private LibraryStats() {
		throw new UnsupportedOperationException();
	}

	private static final Pattern SEASON_FOLDER = Pattern.compile("(?i)(season|stagione|staffel|saison|temporada|series)[ ._-]*\\d{1,4}|s\\d{1,4}|specials?");
	private static final Pattern SAMPLE = Pattern.compile("(?i)(^|[ ._\\-(\\[])sample([ ._\\-)\\]]|$)");
	private static final Pattern PART = Pattern.compile("(?i)(cd|part|disc|disk|pt)[ ._-]*\\d");
	private static final Pattern MOVIE = Pattern.compile("^(.+?)[ ._(\\[]+((?:19|20)\\d{2})(?![0-9p])");
	private static final Pattern SERIES_FROM_FILE = Pattern.compile("(?i)^(.+?)[ ._-]+(?:s\\d{1,4}[ ._-]?e\\d|\\d{1,2}x\\d{2})");
	private static final Pattern RELEASE_TAIL = Pattern.compile("(?i)[ ._\\-\\[(]+(s\\d{1,4}(e\\d+)?|season[ ._-]?\\d+|stagione[ ._-]?\\d+|complete|\\d{3,4}[pi]|2160|4k|uhd|web[ ._-]?dl|webrip|bluray|hdtv|x26[45]|h[ ._]?26[45]|hevc|ita|eng|multi)\\b.*$");
	private static final Pattern YEAR = Pattern.compile("\\(?((?:19|20)\\d{2})\\)?");

	/**
	 * Read the library below the given folder.
	 *
	 * @param progress
	 *            number of video files seen so far and the current folder
	 */
	public static Result scan(File root, BooleanSupplier cancelled, BiConsumer<Integer, String> progress) throws IOException {
		List<File> videos = new ArrayList<File>();
		Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (cancelled.getAsBoolean()) {
					throw new CancellationException();
				}
				String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
				if (!dir.equals(root.toPath()) && (name.startsWith(".") || name.startsWith("@") || name.equals("#recycle") || name.equals("$RECYCLE.BIN"))) {
					return FileVisitResult.SKIP_SUBTREE; // hidden, trash and NAS system folders
				}
				progress.accept(videos.size(), name);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				File f = file.toFile();
				if (attrs.isRegularFile() && VIDEO_FILES.accept(f)) {
					videos.add(f);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException e) {
				return FileVisitResult.CONTINUE; // e.g. no permission
			}
		});

		Result result = new Result(root);
		Map<String, Title> titles = new LinkedHashMap<String, Title>();
		Map<Title, Map<String, Integer>> copies = new HashMap<Title, Map<String, Integer>>();
		Map<File, String[]> listings = new HashMap<File, String[]>();

		for (File f : videos) {
			if (isSample(f)) {
				continue;
			}
			long size = f.length();
			result.size += size;
			String resolution = getResolution(f.getName());
			result.resolutions.merge(resolution, 1, Integer::sum);
			result.resolutionSizes.merge(resolution, size, Long::sum);

			if (ExtraType.detect(f) != null) {
				result.extras++;
				continue;
			}

			List<SxE> sxe = MediaDetection.parseEpisodeNumber(f, true);
			Title title;
			String copy;
			if (sxe != null && !sxe.isEmpty()) {
				File folder = getSeriesFolder(f, root);
				String name = folder != null ? cleanName(folder.getName()) : seriesNameFromFile(f.getName());
				title = titles.computeIfAbsent("series:" + key(name), k -> new Title(Kind.SERIES, name, folder));
				StringBuilder episodes = new StringBuilder();
				for (SxE it : sxe) {
					int season = it.season < 0 ? 1 : it.season;
					String ep = String.format("S%02dE%02d", season, it.episode);
					title.episodes.add(ep);
					title.seasons.add(season);
					episodes.append(ep);
				}
				copy = episodes.toString();
			} else {
				File folder = f.getParentFile().equals(root) ? null : f.getParentFile();
				String name = movieName(folder != null && MOVIE.matcher(folder.getName()).find() ? folder.getName() : f.getName());
				title = titles.computeIfAbsent("movie:" + key(name), k -> new Title(Kind.MOVIE, name, folder != null ? folder : f));
				// CD1 / CD2 are parts of one movie, not copies
				copy = PART.matcher(f.getName()).find() ? f.getName() : "movie";
			}

			title.files++;
			title.size += size;
			title.resolutions.merge(resolution, 1, Integer::sum);
			if (!hasSubtitles(f, listings)) {
				title.withoutSubtitles++;
			}
			if (copies.computeIfAbsent(title, t -> new HashMap<String, Integer>()).merge(copy, 1, Integer::sum) > 1) {
				title.duplicates++;
			}
		}

		result.titles.addAll(titles.values());
		result.titles.sort(Comparator.comparing((Title t) -> t.name, String.CASE_INSENSITIVE_ORDER));
		return result;
	}

	/**
	 * Sample clips that come with releases: "sample" in the name or a Sample folder.
	 */
	public static boolean isSample(File f) {
		return SAMPLE.matcher(f.getName()).find() && f.length() < 500L * 1024 * 1024 || f.getParentFile().getName().matches("(?i)samples?");
	}

	static String getResolution(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.matches(".*(2160p|4k|uhd).*")) {
			return "4K";
		}
		if (n.matches(".*1080[pi].*")) {
			return "1080p";
		}
		if (n.matches(".*720p.*")) {
			return "720p";
		}
		if (n.matches(".*(480p|576p|dvdrip|\\bsd\\b|xvid).*")) {
			return "SD";
		}
		return "Unknown";
	}

	/**
	 * The folder of the series: the parent, or the one above a season folder. Null if the episode lies directly in the scanned folder (the name then comes from the file).
	 */
	public static File getSeriesFolder(File episode, File root) {
		File parent = episode.getParentFile();
		if (parent.equals(root)) {
			return null;
		}
		// Series/Season 1/episode: the scanned folder may be the series folder itself
		if (SEASON_FOLDER.matcher(parent.getName()).matches()) {
			return parent.getParentFile();
		}
		return parent;
	}

	/**
	 * "Neagley.S01.1080p.AMZN.WEB-DL-TBK" => "Neagley", "Neagley (2026)" stays.
	 */
	public static String cleanName(String folder) {
		String name = RELEASE_TAIL.matcher(folder).replaceAll("");
		Matcher year = Pattern.compile("[ ._(\\[]+((?:19|20)\\d{2})[)\\]]?$").matcher(name);
		String y = null;
		if (year.find()) {
			y = year.group(1);
			name = name.substring(0, year.start());
		}
		name = name.replaceAll("[._]+", " ").replaceAll("\\s+", " ").trim();
		return y == null ? name : name + " (" + y + ")";
	}

	public static String seriesNameFromFile(String file) {
		Matcher m = SERIES_FROM_FILE.matcher(file);
		return m.find() ? cleanName(m.group(1)) : cleanName(file.replaceFirst("\\.[^.]+$", ""));
	}

	/**
	 * "Avatar.2009.1080p.mkv" => "Avatar (2009)".
	 */
	static String movieName(String name) {
		Matcher m = MOVIE.matcher(name);
		if (m.find()) {
			return m.group(1).replaceAll("[._]+", " ").replaceAll("\\s+", " ").trim() + " (" + m.group(2) + ")";
		}
		return cleanName(name.replaceFirst("\\.[a-zA-Z0-9]{2,4}$", ""));
	}

	/**
	 * Same title despite spelling: "Neagley", "neagley" and "Neagley (2026)" are one series.
	 */
	static String key(String name) {
		return LibraryLayout.key(YEAR.matcher(name).replaceAll(" "));
	}

	private static boolean hasSubtitles(File video, Map<File, String[]> listings) {
		File dir = video.getParentFile();
		String base = video.getName().replaceFirst("\\.[^.]+$", "").toLowerCase(Locale.ROOT);
		String[] names = listings.computeIfAbsent(dir, d -> {
			String[] list = d.list();
			return list == null ? new String[0] : list;
		});
		for (String n : names) {
			String lower = n.toLowerCase(Locale.ROOT);
			if (lower.startsWith(base) && SUBTITLE_FILES.accept(n)) {
				return true;
			}
			// a Subs folder next to the video
			if (lower.equals("subs") || lower.equals("subtitles")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Look up the aired episodes of each series on TheMovieDB and keep those that are not in the library.
	 */
	public static void findMissing(Collection<Title> series, Locale locale, BooleanSupplier cancelled, BiConsumer<Integer, String> progress) throws Exception {
		SimpleDate today = new SimpleDate(LocalDate.now());
		int i = 0;
		for (Title t : series) {
			if (cancelled.getAsBoolean()) {
				throw new CancellationException();
			}
			progress.accept(i++, t.name);
			if (t.kind != Kind.SERIES) {
				continue;
			}

			// the best match comes first, the year in the folder name helps TheMovieDB pick it
			List<SearchResult> results = WebServices.TheMovieDB_TV.search(t.name.replaceAll("[()]", ""), locale);
			if (results.isEmpty()) {
				results = WebServices.TheMovieDB_TV.search(YEAR.matcher(t.name).replaceAll("").trim(), locale);
			}
			if (results.isEmpty()) {
				t.missing = null;
				continue;
			}
			SearchResult match = results.get(0);

			List<String> missing = new ArrayList<String>();
			for (Episode e : WebServices.TheMovieDB_TV.getEpisodeList(match, SortOrder.Airdate, locale)) {
				if (e.getSeason() == null || e.getSeason() == 0 || e.getEpisode() == null || e.getAirdate() == null || e.getAirdate().compareTo(today) > 0) {
					continue;
				}
				String key = String.format("S%02dE%02d", e.getSeason(), e.getEpisode());
				if (!t.episodes.contains(key)) {
					missing.add(key + (e.getTitle() == null ? "" : " " + e.getTitle()));
				}
			}
			t.missing = missing;
		}
	}

}
