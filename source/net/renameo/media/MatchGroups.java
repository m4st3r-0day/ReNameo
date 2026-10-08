package net.renameo.media;

import static net.renameo.MediaTypes.*;
import static net.renameo.media.MediaDetection.*;
import static net.renameo.util.FileUtilities.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.renameo.similarity.Match;
import net.renameo.util.FileUtilities.ParentFilter;
import net.renameo.web.Episode;
import net.renameo.web.Movie;
import net.renameo.web.MovieIdentificationService;
import net.renameo.web.MoviePart;

/**
 * Grouping of files around the movies and episodes they belong to, shared by the command line and the app so both rename the same way.
 */
public final class MatchGroups {

	private MatchGroups() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Files that belong to a movie file (e.g. subtitles with the same name next to it). Every file that is assigned is removed from the orphans.
	 */
	public static Map<File, List<File>> mapDerivatives(Collection<File> movieFiles, List<File> orphanedFiles) {
		Map<File, List<File>> derivatesByMovieFile = new HashMap<File, List<File>>();
		for (File movieFile : movieFiles) {
			derivatesByMovieFile.put(movieFile, new ArrayList<File>());
		}
		for (File file : orphanedFiles) {
			List<File> orphanParent = listPath(file);
			for (File movieFile : movieFiles) {
				if (orphanParent.contains(movieFile.getParentFile()) && isDerived(file, movieFile)) {
					derivatesByMovieFile.get(movieFile).add(file);
					break;
				}
			}
		}
		for (List<File> derivates : derivatesByMovieFile.values()) {
			orphanedFiles.removeAll(derivates);
		}
		return derivatesByMovieFile;
	}

	/**
	 * Look up the movies named in .nfo files (the selected ones and those next to the movies) and assign them to the matching movie files and disc folders.
	 */
	public static void matchNfoFiles(Collection<File> fileset, Set<File> movieFiles, Set<File> nfoFiles, MovieIdentificationService service, Locale locale, Map<File, Movie> movieByFile, Logger logger) {
		// collect useful nfo files even if they are not part of the selected fileset
		Set<File> effectiveNfoFileSet = new TreeSet<File>(nfoFiles);
		for (File dir : mapByFolder(movieFiles).keySet()) {
			effectiveNfoFileSet.addAll(getChildren(dir, NFO_FILES));
		}
		for (File dir : filter(fileset, FOLDERS)) {
			effectiveNfoFileSet.addAll(getChildren(dir, NFO_FILES));
		}

		for (File nfo : effectiveNfoFileSet) {
			try {
				Movie movie = grepMovie(nfo, service, locale);

				// ignore illegal nfos
				if (movie == null) {
					continue;
				}

				if (nfoFiles.contains(nfo)) {
					movieByFile.put(nfo, movie);
				}

				if (isDiskFolder(nfo.getParentFile())) {
					// special handling for disk folders
					for (File folder : fileset) {
						if (nfo.getParentFile().equals(folder)) {
							movieByFile.put(folder, movie);
						}
					}
				} else {
					// match movie info to movie files that match the nfo file name
					SortedSet<File> siblingMovieFiles = new TreeSet<File>(filter(movieFiles, new ParentFilter(nfo.getParentFile())));
					String baseName = stripReleaseInfo(getName(nfo)).toLowerCase();

					for (File movieFile : siblingMovieFiles) {
						if (!baseName.isEmpty() && stripReleaseInfo(getName(movieFile)).toLowerCase().startsWith(baseName)) {
							movieByFile.put(movieFile, movie);
						}
					}
				}
			} catch (Exception e) {
				logger.log(Level.WARNING, "Failed to grep IMDbID: " + nfo.getName(), e);
			}
		}
	}

	/**
	 * One match per file: multi-part movies become CD1, CD2, ..., bonus material keeps the plain movie, and derived files follow their movie part.
	 */
	public static List<Match<File, ?>> movieMatches(Map<Movie, ? extends Collection<File>> filesByMovie, Map<File, List<File>> derivatesByMovieFile) {
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		filesByMovie.forEach((movie, fs) -> {
			// bonus material belongs to the movie but must not be numbered as CD1, CD2, ...
			SortedSet<File> features = new TreeSet<File>();
			for (File f : fs) {
				if (VIDEO_FILES.accept(f) && ExtraType.detect(f) != null) {
					matches.add(new Match<File, Movie>(f, movie.clone()));
				} else {
					features.add(f);
				}
			}

			groupByMediaCharacteristics(features).forEach(moviePartFiles -> {
				// resolve movie parts
				for (int i = 0; i < moviePartFiles.size(); i++) {
					Movie moviePart = moviePartFiles.size() == 1 ? movie : new MoviePart(movie, i + 1, moviePartFiles.size());
					matches.add(new Match<File, Movie>(moviePartFiles.get(i), moviePart.clone()));

					// automatically add matches for derived files
					List<File> derivates = derivatesByMovieFile.get(moviePartFiles.get(i));
					if (derivates != null) {
						for (File derivate : derivates) {
							matches.add(new Match<File, Movie>(derivate, moviePart.clone()));
						}
					}
				}
			});
		});

		return matches;
	}

	/**
	 * Matches for files that belong to an already matched episode file (e.g. its subtitles).
	 */
	public static List<Match<File, ?>> episodeDerivates(Collection<File> fileset, Collection<File> mediaFiles, List<Match<File, ?>> matches) {
		List<Match<File, ?>> derivateMatches = new ArrayList<Match<File, ?>>();
		SortedSet<File> derivateFiles = new TreeSet<File>(fileset);
		derivateFiles.removeAll(mediaFiles);

		for (File file : derivateFiles) {
			for (Match<File, ?> match : matches) {
				if (file.getPath().startsWith(match.getValue().getParentFile().getPath()) && isDerived(file, match.getValue()) && match.getCandidate() instanceof Episode) {
					derivateMatches.add(new Match<File, Object>(file, ((Episode) match.getCandidate()).clone()));
					break;
				}
			}
		}
		return derivateMatches;
	}

}
