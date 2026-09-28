package net.renameo;

import static net.renameo.Logging.*;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import net.renameo.UserFiles.FileChooser;
import net.renameo.cli.ArgumentBean;
import net.renameo.util.PreferencesList;
import net.renameo.util.PreferencesMap;
import net.renameo.util.PreferencesMap.JsonAdapter;
import net.renameo.util.PreferencesMap.PreferencesEntry;
import net.renameo.util.PreferencesMap.StringAdapter;

public final class Settings {

	public static String getApplicationName() {
		return getApplicationProperty("application.name");
	}

	public static String getApplicationVersion() {
		return getApplicationProperty("application.version");
	}

	public static int getApplicationRevisionNumber() {
		try {
			return Integer.parseInt(getApplicationProperty("application.revision"));
		} catch (Exception e) {
			return 0;
		}
	}

	public static String getApplicationProperty(String key) {
		return ResourceBundle.getBundle(Settings.class.getName(), Locale.ROOT).getString(key);
	}

	public static String getApiKey(String name) {
		return getApplicationProperty("apikey." + name);
	}

	public static boolean isUnixFS() {
		return Boolean.parseBoolean(System.getProperty("unixfs"));
	}

	public static boolean useNativeShell() {
		return Boolean.parseBoolean(System.getProperty("useNativeShell"));
	}

	public static boolean useGVFS() {
		return Boolean.parseBoolean(System.getProperty("useGVFS"));
	}

	public static boolean useExtendedFileAttributes() {
		return Boolean.parseBoolean(System.getProperty("useExtendedFileAttributes"));
	}

	public static boolean useCreationDate() {
		return Boolean.parseBoolean(System.getProperty("useCreationDate"));
	}

	public static boolean useRenameHistory() {
		return Boolean.parseBoolean(System.getProperty("application.rename.history", "true"));
	}

	public static String getApplicationDeployment() {
		return System.getProperty("application.deployment", "jar");
	}

	public static boolean isPortableApp() {
		return isApplicationDeployment("portable", "jar");
	}

	public static boolean isAppStore() {
		return isApplicationDeployment("mas", "appx");
	}

	public static boolean isWindowsApp() {
		return isApplicationDeployment("appx", "msi");
	}

	public static boolean isUbuntuApp() {
		return isApplicationDeployment("deb", "snap");
	}

	public static boolean isMacApp() {
		return isApplicationDeployment("mas", "cask");
	}

	public static boolean isMacSandbox() {
		return isApplicationDeployment("mas");
	}

	public static boolean isAutoUpdateEnabled() {
		return isApplicationDeployment("mas", "appx", "snap", "spk", "aur");
	}

	private static boolean isApplicationDeployment(String... ids) {
		String current = getApplicationDeployment();
		for (String id : ids) {
			if (current != null && current.equals(id))
				return true;
		}
		return false;
	}

	public static String getApplicationUserModelID() {
		return System.getProperty("net.renameo.AppUserModelID", getApplicationName());
	}

	public static FileChooser getPreferredFileChooser() {
		// use the native file dialog (modern platform look) by default, allow override via -Dnet.renameo.UserFiles.fileChooser=Swing
		return FileChooser.valueOf(System.getProperty("net.renameo.UserFiles.fileChooser", "AWT"));
	}

	public static int getPreferredThreadPoolSize() {
		try {
			String threadPool = System.getProperty("threadPool");
			if (threadPool != null) {
				return Integer.parseInt(threadPool);
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, e.getMessage(), e);
		}

		return Runtime.getRuntime().availableProcessors();
	}

	public static String getAppStoreName() {
		if (isMacApp())
			return "Mac App Store";
		if (isWindowsApp())
			return "Windows Store";
		if (isUbuntuApp())
			return "Ubuntu Software Center";

		return null;
	}

	public static String getAppStoreLink() {
		if (isMacApp())
			return getApplicationProperty("link.mas");
		if (isWindowsApp())
			return getApplicationProperty("link.mws");
		if (isUbuntuApp())
			return null;

		return null;
	}

	/**
	 * The user guides ship inside the jar; they are copied to the application folder so both the help window and an external browser can open them.
	 */
	public static String getEmbeddedHelpURL() {
		try {
			File folder = ApplicationFolder.AppData.resolve("help");
			folder.mkdirs();
			for (String page : new String[] { "guida-gui.html", "guida-cli.html", "guide-gui.html", "guide-cli.html" }) {
				try (InputStream in = ResourceManager.class.getResourceAsStream("resources/help/" + page)) {
					if (in != null) {
						Files.copy(in, new File(folder, page).toPath(), StandardCopyOption.REPLACE_EXISTING);
					}
				}
			}
			// the guide is written in Italian and English, open the one matching the system language
			String start = "it".equals(Locale.getDefault().getLanguage()) ? "guida-gui.html" : "guide-gui.html";
			return new File(folder, start).toURI().toString();
		} catch (Exception e) {
			debug.log(Level.WARNING, "Failed to prepare help pages: " + e);
			return getApplicationProperty("link.app.help");
		}
	}

	public static String getApplicationIdentifier() {
		return String.format("%s %s (r%d)", getApplicationName(), getApplicationVersion(), getApplicationRevisionNumber());
	}

	public static String getJavaRuntimeIdentifier() {
		return String.format("%s %s", System.getProperty("java.runtime.name"), System.getProperty("java.version"));
	}

	public static String getSystemIdentifier() {
		return String.format("%s %s (%s)", System.getProperty("os.name"), System.getProperty("os.version"), System.getProperty("os.arch"));
	}

	private static ArgumentBean applicationArguments;

	public static void setApplicationArguments(ArgumentBean args) {
		applicationArguments = args;
	}

	public static ArgumentBean getApplicationArguments() {
		return applicationArguments;
	}

	/**
	 * Preferences are keyed by package name, so settings saved before the package was renamed live under the legacy node.
	 */
	public static void migrateLegacyPreferences() {
		try {
			Preferences root = Preferences.userRoot();
			if (!root.nodeExists("/net/renameo") && root.nodeExists("/net/filebot")) {
				copyPreferences(root.node("/net/filebot"), root.node("/net/renameo"));
				root.flush();
			}
		} catch (Exception e) {
			debug.log(Level.WARNING, "Failed to migrate legacy preferences: " + e);
		}
	}

	private static void copyPreferences(Preferences from, Preferences to) throws Exception {
		for (String key : from.keys()) {
			to.put(key, from.get(key, null));
		}
		for (String child : from.childrenNames()) {
			copyPreferences(from.node(child), to.node(child));
		}
	}

	public static Settings forPackage(Class<?> type) {
		return new Settings(Preferences.userNodeForPackage(type));
	}

	private final Preferences prefs;

	private Settings(Preferences prefs) {
		this.prefs = prefs;
	}

	public Settings node(String nodeName) {
		return new Settings(prefs.node(nodeName));
	}

	public String get(String key) {
		return get(key, null);
	}

	public String get(String key, String def) {
		return prefs.get(key, def);
	}

	public void put(String key, String value) {
		if (value != null) {
			prefs.put(key, value);
		} else {
			remove(key);
		}
	}

	public void remove(String key) {
		prefs.remove(key);
	}

	public PreferencesEntry<String> entry(String key) {
		return new PreferencesEntry<String>(prefs, key, new StringAdapter());
	}

	public PreferencesMap<String> asMap() {
		return PreferencesMap.map(prefs);
	}

	public <T> PreferencesMap<T> asMap(Class<T> cls) {
		return PreferencesMap.map(prefs, new JsonAdapter(cls));
	}

	public PreferencesList<String> asList() {
		return PreferencesList.map(prefs);
	}

	public <T> PreferencesList<T> asList(Class<T> cls) {
		return PreferencesList.map(prefs, new JsonAdapter(cls));
	}

	public void clear() {
		try {
			// remove child nodes
			for (String nodeName : prefs.childrenNames()) {
				prefs.node(nodeName).removeNode();
			}

			// remove entries
			prefs.clear();
		} catch (BackingStoreException e) {
			debug.warning(e.getMessage());
		}
	}

	public static void store(File f) {
		try (OutputStream out = new BufferedOutputStream(new FileOutputStream(f))) {
			Preferences.userRoot().exportSubtree(out);
		} catch (Exception e) {
			debug.log(Level.SEVERE, e, e::toString);
		}
	}

	public static void restore(File f) {
		try (InputStream in = new BufferedInputStream(new FileInputStream(f))) {
			Preferences.importPreferences(in);
		} catch (Exception e) {
			debug.log(Level.SEVERE, e, e::toString);
		}
	}

}
