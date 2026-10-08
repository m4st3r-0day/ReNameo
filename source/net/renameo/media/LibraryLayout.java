package net.renameo.media;

import static net.renameo.Logging.*;

import java.io.File;
import java.nio.file.Files;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.renameo.util.FileUtilities;

/**
 * Places a media server path (e.g. {@code Shows/Series (2018) [tmdbid-1]/Season 01/...}) inside the folder the user picked, without repeating what that folder already is.
 *
 * The picked folder may be a library root ({@code Media} with {@code Movies}, {@code Shows} inside), a library of one kind ({@code Serie TV}), or even the folder of the series itself.
 */
public final class LibraryLayout {

	private LibraryLayout() {
		throw new UnsupportedOperationException();
	}

	public static File place(File standardPath, File library) {
		List<String> segments = new ArrayList<String>(Arrays.asList(standardPath.getPath().replace('\\', '/').split("/")));
		if (segments.size() < 2) {
			return new File(library, standardPath.getPath());
		}

		String category = segments.get(0);

		// picked folder already is (or is inside) the movie / series folder: keep only what comes below it
		for (File dir = library; dir != null && dir.getParentFile() != null; dir = dir.getParentFile()) {
			if (key(dir.getName()).isEmpty()) {
				continue;
			}
			for (int i = segments.size() - 2; i >= 1; i--) {
				if (sameFolder(dir.getName(), segments.get(i))) {
					return descend(dir, segments.subList(i + 1, segments.size()));
				}
			}
		}

		// picked folder is the root of several libraries and already has the category folder
		File existing = findChild(library, category);
		if (existing != null) {
			return descend(existing, segments.subList(1, segments.size()));
		}

		// picked folder is the library of this kind (or any other folder): Jellyfin / Plex point a library straight at it
		return descend(library, segments.subList(1, segments.size()));
	}

	/**
	 * The media server path, but next to where the file is now: the folder of the series or movie is renamed in place (e.g. {@code Neagley.S01.1080p.WEB-DL-TBK} becomes {@code Neagley (2024)}) and
	 * episodes go into season folders inside it. A file that isn't in a folder of its own (e.g. loose in {@code Downloads}) only gets its new name.
	 *
	 * @param titles
	 *            names the folder of the series or movie may start with (title, original title, alias names)
	 */
	public static File tidy(File standardPath, File original, Collection<String> titles) {
		List<String> segments = new ArrayList<String>(Arrays.asList(standardPath.getPath().replace('\\', '/').split("/")));
		if (original == null || original.getParentFile() == null || segments.size() < 3 || titles.isEmpty()) {
			return original == null || original.getParentFile() == null ? new File(standardPath.getName()) : new File(original.getParentFile(), standardPath.getName());
		}

		File media = getMediaFolder(original, titles);
		if (media == null) {
			return new File(original.getParentFile(), standardPath.getName());
		}

		// just name and year: Neagley (2024), without database tags like [tmdbid-123]
		String name = segments.get(1).replaceAll("\\s*[\\[{][a-zA-Z]+(id)?[-=][^\\]}]*[\\]}]", "").trim();

		// the series may already have a clean folder next to this one (e.g. a new season in a download folder)
		File target = new File(media.getParentFile(), name);
		File[] siblings = media.getParentFile().listFiles(File::isDirectory);
		if (siblings != null && !target.exists()) {
			for (File it : siblings) {
				if (!it.equals(media) && sameFolder(it.getName(), name)) {
					target = it;
					break;
				}
			}
		}

		if (!media.equals(target)) {
			relocations.put(media, target);
		}
		return descend(target, segments.subList(2, segments.size()));
	}

	/**
	 * The folder that holds the series or movie (and nothing else): the parent of the file or, for {@code Series/Season 1/episode.mkv}, the one above it.
	 */
	static File getMediaFolder(File original, Collection<String> titles) {
		File parent = original.getParentFile();
		if (parent == null || parent.getParentFile() == null) {
			return null;
		}
		File grand = parent.getParentFile();
		if (grand.getParentFile() != null && isTitleFolder(grand.getName(), titles)) {
			return grand;
		}
		return isTitleFolder(parent.getName(), titles) ? parent : null;
	}

	/**
	 * {@code Neagley.S01.1080p.AMZN.WEB-DL-TBK}, {@code Neagley (2024)} and {@code Neagley} are folders of Neagley, {@code Downloads} and {@code Upload.S01} are not.
	 */
	static boolean isTitleFolder(String folder, Collection<String> titles) {
		String name = key(folder);
		for (String title : titles) {
			String t = title == null ? "" : YEAR.matcher(key(title)).replaceAll("").trim();
			if (t.length() >= 2 && (name.equals(t) || name.startsWith(t + " "))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Folders whose series or movie got a new folder name while renaming: old folder => new folder.
	 */
	private static final Map<File, File> relocations = new ConcurrentHashMap<File, File>();

	/**
	 * After the files were moved, move what is left in their old folder (other seasons, .nfo, samples …) into the new folder and remove the old one, as if the folder had been renamed.
	 *
	 * @return the moves, for the history (so they can be undone too)
	 */
	public static Map<File, File> finishRelocations(Map<File, File> renamed) {
		Map<File, File> moves = new LinkedHashMap<File, File>();
		for (Entry<File, File> it : new ArrayList<Entry<File, File>>(relocations.entrySet())) {
			File from = it.getKey(), to = it.getValue();
			boolean used = renamed.entrySet().stream().anyMatch(r -> isInside(r.getKey(), from) && isInside(FileUtilities.resolve(r.getKey(), r.getValue()), to));
			if (!used) {
				continue;
			}
			relocations.remove(from);
			if (!from.isDirectory() || !to.isDirectory()) {
				continue;
			}
			try {
				removeEmptyFolders(from);
				moveContents(from, to, moves);
				removeEmptyFolders(from);
			} catch (Exception e) {
				debug.log(Level.WARNING, "Failed to move the rest of " + from + " to " + to, e);
			}
		}
		return moves;
	}

	private static void moveContents(File from, File to, Map<File, File> moves) throws Exception {
		File[] children = from.listFiles();
		if (children == null) {
			return;
		}
		for (File it : children) {
			if (JUNK.contains(it.getName())) {
				continue;
			}
			File target = new File(to, it.getName());
			if (it.isDirectory()) {
				// file by file, so the history can undo every move
				target.mkdirs();
				if (target.isDirectory()) {
					moveContents(it, target, moves); // e.g. both have "Season 01"
				}
			} else if (!target.exists()) {
				Files.move(it.toPath(), target.toPath());
				moves.put(it, target);
			}
			// a file that exists on both sides stays where it is
		}
	}

	/**
	 * Remove folders that are empty or only hold files the system creates (.DS_Store, Thumbs.db), bottom up.
	 */
	private static void removeEmptyFolders(File folder) {
		File[] children = folder.listFiles();
		if (children == null) {
			return;
		}
		for (File it : children) {
			if (it.isDirectory()) {
				removeEmptyFolders(it);
			}
		}
		children = folder.listFiles();
		if (children != null && Arrays.stream(children).allMatch(f -> f.isFile() && JUNK.contains(f.getName()))) {
			for (File f : children) {
				f.delete();
			}
			folder.delete();
		}
	}

	private static final Set<String> JUNK = new HashSet<String>(Arrays.asList(".DS_Store", "Thumbs.db", "desktop.ini"));

	private static boolean isInside(File file, File folder) {
		return file.getPath().startsWith(folder.getPath() + File.separator);
	}

	/**
	 * Reuse folders that already exist under another spelling ({@code Season 1}, {@code 01. Series (2018)}) instead of creating a second one next to them.
	 */
	private static File descend(File base, List<String> segments) {
		File dir = base;
		for (int i = 0; i < segments.size() - 1; i++) {
			File match = null;
			File[] children = dir.listFiles(File::isDirectory);
			if (children != null) {
				for (File it : children) {
					if (it.getName().equals(segments.get(i))) {
						match = it;
						break;
					}
					if (match == null && (sameFolder(it.getName(), segments.get(i)) || sameSeason(it.getName(), segments.get(i)))) {
						match = it;
					}
				}
			}
			dir = match != null ? match : new File(dir, segments.get(i));
		}
		return new File(dir, segments.get(segments.size() - 1));
	}

	private static boolean sameSeason(String existing, String standard) {
		Matcher a = SEASON.matcher(existing), b = SEASON.matcher(standard);
		return a.matches() && b.matches() && Integer.parseInt(a.group(1)) == Integer.parseInt(b.group(1));
	}

	private static final Pattern SEASON = Pattern.compile("(?i)(?:season|stagione|staffel|saison|temporada|series|s)[ ._-]*(\\d{1,4})");

	private static File findChild(File folder, String name) {
		File[] children = folder.listFiles(File::isDirectory);
		if (children != null) {
			for (File it : children) {
				if (it.getName().equalsIgnoreCase(name)) {
					return it;
				}
			}
		}
		return null;
	}

	private static boolean sameFolder(String existing, String standard) {
		String a = key(existing), b = key(standard);
		if (a.equals(b)) {
			return true;
		}
		// "Dexter" and "Dexter (2006)" are the same series folder, "Dune (1984)" and "Dune (2021)" are not
		boolean yearA = YEAR.matcher(a).find(), yearB = YEAR.matcher(b).find();
		return yearA != yearB && YEAR.matcher(a).replaceAll("").equals(YEAR.matcher(b).replaceAll(""));
	}

	private static final Pattern YEAR = Pattern.compile("\\s(19|20)\\d{2}$");

	/**
	 * Comparable form of a folder name: without sort prefixes ({@code 01. }), database tags ({@code [tmdbid-1]}, {@code {tvdb-1}}), punctuation and accents.
	 */
	static String key(String name) {
		String s = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		s = s.replaceAll("[\\[{][a-zA-Z]+(id)?[-=][^\\]}]*[\\]}]", " ");
		s = s.replaceAll("^\\s*\\d{1,3}\\s*[.)\\-]\\s+", "");
		s = s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
		return s;
	}

}
