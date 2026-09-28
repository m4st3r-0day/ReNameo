package net.renameo.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static net.renameo.Logging.*;
import static net.renameo.util.JsonUtilities.*;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;

/**
 * Builds the offline title index (popular, top rated and current movies and series from TheMovieDB) that lets ReNameo cross-reference titles without a web request, e.g. "Il Trono di
 * Spade" to "Game of Thrones". Same output as tools/build_index.py.
 */
public class TMDbIndexBuilder {

	private static final String[][] SERIES = { { "tv/popular", "8" }, { "tv/top_rated", "8" }, { "tv/on_the_air", "3" }, { "tv/airing_today", "3" } };
	private static final String[][] MOVIES = { { "movie/popular", "8" }, { "movie/top_rated", "8" }, { "movie/now_playing", "3" }, { "movie/upcoming", "3" } };

	private final TMDbClient tmdb;

	public TMDbIndexBuilder(TMDbClient tmdb) {
		this.tmdb = tmdb;
	}

	public static class Result {

		public final int series;
		public final int movies;

		Result(int series, int movies) {
			this.series = series;
			this.movies = movies;
		}
	}

	private static class Entry {

		String name;
		String originalName;
		String year;
		double popularity;
		final Set<String> localNames = new LinkedHashSet<String>();
	}

	/**
	 * @param languages
	 *            the first one gives the primary names (English), the others add aliases (e.g. Italian titles)
	 */
	public Result build(File folder, List<Locale> languages, BiConsumer<Long, Long> progress, Supplier<Boolean> cancelled) throws Exception {
		long total = 0, done = 0;
		for (String[][] lists : new String[][][] { SERIES, MOVIES }) {
			for (String[] list : lists) {
				total += Integer.parseInt(list[1]) * languages.size();
			}
		}

		Map<Integer, Entry> series = new LinkedHashMap<Integer, Entry>();
		Map<Integer, Entry> movies = new LinkedHashMap<Integer, Entry>();

		for (int i = 0; i < 2; i++) {
			boolean tv = i == 0;
			for (String[] list : tv ? SERIES : MOVIES) {
				for (Locale language : languages) {
					for (int page = 1; page <= Integer.parseInt(list[1]); page++) {
						if (cancelled.get()) {
							return null;
						}
						progress.accept(done++, total);
						try {
							Object json = tmdb.request(list[0], singletonMap("page", page), language);
							for (Map<?, ?> it : getMapArray(json, "results")) {
								add(tv ? series : movies, it, tv, language == languages.get(0));
							}
						} catch (Exception e) {
							// one missing page must not abort the whole index
							debug.warning(String.format("Index: %s page %d [%s] => %s", list[0], page, language, e.getMessage()));
						}
					}
				}
			}
		}

		folder.mkdirs();
		write(new File(folder, "thetvdb.txt.xz"), rows(series, false));
		write(new File(folder, "moviedb.txt.xz"), rows(movies, true));
		return new Result(series.size(), movies.size());
	}

	private static void add(Map<Integer, Entry> index, Map<?, ?> item, boolean tv, boolean primary) {
		Integer id = getInteger(item, "id");
		String name = getString(item, tv ? "name" : "title");
		if (id == null || name == null || name.trim().isEmpty()) {
			return;
		}

		Entry e = index.computeIfAbsent(id, k -> new Entry());
		if (primary) {
			e.name = name;
		} else {
			e.localNames.add(name);
		}
		e.originalName = getString(item, tv ? "original_name" : "original_title");
		String date = getString(item, tv ? "first_air_date" : "release_date");
		e.year = date != null && date.length() >= 4 ? date.substring(0, 4) : "";
		Double popularity = getDecimal(item, "popularity");
		e.popularity = Math.max(e.popularity, popularity == null ? 0 : popularity);
	}

	private static List<String> rows(Map<Integer, Entry> index, boolean movies) {
		List<Map.Entry<Integer, Entry>> entries = new ArrayList<Map.Entry<Integer, Entry>>(index.entrySet());
		entries.sort((a, b) -> Double.compare(b.getValue().popularity, a.getValue().popularity));

		List<String> rows = new ArrayList<String>();
		for (Map.Entry<Integer, Entry> it : entries) {
			Entry e = it.getValue();
			String name = e.name != null ? e.name : e.originalName != null ? e.originalName : e.localNames.stream().findFirst().orElse(null);
			if (name == null) {
				continue;
			}

			Set<String> aliases = new LinkedHashSet<String>();
			if (e.originalName != null) {
				aliases.add(e.originalName);
			}
			aliases.addAll(e.localNames);
			aliases.remove(name);

			List<String> columns = new ArrayList<String>();
			if (movies) {
				columns.add("-1");
				columns.add(it.getKey().toString());
				columns.add(e.year);
			} else {
				columns.add(it.getKey().toString());
			}
			columns.add(clean(name));
			aliases.stream().map(TMDbIndexBuilder::clean).forEach(columns::add);
			rows.add(String.join("\t", columns));
		}
		return rows;
	}

	private static String clean(String s) {
		return s.replaceAll("[\\t\\r\\n]+", " ").trim();
	}

	private static void write(File file, List<String> rows) throws IOException {
		File temp = new File(file.getPath() + ".part");
		try (OutputStream out = new XZOutputStream(new FileOutputStream(temp), new LZMA2Options(6))) {
			out.write(String.join("\n", rows).getBytes(UTF_8));
		}
		if (!temp.renameTo(file)) {
			file.delete();
			if (!temp.renameTo(file)) {
				throw new IOException("Failed to replace " + file);
			}
		}
	}

}
