package net.renameo.format;

import java.io.File;
import java.util.List;

import net.renameo.media.LibraryLayout;

/**
 * Path by a media server convention, e.g. {@code Shows/Neagley (2024)/Season 01/Neagley (2024) - S01E01 - Title.mkv}, that knows where the file is now:
 * {@code {jellyfin.tidy}} renames the folder of the series in place and adds the season folder, {@code {jellyfin.name}} only renames the file.
 */
public class MediaServerPath extends File {

	private final transient File original;
	private final transient List<String> titles;

	MediaServerPath(String path, File original, List<String> titles) {
		super(path);
		this.original = original;
		this.titles = titles;
	}

	public File getTidy() {
		return LibraryLayout.tidy(this, original, titles);
	}

}
