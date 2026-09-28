package net.renameo;

import static net.renameo.Logging.*;
import static net.renameo.Settings.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.logging.Level;

public enum ApplicationFolder {

	// real user home (the user.home will point to the application-specific container in sandbox environments)
	UserHome(isMacSandbox() ? System.getProperty("UserHome") : System.getProperty("user.home")),

	AppData(System.getProperty("application.dir", defaultAppData())),

	TemporaryFiles(System.getProperty("java.io.tmpdir")),

	Cache(System.getProperty("application.cache", AppData.resolve("cache").getPath()));

	/**
	 * Earlier builds kept caches and history in ~/.filebot; adopt that folder once instead of starting from scratch.
	 */
	private static String defaultAppData() {
		File folder = UserHome.resolve(".renameo");
		File legacy = UserHome.resolve(".filebot");
		if (!folder.exists() && legacy.isDirectory() && !legacy.renameTo(folder)) {
			return legacy.getPath();
		}
		return folder.getPath();
	}

	private File path;

	ApplicationFolder(String path) {
		// first start: the data and cache folders don't exist yet
		new File(path).mkdirs();

		try {
			// use canonical file path
			this.path = Paths.get(path).toRealPath(LinkOption.NOFOLLOW_LINKS).toFile();
		} catch (IOException e) {
			debug.log(Level.WARNING, e, e::toString);

			// default to file path as is
			this.path = new File(path).getAbsoluteFile();
		}
	}

	public File get() {
		return path;
	}

	public File resolve(String name) {
		return new File(path, name);
	}

}
