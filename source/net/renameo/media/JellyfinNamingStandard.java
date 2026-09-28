package net.renameo.media;

/**
 * Library layout recommended by Jellyfin: {@code Movies/Name (Year) [tmdbid-ID]/Name (Year) [tmdbid-ID].ext} and {@code Shows/Series (Year) [tvdbid-ID]/Season 01/Series (Year) - S01E01 - Title.ext}.
 */
public class JellyfinNamingStandard extends PlexNamingStandard {

	@Override
	public String getSeriesFolder() {
		return "Shows";
	}

	@Override
	protected String formatIdTag(String db, String id) {
		return "[" + db + "id-" + id + "]";
	}

}
