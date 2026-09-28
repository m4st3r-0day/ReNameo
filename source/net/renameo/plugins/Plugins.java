package net.renameo.plugins;

import static java.nio.charset.StandardCharsets.*;
import static net.renameo.Logging.*;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.runtime.MethodClosure;
import net.renameo.ApplicationFolder;
import net.renameo.Settings;
import net.renameo.util.PreferencesMap.PreferencesEntry;

/**
 * Plugins are Groovy scripts in the plugins folder. They run with the rights of the user, like any script the user installs, and can
 *
 * <pre>
 * description "Tell Jellyfin to rescan the library"
 * onRename { from, to -&gt; ... }             // after every rename (app, watch folder, command line, Docker)
 * binding("quality") { m -&gt; ... }           // {plugin.quality} in formats, m = the format bindings ({n}, {y}, {vf}, ...)
 * log "message"
 * </pre>
 */
public final class Plugins {

	public static class Plugin {

		public final String name;
		public final File file;
		public boolean enabled;
		public String description = "";
		public String error;

		final List<Closure<?>> onRename = new ArrayList<Closure<?>>();
		final Map<String, Closure<?>> bindings = new LinkedHashMap<String, Closure<?>>();

		Plugin(File file, boolean enabled) {
			this.file = file;
			this.name = file.getName().replaceFirst("\\.groovy$", "");
			this.enabled = enabled;
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

		public void onRename(Closure<?> handler) {
			plugin.onRename.add(handler);
		}

		public void binding(String name, Closure<?> value) {
			plugin.bindings.put(name, value);
		}

		public void log(Object message) {
			net.renameo.Logging.log.info(String.format("[%s] %s", plugin.name, message));
		}
	}

	private static final PreferencesEntry<String> disabled = Settings.forPackage(Plugins.class).entry("plugins.disabled").defaultValue("");

	private static List<Plugin> plugins = Collections.emptyList();

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
		Set<String> off = getDisabled();
		List<Plugin> loaded = new ArrayList<Plugin>();

		File[] files = getFolder().listFiles(f -> f.isFile() && f.getName().endsWith(".groovy"));
		if (files != null) {
			Arrays.sort(files);
			for (File file : files) {
				Plugin plugin = new Plugin(file, !off.contains(file.getName().replaceFirst("\\.groovy$", "")));
				if (plugin.enabled) {
					try {
						// the plugin api methods are closures in the script binding, so a script simply calls description "…", onRename { … }
						Api api = new Api(plugin);
						Binding binding = new Binding();
						for (String method : new String[] { "description", "onRename", "binding", "log" }) {
							binding.setVariable(method, new MethodClosure(api, method));
						}
						new GroovyShell(Plugins.class.getClassLoader(), binding).evaluate(new String(Files.readAllBytes(file.toPath()), UTF_8), file.getName());
						debug.fine("Loaded plugin " + plugin.name);
					} catch (Throwable e) {
						plugin.error = e.getClass().getSimpleName() + ": " + e.getMessage();
						log.log(Level.WARNING, String.format("Plugin %s failed to load: %s", plugin.name, e.getMessage()));
					}
				}
				loaded.add(plugin);
			}
		}

		plugins = loaded;
	}

	public static synchronized void setEnabled(Plugin plugin, boolean enabled) {
		Set<String> off = getDisabled();
		if (enabled) {
			off.remove(plugin.name);
		} else {
			off.add(plugin.name);
		}
		disabled.setValue(String.join("\n", off));
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

	/**
	 * Called after every successful rename.
	 */
	public static void fireRename(File from, File to) {
		for (Plugin plugin : list()) {
			for (Closure<?> handler : plugin.onRename) {
				try {
					handler.call(from, to);
				} catch (Throwable e) {
					log.log(Level.WARNING, String.format("Plugin %s: %s", plugin.name, e.getMessage()), e);
				}
			}
		}
	}

	/**
	 * Value of {plugin.name} for the given format bindings, or null if no plugin defines it.
	 */
	public static Object binding(String name, Object bindings) {
		for (Plugin plugin : list()) {
			Closure<?> value = plugin.bindings.get(name);
			if (value != null) {
				return value.getMaximumNumberOfParameters() == 0 ? value.call() : value.call(bindings);
			}
		}
		return null;
	}

	public static boolean hasBinding(String name) {
		return list().stream().anyMatch(p -> p.bindings.containsKey(name));
	}

}
