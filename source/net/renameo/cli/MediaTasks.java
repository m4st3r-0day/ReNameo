package net.renameo.cli;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.renameo.Logging.*;
import static net.renameo.MediaTypes.*;
import static net.renameo.util.FileUtilities.*;
import static net.renameo.web.WebRequest.*;

import java.io.File;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.renameo.WebServices;
import net.renameo.media.MediaDetection;
import net.renameo.media.XattrMetaInfo;
import net.renameo.similarity.SeasonEpisodeMatcher.SxE;
import net.renameo.web.Artwork;
import net.renameo.web.ArtworkProvider;
import net.renameo.web.Episode;
import net.renameo.web.Movie;
import net.renameo.web.MovieInfo;
import net.renameo.web.SearchResult;
import net.renameo.web.SeriesInfo;
import net.renameo.web.SortOrder;

/**
 * Post-processing tasks that can be applied to media files, e.g. writing .nfo files, fetching artwork and metadata.
 */
public class MediaTasks {

	public static final List<String> SUPPORTED_TASKS = asList("nfo", "url", "artwork", "cover", "metadata");

	public static List<File> apply(Collection<File> files, String tasks, File outputDir, Locale locale) throws Exception {
		// parse enabled tasks
		Set<String> enabled = stream(tasks.split("[,\\s]+")).filter(s -> !s.isEmpty()).collect(toCollection(LinkedHashSet::new));

		if (enabled.isEmpty()) {
			throw new IllegalArgumentException("No apply tasks specified: " + tasks);
		}

		// log unsupported tasks (best-effort, i.e. OpenSubtitles is no longer available)
		for (String task : enabled) {
			if (!SUPPORTED_TASKS.contains(task)) {
				log.warning(format("Unsupported apply task: %s", task));
			}
		}

		List<File> artifacts = new ArrayList<File>();
		for (File file : files) {
			try {
				artifacts.addAll(apply(file, enabled, outputDir, locale));
			} catch (Exception e) {
				log.warning(format("Failed to apply tasks to [%s]: %s", file, e));
			}
		}
		return artifacts;
	}

	public static List<File> apply(File file, Set<String> enabled, File outputDir, Locale locale) throws Exception {
		// only ever process video files (ignore .nfo/.url/artwork sidecar files)
		if (!VIDEO_FILES.accept(file)) {
			return emptyList();
		}

		// identify movie or episode
		Object media = identify(file, locale);
		if (media == null) {
			log.warning(format("Failed to identify media file: %s", file));
			return emptyList();
		}

		File targetFolder = outputDir != null ? outputDir : file.getParentFile();
		String base = getNameWithoutExtension(file.getName());

		// apply enabled tasks
		List<File> artifacts = new ArrayList<File>();

		for (String task : enabled) {
			try {
				switch (task) {
				case "nfo":
					artifacts.addAll(writeNfo(file, media, targetFolder, base, locale));
					break;
				case "url":
					artifacts.addAll(writeUrl(file, media, targetFolder, base));
					break;
				case "artwork":
				case "cover":
					artifacts.addAll(writeArtwork(file, media, targetFolder, base, locale));
					break;
				case "metadata":
					writeXattr(file, media);
					break;
				}
			} catch (Exception e) {
				log.warning(format("Failed to apply task [%s] to [%s]: %s", task, file, e));
			}
		}

		return artifacts;
	}

	private static Object identify(File file, Locale locale) throws Exception {
		// try movie match first
		try {
			List<Movie> matches = MediaDetection.detectMovieWithYear(file, file.isDirectory() ? null : WebServices.TheMovieDB, locale, false);

			if (matches != null && !matches.isEmpty()) {
				// reduce to best matches if possible
				List<Movie> bestMatches = MediaDetection.matchMovieByWordSequence(getName(file), matches, 0);
				Movie movie = bestMatches.size() > 0 ? bestMatches.get(0) : matches.get(0);

				// make sure we get a localized movie object with the correct ids
				if (movie != null) {
					movie = MediaDetection.getLocalizedMovie(WebServices.TheMovieDB, movie, locale);
					if (movie != null && movie.getImdbId() > 0) {
						return movie;
					}
				}
			}
		} catch (Exception e) {
			// ignore movie detection failures, try episode matching next
		}

		// try episode match
		try {
			List<SxE> numbers = MediaDetection.parseEpisodeNumber(file, false);
			if (numbers != null && !numbers.isEmpty()) {
				Episode episode = identifyEpisode(file, numbers.get(0), locale);
				if (episode != null) {
					return episode;
				}
			}
		} catch (Exception e) {
			// ignore episode detection failures
		}

		return null;
	}

	private static Episode identifyEpisode(File file, SxE sxe, Locale locale) throws Exception {
		// detect series names for this folder
		List<String> seriesNames = MediaDetection.detectSeriesNames(singleton(file), false, locale);
		if (seriesNames.isEmpty()) {
			return null;
		}

		// search for the series and find the matching episode
		Set<Object> reject = new LinkedHashSet<Object>();
		for (String query : seriesNames) {
			for (SearchResult series : WebServices.TheMovieDB_TV.search(query, locale)) {
				if (reject.contains(series)) {
					continue;
				}
				reject.add(series);

				List<Episode> episodes = WebServices.TheMovieDB_TV.getEpisodeList(series, SortOrder.Airdate, locale);
				for (Episode episode : episodes) {
					if (sxe.season > 0 && sxe.season == toInt(episode.getSeason()) && sxe.episode == toInt(episode.getEpisode())) {
						return episode;
					}
				}
			}
		}
		return null;
	}

	private static List<File> writeNfo(File file, Object media, File targetFolder, String base, Locale locale) throws Exception {
		File nfo = new File(targetFolder, base + ".nfo");

		if (media instanceof Movie) {
			MovieInfo info = WebServices.TheMovieDB.getMovieInfo((Movie) media, locale, true);
			writeFile(ByteBuffer.wrap(toMovieNfo(info).getBytes(UTF_8)), nfo);
		} else if (media instanceof Episode) {
			writeFile(ByteBuffer.wrap(toEpisodeNfo((Episode) media).getBytes(UTF_8)), nfo);
		}

		log.info(format("Write [%s] to [%s]", "nfo", nfo));
		return singletonList(nfo);
	}

	private static List<File> writeUrl(File file, Object media, File targetFolder, String base) {
		File url = new File(targetFolder, base + ".url");

		String link = null;
		if (media instanceof Movie) {
			Movie movie = (Movie) media;
			String id = String.valueOf(movie.getTmdbId() > 0 ? movie.getTmdbId() : movie.getImdbId());
			link = "https://www.themoviedb.org/movie/" + id;
		} else if (media instanceof Episode) {
			Episode episode = (Episode) media;
			SeriesInfo info = episode.getSeriesInfo();
			String id = info == null ? null : String.valueOf(info.getId());
			id = id != null ? id : "0";
			link = String.format("https://www.themoviedb.org/tv/%s/season/%d/episode/%d", id, toInt(episode.getSeason()), toInt(episode.getEpisode()));
		}

		if (link == null) {
			return emptyList();
		}

		log.info(format("Write [%s] to [%s]", "url", url));
		return singletonList(writeString("[InternetShortcut]\nURL=" + link + "\n", url));
	}

	private static List<File> writeArtwork(File file, Object media, File targetFolder, String base, Locale locale) throws Exception {
		List<File> artifacts = new ArrayList<File>(2);

		if (media instanceof Movie) {
			Movie movie = (Movie) media;
			int id = movie.getTmdbId() > 0 ? movie.getTmdbId() : movie.getImdbId();

			// poster + backdrop
			for (String category : new String[] { "posters", "backdrops" }) {
				String name = "posters".equals(category) ? base + ".jpg" : base + "-fanart.jpg";
				File dest = new File(targetFolder, name);
				if (downloadArtwork(WebServices.TheMovieDB, id, category, locale, dest)) {
					artifacts.add(dest);
				}
			}
		} else if (media instanceof Episode) {
			Episode episode = (Episode) media;
			SeriesInfo info = episode.getSeriesInfo();

			// the id is only a TheMovieDB id if the series came from TheMovieDB (not TVmaze or AniDB)
			if (info != null && info.getId() != null && info.getDatabase() != null && info.getDatabase().toLowerCase().contains("themoviedb")) {
				// series poster + backdrop
				int id = info.getId();
				for (String category : new String[] { "posters", "backdrops" }) {
					String name = "posters".equals(category) ? base + ".jpg" : base + "-fanart.jpg";
					File dest = new File(targetFolder, name);
					if (downloadArtwork(WebServices.TheMovieDB_TV, id, category, locale, dest)) {
						artifacts.add(dest);
					}
				}
			}
		}

		return artifacts;
	}

	private static boolean downloadArtwork(ArtworkProvider provider, int id, String category, Locale locale, File dest) throws Exception {
		List<Artwork> artwork = provider.getArtwork(id, category, locale);
		if (artwork.isEmpty()) {
			return false;
		}

		// artwork is sorted by rating (best first)
		Artwork best = artwork.get(0);
		URL url = best.getUrl();
		if (url == null) {
			return false;
		}

		ByteBuffer data = fetch(url);
		if (data == null || data.remaining() <= 0) {
			return false;
		}

		log.info(format("Fetch [%s] from [%s]", url, dest));
		writeFile(data, dest);
		return true;
	}

	private static void writeXattr(File file, Object media) {
		try {
			XattrMetaInfo.xattr.setMetaInfo(file, media, getName(file));
			log.info(format("Write [%s] to [%s]", "xattr", file));
		} catch (Exception e) {
			log.warning(format("Failed to write xattr metadata to [%s]: %s", file, e));
		}
	}

	protected static String toMovieNfo(MovieInfo info) {
		StringBuilder xml = new StringBuilder(512);
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xml.append("<movie>\n");

		if (info.get("imdb_id") != null) {
			xml.append("  <uniqueid type=\"imdb\" default=\"true\">" + escape(info.get("imdb_id")) + "</uniqueid>\n");
		}
		if (info.get("id") != null) {
			xml.append("  <uniqueid type=\"tmdb\">" + escape(info.get("id")) + "</uniqueid>\n");
		}
		appendValue(xml, "title", info.get("title"));
		appendValue(xml, "originaltitle", info.get("original_title"));
		appendValue(xml, "year", info.get("release_date") != null && info.get("release_date").length() >= 4 ? info.get("release_date").substring(0, 4) : null);
		appendValue(xml, "rating", info.get("vote_average"));
		appendValue(xml, "votes", info.get("vote_count"));
		appendValue(xml, "runtime", info.get("runtime"));
		appendValue(xml, "mpaa", info.get("certification"));
		appendValue(xml, "plot", info.get("overview"));
		appendValue(xml, "tagline", info.get("tagline"));

		if (info.getGenres() != null) {
			for (String genre : info.getGenres()) {
				appendValue(xml, "genre", genre);
			}
		}

		// basic cast information (first 10)
		int i = 0;
		for (net.renameo.web.Person person : info.getCast()) {
			if (i++ >= 10) {
				break;
			}
			xml.append("  <actor>\n");
			appendValue(xml, "name", person.getName());
			appendValue(xml, "role", person.getCharacter());
			xml.append("  </actor>\n");
		}

		xml.append("</movie>\n");
		return xml.toString();
	}

	protected static String toEpisodeNfo(Episode episode) {
		StringBuilder xml = new StringBuilder(256);
		xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
		xml.append("<episodedetails>\n");

		SeriesInfo info = episode.getSeriesInfo();
		if (info != null && info.getId() != null) {
			xml.append("  <uniqueid type=\"tmdb\" default=\"true\">" + escape(String.valueOf(info.getId())) + "</uniqueid>\n");
		}

		appendValue(xml, "title", episode.getTitle());
		appendValue(xml, "season", toInt(episode.getSeason()) > 0 ? String.valueOf(episode.getSeason()) : null);
		appendValue(xml, "episode", toInt(episode.getEpisode()) > 0 ? String.valueOf(episode.getEpisode()) : null);
		if (episode.getAirdate() != null) {
			appendValue(xml, "aired", episode.getAirdate().toString());
		}

		if (info != null) {
			appendValue(xml, "series", info.getName());
		}

		xml.append("</episodedetails>\n");
		return xml.toString();
	}

	private static void appendValue(StringBuilder xml, String tag, String value) {
		if (value == null || value.isEmpty()) {
			return;
		}
		xml.append("  <" + tag + ">" + escape(value) + "</" + tag + ">\n");
	}

	private static String escape(String value) {
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	private static File writeString(String text, File destination) {
		try {
			writeFile(ByteBuffer.wrap(text.getBytes(UTF_8)), destination);
			return destination;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private static int toInt(Integer value) {
		return value == null ? 0 : value;
	}
}