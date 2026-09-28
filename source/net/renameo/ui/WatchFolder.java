package net.renameo.ui;

import static net.renameo.Logging.*;
import static net.renameo.MediaTypes.*;
import static net.renameo.util.FileUtilities.*;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

import net.renameo.Settings;
import net.renameo.StandardRenameAction;
import net.renameo.cli.CmdlineOperations;
import net.renameo.cli.ConflictAction;
import net.renameo.format.ExpressionFileFormat;
import net.renameo.util.DefaultThreadFactory;
import net.renameo.util.PreferencesMap.PreferencesEntry;
import net.renameo.util.ui.SwingEventBus;
import net.renameo.web.SortOrder;

/**
 * Renames what arrives in a folder (e.g. Downloads) while the app is open, with the same engine as the command line and the Docker watch mode. Every rename goes into the history, so it
 * can be undone.
 */
public final class WatchFolder {

	public enum Naming {

		DEFAULT("ReNameo default names", null),

		PLEX("Plex", "{plex.name}"),

		JELLYFIN("Jellyfin", "{jellyfin.name}"),

		EMBY("Emby", "{emby.name}"),

		KODI("Kodi", "{kodi.name}");

		public final String title;
		public final String format;

		Naming(String title, String format) {
			this.title = title;
			this.format = format;
		}

		@Override
		public String toString() {
			return title;
		}
	}

	/** files changed more recently are probably still being downloaded or copied */
	public static final long SETTLE_MILLIS = TimeUnit.MINUTES.toMillis(2);

	private static final Settings settings = Settings.forPackage(WatchFolder.class);

	public static final PreferencesEntry<String> enabled = settings.entry("watch.enabled").defaultValue("false");
	public static final PreferencesEntry<String> folder = settings.entry("watch.folder").defaultValue("");
	public static final PreferencesEntry<String> naming = settings.entry("watch.naming").defaultValue(Naming.JELLYFIN.name());
	public static final PreferencesEntry<String> action = settings.entry("watch.action").defaultValue(StandardRenameAction.MOVE.name());
	public static final PreferencesEntry<String> interval = settings.entry("watch.interval").defaultValue("5");

	private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("WatchFolder", Thread.MIN_PRIORITY, true));
	private static ScheduledFuture<?> task;

	// files that could not be matched: only retried when they change
	private static final Map<File, Long> unmatched = new ConcurrentHashMap<File, Long>();

	private WatchFolder() {
		throw new UnsupportedOperationException();
	}

	public static boolean isEnabled() {
		return Boolean.parseBoolean(enabled.getValue()) && getFolder() != null;
	}

	public static File getFolder() {
		String path = folder.getValue();
		return path == null || path.isEmpty() ? null : new File(path);
	}

	public static Naming getNaming() {
		try {
			return Naming.valueOf(naming.getValue());
		} catch (Exception e) {
			return Naming.JELLYFIN;
		}
	}

	public static StandardRenameAction getAction() {
		try {
			return StandardRenameAction.valueOf(action.getValue());
		} catch (Exception e) {
			return StandardRenameAction.MOVE;
		}
	}

	public static int getIntervalMinutes() {
		try {
			return Math.max(1, Integer.parseInt(interval.getValue()));
		} catch (Exception e) {
			return 5;
		}
	}

	/**
	 * (Re)start or stop the background task according to the settings.
	 */
	public static synchronized void restart() {
		if (task != null) {
			task.cancel(false);
			task = null;
		}
		unmatched.clear();

		if (isEnabled()) {
			task = scheduler.scheduleWithFixedDelay(WatchFolder::run, 10, TimeUnit.MINUTES.toSeconds(getIntervalMinutes()), TimeUnit.SECONDS);
			debug.info(String.format("Watching %s every %d minutes", getFolder(), getIntervalMinutes()));
		}
	}

	private static void run() {
		File root = getFolder();
		if (root == null || !root.isDirectory()) {
			return;
		}

		long settled = System.currentTimeMillis() - SETTLE_MILLIS;
		List<File> files = listFiles(root, MEDIA_FILES, HUMAN_NAME_ORDER).stream().filter(f -> {
			return f.lastModified() < settled && !Long.valueOf(f.lastModified()).equals(unmatched.get(f));
		}).collect(Collectors.toList());

		if (files.isEmpty()) {
			return;
		}

		try {
			Naming n = getNaming();
			ExpressionFileFormat format = n.format == null ? null : new ExpressionFileFormat(n.format);
			Locale locale = Locale.forLanguageTag(Settings.forPackage(net.renameo.ui.rename.RenamePanel.class).entry("rename.language").defaultValue("en").getValue());

			List<File> renamed = new CmdlineOperations().rename(files, getAction(), ConflictAction.SKIP, null, format, null, null, SortOrder.Airdate, null, locale, false, null);

			for (File f : files) {
				if (f.exists()) {
					unmatched.put(f, f.lastModified());
				}
			}

			if (renamed.size() > 0) {
				String title = String.format("%d %s renamed in %s", renamed.size(), renamed.size() == 1 ? "file" : "files", root.getName());
				SwingUtilities.invokeLater(() -> SwingEventBus.getInstance().post(new AppEvents.Toast(title, "Watch folder · undo from History", AppEvents.Status.Kind.READY)));
			}
		} catch (Throwable e) {
			// e.g. nothing could be matched
			debug.log(Level.FINE, "Watch folder: " + e.getMessage(), e);
			for (File f : files) {
				if (f.exists()) {
					unmatched.put(f, f.lastModified());
				}
			}
		}
	}

}
