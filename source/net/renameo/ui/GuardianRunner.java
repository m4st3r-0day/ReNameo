package net.renameo.ui;

import static net.renameo.Logging.*;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import javax.swing.SwingUtilities;

import net.renameo.Settings;
import net.renameo.guardian.Guardian;
import net.renameo.util.DefaultThreadFactory;
import net.renameo.util.ui.SwingEventBus;

/**
 * Runs the guardian in the background: every night at the chosen hour while the app is open, or when asked. One run at a time; the Guardian page shows its
 * progress.
 */
public final class GuardianRunner {

	private GuardianRunner() {
		throw new UnsupportedOperationException();
	}

	private static volatile boolean running;
	private static volatile boolean cancelled;
	private static volatile String progress = "";

	private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(new DefaultThreadFactory("Guardian", Thread.MIN_PRIORITY, true));

	/**
	 * Look every 10 minutes whether tonight's run is due.
	 */
	public static void start() {
		scheduler.scheduleWithFixedDelay(() -> {
			try {
				if (isDue()) {
					run(false);
				}
			} catch (Throwable e) {
				debug.log(Level.WARNING, "Guardian: " + e, e);
			}
		}, 2, 10, TimeUnit.MINUTES);
	}

	static boolean isDue() {
		File folder = Guardian.getFolder();
		if (!Boolean.parseBoolean(Guardian.enabled.getValue()) || folder == null || !folder.isDirectory() || running) {
			return false;
		}
		int hour;
		try {
			hour = Integer.parseInt(Guardian.hour.getValue());
		} catch (NumberFormatException e) {
			hour = 3;
		}
		String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
		String last = Guardian.getLastRun();
		int now = Integer.parseInt(new SimpleDateFormat("H").format(new Date()));
		return now >= hour && (last == null || !last.startsWith(today));
	}

	public static boolean isRunning() {
		return running;
	}

	public static String getProgress() {
		return progress;
	}

	public static void cancel() {
		cancelled = true;
		progress = "Stopping…";
	}

	/**
	 * Check the library now (in the background).
	 */
	public static void runNow() {
		if (!running) {
			scheduler.execute(() -> run(true));
		}
	}

	private static void run(boolean manual) {
		File folder = Guardian.getFolder();
		if (folder == null || !folder.isDirectory()) {
			return;
		}
		running = true;
		cancelled = false;
		progress = "Reading " + folder.getName();
		try {
			Locale locale = Locale.forLanguageTag(Settings.forPackage(net.renameo.ui.rename.RenamePanel.class).entry("rename.language").defaultValue("en").getValue());
			String profile = Guardian.profile.getValue();
			Guardian.Report report = Guardian.scan(folder, Guardian.getMode(), profile, Boolean.parseBoolean(Guardian.thorough.getValue()), locale, () -> cancelled, (n, title) -> {
				progress = String.format("Checking %s · %d files", title, n);
			});
			String title = report.pending > 0 ? report.pending + (report.pending == 1 ? " change to review" : " changes to review") : report.applied > 0 ? report.applied + " corrected" : "Library is in order";
			toast("Guardian: " + title, report.toString(), report.pending > 0 || report.failed > 0);
		} catch (CancellationException e) {
			toast("Guardian stopped", "Nothing was changed after the stop.", false);
		} catch (Exception e) {
			log.log(Level.WARNING, "Guardian: " + e.getMessage(), e);
			if (manual) {
				toast("Guardian failed", e.getMessage(), true);
			}
		} finally {
			running = false;
			progress = "";
		}
	}

	private static void toast(String title, String detail, boolean attention) {
		SwingUtilities.invokeLater(() -> SwingEventBus.getInstance().post(new AppEvents.Toast(title, detail, attention ? AppEvents.Status.Kind.WARNING : AppEvents.Status.Kind.READY)));
	}

}
