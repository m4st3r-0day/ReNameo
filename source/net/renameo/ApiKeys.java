package net.renameo;

import static net.renameo.Logging.*;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.concurrent.ConcurrentHashMap;

import net.renameo.platform.mac.Keychain;
import net.renameo.util.PreferencesMap.PreferencesEntry;

/**
 * API keys of the web services. Each user brings their own keys, so builds don't have to ship any.
 *
 * Lookup order: environment variable (Docker, NAS, scripts), then the key saved by the user (macOS Keychain, or the preferences where there is no keychain), then a key built into the jar via
 * profile.properties.
 */
public final class ApiKeys {

	public enum Service {

		TMDB("themoviedb", "TMDB_API_KEY", "TheMovieDB", "https://www.themoviedb.org/settings/api", true),

		OMDB("omdb", "OMDB_API_KEY", "OMDb", "https://www.omdbapi.com/apikey.aspx", false),

		FANART_TV("fanart.tv", "FANARTTV_API_KEY", "Fanart.tv", "https://fanart.tv/get-an-api-key/", false),

		ACOUSTID("acoustid", "ACOUSTID_API_KEY", "AcoustID", "https://acoustid.org/new-application", false);

		public final String id;
		public final String environment;
		public final String title;
		public final String signup;
		public final boolean required;

		Service(String id, String environment, String title, String signup, boolean required) {
			this.id = id;
			this.environment = environment;
			this.title = title;
			this.signup = signup;
			this.required = required;
		}
	}

	public enum Source {
		ENVIRONMENT, SAVED, BUILT_IN, MISSING
	}

	private static final String KEYCHAIN_SERVICE = "ReNameo API keys";

	private ApiKeys() {
		throw new UnsupportedOperationException();
	}

	public static String get(Service service) {
		String env = System.getenv(service.environment);
		if (!isBlank(env)) {
			return env.trim();
		}

		String saved = getSaved(service);
		if (!isBlank(saved)) {
			return saved;
		}

		return getBuiltIn(service);
	}

	public static Source getSource(Service service) {
		if (!isBlank(System.getenv(service.environment))) {
			return Source.ENVIRONMENT;
		}
		if (!isBlank(getSaved(service))) {
			return Source.SAVED;
		}
		if (!isBlank(getBuiltIn(service))) {
			return Source.BUILT_IN;
		}
		return Source.MISSING;
	}

	public static boolean isMissing(Service service) {
		return isBlank(get(service));
	}

	/**
	 * @return the key the user saved in this app (not the environment or built-in one)
	 */
	public static String getSaved(Service service) {
		// every keychain read starts a process, so remember what it said
		return saved.computeIfAbsent(service, s -> {
			if (Keychain.isSupported()) {
				String key = Keychain.get(KEYCHAIN_SERVICE, s.id);
				return key == null ? "" : key.trim();
			}
			return preference(s).getValue().trim();
		});
	}

	private static final Map<Service, String> saved = new ConcurrentHashMap<Service, String>();

	/**
	 * Save (or with an empty value forget) the key and pass it to the running web service clients.
	 */
	public static void save(Service service, String key) {
		key = key == null ? "" : key.trim();

		if (Keychain.isSupported()) {
			if (key.isEmpty()) {
				Keychain.delete(KEYCHAIN_SERVICE, service.id);
			} else if (!Keychain.set(KEYCHAIN_SERVICE, service.id, key)) {
				throw new IllegalStateException("The key could not be saved in the macOS Keychain");
			}
		} else if (key.isEmpty()) {
			preference(service).remove();
		} else {
			preference(service).setValue(key);
		}

		saved.put(service, key);
		WebServices.setApiKey(service, get(service));
	}

	/**
	 * Ask TheMovieDB whether the key works.
	 *
	 * @return null if the key is valid, otherwise a message for the user
	 */
	public static String checkTheMovieDB(String key) {
		key = key == null ? "" : key.trim();
		if (key.isEmpty()) {
			return "Please enter your TheMovieDB API key.";
		}
		if (key.startsWith("eyJ")) {
			return "This is the \"API Read Access Token\". Please copy the shorter \"API Key\" from the same page.";
		}
		try {
			HttpURLConnection c = (HttpURLConnection) new URL("https://api.themoviedb.org/3/configuration?api_key=" + key).openConnection();
			c.setConnectTimeout(10000);
			c.setReadTimeout(15000);
			int status = c.getResponseCode();
			if (status == 200) {
				return null;
			}
			if (status == 401) {
				return "TheMovieDB did not accept this key. Please check it on themoviedb.org (Settings, API).";
			}
			return "TheMovieDB answered with error " + status + ". Please try again later.";
		} catch (Exception e) {
			debug.warning("Failed to check TheMovieDB API key: " + e);
			return "TheMovieDB could not be reached. Please check your internet connection.";
		}
	}

	private static String getBuiltIn(Service service) {
		try {
			return Settings.getApplicationProperty("apikey." + service.id).trim();
		} catch (MissingResourceException e) {
			return "";
		}
	}

	private static PreferencesEntry<String> preference(Service service) {
		return Settings.forPackage(ApiKeys.class).entry("apikey." + service.id).defaultValue("");
	}

	private static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

}
