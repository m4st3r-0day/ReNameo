package net.renameo;

import static java.awt.GraphicsEnvironment.*;
import static java.util.stream.Collectors.*;
import static net.renameo.Logging.*;
import static net.renameo.Settings.*;
import static net.renameo.util.FileUtilities.*;
import static net.renameo.util.XPathUtilities.*;
import static net.renameo.util.ui.SwingUI.*;

import java.io.File;
import java.io.IOException;
import java.security.CodeSource;
import java.security.Permission;
import java.security.PermissionCollection;
import java.security.Permissions;
import java.security.Policy;
import java.security.ProtectionDomain;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.prefs.Preferences;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

import org.kohsuke.args4j.CmdLineException;

import net.renameo.cli.ArgumentBean;
import net.renameo.cli.ArgumentProcessor;
import net.renameo.format.ExpressionFormat;
import net.renameo.platform.mac.MacAppUtilities;
import net.renameo.platform.windows.WinAppUtilities;
import net.renameo.ui.ReNameoMenuBar;
import net.renameo.ui.GettingStartedStage;
import net.renameo.ui.MainFrame;
import net.renameo.ui.NotificationHandler;
import net.renameo.ui.PanelBuilder;
import net.renameo.ui.SinglePanelFrame;
import net.renameo.ui.transfer.FileTransferable;
import net.renameo.util.PreferencesMap.PreferencesEntry;
import net.renameo.util.ui.NightTheme;
import net.renameo.util.ui.SwingEventBus;

public class Main {

	public static void main(String[] argv) {
		try {
			Settings.migrateLegacyPreferences();

			// parse arguments
			ArgumentBean args = new ArgumentBean(argv);

			// just print help message or version string and then exit
			if (args.printHelp()) {
				log.info(String.format("%s%n%n%s", getApplicationIdentifier(), args.usage()));
				System.exit(0);
			}

			if (args.printVersion()) {
				log.info(String.join(" / ", getApplicationIdentifier(), getJavaRuntimeIdentifier(), getSystemIdentifier()));
				System.exit(0);
			}

			if (args.clearCache() || args.clearUserData()) {
				// clear persistent user preferences
				if (args.clearUserData()) {
					log.info("Reset preferences");
					Settings.forPackage(Main.class).clear();

					// restore preferences on start if empty (TODO: remove after a few releases)
					ApplicationFolder.AppData.resolve("preferences.backup.xml").delete();
				}

				// clear caches
				if (args.clearCache()) {
					// clear cache must be called manually
					if (System.console() == null) {
						log.severe("`renameo -clear-cache` has been disabled due to abuse.");
						System.exit(1);
					}

					log.info("Clear cache");
					for (File folder : getChildren(ApplicationFolder.Cache.get(), FOLDERS)) {
						log.fine("* Delete " + folder);
						delete(folder);
					}
				}

				// just clear cache and/or settings and then exit
				System.exit(0);
			}

			// make sure we can access application arguments at any time
			setApplicationArguments(args);

			// update system properties
			initializeSystemProperties(args);
			initializeLogging(args);

			// initialize this stuff before anything else
			CacheManager.getInstance();
			initializeSecurityManager();

			// initialize history spooler
			HistorySpooler.getInstance().setPersistentHistoryEnabled(useRenameHistory());

			// CLI mode => run command-line interface and then exit
			if (args.runCLI()) {
				int status = new ArgumentProcessor().run(args);
				System.exit(status);
			}

			if (isHeadless()) {
				log.info(String.format("%s / %s (headless)%n%n%s", getApplicationIdentifier(), getJavaRuntimeIdentifier(), args.usage()));
				System.exit(1);
			}

			// GUI mode => start user interface
			SwingUtilities.invokeLater(() -> {
				// restore preferences on start if empty (TODO: remove after a few releases)
				try {
					if (Preferences.userNodeForPackage(Main.class).keys().length == 0) {
						File f = ApplicationFolder.AppData.resolve("preferences.backup.xml");
						if (f.exists()) {
							log.fine("Restore user preferences: " + f);
							Settings.restore(f);
						} else {
							log.fine("No user preferences found: " + f);
						}
					}
				} catch (Exception e) {
					debug.log(Level.WARNING, "Failed to restore preferences", e);
				}

				startUserInterface(args);

				// run background tasks
				newSwingWorker(() -> onStart(args)).execute();
			});
		} catch (CmdLineException e) {
			// illegal arguments => print CLI error message
			log.severe(e::getMessage);
			System.exit(1);
		} catch (Throwable e) {
			// unexpected error => dump stack
			debug.log(Level.SEVERE, "Error during startup", e);
			System.exit(1);
		}
	}

	private static void onStart(ArgumentBean args) {
		// publish file arguments
		List<File> files = args.getFiles(false);
		if (files.size() > 0) {
			SwingEventBus.getInstance().post(new FileTransferable(files));
		}

		// JavaFX is used for GettingStartedDialog
		try {
			initJavaFX();
		} catch (Throwable e) {
			log.log(Level.SEVERE, "Failed to initialize JavaFX. Please install JavaFX.", e);
		}

		// check if application help should be shown
		if (!"skip".equals(System.getProperty("application.help"))) {
			try {
				checkGettingStarted();
			} catch (Throwable e) {
				debug.log(Level.WARNING, "Failed to show Getting Started help", e);
			}
		}
	}

	private static void startUserInterface(ArgumentBean args) {
		// use native LaF an all platforms (use platform-independent laf for standalone jar deployment)
		if (isPortableApp()) {
			setNimbusLookAndFeel();
		} else {
			setSystemLookAndFeel();
		}

		// apply modern UI font
		installModernUIFont();

		// start multi panel or single panel frame
		PanelBuilder[] panels = args.getPanelBuilders();
		JFrame frame = panels.length > 1 ? new MainFrame(panels) : new SinglePanelFrame(panels[0]);

		try {
			restoreWindowBounds(frame, Settings.forPackage(MainFrame.class)); // restore previous size and location
		} catch (Exception e) {
			frame.setLocation(120, 80); // make sure the main window is not displayed out of screen bounds
		}

		frame.addWindowListener(windowClosed(evt -> {
			evt.getWindow().setVisible(false);

			// make sure any long running operations are done now and not later on the shutdown hook thread
			HistorySpooler.getInstance().commit();

			// restore preferences on start if empty (TODO: remove after a few releases)
			Settings.store(ApplicationFolder.AppData.resolve("preferences.backup.xml"));

			System.exit(0);
		}));

		// configure main window
		if (isMacApp()) {
			// Mac specific configuration
			MacAppUtilities.initializeApplication(ReNameoMenuBar.createMenuBar(), files -> SwingEventBus.getInstance().post(new FileTransferable(files)));
		} else if (isUbuntuApp()) {
			// Ubuntu/Debian specific configuration
			frame.setIconImages(ResourceManager.getApplicationIconImages());
			frame.setJMenuBar(ReNameoMenuBar.createMenuBar());
		} else if (isWindowsApp()) {
			// Windows specific configuration
			WinAppUtilities.initializeApplication();
			frame.setIconImages(ResourceManager.getApplicationIconImages());
			frame.setJMenuBar(ReNameoMenuBar.createMenuBar());
		} else {
			// generic Linux/FreeBSD/Solaris configuration
			frame.setIconImages(ResourceManager.getApplicationIconImages());
			frame.setJMenuBar(ReNameoMenuBar.createMenuBar());
		}

		// apply night mode theme (if previously enabled)
		NightTheme.applyNightModeIfEnabled();

		// start application
		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		frame.setVisible(true);
	}

	/**
	 * Show Getting Started to new users
	 */
	private static void checkGettingStarted() throws Exception {
		PreferencesEntry<String> started = Settings.forPackage(Main.class).entry("getting.started").defaultValue("0");
		if ("0".equals(started.getValue())) {
			started.setValue("1");
			started.flush();

			// open Getting Started
			SwingUtilities.invokeLater(GettingStartedStage::start);
		}
	}

	private static void restoreWindowBounds(JFrame window, Settings settings) {
		// store bounds on close
		window.addWindowListener(windowClosed(evt -> {
			// don't save window bounds if window is maximized
			if (!isMaximized(window)) {
				settings.put("window.x", String.valueOf(window.getX()));
				settings.put("window.y", String.valueOf(window.getY()));
				settings.put("window.width", String.valueOf(window.getWidth()));
				settings.put("window.height", String.valueOf(window.getHeight()));
			}
		}));

		// restore bounds
		int x = Integer.parseInt(settings.get("window.x"));
		int y = Integer.parseInt(settings.get("window.y"));
		int width = Integer.parseInt(settings.get("window.width"));
		int height = Integer.parseInt(settings.get("window.height"));
		window.setBounds(x, y, width, height);
	}

	/**
	 * Initialize default SecurityManager and grant all permissions via security policy. Initialization is required in order to run {@link ExpressionFormat} in a secure sandbox.
	 */
	private static void initializeSecurityManager() {
		try {
			// initialize security policy used by the default security manager
			// because default the security policy is very restrictive (e.g. no FilePermission)
			Policy.setPolicy(new Policy() {

				@Override
				public boolean implies(ProtectionDomain domain, Permission permission) {
					// all permissions
					return true;
				}

				@Override
				public PermissionCollection getPermissions(CodeSource codesource) {
					// VisualVM can't connect if this method does return
					// a checked immutable PermissionCollection
					return new Permissions();
				}
			});

			// set default security manager
			System.setSecurityManager(new SecurityManager());
		} catch (Exception e) {
			// Java 18+ no longer allows installing a security manager at runtime, which is harmless for local use
			debug.fine("Security manager not available: " + e.getMessage());
		}
	}

	public static void initializeSystemProperties(ArgumentBean args) {
		System.setProperty("http.agent", String.format("%s %s", getApplicationName(), getApplicationVersion()));
		System.setProperty("sun.net.client.defaultConnectTimeout", "10000");
		System.setProperty("sun.net.client.defaultReadTimeout", "60000");

		System.setProperty("swing.crossplatformlaf", "javax.swing.plaf.nimbus.NimbusLookAndFeel");

		// the main window draws its own top bar, so the menu must live in the macOS menu bar
		if (System.getProperty("os.name", "").toLowerCase().startsWith("mac")) {
			System.setProperty("apple.laf.useScreenMenuBar", "true");

			// native title bar and dialogs follow the app theme rather than the system one
			System.setProperty("apple.awt.application.appearance", NightTheme.isNightMode() ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
		}
		System.setProperty("grape.root", ApplicationFolder.AppData.resolve("grape").getPath());
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");

		if (args.unixfs) {
			System.setProperty("unixfs", "true");
		}

		if (args.disableExtendedAttributes) {
			System.setProperty("useExtendedFileAttributes", "false");
			System.setProperty("useCreationDate", "false");
		}
	}

	public static void initializeLogging(ArgumentBean args) throws IOException {
		// make sure that these folders exist
		ApplicationFolder.TemporaryFiles.get().mkdirs();
		ApplicationFolder.AppData.get().mkdirs();

		if (args.runCLI()) {
			// CLI logging settings
			log.setLevel(args.getLogLevel());
		} else {
			// GUI logging settings
			log.setLevel(Level.INFO);
			log.addHandler(new NotificationHandler(getApplicationName()));

			// log errors to file
			try {
				Handler errorLogHandler = createSimpleFileHandler(ApplicationFolder.AppData.resolve("error.log"), Level.WARNING);
				log.addHandler(errorLogHandler);
				debug.addHandler(errorLogHandler);
			} catch (Exception e) {
				log.log(Level.WARNING, "Failed to initialize error log", e);
			}
		}

		// tee stdout and stderr to log file if --log-file is set
		if (args.logFile != null) {
			Handler logFileHandler = createLogFileHandler(args.getLogFile(), args.logLock, Level.ALL);
			log.addHandler(logFileHandler);
			debug.addHandler(logFileHandler);
		}
	}

}
