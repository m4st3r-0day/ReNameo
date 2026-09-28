
package net.renameo.ui.rename;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.Comparator.*;
import static java.util.stream.Collectors.*;
import static net.renameo.Logging.*;
import static net.renameo.Settings.*;
import static net.renameo.WebServices.*;
import static net.renameo.util.ExceptionUtilities.*;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Stream;

import net.renameo.media.AutoDetection;
import net.renameo.media.AutoDetection.Group;
import net.renameo.media.AutoDetection.Type;
import net.renameo.similarity.Match;
import net.renameo.web.SortOrder;

class AutoDetectMatcher implements AutoCompleteMatcher {

	private AutoCompleteMatcher movie = new MovieMatcher(TheMovieDB);
	private AutoCompleteMatcher episode = new EpisodeListMatcher(TheMovieDB_TV, false);
	private AutoCompleteMatcher anime = new EpisodeListMatcher(AniDB, true);
	private AutoCompleteMatcher music = new MusicMatcher(MediaInfoID3, AcoustID);

	private AutoCompleteMatcher quietMovie = new MovieMatcher(TheMovieDB, true);
	private AutoCompleteMatcher quietEpisode = new EpisodeListMatcher(TheMovieDB_TV, false, true);
	private AutoCompleteMatcher quietAnime = new EpisodeListMatcher(AniDB, true, true);

	@Override
	public List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception {
		Map<Group, Set<File>> groups = new AutoDetection(files, false, locale).group();

		// can't use parallel stream because default fork/join pool doesn't play well with the security manager
		ExecutorService workerThreadPool = Executors.newFixedThreadPool(getPreferredThreadPoolSize());
		try {
			// match groups in parallel
			List<Future<List<Match<File, ?>>>> matches = groups.entrySet().stream().filter(it -> {
				return it.getKey().types().length > 0;
			}).map(it -> {
				return workerThreadPool.submit(() -> match(it.getKey(), it.getValue(), strict, order, locale, autodetection, parent));
			}).collect(toList());

			// collect results
			return matches.stream().flatMap(it -> {
				try {
					return it.get().stream();
				} catch (Exception e) {
					// CancellationException is expected
					if (findCause(e, CancellationException.class) == null) {
						log.log(Level.WARNING, e, cause("Failed to match group", e));
					}
					return Stream.empty();
				}
			}).sorted(comparing(Match::getValue, OriginalOrder.of(files))).collect(toList());
		} finally {
			workerThreadPool.shutdownNow();
		}
	}

	/**
	 * Groups can be misclassified (a new movie that is not in the local index looks like a series) or ambiguous (a movie folder with bonus material). Try the likely media types quietly first, without asking the user, and only fall back to the interactive matcher for files that are still unmatched.
	 */
	private List<Match<File, ?>> match(Group group, Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception {
		List<Type> types = getAttempts(group);
		if (types.isEmpty()) {
			return emptyList();
		}

		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();
		Set<File> remaining = new LinkedHashSet<File>(files);

		for (Type type : types) {
			if (remaining.isEmpty()) {
				break;
			}
			try {
				collect(getMatcher(type, true).match(new ArrayList<File>(remaining), false, order, locale, autodetection, parent), matches, remaining);
			} catch (Exception e) {
				debug.fine(format("%s: %s", type, e));
			}
		}

		if (remaining.size() > 0 && !strict) {
			collect(getMatcher(types.get(0), false).match(new ArrayList<File>(remaining), false, order, locale, autodetection, parent), matches, remaining);
		}

		return matches;
	}

	private static void collect(List<Match<File, ?>> found, List<Match<File, ?>> matches, Set<File> remaining) {
		for (Match<File, ?> it : found) {
			if (remaining.remove(it.getValue())) {
				matches.add(it);
			}
		}
	}

	private static List<Type> getAttempts(Group group) {
		List<Type> types = new ArrayList<Type>(asList(group.types()));
		if (types.size() > 1 && types.remove(Type.Movie)) {
			types.add(0, Type.Movie); // mixed groups are mostly movies with bonus material
		}
		for (Type type : group.types()) {
			if (type == Type.Movie && !types.contains(Type.Series)) {
				types.add(Type.Series);
			} else if ((type == Type.Series || type == Type.Anime) && !types.contains(Type.Movie)) {
				types.add(Type.Movie);
			}
		}
		return types;
	}

	private AutoCompleteMatcher getMatcher(Type type, boolean quiet) {
		switch (type) {
		case Movie:
			return quiet ? quietMovie : movie;
		case Series:
			return quiet ? quietEpisode : episode;
		case Anime:
			return quiet ? quietAnime : anime;
		default:
			return music;
		}
	}

}
