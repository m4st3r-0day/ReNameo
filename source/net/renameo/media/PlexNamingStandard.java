package net.renameo.media;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static net.renameo.similarity.Normalization.*;
import static net.renameo.util.FileUtilities.*;
import static net.renameo.web.EpisodeUtilities.*;

import java.util.Objects;

import net.renameo.web.AudioTrack;
import net.renameo.web.Episode;
import net.renameo.web.EpisodeFormat;
import net.renameo.web.Movie;
import net.renameo.web.MoviePart;
import net.renameo.web.SeriesInfo;

/**
 * Library layout recommended by Plex: {@code Movies/Name (Year) {tmdb-ID}/Name (Year) {tmdb-ID}.ext} and {@code TV Shows/Series (Year) {tvdb-ID}/Season 01/Series (Year) - S01E01 - Title.ext}.
 */
public class PlexNamingStandard {

	public String getPath(Object o) {
		if (o instanceof Episode)
			return getPath((Episode) o);
		if (o instanceof Movie)
			return getPath((Movie) o);
		if (o instanceof AudioTrack)
			return getPath((AudioTrack) o);

		return null;
	}

	public String getPath(Episode e) {
		if (isAnime(e)) {
			// Anime
			return path(getAnimeFolder(), getSeriesFolder(e), getEpisodeName(e));
		} else {
			// TV Series
			return path(getSeriesFolder(), getSeriesFolder(e), getSeasonFolder(e), getEpisodeName(e));
		}
	}

	public String getPath(Movie m) {
		String folder = getMovieName(m);
		String name = folder;

		// Movie (multi-part)
		if (m instanceof MoviePart) {
			name = String.format("%s - part%d", name, ((MoviePart) m).getPartIndex());
		}

		return path(getMovieFolder(), folder, name);
	}

	/**
	 * Extras (deleted scenes, featurettes, trailers, ...) go into a sub folder of the movie or series they belong to and keep their own title.
	 */
	public String getExtraPath(Object o, ExtraType type, String title) {
		String parent = null;
		if (o instanceof Movie) {
			parent = path(getMovieFolder(), getMovieName((Movie) o));
		} else if (o instanceof Episode) {
			Episode e = (Episode) o;
			parent = isAnime(e) ? path(getAnimeFolder(), getSeriesFolder(e)) : path(getSeriesFolder(), getSeriesFolder(e));
		}

		if (parent == null) {
			return null;
		}

		return parent + "/" + path(getExtraFolder(type), title);
	}

	public String getExtraFolder(ExtraType type) {
		return type.getFolder();
	}

	public String getPath(AudioTrack a) {
		// Music
		String name = first(a.getTrackTitle(), a.getTitle());

		// prepend track number
		if (a.getTrack() != null) {
			name = String.format("%02d - %s", a.getTrack(), name);
		}

		return path(getMusicFolder(), first(a.getAlbumArtist(), a.getArtist()), a.getAlbum(), name);
	}

	protected static String path(String... name) {
		return stream(name).filter(Objects::nonNull).map(s -> {
			return replaceColon(s, ".", " - ");
		}).map(s -> {
			return replacePathSeparators(s, " ");
		}).map(s -> {
			return normalizeQuotationMarks(s);
		}).map(s -> {
			return trimTrailingPunctuation(s);
		}).map(s -> {
			return validateFileName(s);
		}).filter(s -> s.length() > 0).collect(joining("/"));
	}

	private static String first(String... options) {
		return stream(options).filter(Objects::nonNull).findFirst().orElse(null);
	}

	public String getMovieFolder() {
		return "Movies";
	}

	public String getSeriesFolder() {
		return "TV Shows";
	}

	public String getAnimeFolder() {
		return "Anime";
	}

	public String getMusicFolder() {
		return "Music";
	}

	/**
	 * Name (Year) plus the database id tag, used both for the movie folder and the movie file.
	 */
	public String getMovieName(Movie m) {
		String name = m.getNameWithYear();
		String tag = m.getTmdbId() > 0 ? formatIdTag("tmdb", String.valueOf(m.getTmdbId())) : m.getImdbId() > 0 ? formatIdTag("imdb", String.format("tt%07d", m.getImdbId())) : null;
		return tag == null ? name : name + " " + tag;
	}

	public String getSeriesFolder(Episode e) {
		String name = getSeriesNameWithYear(e);
		String tag = getSeriesIdTag(e);
		return tag == null ? name : name + " " + tag;
	}

	public String getSeasonFolder(Episode e) {
		if (e.getSpecial() != null) {
			return "Season 00";
		}

		if (e.getSeason() != null) {
			return String.format("Season %02d", e.getSeason());
		}

		return null;
	}

	public String getEpisodeName(Episode e) {
		return String.join(" - ", isAnime(e) ? formatSeriesName(e) : getSeriesNameWithYear(e), formatEpisodeNumbers(e), formatEpisodeTitle(e));
	}

	public String formatSeriesName(Episode e) {
		if (isAnime(e)) {
			// Anime
			return e.getSeriesInfo().getName(); // series info name is the primary Anime name
		} else {
			// TV Series
			return e.getSeriesName();
		}
	}

	/**
	 * Series names are ambiguous (remakes, reboots), so the first air year is part of the folder name.
	 */
	public String getSeriesNameWithYear(Episode e) {
		String name = formatSeriesName(e);
		SeriesInfo info = e.getSeriesInfo();
		if (info != null && info.getStartDate() != null && !name.matches(".*\\(\\d{4}\\)$")) {
			return String.format("%s (%d)", name, info.getStartDate().getYear());
		}
		return name;
	}

	protected String getSeriesIdTag(Episode e) {
		SeriesInfo info = e.getSeriesInfo();
		if (info == null || info.getId() == null || info.getDatabase() == null) {
			return null;
		}

		String db = info.getDatabase().toLowerCase();
		if (db.contains("tvdb")) {
			return formatIdTag("tvdb", info.getId().toString());
		}
		if (db.contains("themoviedb")) {
			return formatIdTag("tmdb", info.getId().toString());
		}
		return null;
	}

	protected String formatIdTag(String db, String id) {
		return "{" + db + "-" + id + "}";
	}

	public String formatEpisodeNumbers(Episode e) {
		if (isAnime(e)) {
			// Anime
			return EpisodeFormat.SeasonEpisode.formatSxE(e);
		} else {
			// TV Series
			return EpisodeFormat.SeasonEpisode.formatS00E00(e);
		}
	}

	public String formatEpisodeTitle(Episode e) {
		// enforce title length limit by default
		return truncateText(EpisodeFormat.SeasonEpisode.formatMultiTitle(getMultiEpisodeList(e)), TITLE_MAX_LENGTH);
	}

	public static final int TITLE_MAX_LENGTH = 150;

}
