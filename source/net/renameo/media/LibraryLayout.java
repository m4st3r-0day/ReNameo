package net.renameo.media;

import java.io.File;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
