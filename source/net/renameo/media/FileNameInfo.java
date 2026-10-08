package net.renameo.media;

import static java.util.regex.Pattern.*;
import static net.renameo.MediaTypes.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.renameo.Language;
import net.renameo.similarity.SeasonEpisodeMatcher.SxE;
import net.renameo.util.FileUtilities;

/**
 * What the file name tells about a media file before any database lookup: title, year, season / episode, quality tags and languages. Used for the technical badges and to show how a name was interpreted.
 */
public class FileNameInfo {

	private static final Pattern RESOLUTION = compile("(?<![\\p{Alnum}])(2160p|4K|UHD|1080p|1080i|720p|576p|480p)(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern HEVC = compile("(?<![\\p{Alnum}])(x265|h\\.?265|hevc)(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern AVC = compile("(?<![\\p{Alnum}])(x264|h\\.?264|avc)(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern AV1 = compile("(?<![\\p{Alnum}])av1(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern DOLBY_VISION = compile("(?<![\\p{Alnum}])(dv|dovi|dolby[ ._-]?vision)(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern HDR = compile("(?<![\\p{Alnum}])(hdr10\\+|hdr10plus|hdr10|hdr)(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern SOURCE = compile("(?<![\\p{Alnum}])(remux|blu[ ._-]?ray|bdrip|brrip|web[ ._-]?dl|webrip|web|hdtv|dvdrip|dvd)(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern ATMOS = compile("(?<![\\p{Alnum}])atmos(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern AUDIO = compile("(?<![\\p{Alnum}])(truehd|dts[ ._-]?hd(?:[ ._-]?ma)?|dts[ ._-]?x|dts|ddp|dd\\+|e[ ._-]?ac[ ._-]?3|ac[ ._-]?3|dd(?=[ ._]?[257][ ._][01])|aac|flac|opus|mp3)(?:[ ._]?[257][ ._][01])?(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern GROUP_SUFFIX = compile("-([\\p{Alnum}]{2,15})$");
	private static final Pattern TITLE_END = compile("[\\(\\[]|(?<=\\S)[\\s._-]+(?:19|20)\\d{2}(?![\\p{Alnum}])|(?<![\\p{Alnum}])(?:2160p|1080p|720p|480p|4k)(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern LANGUAGE = compile("(?<![\\p{Alnum}])(ita|italian|eng|english|multi|fre|french|ger|german|spa|spanish|jpn|japanese|kor|korean)(?![\\p{Alnum}])", CASE_INSENSITIVE);
	private static final Pattern SUBTITLE_LANGUAGE = compile("[._]([a-z]{2,3})(?:[._](forced|sdh|cc|hi))?$", CASE_INSENSITIVE);

	public final String fileName;
	public final String title;
	public final Integer year;
	public final Integer season;
	public final Integer episode;
	public final String resolution;
	public final String videoCodec;
	public final String dynamicRange;
	public final String audio;
	public final String source;
	public final String releaseGroup;
	public final List<String> languages;
	public final String subtitleLanguage;

	private FileNameInfo(File file) {
		this.fileName = file.getName();

		String name = FileUtilities.getNameWithoutExtension(file.getName());
		String path = name;
		File parent = file.getParentFile();
		if (parent != null) {
			path = parent.getName() + " " + name;
		}

		List<SxE> sxe = null;
		try {
			sxe = MediaDetection.parseEpisodeNumber(file, true); // explicit markers only, so years are not read as 20x01
		} catch (Exception e) {
			// not an episode
		}
		SxE first = sxe == null || sxe.isEmpty() ? null : sxe.get(0);
		this.season = first != null && first.season >= 0 ? first.season : null;
		this.episode = first != null && first.episode >= 0 ? first.episode : null;

		List<Integer> years = MediaDetection.parseMovieYear(path);
		this.year = years.isEmpty() ? null : years.get(years.size() - 1);

		this.title = guessTitle(file);
		this.resolution = normalizeResolution(find(RESOLUTION, path));
		this.videoCodec = find(HEVC, path) != null ? "HEVC" : find(AVC, path) != null ? "AVC" : find(AV1, path) != null ? "AV1" : null;

		String hdr = find(HDR, path);
		boolean dv = find(DOLBY_VISION, path) != null;
		this.dynamicRange = dv && hdr != null ? "DV HDR" : dv ? "DV" : hdr != null ? (hdr.toUpperCase().contains("+") || hdr.toUpperCase().contains("PLUS") ? "HDR10+" : "HDR") : null;

		String audio = normalizeAudio(find(AUDIO, path));
		this.audio = find(ATMOS, path) != null ? (audio == null ? "Atmos" : audio + " Atmos") : audio;
		this.source = normalizeSource(find(SOURCE, path));

		String group = null;
		try {
			group = MediaDetection.releaseInfo.getReleaseGroup(name);
		} catch (Exception e) {
			// no release group
		}
		if (group == null && (resolution != null || source != null)) {
			Matcher g = GROUP_SUFFIX.matcher(name);
			if (g.find() && !g.group(1).matches("(?i)\\d+p|dl|rip|hd")) {
				group = g.group(1);
			}
		}
		this.releaseGroup = group;

		Set<String> languages = new LinkedHashSet<String>();
		Matcher m = LANGUAGE.matcher(path);
		while (m.find()) {
			languages.add(normalizeLanguage(m.group(1)));
		}

		String subtitle = null;
		if (SUBTITLE_FILES.accept(file)) {
			Matcher s = SUBTITLE_LANGUAGE.matcher(name);
			if (s.find() && Language.findLanguage(s.group(1)) != null) {
				subtitle = s.group(0).substring(1);
				languages.add(Language.findLanguage(s.group(1)).getISO3().toUpperCase(Locale.ROOT));
			}
		}
		this.subtitleLanguage = subtitle;
		this.languages = new ArrayList<String>(languages);
	}

	public static FileNameInfo parse(File file) {
		return new FileNameInfo(file);
	}

	/**
	 * Language tag of a subtitle file name, e.g. "it" for "Movie.it.srt" or "eng.forced" for "Movie.eng.forced.srt".
	 */
	public static String getSubtitleLanguageTag(File file) {
		if (!SUBTITLE_FILES.accept(file)) {
			return null;
		}
		Matcher s = SUBTITLE_LANGUAGE.matcher(FileUtilities.getNameWithoutExtension(file.getName()));
		if (s.find() && Language.findLanguage(s.group(1)) != null) {
			return s.group(0).substring(1);
		}
		return null;
	}

	/**
	 * Short badges for the list rows: 4K, HDR, HEVC, DTS, ITA, SRT, ...
	 */
	public List<String> getBadges() {
		List<String> badges = new ArrayList<String>();
		if (resolution != null) {
			badges.add(resolution);
		}
		if (dynamicRange != null) {
			badges.add(dynamicRange);
		}
		if (videoCodec != null) {
			badges.add(videoCodec);
		}
		if (audio != null) {
			badges.add(audio);
		}
		badges.addAll(languages);
		String ext = FileUtilities.getExtension(fileName);
		if (ext != null && SUBTITLE_FILES.acceptExtension(ext)) {
			badges.add(ext.toUpperCase(Locale.ROOT));
		}
		return badges;
	}

	/**
	 * How the file name was interpreted, as label / value pairs.
	 */
	public Map<String, String> getFields() {
		Map<String, String> fields = new LinkedHashMap<String, String>();
		put(fields, "Title", title);
		put(fields, "Year", year);
		put(fields, "Season", season == null ? null : String.format("%02d", season));
		put(fields, "Episode", episode == null ? null : String.format("%02d", episode));
		put(fields, "Quality", resolution);
		put(fields, "Video", join(videoCodec, dynamicRange));
		put(fields, "Audio", audio);
		put(fields, "Source", source);
		put(fields, "Languages", languages.isEmpty() ? null : String.join(", ", languages));
		put(fields, "Release group", releaseGroup);
		return fields;
	}

	private static void put(Map<String, String> fields, String key, Object value) {
		if (value != null && value.toString().length() > 0) {
			fields.put(key, value.toString());
		}
	}

	private static String join(String... values) {
		StringBuilder sb = new StringBuilder();
		for (String v : values) {
			if (v != null) {
				sb.append(sb.length() > 0 ? " " : "").append(v);
			}
		}
		return sb.length() == 0 ? null : sb.toString();
	}

	/**
	 * Title as written in the (preferred) file or folder name: everything before the episode marker, the year or the first quality tag, keeping the original casing.
	 */
	private static String guessTitle(File file) {
		try {
			List<String> names = MediaDetection.getMovieNameCandidates(file);
			String name = names.isEmpty() ? FileUtilities.getNameWithoutExtension(file.getName()) : names.get(0);
			String head = MediaDetection.getSeasonEpisodeMatcher(true).head(name);
			if (head != null && head.length() > 1) {
				name = head;
			}
			Matcher end = TITLE_END.matcher(name);
			if (end.find() && end.start() > 0) {
				name = name.substring(0, end.start());
			}
			if (!name.contains(" ")) {
				name = name.replaceAll("[._]+", " ");
			}
			name = name.replaceAll("\\s+", " ").replaceAll("^[\\s\\p{Punct}]+|[\\s\\p{Punct}&&[^)!?'.]]+$", "").trim();
			return name.isEmpty() ? null : name;
		} catch (Exception e) {
			return null;
		}
	}

	private static String find(Pattern pattern, String text) {
		Matcher m = pattern.matcher(text);
		return m.find() ? m.group(1 <= m.groupCount() ? 1 : 0) : null;
	}

	private static String normalizeResolution(String r) {
		if (r == null) {
			return null;
		}
		switch (r.toLowerCase()) {
		case "2160p":
		case "4k":
		case "uhd":
			return "4K";
		default:
			return r.toLowerCase();
		}
	}

	private static String normalizeAudio(String a) {
		if (a == null) {
			return null;
		}
		String s = a.toUpperCase().replaceAll("[ ._-]", "");
		if (s.startsWith("DTSHD")) {
			return "DTS-HD";
		}
		if (s.equals("DTSX")) {
			return "DTS:X";
		}
		if (s.equals("DDP") || s.equals("DD+") || s.equals("EAC3")) {
			return "DD+";
		}
		if (s.equals("AC3") || s.startsWith("DD5")) {
			return "DD";
		}
		if (s.equals("TRUEHD")) {
			return "TrueHD";
		}
		return s;
	}

	private static String normalizeSource(String s) {
		if (s == null) {
			return null;
		}
		String v = s.toLowerCase().replaceAll("[ ._-]", "");
		switch (v) {
		case "remux":
			return "Remux";
		case "bluray":
		case "bdrip":
		case "brrip":
			return "BluRay";
		case "webdl":
		case "web":
			return "WEB-DL";
		case "webrip":
			return "WEBRip";
		case "hdtv":
			return "HDTV";
		default:
			return "DVD";
		}
	}

	private static String normalizeLanguage(String l) {
		switch (l.toLowerCase()) {
		case "italian":
			return "ITA";
		case "english":
			return "ENG";
		case "french":
			return "FRE";
		case "german":
			return "GER";
		case "spanish":
			return "SPA";
		case "japanese":
			return "JPN";
		case "korean":
			return "KOR";
		default:
			return l.toUpperCase(Locale.ROOT);
		}
	}

}
