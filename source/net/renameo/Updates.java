package net.renameo;

import static java.nio.charset.StandardCharsets.*;
import static net.renameo.util.JsonUtilities.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;

import net.renameo.util.PreferencesMap.PreferencesEntry;

/**
 * New versions are published as GitHub releases: ask GitHub for the latest one (at most once a day, only in the app).
 */
public final class Updates {

	public static final String REPOSITORY = "m4st3r-0day/ReNameo";

	public static class Release {

		public final String version;
		public final String url;
		public final String notes;

		public Release(String version, String url, String notes) {
			this.version = version;
			this.url = url;
			this.notes = notes == null ? "" : notes;
		}
	}

	private static final Settings settings = Settings.forPackage(Updates.class);

	public static final PreferencesEntry<String> automatic = settings.entry("update.automatic").defaultValue("true");
	private static final PreferencesEntry<String> lastCheck = settings.entry("update.last").defaultValue("0");
	public static final PreferencesEntry<String> skipped = settings.entry("update.skip").defaultValue("");

	private static final long DAY = 24 * 60 * 60 * 1000L;

	private Updates() {
		throw new UnsupportedOperationException();
	}

	/**
	 * @return true if the automatic check is on and the last one was more than a day ago
	 */
	public static boolean isDue() {
		long last = 0;
		try {
			last = Long.parseLong(lastCheck.getValue());
		} catch (NumberFormatException e) {
			// check now
		}
		return Boolean.parseBoolean(automatic.getValue()) && System.currentTimeMillis() - last > DAY;
	}

	/**
	 * The latest published release, or null if there is none yet.
	 */
	public static Release fetchLatest() throws IOException {
		HttpURLConnection c = (HttpURLConnection) URI.create("https://api.github.com/repos/" + REPOSITORY + "/releases/latest").toURL().openConnection();
		c.setConnectTimeout(10000);
		c.setReadTimeout(15000);
		c.setRequestProperty("Accept", "application/vnd.github+json");
		c.setRequestProperty("User-Agent", Settings.getApplicationName() + "/" + Settings.getApplicationVersion());

		int status = c.getResponseCode();
		if (status == 404) {
			return null;
		}
		if (status != 200) {
			throw new IOException("GitHub answered with error " + status);
		}

		try (InputStream in = c.getInputStream()) {
			Object json = readJson(new String(in.readAllBytes(), UTF_8));
			lastCheck.setValue(String.valueOf(System.currentTimeMillis()));
			return new Release(getString(json, "tag_name").replaceFirst("^[vV]", ""), getString(json, "html_url"), getString(json, "body"));
		}
	}

	/**
	 * @return true if the release is newer than this app and the user didn't choose to skip it
	 */
	public static boolean isWanted(Release release, boolean manual) {
		return release != null && compare(release.version, Settings.getApplicationVersion()) > 0 && (manual || !release.version.equals(skipped.getValue()));
	}

	/**
	 * Compare versions like 1.10.0 and 1.9.2 by their numbers: 1.10.0 is newer.
	 */
	public static int compare(String a, String b) {
		String[] x = a.replaceFirst("^[vV]", "").split("[.\\-+]");
		String[] y = b.replaceFirst("^[vV]", "").split("[.\\-+]");
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int u = i < x.length ? number(x[i]) : 0;
			int v = i < y.length ? number(y[i]) : 0;
			if (u != v) {
				return Integer.compare(u, v);
			}
		}
		return 0;
	}

	private static int number(String part) {
		String digits = part.replaceAll("\\D.*$", "");
		return digits.isEmpty() ? 0 : Integer.parseInt(digits);
	}

}
