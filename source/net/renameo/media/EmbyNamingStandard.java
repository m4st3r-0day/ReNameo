package net.renameo.media;

/**
 * Library layout recommended by Emby: {@code Movies/Name (Year) [tmdbid=ID]/Name (Year) [tmdbid=ID].ext} and {@code TV Shows/Series (Year) [tmdbid=ID]/Season 01/Series (Year) - S01E01 - Title.ext}.
 */
public class EmbyNamingStandard extends PlexNamingStandard {

	@Override
	protected String formatIdTag(String db, String id) {
		return "[" + db + "id=" + id + "]";
	}

}
