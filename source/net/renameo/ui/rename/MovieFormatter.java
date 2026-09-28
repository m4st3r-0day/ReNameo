
package net.renameo.ui.rename;

import static net.renameo.MediaTypes.*;
import static net.renameo.util.FileUtilities.*;

import java.io.File;
import java.util.Map;

import net.renameo.media.ExtraType;
import net.renameo.similarity.Match;
import net.renameo.web.Movie;
import net.renameo.web.MoviePart;

class MovieFormatter implements MatchFormatter {

	@Override
	public boolean canFormat(Match<?, ?> match) {
		return match.getValue() instanceof Movie;
	}

	@Override
	public String preview(Match<?, ?> match) {
		Movie movie = (Movie) match.getValue();

		// bonus material keeps its own title and gets the extras suffix instead of taking over the movie name
		if (match.getCandidate() instanceof File) {
			File file = (File) match.getCandidate();
			ExtraType extra = VIDEO_FILES.accept(file) ? ExtraType.detect(file) : null;
			if (extra != null) {
				return replacePathSeparators(ExtraType.getTitle(file) + "-" + extra.getSuffix());
			}
		}

		StringBuilder name = new StringBuilder();

		// format as single-file or multi-part movie
		name.append(movie.getName()).append(" (").append(movie.getYear()).append(")");

		if (movie instanceof MoviePart) {
			MoviePart part = (MoviePart) movie;
			if (part.getPartCount() > 1) {
				name.append(".CD").append(part.getPartIndex());
			}
		}

		// remove path separators if the name contains any / or \
		return replacePathSeparators(name);
	}

	@Override
	public String format(Match<?, ?> match, boolean extension, Map<?, ?> context) {
		return preview(match);
	}

}
