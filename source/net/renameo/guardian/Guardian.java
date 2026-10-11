package net.renameo.guardian;

import static java.nio.charset.StandardCharsets.*;
import static net.renameo.Logging.*;
import static net.renameo.MediaTypes.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import groovy.json.JsonOutput;
import groovy.json.JsonSlurper;
import net.renameo.ApplicationFolder;
import net.renameo.Settings;
import net.renameo.StandardRenameAction;
import net.renameo.cli.ConflictAction;
import net.renameo.format.ExpressionFileFormat;
import net.renameo.media.ExtraType;
import net.renameo.media.LibraryLayout;
import net.renameo.media.LibraryStats;
import net.renameo.media.MediaDetection;
import net.renameo.similarity.SeasonEpisodeMatcher.SxE;
import net.renameo.util.PreferencesMap.PreferencesEntry;
import net.renameo.web.SortOrder;

/**
 * Keeps a library in order: finds files whose names don't follow the naming profile, corrects them (or asks first) and keeps a journal of every change, so each
 * one can be checked and undone. It can be wrong, so it is careful: the first run only suggests, a run with too many changes only suggests, and what you undo
 * or ignore is never touched again.
 */
public final class Guardian {

	public enum Mode {
		/** only suggest, change nothing */
		SUGGEST("Only suggest"),
		/** correct what is certain, suggest the rest */
		EXACT("Correct when certain"),
		/** correct everything, but write it all down */
		ALL("Correct everything");

		public final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Status {
		PENDING, APPLIED, UNDONE, IGNORED, FAILED
	}

	/**
	 * One change: suggested, done, undone or ignored.
	 */
	public static class Entry {

		public String id;
		public long time;
		public String run;
		public String from;
		public String to;
		public String reason;
		public boolean exact;
		public Status status;
		public String note;

		/** files that followed their folder with this change: [old path, new path] */
		public List<List<String>> extra = new ArrayList<List<String>>();

		public File getFrom() {
			return new File(from);
		}

		public File getTo() {
			return new File(to);
		}
	}

	public static class Report {

		public final String run = new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date());
		public int checked;
		public int fine;
		public int applied;
		public int pending;
		public int failed;
		public String note;

		@Override
		public String toString() {
			return String.format("%d files checked, %d fine, %d corrected, %d to review%s%s", checked, fine, applied, pending, failed > 0 ? ", " + failed + " failed" : "", note != null ? " (" + note + ")" : "");
		}
	}

	private static final Settings settings = Settings.forPackage(Guardian.class);

	public static final PreferencesEntry<String> folder = settings.entry("library.folder").defaultValue("");
	public static final PreferencesEntry<String> enabled = settings.entry("guardian.enabled").defaultValue("false");
	public static final PreferencesEntry<String> mode = settings.entry("guardian.mode").defaultValue(Mode.SUGGEST.name());
	public static final PreferencesEntry<String> profile = settings.entry("guardian.profile").defaultValue("jellyfin");
	public static final PreferencesEntry<String> hour = settings.entry("guardian.hour").defaultValue("3");
	public static final PreferencesEntry<String> limit = settings.entry("guardian.limit").defaultValue("50");
	public static final PreferencesEntry<String> thorough = settings.entry("guardian.thorough").defaultValue("false");

	public static final String[] PROFILES = { "plex", "jellyfin", "emby", "kodi" };

	/** files changed more recently are left alone (still copying, being watched …) */
	private static final long SETTLE = 10 * 60 * 1000L;

	private Guardian() {
		throw new UnsupportedOperationException();
	}

	public static File getFolder() {
		String path = folder.getValue();
		return path.isEmpty() ? null : new File(path);
	}

	public static Mode getMode() {
		try {
			return Mode.valueOf(mode.getValue());
		} catch (Exception e) {
			return Mode.SUGGEST;
		}
	}

	public static int getLimit() {
		try {
			return Math.max(1, Integer.parseInt(limit.getValue()));
		} catch (NumberFormatException e) {
			return 50;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// journal: guardian.json in the data folder, never cleaned up on its own
	// ---------------------------------------------------------------------------------------------

	private static List<Entry> entries;
	private static Set<String> ignored;
	private static Map<String, String> titles;
	/** libraries checked before: the first run of a library only suggests */
	private static Set<String> libraries;
	private static String lastRun;
	private static final List<Runnable> listeners = new java.util.concurrent.CopyOnWriteArrayList<Runnable>();

	private static File getJournalFile() {
		return ApplicationFolder.AppData.resolve("guardian.json");
	}

	@SuppressWarnings("unchecked")
	private static synchronized void load() {
		if (entries != null) {
			return;
		}
		entries = new ArrayList<Entry>();
		ignored = new LinkedHashSet<String>();
		titles = new LinkedHashMap<String, String>();
		libraries = new LinkedHashSet<String>();

		File file = getJournalFile();
		if (!file.isFile()) {
			return;
		}
		try {
			Map<String, Object> json = (Map<String, Object>) new JsonSlurper().parseText(new String(Files.readAllBytes(file.toPath()), UTF_8));
			for (Map<String, Object> it : (List<Map<String, Object>>) json.getOrDefault("entries", new ArrayList<Object>())) {
				Entry e = new Entry();
				e.id = (String) it.get("id");
				e.time = ((Number) it.get("time")).longValue();
				e.run = (String) it.get("run");
				e.from = (String) it.get("from");
				e.to = (String) it.get("to");
				e.reason = (String) it.get("reason");
				e.exact = Boolean.TRUE.equals(it.get("exact"));
				e.status = Status.valueOf((String) it.get("status"));
				e.note = (String) it.get("note");
				if (it.get("extra") instanceof List) {
					((List<List<String>>) it.get("extra")).forEach(m -> e.extra.add(new ArrayList<String>(m)));
				}
				entries.add(e);
			}
			ignored.addAll((List<String>) json.getOrDefault("ignored", new ArrayList<String>()));
			titles.putAll((Map<String, String>) json.getOrDefault("titles", new LinkedHashMap<String, String>()));
			libraries.addAll((List<String>) json.getOrDefault("libraries", new ArrayList<String>()));
			lastRun = (String) json.get("last");
		} catch (Exception e) {
			// keep the broken file, a new journal starts next to it
			log.log(Level.WARNING, "Guardian: the journal could not be read, a new one is started: " + e.getMessage());
			try {
				Files.move(file.toPath(), new File(file.getPath() + ".broken").toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException x) {
				debug.warning(x.toString());
			}
		}
	}

	private static synchronized void save() {
		Map<String, Object> json = new LinkedHashMap<String, Object>();
		List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
		for (Entry e : entries) {
			Map<String, Object> m = new LinkedHashMap<String, Object>();
			m.put("id", e.id);
			m.put("time", e.time);
			m.put("run", e.run);
			m.put("from", e.from);
			m.put("to", e.to);
			m.put("reason", e.reason);
			m.put("exact", e.exact);
			m.put("status", e.status.name());
			m.put("note", e.note);
			m.put("extra", e.extra);
			list.add(m);
		}
		json.put("entries", list);
		json.put("ignored", new ArrayList<String>(ignored));
		json.put("titles", titles);
		json.put("libraries", new ArrayList<String>(libraries));
		json.put("last", lastRun);
		try {
			File file = getJournalFile();
			File temp = new File(file.getPath() + ".tmp");
			Files.write(temp.toPath(), JsonOutput.prettyPrint(JsonOutput.toJson(json)).getBytes(UTF_8));
			Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			log.log(Level.WARNING, "Guardian: failed to save the journal: " + e.getMessage(), e);
		}
		listeners.forEach(Runnable::run);
	}

	/**
	 * Newest first.
	 */
	public static synchronized List<Entry> getEntries() {
		load();
		List<Entry> list = new ArrayList<Entry>(entries);
		java.util.Collections.reverse(list);
		return list;
	}

	/**
	 * e.g. "2026-10-11 03:00 · 812 files checked, 805 fine, 4 corrected, 3 to review", or null
	 */
	public static synchronized String getLastRun() {
		load();
		return lastRun;
	}

	public static synchronized List<String> getIgnored() {
		load();
		return new ArrayList<String>(ignored);
	}

	public static synchronized Map<String, String> getTitles() {
		load();
		return new LinkedHashMap<String, String>(titles);
	}

	public static void addListener(Runnable listener) {
		listeners.add(listener);
	}

	public static void removeListener(Runnable listener) {
		listeners.remove(listener);
	}

	// ---------------------------------------------------------------------------------------------
	// scan
	// ---------------------------------------------------------------------------------------------

	/**
	 * Check the library and correct or suggest, depending on the mode.
	 *
	 * @param progress
	 *            files checked so far and the current title
	 */
	public static Report scan(File root, Mode mode, String profile, boolean thorough, Locale locale, BooleanSupplier cancelled, BiConsumer<Integer, String> progress) throws Exception {
		load();
		Report report = new Report();
		ExpressionFileFormat format = new ExpressionFileFormat("{" + profile + ".tidy}");

		// what to look at: videos and subtitles, no samples, extras, ignored or busy files
		long settled = System.currentTimeMillis() - SETTLE;
		Set<String> skip;
		synchronized (Guardian.class) {
			skip = new LinkedHashSet<String>(ignored);
		}
		Map<File, List<File>> groups = new LinkedHashMap<File, List<File>>();
		for (File f : listMedia(root, cancelled)) {
			if (skip.contains(f.getPath()) || f.lastModified() > settled || ExtraType.detect(f) != null || (VIDEO_FILES.accept(f) && LibraryStats.isSample(f))) {
				continue;
			}
			groups.computeIfAbsent(getGroup(f, root), k -> new ArrayList<File>()).add(f);
		}

		// plan, title by title
		List<Entry> changes = new ArrayList<Entry>();
		for (Map.Entry<File, List<File>> group : groups.entrySet()) {
			if (cancelled.getAsBoolean()) {
				throw new CancellationException();
			}
			List<File> files = group.getValue();
			report.checked += files.size();
			progress.accept(report.checked, group.getKey().getName());

			if (!thorough && files.stream().filter(VIDEO_FILES::accept).allMatch(f -> looksTidy(f, root))) {
				report.fine += files.size();
				continue;
			}

			String query;
			synchronized (Guardian.class) {
				query = titles.get(group.getKey().getPath());
			}
			// you said which title this folder is: it gets the proper name even if its name doesn't start with the title
			boolean claim = query != null && group.getKey().isDirectory();
			if (claim) {
				LibraryLayout.claimFolder(group.getKey());
			}
			Map<File, File> plan, strict;
			try {
				plan = plan(files, format, query, locale, false);
				// certain = the strict lookup agrees; for a series it needs the title, which the folder (or what you taught it) tells
				strict = plan.isEmpty() ? plan : plan(files, format, query != null ? query : getSeriesTitle(group.getKey(), files), locale, true);
			} finally {
				if (claim) {
					LibraryLayout.releaseFolder(group.getKey());
				}
			}

			for (File f : files) {
				File to = plan.get(f);
				if (to == null || to.equals(f)) {
					report.fine++;
					continue;
				}
				Entry e = new Entry();
				e.id = UUID.randomUUID().toString();
				e.time = System.currentTimeMillis();
				e.run = report.run;
				e.from = f.getPath();
				e.to = to.getPath();
				e.exact = to.equals(strict.get(f));
				e.reason = reason(f, to, profile);
				changes.add(e);
			}
		}

		// what is done right away
		boolean first;
		synchronized (Guardian.class) {
			first = !libraries.contains(root.getPath());
		}
		List<Entry> auto = new ArrayList<Entry>();
		if (!first && mode != Mode.SUGGEST) {
			changes.stream().filter(e -> mode == Mode.ALL || e.exact).forEach(auto::add);
		}
		if (auto.size() > getLimit()) {
			report.note = String.format("%d changes are more than the limit of %d, nothing was changed: please review them", auto.size(), getLimit());
			auto.clear();
		} else if (first && !changes.isEmpty()) {
			report.note = "first run: only suggestions";
		}

		synchronized (Guardian.class) {
			libraries.add(root.getPath());
			// a new suggestion replaces the old one for the same file
			Set<String> planned = new LinkedHashSet<String>();
			changes.forEach(e -> planned.add(e.from));
			entries.removeIf(e -> e.status == Status.PENDING && planned.contains(e.from));
			for (Entry e : changes) {
				e.status = Status.PENDING;
				entries.add(e);
			}
			save();
		}

		if (!auto.isEmpty()) {
			apply(auto);
		}
		for (Entry e : changes) {
			if (e.status == Status.APPLIED) {
				report.applied++;
			} else if (e.status == Status.FAILED) {
				report.failed++;
			} else if (e.status == Status.PENDING) {
				report.pending++;
			}
		}

		synchronized (Guardian.class) {
			lastRun = report.run + " · " + report;
			save();
		}
		log.info("Guardian: " + report);
		return report;
	}

	/**
	 * "Dark (2017)" => "Dark" for the folder of a series, the title from the file name for a loose episode, null for a movie.
	 */
	static String getSeriesTitle(File group, List<File> files) {
		boolean series = files.stream().anyMatch(f -> {
			List<SxE> sxe = MediaDetection.parseEpisodeNumber(f, true);
			return sxe != null && !sxe.isEmpty();
		});
		if (!series) {
			return null;
		}
		String title = group.isFile() ? LibraryStats.seriesNameFromFile(group.getName()) : LibraryStats.cleanName(group.getName());
		return title.replaceAll("\\s*\\((19|20)\\d{2}\\)$", "").trim();
	}

	private static Map<File, File> plan(List<File> files, ExpressionFileFormat format, String query, Locale locale, boolean strict) {
		try {
			GuardianOperations ops = new GuardianOperations(true);
			ops.rename(files, StandardRenameAction.TEST, ConflictAction.SKIP, null, format, null, query, SortOrder.Airdate, null, locale, strict, null);
			return ops.planned;
		} catch (Exception e) {
			debug.fine("Guardian: " + e.getMessage());
			return new LinkedHashMap<File, File>();
		}
	}

	/**
	 * The folder a file belongs to: the series (above the season folder) or the movie folder; a loose file is its own group.
	 */
	static File getGroup(File f, File root) {
		List<SxE> sxe = MediaDetection.parseEpisodeNumber(f, true);
		if (sxe != null && !sxe.isEmpty()) {
			File series = LibraryStats.getSeriesFolder(f, root);
			return series != null ? series : f.getParentFile().equals(root) ? f : f.getParentFile();
		}
		return f.getParentFile().equals(root) ? f : f.getParentFile();
	}

	private static final Pattern TAG = Pattern.compile("\\s*[\\[{][a-zA-Z]+(id)?[-=][^\\]}]*[\\]}]");
	private static final Pattern TITLE_YEAR = Pattern.compile("^.+ \\((19|20)\\d{2}\\)$");
	private static final Pattern SEASON = Pattern.compile("(?i)(?:season|stagione|staffel|saison|temporada) (\\d+)|(specials)");

	/**
	 * Already like Name (Year)/Season 01/Name (Year) - S01E02 … or Name (Year)/Name (Year).mkv: no need to ask TheMovieDB.
	 */
	static boolean looksTidy(File f, File root) {
		String name = TAG.matcher(f.getName().replaceFirst("\\.[^.]+$", "")).replaceAll("").trim();
		File parent = f.getParentFile();
		if (parent == null || parent.equals(root)) {
			return false;
		}
		List<SxE> sxe = MediaDetection.parseEpisodeNumber(f, true);
		if (sxe != null && !sxe.isEmpty()) {
			Matcher season = SEASON.matcher(parent.getName());
			File series = parent.getParentFile();
			if (!season.matches() || series == null || series.equals(root.getParentFile())) {
				return false;
			}
			int number = season.group(1) != null ? Integer.parseInt(season.group(1)) : 0;
			String title = TAG.matcher(series.getName()).replaceAll("").trim();
			return TITLE_YEAR.matcher(title).matches() && sxe.get(0).season == number && name.matches(Pattern.quote(title) + " - S\\d{2,}E\\d{2,}.*");
		}
		String title = TAG.matcher(parent.getName()).replaceAll("").trim();
		return TITLE_YEAR.matcher(title).matches() && name.replaceFirst("(?i)[ ._-]+(cd|part|disc)\\s*\\d+$", "").equals(title);
	}

	static String reason(File from, File to, String profile) {
		String server = Character.toUpperCase(profile.charAt(0)) + profile.substring(1);
		if (from.getParentFile().equals(to.getParentFile())) {
			return "Name doesn't follow the " + server + " naming";
		}
		File fromTitle = titleFolder(from), toTitle = titleFolder(to);
		if (!fromTitle.getName().equals(toTitle.getName())) {
			return "Folder gets the proper name: " + toTitle.getName();
		}
		return SEASON.matcher(to.getParentFile().getName()).matches() ? "Goes into its season folder" : "Moves into the folder of its title";
	}

	/**
	 * The folder of the series or movie: above the season folder, or the parent.
	 */
	private static File titleFolder(File f) {
		File parent = f.getParentFile();
		return SEASON.matcher(parent.getName()).matches() && parent.getParentFile() != null ? parent.getParentFile() : parent;
	}

	private static List<File> listMedia(File root, BooleanSupplier cancelled) throws IOException {
		List<File> files = new ArrayList<File>();
		Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>() {

			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
				if (cancelled.getAsBoolean()) {
					throw new CancellationException();
				}
				String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
				return !dir.equals(root.toPath()) && (name.startsWith(".") || name.startsWith("@") || name.equals("#recycle") || name.equals("$RECYCLE.BIN")) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				File f = file.toFile();
				if (attrs.isRegularFile() && (VIDEO_FILES.accept(f) || SUBTITLE_FILES.accept(f))) {
					files.add(f);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFileFailed(Path file, IOException e) {
				return FileVisitResult.CONTINUE;
			}
		});
		return files;
	}

	// ---------------------------------------------------------------------------------------------
	// apply, ignore, undo
	// ---------------------------------------------------------------------------------------------

	/**
	 * Make the suggested changes and write down what happened, including files that followed their folder.
	 */
	public static void apply(Collection<Entry> selection) {
		load();
		Map<File, File> map = new LinkedHashMap<File, File>();
		for (Entry e : selection) {
			if (e.status == Status.PENDING && e.getFrom().exists()) {
				map.put(e.getFrom(), e.getTo());
			}
		}
		Map<File, File> moved = new LinkedHashMap<File, File>();
		if (!map.isEmpty()) {
			try {
				GuardianOperations ops = new GuardianOperations(false);
				ops.rename(map, StandardRenameAction.MOVE, ConflictAction.SKIP);
				moved.putAll(ops.moved);
			} catch (Exception e) {
				log.log(Level.WARNING, "Guardian: " + e.getMessage(), e);
			}
		}

		synchronized (Guardian.class) {
			for (Entry e : selection) {
				if (e.status != Status.PENDING) {
					continue;
				}
				File done = moved.remove(e.getFrom());
				if (done != null) {
					e.status = Status.APPLIED;
					e.to = done.getPath();
					e.time = System.currentTimeMillis();
				} else {
					e.status = Status.FAILED;
					e.note = e.getFrom().exists() ? "the new name already exists" : "the file is gone";
				}
			}
			// files that followed their folder belong to the change from the same folder
			for (Map.Entry<File, File> m : moved.entrySet()) {
				selection.stream().filter(e -> e.status == Status.APPLIED && isInside(m.getKey(), e.getFrom().getParentFile())).findFirst().ifPresent(e -> e.extra.add(java.util.Arrays.asList(m.getKey().getPath(), m.getValue().getPath())));
			}
			save();
		}
	}

	/**
	 * Leave the file as it is, now and in later runs.
	 */
	public static synchronized void ignore(Collection<Entry> selection) {
		load();
		for (Entry e : selection) {
			if (e.status == Status.PENDING) {
				e.status = Status.IGNORED;
				ignored.add(e.from);
			}
		}
		save();
	}

	public static synchronized void unignore(Collection<String> paths) {
		load();
		ignored.removeAll(paths);
		save();
	}

	/**
	 * Put the files back where they were. They are ignored from now on, so the guardian doesn't make the same change again.
	 */
	public static void undo(Collection<Entry> selection, Consumer<String> failures) {
		load();
		for (Entry e : selection) {
			if (e.status != Status.APPLIED) {
				continue;
			}
			List<String> problems = new ArrayList<String>();
			// the files that followed the folder first, then the file itself
			List<List<String>> extra = new ArrayList<List<String>>(e.extra);
			java.util.Collections.reverse(extra);
			for (List<String> m : extra) {
				revert(new File(m.get(1)), new File(m.get(0)), problems);
			}
			revert(e.getTo(), e.getFrom(), problems);

			synchronized (Guardian.class) {
				e.status = Status.UNDONE;
				e.note = problems.isEmpty() ? "undone " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()) : String.join("; ", problems);
				ignored.add(e.from);
			}
			problems.forEach(failures);
		}
		synchronized (Guardian.class) {
			save();
		}
	}

	private static void revert(File current, File original, List<String> problems) {
		try {
			if (current.exists()) {
				StandardRenameAction.revert(current, original);
			} else if (!original.exists()) {
				problems.add(current.getName() + " is gone");
			}
		} catch (Exception x) {
			problems.add(current.getName() + ": " + x.getMessage());
		}
	}

	/**
	 * The guardian picked the wrong series or movie: undo, and look for the given title for this folder from now on.
	 */
	public static void wrongTitle(Entry entry, String title, File root) {
		undo(java.util.Collections.singletonList(entry), problem -> log.warning("Guardian: " + problem));
		synchronized (Guardian.class) {
			File group = getGroup(entry.getFrom(), root);
			titles.put(group.getPath(), title.trim());
			// the files of this title may be checked again, now with the right title
			ignored.removeIf(path -> path.equals(entry.from) || isInside(new File(path), group));
			entry.note = "wrong title, next time looked up as \"" + title.trim() + "\"";
			save();
		}
	}

	private static boolean isInside(File file, File folder) {
		return file.getPath().startsWith(folder.getPath() + File.separator);
	}

	/**
	 * The journal as a table, e.g. to check it in a spreadsheet or send an error to someone.
	 */
	public static String toCsv(Collection<Entry> list) {
		StringBuilder csv = new StringBuilder("time;status;exact;reason;from;to;note\n");
		SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		for (Entry e : list) {
			csv.append(String.join(";", time.format(new Date(e.time)), e.status.name(), String.valueOf(e.exact), quote(e.reason), quote(e.from), quote(e.to), quote(e.note))).append('\n');
		}
		return csv.toString();
	}

	private static String quote(String s) {
		return s == null ? "" : "\"" + s.replace("\"", "\"\"") + "\"";
	}

}
