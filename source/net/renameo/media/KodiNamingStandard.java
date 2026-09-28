package net.renameo.media;

/**
 * Library layout Kodi scrapes reliably: {@code Movies/Name (Year)/Name (Year).ext} and {@code TV Shows/Series (Year)/Season 01/Series (Year) - S01E01 - Title.ext}, without database id tags.
 */
public class KodiNamingStandard extends PlexNamingStandard {

	@Override
	protected String formatIdTag(String db, String id) {
		return null;
	}

}
