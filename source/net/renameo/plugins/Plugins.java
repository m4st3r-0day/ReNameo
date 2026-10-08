package net.renameo.plugins;

import static java.nio.charset.StandardCharsets.*;
import static net.renameo.Logging.*;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import org.codehaus.groovy.runtime.MethodClosure;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import net.renameo.ApplicationFolder;
import net.renameo.Settings;
import net.renameo.UserFiles;
import net.renameo.util.DefaultThreadFactory;
import net.renameo.util.PreferencesMap.PreferencesEntry;

/**
 * Plugins are Groovy scripts in the plugins folder. They run with the rights of the user, like any script the user installs.
 *
 * <pre>
 * description "Tell Jellyfin to rescan the library"
 * setting "server", "Server address", "http://localhost:8096"   // shown on the Plugins page, read with settings.server
 * secret "apiKey", "API key"                                    // same, but masked
 * onRename { from, to -&gt; ... }                                  // after every renamed file (app, watch folder, command line, Docker)
 * onRenameBatch { renames -&gt; ... }                              // once per batch, renames = [[from, to], ...]
 * binding("quality") { m -&gt; ... }                                // {plugin.quality} in formats, m = the format bindings ({n}, {y}, {vf}, ...)
 * action("Find missing episodes", "...") { folder -&gt; ... }        // a button on the Plugins page, run for a folder the user picks
 * log "message"
 * trash file                                                    // move a file to the trash
 * </pre>
 */
public final class Plugins {

	public static class Setting {

		public final String key;
		public final String label;
		public final String defaultValue;
		public final boolean secret;

		Setting(String key, String label, String defaultValue, boolean secret) {
			this.key = key;
			this.label = label;
			this.defaultValue = defaultValue == null ? "" : defaultValue;
			this.secret = secret;
		}
	}

	public static class Action {

		public final String name;
		public final String description;
		final Closure<?> handler;

		Action(String name, String description, Closure<?> handler) {
			this.name = name;
			this.description = description == null ? "" : description;
			this.handler = handler;
		}
	}

	public static class Plugin {

		public final String name;
		public final File file;
		public boolean enabled;
		public String description = "";
		public String error;

		public final Map<String, Setting> settings = new LinkedHashMap<String, Setting>();
		public final List<Action> actions = new ArrayList<Action>();

		/**
		 * What the plugin is doing right now, or null.
		 */
		public volatile Activity activity;

		/**
		 * e.g. "Find duplicates · 12:31 · 3.2 s", or null if it never ran.
		 */
		public volatile String lastRun;
		public volatile boolean lastRunFailed;

		final List<Closure<?>> onRename = new ArrayList<Closure<?>>();
		final List<Closure<?>> onRenameBatch = new ArrayList<Closure<?>>();
		final Map<String, Closure<?>> bindings = new LinkedHashMap<String, Closure<?>>();

		Plugin(File file, boolean enabled) {
			this.file = file;
			this.name = file.getName().replaceFirst("\\.groovy$", "");
			this.enabled = enabled;
		}

		/**
		 * The environment wins (Docker, NAS: e.g. PLUGIN_JELLYFIN_REFRESH_APIKEY), then what was entered on the Plugins page, then the default.
		 */
		public String getSetting(String key) {
			String env = System.getenv(getEnvironmentName(key));
			if (env != null && !env.trim().isEmpty()) {
				return env.trim();
			}
			String value = settingsNode(name).get(key);
			if (value != null) {
				return value;
			}
			Setting setting = settings.get(key);
			return setting != null ? setting.defaultValue : "";
		}

		public String getEnvironmentName(String key) {
			return ("PLUGIN_" + name + "_" + key).toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_");
		}

		public void setSetting(String key, String value) {
			settingsNode(name).put(key, value == null ? "" : value.trim());
		}
	}

	/**
	 * What a plugin script sees as its own methods.
	 */
	public static class Api {

		private final Plugin plugin;

		Api(Plugin plugin) {
			this.plugin = plugin;
		}

		public void description(String text) {
			plugin.description = text;
		}

		public void setting(String key, String label) {
			setting(key, label, "");
		}

		public void setting(String key, String label, String defaultValue) {
			plugin.settings.put(key, new Setting(key, label, defaultValue, false));
		}

		public void secret(String key, String label) {
			plugin.settings.put(key, new Setting(key, label, "", true));
		}

		public void onRename(Closure<?> handler) {
			plugin.onRename.add(handler);
		}

		public void onRenameBatch(Closure<?> handler) {
			plugin.onRenameBatch.add(handler);
		}

		public void binding(String name, Closure<?> value) {
			plugin.bindings.put(name, value);
		}

		public void action(String name, String description, Closure<?> handler) {
			plugin.actions.add(new Action(name, description, handler));
		}

		public void action(String name, Closure<?> handler) {
			action(name, null, handler);
		}

		public void log(Object message) {
			Plugins.log(plugin, LogEntry.Kind.INFO, String.valueOf(message));
		}

		/**
		 * progress 12, 40 shows "12 of 40" and a bar on the Plugins page.
		 */
		public void progress(Number done, Number total) {
			progress(done, total, null);
		}

		public void progress(Number done, Number total, String message) {
			Activity a = plugin.activity;
			if (a != null) {
				a.done = done.intValue();
				a.total = total.intValue();
				if (message != null) {
					a.message = message;
				}
			}
		}

		public void progress(String message) {
			Activity a = plugin.activity;
			if (a != null) {
				a.message = message;
			}
		}

		/**
		 * True after the user pressed Stop: long loops should check it and return.
		 */
		public boolean cancelled() {
			Activity a = plugin.activity;
			return a != null && a.cancelled;
		}

		/**
		 * Move a file to the trash. Without a desktop (Docker, NAS) it goes to the trash folder in the data folder, so nothing is lost by mistake.
		 */
		public void trash(File file) throws Exception {
			if (!GraphicsEnvironment.isHeadless()) {
				UserFiles.trash(file);
				return;
			}
			File bin = ApplicationFolder.AppData.resolve("trash");
			bin.mkdirs();
			File target = new File(bin, file.getName());
			for (int i = 2; target.exists(); i++) {
				target = new File(bin, i + " " + file.getName());
			}
			Files.move(file.toPath(), target.toPath());
		}
	}

	private static final PreferencesEntry<String> disabled = Settings.forPackage(Plugins.class).entry("plugins.disabled").defaultValue("");

	private static List<Plugin> plugins = Collections.emptyList();

	private static final List<Consumer<LogEntry>> logListeners = new CopyOnWriteArrayList<Consumer<LogEntry>>();

	private Plugins() {
		throw new UnsupportedOperationException();
	}

	public static File getFolder() {
		File folder = ApplicationFolder.AppData.resolve("plugins");
		folder.mkdirs();
		return folder;
	}

	public static synchronized List<Plugin> list() {
		return Collections.unmodifiableList(plugins);
	}

	/**
	 * (Re)load all plugins. A plugin that fails to load is listed with its error and does nothing.
	 */
	public static synchronized void load() {
		installFromEnvironment();

		Set<String> off = getDisabled();
		List<Plugin> loaded = new ArrayList<Plugin>();

		File[] files = getFolder().listFiles(f -> f.isFile() && f.getName().endsWith(".groovy"));
		if (files != null) {
			Arrays.sort(files);
			for (File file : files) {
				Plugin plugin = new Plugin(file, !off.contains(file.getName().replaceFirst("\\.groovy$", "")));
				try {
					// the plugin api methods are closures in the script binding, so a script simply calls description "…", onRename { … }
					Api api = new Api(plugin);
					Binding binding = new Binding();
					for (String method : new String[] { "description", "setting", "secret", "onRename", "onRenameBatch", "binding", "action", "log", "trash", "progress", "cancelled" }) {
						binding.setVariable(method, new MethodClosure(api, method));
					}
					binding.setVariable("settings", settingsView(plugin));

					// disabled plugins are read too (description, settings), but their hooks are never called
					new GroovyShell(Plugins.class.getClassLoader(), binding).evaluate(new String(Files.readAllBytes(file.toPath()), UTF_8), file.getName());
					debug.fine("Loaded plugin " + plugin.name);
				} catch (Throwable e) {
					plugin.error = e.getClass().getSimpleName() + ": " + e.getMessage();
					log(plugin, LogEntry.Kind.ERROR, "Failed to load: " + plugin.error);
				}
				loaded.add(plugin);
			}
		}

		plugins = loaded;
	}

	private static List<Plugin> active() {
		return list().stream().filter(p -> p.enabled && p.error == null).collect(Collectors.toList());
	}

	/**
	 * Live view of the settings of a plugin: settings.server returns what the user entered on the Plugins page, or the default.
	 */
	private static Map<String, String> settingsView(Plugin plugin) {
		return new AbstractMap<String, String>() {

			@Override
			public String get(Object key) {
				return plugin.getSetting(String.valueOf(key));
			}

			@Override
			public Set<Entry<String, String>> entrySet() {
				Map<String, String> values = new LinkedHashMap<String, String>();
				plugin.settings.keySet().forEach(k -> values.put(k, plugin.getSetting(k)));
				return values.entrySet();
			}
		};
	}

	private static Settings settingsNode(String plugin) {
		return Settings.forPackage(Plugins.class).node("settings." + plugin);
	}

	public static synchronized void setEnabled(Plugin plugin, boolean enabled) {
		Set<String> off = getDisabled();
		if (enabled) {
			off.remove(plugin.name);
		} else {
			off.add(plugin.name);
		}
		disabled.setValue(String.join("\n", off));
		plugin.enabled = enabled;
	}

	private static Set<String> getDisabled() {
		Set<String> names = new LinkedHashSet<String>();
		for (String line : disabled.getValue().split("\n")) {
			if (!line.trim().isEmpty()) {
				names.add(line.trim());
			}
		}
		return names;
	}

	// plugins may be slow (e.g. notify a media server), so in the app they run in order on their own thread
	private static final ExecutorService events = Executors.newSingleThreadExecutor(new DefaultThreadFactory("Plugins", Thread.NORM_PRIORITY, true));

	/**
	 * Called once per rename batch. On the command line it runs right away, so it finishes before the program exits.
	 */
	public static void fireRenameBatch(List<Entry<File, File>> renames) {
		if (renames.isEmpty() || active().stream().allMatch(p -> p.onRename.isEmpty() && p.onRenameBatch.isEmpty())) {
			return;
		}
		if (SwingUtilities.isEventDispatchThread()) {
			events.submit(() -> notifyRenames(renames));
		} else {
			notifyRenames(renames);
		}
	}

	private static void notifyRenames(List<Entry<File, File>> renames) {
		List<List<File>> pairs = renames.stream().map(e -> Arrays.asList(e.getKey(), e.getValue())).collect(Collectors.toList());
		String task = renames.size() == 1 ? "After renaming 1 file" : "After renaming " + renames.size() + " files";
		for (Plugin plugin : active()) {
			if (plugin.onRename.isEmpty() && plugin.onRenameBatch.isEmpty()) {
				continue;
			}
			run(plugin, new Activity(task), () -> {
				for (Entry<File, File> it : renames) {
					for (Closure<?> handler : plugin.onRename) {
						handler.call(it.getKey(), it.getValue());
					}
				}
				for (Closure<?> handler : plugin.onRenameBatch) {
					handler.call(pairs);
				}
			});
		}
	}

	/**
	 * Run a task of a plugin: shows it as running on the Plugins page and writes start, end and errors to the plugin log.
	 */
	private static void run(Plugin plugin, Activity activity, Runnable body) {
		String task = activity.task;
		activity.thread = Thread.currentThread();
		plugin.activity = activity;
		log(plugin, LogEntry.Kind.START, task);

		boolean failed = false;
		try {
			if (!activity.cancelled) {
				body.run();
			}
		} catch (Throwable e) {
			if (!activity.cancelled) {
				failed = true;
				log(plugin, LogEntry.Kind.ERROR, e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.toString());
				debug.log(Level.WARNING, "Plugin " + plugin.name, e);
			}
		} finally {
			activity.thread = null;
			Thread.interrupted(); // Stop may have interrupted this thread
			plugin.activity = null;
		}

		String time = formatDuration(System.currentTimeMillis() - activity.started);
		String result = activity.cancelled ? "stopped after " + time : failed ? "failed after " + time : "done in " + time;
		log(plugin, activity.cancelled ? LogEntry.Kind.STOPPED : failed ? LogEntry.Kind.ERROR : LogEntry.Kind.DONE, task + ": " + result);
		plugin.lastRun = String.format("%s · %s · %s", task, new SimpleDateFormat("HH:mm").format(new Date()), result);
		plugin.lastRunFailed = failed;
	}

	private static String formatDuration(long ms) {
		return ms < 60000 ? String.format(Locale.ROOT, "%.1f s", ms / 1000.0) : String.format("%d:%02d min", ms / 60000, ms / 1000 % 60);
	}

	// actions may take minutes, so each one gets its own thread
	private static final ExecutorService actions = Executors.newCachedThreadPool(new DefaultThreadFactory("PluginAction", Thread.NORM_PRIORITY, true));

	/**
	 * Start a plugin action for the given folder in the background.
	 *
	 * @return false if the plugin is busy
	 */
	public static boolean startAction(Plugin plugin, Action action, File folder) {
		if (plugin.activity != null) {
			return false;
		}
		// shown as running right away, before the thread starts
		Activity activity = new Activity(action.name);
		plugin.activity = activity;
		actions.submit(() -> run(plugin, activity, () -> action.handler.call(folder)));
		return true;
	}

	/**
	 * Run a plugin action for the given folder (blocking).
	 */
	public static void runAction(Plugin plugin, Action action, File folder) {
		run(plugin, new Activity(action.name), () -> action.handler.call(folder));
	}

	/**
	 * Value of {plugin.name} for the given format bindings, or null if no plugin defines it.
	 */
	public static Object binding(String name, Object bindings) {
		for (Plugin plugin : active()) {
			Closure<?> value = plugin.bindings.get(name);
			if (value != null) {
				return value.getMaximumNumberOfParameters() == 0 ? value.call() : value.call(bindings);
			}
		}
		return null;
	}

	public static boolean hasBinding(String name) {
		return active().stream().anyMatch(p -> p.bindings.containsKey(name));
	}

	public static void addLogListener(Consumer<LogEntry> listener) {
		logListeners.add(listener);
	}

	public static void removeLogListener(Consumer<LogEntry> listener) {
		logListeners.remove(listener);
	}

	private static final int LOG_SIZE = 2000;

	private static final Deque<LogEntry> history = new ArrayDeque<LogEntry>();

	/**
	 * The last messages of all plugins, also those written while the Plugins page was closed.
	 */
	public static List<LogEntry> getLog() {
		synchronized (history) {
			return new ArrayList<LogEntry>(history);
		}
	}

	public static void clearLog() {
		synchronized (history) {
			history.clear();
		}
	}

	private static void log(Plugin plugin, LogEntry.Kind kind, String message) {
		LogEntry entry = new LogEntry(plugin.name, kind, message);
		synchronized (history) {
			history.addLast(entry);
			while (history.size() > LOG_SIZE) {
				history.removeFirst();
			}
		}

		// start and end are for the Plugins page, the terminal only gets what the plugin says and errors
		String line = String.format("[%s] %s", plugin.name, message);
		if (kind == LogEntry.Kind.ERROR) {
			net.renameo.Logging.log.warning(line);
		} else if (kind == LogEntry.Kind.INFO) {
			net.renameo.Logging.log.info(line);
		} else {
			debug.fine(line);
		}
		logListeners.forEach(l -> l.accept(entry));
	}

	public static class LogEntry {

		public enum Kind {
			INFO, START, DONE, STOPPED, ERROR
		}

		public final long time = System.currentTimeMillis();
		public final String plugin;
		public final Kind kind;
		public final String message;

		LogEntry(String plugin, Kind kind, String message) {
			this.plugin = plugin;
			this.kind = kind;
			this.message = message;
		}

		@Override
		public String toString() {
			return String.format("%s  %-7s %-18s %s", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(time)), kind, plugin, message);
		}
	}

	/**
	 * A task a plugin is working on.
	 */
	public static class Activity {

		public final String task;
		public final long started = System.currentTimeMillis();

		public volatile int done = -1;
		public volatile int total = -1;
		public volatile String message;
		public volatile boolean cancelled;

		private volatile Thread thread;

		Activity(String task) {
			this.task = task;
		}

		/**
		 * Ask the plugin to stop: cancelled() turns true and waiting (network, sleep) is interrupted.
		 */
		public void cancel() {
			cancelled = true;
			Thread t = thread;
			if (t != null) {
				t.interrupt();
			}
		}
	}

	/**
	 * Plugins that come with the app and can be installed with one click.
	 */
	public static class CatalogEntry {

		public final String name;
		public final String description;

		CatalogEntry(String name, String description) {
			this.name = name;
			this.description = description;
		}

		public boolean isInstalled() {
			return new File(getFolder(), name + ".groovy").exists();
		}
	}

	private static final String CATALOG = "catalog/";

	private static final Pattern DESCRIPTION = Pattern.compile("^description\\s+\"(.*)\"\\s*$", Pattern.MULTILINE);

	public static List<CatalogEntry> catalog() {
		List<CatalogEntry> entries = new ArrayList<CatalogEntry>();
		try (InputStream index = Plugins.class.getResourceAsStream(CATALOG + "index.txt")) {
			if (index == null) {
				return entries;
			}
			for (String name : new String(index.readAllBytes(), UTF_8).split("\\R")) {
				name = name.trim();
				if (name.isEmpty()) {
					continue;
				}
				String source = readCatalog(name);
				Matcher m = DESCRIPTION.matcher(source);
				entries.add(new CatalogEntry(name, m.find() ? m.group(1) : ""));
			}
		} catch (IOException e) {
			debug.log(Level.WARNING, "Failed to read the plugin catalog", e);
		}
		return entries;
	}

	private static String readCatalog(String name) throws IOException {
		try (InputStream in = Plugins.class.getResourceAsStream(CATALOG + name + ".groovy")) {
			if (in == null) {
				throw new IOException("Missing catalog plugin: " + name);
			}
			return new String(in.readAllBytes(), UTF_8);
		}
	}

	/**
	 * Without a desktop, catalog plugins are installed by name: RENAMEO_PLUGINS=jellyfin-refresh,notify
	 */
	private static void installFromEnvironment() {
		String names = System.getenv("RENAMEO_PLUGINS");
		if (names == null || names.trim().isEmpty()) {
			return;
		}
		List<CatalogEntry> catalog = catalog();
		for (String name : names.split("[,\\s]+")) {
			CatalogEntry entry = catalog.stream().filter(c -> c.name.equalsIgnoreCase(name)).findFirst().orElse(null);
			if (entry == null) {
				log.warning("RENAMEO_PLUGINS: no plugin called " + name);
			} else if (!entry.isInstalled()) {
				try {
					copy(entry);
					log.info("Installed plugin " + entry.name);
				} catch (IOException e) {
					log.warning("Failed to install plugin " + entry.name + ": " + e.getMessage());
				}
			}
		}
	}

	private static void copy(CatalogEntry entry) throws IOException {
		File file = new File(getFolder(), entry.name + ".groovy");
		if (!file.exists()) {
			Files.write(file.toPath(), readCatalog(entry.name).getBytes(UTF_8));
		}
	}

	/**
	 * Copy a catalog plugin into the plugins folder and load it (an existing file of the same name is kept).
	 */
	public static synchronized void install(CatalogEntry entry) throws IOException {
		copy(entry);
		load();
	}

}
