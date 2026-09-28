package net.renameo.ui.rename;

import static net.renameo.Logging.*;
import static net.renameo.util.FileUtilities.*;

import java.awt.Component;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import javax.swing.Icon;

import net.renameo.ResourceManager;
import net.renameo.mediainfo.ImageMetadata;
import net.renameo.similarity.Match;
import net.renameo.web.Datasource;
import net.renameo.web.SortOrder;

public class PhotoFileMatcher implements Datasource, AutoCompleteMatcher {

	@Override
	public String getIdentifier() {
		return "exif";
	}

	@Override
	public String getName() {
		return "Exif Metadata";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.exif");
	}

	@Override
	public List<Match<File, ?>> match(Collection<File> files, boolean strict, SortOrder order, Locale locale, boolean autodetection, Component parent) throws Exception {
		List<Match<File, ?>> matches = new ArrayList<Match<File, ?>>();

		for (File f : filter(files, ImageMetadata.SUPPORTED_FILE_TYPES)) {
			try {
				ImageMetadata metadata = new ImageMetadata(f);
				if (metadata.getDateTaken().isPresent()) {
					matches.add(new Match<File, File>(f, f)); // photo mode is the same as generic file mode (but only select photo files)
				}
			} catch (Exception e) {
				debug.warning(format("%s [%s]", e, f));
			}
		}

		return matches;
	}

}
