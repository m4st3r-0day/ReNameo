package net.renameo.media;

import static java.util.regex.Pattern.*;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.renameo.util.FileUtilities;

/**
 * Bonus material that belongs to a movie or series but is not the feature itself. Folder names follow the Plex / Jellyfin local extras conventions, which both servers understand.
 */
public enum ExtraType {

	DELETED_SCENES("Deleted Scenes", "deleted", "deleted[ ._-]?scenes?|deleted|scene[ ._-]?eliminat[ae]|scene[ ._-]?tagliat[ae]"),

	BEHIND_THE_SCENES("Behind The Scenes", "behindthescenes", "behind[ ._-]?the[ ._-]?scenes|making[ ._-]?of|dietro[ ._-]?le[ ._-]?quinte"),

	FEATURETTES("Featurettes", "featurette", "featurettes?|gag[ ._-]?reels?|bloopers?|outtakes?|papere"),

	INTERVIEWS("Interviews", "interview", "interviews?|intervist[ae]"),

	SHORTS("Shorts", "short", "shorts?|cortometraggi?"),

	TRAILERS("Trailers", "trailer", "trailers?|teasers?"),

	SCENES("Scenes", "scene", "scenes?|clips?"),

	SAMPLES("Samples", "sample", "samples?"),

	OTHER("Other", "other", "extras?|bonus|special[ ._-]?features|contenuti[ ._-]?speciali|other");

	private final String folder;
	private final String suffix;
	private final Pattern folderPattern;
	private final Pattern namePattern;

	private ExtraType(String folder, String suffix, String keywords) {
		this.folder = folder;
		this.suffix = suffix;
		this.folderPattern = compile("^(?:" + keywords + ")$", CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS);
		this.namePattern = compile("(?<![\\p{L}\\p{N}])(?:" + keywords + ")(?![\\p{L}\\p{N}])", CASE_INSENSITIVE | UNICODE_CHARACTER_CLASS);
	}

	public String getFolder() {
		return folder;
	}

	/**
	 * File name suffix both Plex and Jellyfin use to recognize extras next to the main feature, e.g. "Scene 1-deleted.mkv".
	 */
	public String getSuffix() {
		return suffix;
	}

	/**
	 * Folder names such as "Deleted Scenes", "Featurettes" or "Extras".
	 */
	public static boolean isExtrasFolder(String name) {
		for (ExtraType type : values()) {
			if (type.folderPattern.matcher(name.trim()).matches()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Plex style suffixes like "Movie-deleted.mkv" or "Interview with the Director-interview.mp4".
	 */
	private static final Pattern SUFFIX = compile("[-_. ](deleted|behindthescenes|featurette|interview|scene|short|trailer|other|sample)$", CASE_INSENSITIVE);

	/**
	 * Detect bonus material by its folder (e.g. "Deleted Scenes/", "Extras/") or by keywords in the file name. Returns null for regular movies and episodes.
	 */
	public static ExtraType detect(File file) {
		if (file == null) {
			return null;
		}

		// folder conventions are the most reliable signal
		File folder = file.getParentFile();
		for (int depth = 0; folder != null && depth < 2; depth++, folder = folder.getParentFile()) {
			for (ExtraType type : values()) {
				if (type.folderPattern.matcher(folder.getName().trim()).matches()) {
					return type;
				}
			}
		}

		String name = FileUtilities.getNameWithoutExtension(file.getName());

		Matcher suffix = SUFFIX.matcher(name);
		if (suffix.find()) {
			switch (suffix.group(1).toLowerCase()) {
			case "deleted":
				return DELETED_SCENES;
			case "behindthescenes":
				return BEHIND_THE_SCENES;
			case "featurette":
				return FEATURETTES;
			case "interview":
				return INTERVIEWS;
			case "scene":
				return SCENES;
			case "short":
				return SHORTS;
			case "trailer":
				return TRAILERS;
			case "sample":
				return SAMPLES;
			default:
				return OTHER;
			}
		}

		// keywords in the file name, but only the unambiguous ones (a movie can be called "Bonus" or "The Interview")
		for (ExtraType type : new ExtraType[] { DELETED_SCENES, BEHIND_THE_SCENES, FEATURETTES, TRAILERS, SAMPLES }) {
			if (type.namePattern.matcher(name).find()) {
				return type;
			}
		}

		return null;
	}

	/**
	 * The extra keeps its own title, cleaned from release noise and the extras suffix.
	 */
	public static String getTitle(File file) {
		String name = FileUtilities.getNameWithoutExtension(file.getName());
		name = SUFFIX.matcher(name).replaceFirst("");
		name = name.replaceAll("[._]+", " ").replaceAll("\\s+", " ").trim();
		return name.isEmpty() ? file.getName() : name;
	}

}
