package net.renameo.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Word level diff between an old and a new name, for showing what a rename changes.
 */
public final class TextDiff {

	public enum Kind {
		EQUAL, INSERT, DELETE
	}

	public static final class Segment {

		public final Kind kind;
		public final String text;

		Segment(Kind kind, String text) {
			this.kind = kind;
			this.text = text;
		}

		@Override
		public String toString() {
			return kind + ":" + text;
		}
	}

	private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+");

	private TextDiff() {
		throw new UnsupportedOperationException();
	}

	/**
	 * Words are compared ignoring case and separators ("silo.s01e01" vs "Silo - S01E01"), so only real changes are highlighted.
	 */
	public static List<Segment> diff(String before, String after) {
		List<String> a = words(before);
		List<String> b = words(after);

		int[][] lcs = new int[a.size() + 1][b.size() + 1];
		for (int i = a.size() - 1; i >= 0; i--) {
			for (int j = b.size() - 1; j >= 0; j--) {
				lcs[i][j] = same(a.get(i), b.get(j)) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
			}
		}

		List<Segment> segments = new ArrayList<Segment>();
		int i = 0, j = 0;
		while (i < a.size() || j < b.size()) {
			if (i < a.size() && j < b.size() && same(a.get(i), b.get(j))) {
				add(segments, Kind.EQUAL, b.get(j));
				i++;
				j++;
			} else if (j < b.size() && (i == a.size() || lcs[i][j + 1] >= lcs[i + 1][j])) {
				add(segments, Kind.INSERT, b.get(j));
				j++;
			} else {
				add(segments, Kind.DELETE, a.get(i));
				i++;
			}
		}
		return segments;
	}

	private static List<String> words(String s) {
		List<String> tokens = new ArrayList<String>();
		Matcher m = TOKEN.matcher(s == null ? "" : s);
		while (m.find()) {
			tokens.add(m.group());
		}
		return tokens;
	}

	private static boolean same(String x, String y) {
		boolean wx = Character.isLetterOrDigit(x.charAt(0));
		boolean wy = Character.isLetterOrDigit(y.charAt(0));
		if (!wx && !wy) {
			return true; // any separator matches any separator
		}
		return wx && wy && x.toLowerCase(Locale.ROOT).equals(y.toLowerCase(Locale.ROOT));
	}

	private static void add(List<Segment> segments, Kind kind, String text) {
		// separators between changed words belong to the change, which keeps the highlighting calm
		if (!segments.isEmpty() && segments.get(segments.size() - 1).kind == kind) {
			Segment last = segments.remove(segments.size() - 1);
			segments.add(new Segment(kind, last.text + text));
		} else {
			segments.add(new Segment(kind, text));
		}
	}

}
