package net.renameo.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static net.renameo.Logging.*;
import static net.renameo.util.JsonUtilities.*;
import static net.renameo.web.OpenSubtitlesHasher.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.Icon;

import net.renameo.Language;
import net.renameo.ResourceManager;
import net.renameo.util.FileUtilities;
import net.renameo.web.OpenSubtitlesSubtitleDescriptor.Property;

/**
 * Client for the current OpenSubtitles REST API (api.opensubtitles.com). The legacy XML-RPC interface of opensubtitles.org no longer accepts new applications.
 *
 * Every request needs an API key (free, created at opensubtitles.com under "API consumers"); downloads count against the quota of the signed in user.
 */
public class OpenSubtitlesRestClient implements SubtitleProvider, VideoHashSubtitleService, MovieIdentificationService {

	private static final String DEFAULT_API = "https://api.opensubtitles.com/api/v1";

	public static final URI API_KEY_PAGE = URI.create("https://www.opensubtitles.com/en/consumers");
	public static final URI REGISTER_PAGE = URI.create("https://www.opensubtitles.com/en/users/sign_up");

	private final String userAgent;

	private String apiKey;
	private String username = "";
	private String password = "";

	private String token;
	private String api = DEFAULT_API;
	private long lastRequest = 0;

	private final Map<String, Object> responseCache = new ConcurrentHashMap<String, Object>();

	public OpenSubtitlesRestClient(String name, String version) {
		this.userAgent = name + " v" + version;
	}

	@Override
	public String getIdentifier() {
		return "OpenSubtitles";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.opensubtitles");
	}

	@Override
	public URI getLink() {
		return URI.create("https://www.opensubtitles.com");
	}

	public synchronized void setApiKey(String apiKey) {
		this.apiKey = apiKey == null || apiKey.trim().isEmpty() ? null : apiKey.trim();
		this.token = null;
	}

	public synchronized boolean hasApiKey() {
		return apiKey != null;
	}

	public synchronized void setCredentials(String username, String password) {
		this.username = username == null ? "" : username;
		this.password = password == null ? "" : password;
		this.token = null;
		this.api = DEFAULT_API;
	}

	public synchronized void setUser(String username, String password_md5) {
		// the REST API needs the real password, which the legacy settings only store as MD5 hash
		setCredentials(username, "");
	}

	public synchronized boolean isAnonymous() {
		return username.isEmpty() || password.isEmpty();
	}

	public synchronized void login() throws Exception {
		if (token != null || isAnonymous()) {
			return;
		}

		String body = String.format("{\"username\":%s,\"password\":%s}", quote(username), quote(password));
		Object response = request("POST", "/login", null, body);
		token = getString(response, "token");

		String baseUrl = getString(response, "base_url");
		if (baseUrl != null && !baseUrl.isEmpty()) {
			api = "https://" + baseUrl + "/api/v1";
		}
	}

	public synchronized void logout() {
		if (token != null) {
			try {
				request("DELETE", "/logout", null, null);
			} catch (Exception e) {
				debug.fine("Logout failed: " + e.getMessage());
			}
			token = null;
			api = DEFAULT_API;
		}
	}

	/**
	 * Account level and download quota of the signed in user.
	 */
	public synchronized Map<?, ?> getUserInfo() throws Exception {
		login();
		return getMap(request("GET", "/infos/user", null, null), "data");
	}

	public Map<?, ?> getServerInfo() throws Exception {
		return getUserInfo();
	}

	public Map<?, ?> getDownloadLimits() throws Exception {
		return getUserInfo();
	}

	@Override
	public List<SubtitleSearchResult> search(String query) throws Exception {
		Map<String, Object> parameters = new TreeMap<String, Object>();
		parameters.put("query", query);

		List<SubtitleSearchResult> results = new ArrayList<SubtitleSearchResult>();
		for (Map<?, ?> item : getMapArray(cachedRequest("/features", parameters), "data")) {
			Map<?, ?> a = getMap(item, "attributes");
			String type = getString(a, "feature_type");
			if ("Episode".equalsIgnoreCase(type)) {
				continue; // episodes are reached through their series
			}
			SubtitleSearchResult.Kind kind = "Movie".equalsIgnoreCase(type) ? SubtitleSearchResult.Kind.Movie : SubtitleSearchResult.Kind.Series;
			String title = getString(a, "title");
			String original = getString(a, "original_title");
			results.add(new SubtitleSearchResult(title, original == null || original.equals(title) ? new String[0] : new String[] { original }, toInt(getString(a, "year")), toInt(getString(a, "imdb_id")), toInt(getString(a, "tmdb_id")), Locale.ENGLISH, kind, 0));
		}
		return results;
	}

	@Override
	public List<SubtitleSearchResult> guess(String tag) throws Exception {
		return search(tag);
	}

	@Override
	public List<Movie> searchMovie(String query, Locale locale) throws Exception {
		List<Movie> movies = new ArrayList<Movie>();
		for (SubtitleSearchResult it : search(query)) {
			if (it.isMovie()) {
				movies.add(it);
			}
		}
		return movies;
	}

	@Override
	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, int[][] episodeFilter, Locale locale) throws Exception {
		if (episodeFilter == null || episodeFilter.length == 0) {
			return getSubtitleList(searchResult, -1, -1, locale);
		}

		// one request per season / episode of the filter, e.g. "season:2" or "season:1 episode:3"
		Set<SubtitleDescriptor> subtitles = new LinkedHashSet<SubtitleDescriptor>();
		for (int[] it : episodeFilter) {
			subtitles.addAll(getSubtitleList(searchResult, it[0], it.length > 1 ? it[1] : -1, locale));
		}
		return new ArrayList<SubtitleDescriptor>(subtitles);
	}

	public synchronized List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, int season, int episode, Locale locale) throws Exception {
		Map<String, Object> parameters = new TreeMap<String, Object>();
		addLanguage(parameters, locale);

		boolean series = searchResult.isSeries() || season >= 0 || episode >= 0;
		if (searchResult.getImdbId() > 0) {
			parameters.put(series ? "parent_imdb_id" : "imdb_id", searchResult.getImdbId());
		} else if (searchResult.getTmdbId() > 0) {
			parameters.put(series ? "parent_tmdb_id" : "tmdb_id", searchResult.getTmdbId());
		} else {
			parameters.put("query", searchResult.getName());
		}
		if (season >= 0) {
			parameters.put("season_number", season);
		}
		if (episode >= 0) {
			parameters.put("episode_number", episode);
		}

		return searchSubtitles(parameters, null, -1);
	}

	@Override
	public Map<File, List<SubtitleDescriptor>> getSubtitleList(File[] files, Locale locale) throws Exception {
		Map<File, List<SubtitleDescriptor>> results = new LinkedHashMap<File, List<SubtitleDescriptor>>();
		for (File file : files) {
			Map<String, Object> parameters = new TreeMap<String, Object>();
			addLanguage(parameters, locale);

			String hash = null;
			if (file.isFile() && file.length() > HASH_CHUNK_SIZE) {
				try {
					hash = computeHash(file);
					parameters.put("moviehash", hash);
				} catch (Exception e) {
					debug.warning("Failed to compute hash: " + e.getMessage());
				}
			}
			parameters.put("query", FileUtilities.getNameWithoutExtension(file.getName()));

			results.put(file, searchSubtitles(parameters, hash, file.length()));
		}
		return results;
	}

	public Map<File, List<SubtitleDescriptor>> getSubtitleListByHash(File[] files, Locale locale) throws Exception {
		return getSubtitleList(files, locale);
	}

	public Map<File, List<SubtitleDescriptor>> getSubtitleListByTag(File[] files, Locale locale) throws Exception {
		return getSubtitleList(files, locale);
	}

	@Override
	public synchronized Movie getMovieDescriptor(Movie id, Locale locale) throws Exception {
		if (id.getImdbId() <= 0) {
			return null;
		}
		Map<String, Object> parameters = new TreeMap<String, Object>();
		parameters.put("imdb_id", id.getImdbId());
		for (Map<?, ?> item : getMapArray(cachedRequest("/features", parameters), "data")) {
			Map<?, ?> a = getMap(item, "attributes");
			return new Movie(getString(a, "title"), new String[0], toInt(getString(a, "year")), toInt(getString(a, "imdb_id")), toInt(getString(a, "tmdb_id")), Locale.ENGLISH);
		}
		return null;
	}

	public synchronized Map<File, Movie> getMovieDescriptors(Collection<File> movieFiles, Locale locale) throws Exception {
		return emptyMap();
	}

	public synchronized Locale detectLanguage(byte[] data) throws Exception {
		return null; // not offered by the REST API
	}

	@Override
	public URI getSubtitleListLink(SubtitleSearchResult searchResult, Locale locale) {
		return getLink();
	}

	/**
	 * Resolve a file id to a temporary download link (this counts against the download quota) and fetch the subtitle.
	 */
	synchronized ByteBuffer download(int fileId) throws Exception {
		login();
		Object response = request("POST", "/download", null, "{\"file_id\":" + fileId + "}");
		String link = getString(response, "link");
		if (link == null) {
			throw new IOException(String.valueOf(getString(response, "message")));
		}
		Integer remaining = getInteger(response, "remaining");
		if (remaining != null) {
			debug.fine("OpenSubtitles downloads remaining today: " + remaining);
		}

		HttpURLConnection c = (HttpURLConnection) new URL(link).openConnection();
		c.setRequestProperty("User-Agent", userAgent);
		try (InputStream in = c.getInputStream()) {
			return ByteBuffer.wrap(readAll(in));
		}
	}

	private List<SubtitleDescriptor> searchSubtitles(Map<String, Object> parameters, String videoHash, long videoSize) throws Exception {
		List<SubtitleDescriptor> subtitles = new ArrayList<SubtitleDescriptor>();

		for (Map<?, ?> item : getMapArray(cachedRequest("/subtitles", parameters), "data")) {
			Map<?, ?> a = getMap(item, "attributes");
			Map<?, ?> feature = getMap(a, "feature_details");
			Map<?, ?>[] files = getMapArray(a, "files");
			if (files.length == 0) {
				continue;
			}

			String fileName = getString(files[0], "file_name");
			String language = getString(a, "language");
			Language lang = language == null ? null : Language.findLanguage(language.replaceAll("-.*", ""));
			boolean hashMatch = Boolean.parseBoolean(String.valueOf(a.get("moviehash_match")));

			Map<Property, String> p = new EnumMap<Property, String>(Property.class);
			p.put(Property.IDSubtitle, getString(item, "id"));
			p.put(Property.IDSubtitleFile, getString(files[0], "file_id"));
			p.put(Property.SubFileName, fileName == null ? getString(a, "release") + ".srt" : fileName.contains(".") ? fileName : fileName + ".srt");
			p.put(Property.SubFormat, "srt");
			p.put(Property.SubSize, "0");
			p.put(Property.LanguageName, lang != null ? lang.getName() : String.valueOf(language));
			p.put(Property.ISO639, language);
			p.put(Property.SubLanguageID, lang != null ? lang.getISO3() : language);
			p.put(Property.MovieReleaseName, getString(a, "release"));
			p.put(Property.MovieName, feature == null ? null : getString(feature, "movie_name"));
			p.put(Property.MovieYear, feature == null ? null : getString(feature, "year"));
			p.put(Property.IDMovieImdb, feature == null ? null : getString(feature, "imdb_id"));
			p.put(Property.SeriesSeason, feature == null ? null : getString(feature, "season_number"));
			p.put(Property.SeriesEpisode, feature == null ? null : getString(feature, "episode_number"));
			p.put(Property.MovieKind, feature == null ? null : getString(feature, "feature_type"));
			p.put(Property.MovieFPS, String.valueOf(a.get("fps") == null ? 0 : a.get("fps")));
			p.put(Property.MovieTimeMS, "0");
			p.put(Property.SubDownloadsCnt, getString(a, "download_count"));
			p.put(Property.SubHearingImpaired, String.valueOf(a.get("hearing_impaired")));
			p.put(Property.SubActualCD, "1");
			p.put(Property.SubSumCD, "1");
			p.put(Property.QueryNumber, "0");
			p.put(Property.MatchedBy, hashMatch ? "moviehash" : "fulltext");
			if (hashMatch && videoHash != null) {
				p.put(Property.MovieHash, videoHash);
				p.put(Property.MovieByteSize, String.valueOf(videoSize));
			}
			p.values().removeIf(v -> v == null || v.equals("null"));

			subtitles.add(new OpenSubtitlesRestDescriptor(p, this, toInt(getString(files[0], "file_id"))));
		}

		// exact hash matches first
		subtitles.sort((x, y) -> Boolean.compare("moviehash".equals(((OpenSubtitlesSubtitleDescriptor) y).getProperty(Property.MatchedBy)), "moviehash".equals(((OpenSubtitlesSubtitleDescriptor) x).getProperty(Property.MatchedBy))));
		return subtitles;
	}

	private void addLanguage(Map<String, Object> parameters, Locale locale) {
		if (locale != null && !locale.getLanguage().isEmpty() && !locale.getLanguage().equals("und")) {
			String code = locale.getLanguage();
			if (!locale.getCountry().isEmpty() && (code.equals("pt") || code.equals("zh"))) {
				code = code + "-" + locale.getCountry().toLowerCase(Locale.ROOT);
			}
			parameters.put("languages", code);
		}
	}

	private Object cachedRequest(String path, Map<String, Object> parameters) throws Exception {
		String key = path + "?" + parameters;
		Object cached = responseCache.get(key);
		if (cached != null) {
			return cached;
		}
		Object response = request("GET", path, parameters, null);
		responseCache.put(key, response);
		return response;
	}

	/**
	 * Parameters are sent lower case and in alphabetical order, as the API documentation asks (otherwise requests get redirected).
	 */
	private synchronized Object request(String method, String path, Map<String, Object> parameters, String body) throws Exception {
		if (apiKey == null) {
			throw new IllegalStateException("OpenSubtitles API key missing: create a free key on opensubtitles.com (API consumers) and enter it in the Subtitles login.");
		}

		StringBuilder url = new StringBuilder(api).append(path);
		if (parameters != null && !parameters.isEmpty()) {
			String sep = "?";
			for (Map.Entry<String, Object> it : new TreeMap<String, Object>(parameters).entrySet()) {
				url.append(sep).append(it.getKey()).append('=').append(URLEncoder.encode(String.valueOf(it.getValue()).toLowerCase(Locale.ROOT), "UTF-8").replace("+", "%20"));
				sep = "&";
			}
		}

		for (int attempt = 0; attempt < 3; attempt++) {
			// the API allows 5 requests per second
			long wait = 250 - (System.currentTimeMillis() - lastRequest);
			if (wait > 0) {
				Thread.sleep(wait);
			}
			lastRequest = System.currentTimeMillis();

			HttpURLConnection c = (HttpURLConnection) new URL(url.toString()).openConnection();
			c.setRequestMethod(method);
			c.setConnectTimeout(10000);
			c.setReadTimeout(30000);
			c.setRequestProperty("Api-Key", apiKey);
			c.setRequestProperty("User-Agent", userAgent);
			c.setRequestProperty("Accept", "application/json");
			if (token != null && !path.equals("/login")) {
				c.setRequestProperty("Authorization", "Bearer " + token);
			}
			if (body != null) {
				c.setDoOutput(true);
				c.setRequestProperty("Content-Type", "application/json");
				try (OutputStream out = c.getOutputStream()) {
					out.write(body.getBytes(UTF_8));
				}
			}

			int status = c.getResponseCode();
			if (status == 429) {
				Thread.sleep(1000L * (attempt + 1)); // rate limited, try again
				continue;
			}

			InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
			String text = in == null ? "" : new String(readAll(in), UTF_8);
			if (status >= 400) {
				boolean expired = status == 401 && token != null && !path.equals("/login");
				if (expired) {
					token = null;
				}
				throw new IOException(getErrorMessage(status, path, text, expired));
			}
			return text.isEmpty() ? emptyMap() : readJson(text);
		}
		throw new IOException("OpenSubtitles is busy (too many requests), please try again in a moment.");
	}

	private static String getErrorMessage(int status, String path, String text, boolean expired) {
		String message = null;
		try {
			Object json = readJson(text);
			message = getString(json, "message");
			if (message == null && getArray(json, "errors").length > 0) {
				message = String.valueOf(getArray(json, "errors")[0]);
			}
		} catch (Exception e) {
			// not JSON
		}
		switch (status) {
		case 401:
			if (path.equals("/login")) {
				return "Wrong username or password.";
			}
			return expired ? "Your session has expired, please sign in again." : "The API key was not accepted. Check it on opensubtitles.com (API consumers).";
		case 403:
			return "The API key was not accepted. Check it on opensubtitles.com (API consumers).";
		case 406:
			return message != null ? message : "Your daily download quota has been reached.";
		default:
			return String.format("OpenSubtitles error %d%s", status, message != null ? ": " + message : "");
		}
	}

	private static byte[] readAll(InputStream in) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[8192];
		for (int n; (n = in.read(buffer)) > 0;) {
			out.write(buffer, 0, n);
		}
		return out.toByteArray();
	}

	private static int toInt(String value) {
		try {
			return value == null ? 0 : (int) Double.parseDouble(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static String quote(String value) {
		StringBuilder sb = new StringBuilder("\"");
		for (char ch : value.toCharArray()) {
			switch (ch) {
			case '"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			default:
				if (ch < 0x20) {
					sb.append(String.format("\\u%04x", (int) ch));
				} else {
					sb.append(ch);
				}
			}
		}
		return sb.append('"').toString();
	}

}
