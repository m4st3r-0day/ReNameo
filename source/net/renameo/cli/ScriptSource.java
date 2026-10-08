package net.renameo.cli;

import static java.util.Arrays.*;
import static net.renameo.Logging.*;
import static net.renameo.util.FileUtilities.*;

import java.io.File;
import java.net.URI;
import java.time.Duration;


import net.renameo.ApplicationFolder;
import net.renameo.Cache;
import net.renameo.CacheType;

public enum ScriptSource {

	/**
	 * fn:name runs name.groovy from the user's scripts folder (FileBot's online script repository no longer exists).
	 */
	USER_SCRIPTS {

		@Override
		public String accept(String input) {
			return input.startsWith("fn:") ? input.substring(3) : null;
		}

		@Override
		public ScriptProvider getScriptProvider(String input) throws Exception {
			return name -> {
				File script = new File(getScriptsFolder(), name + ".groovy");
				if (!script.isFile()) {
					throw new CmdlineException(String.format("Script not found: %s (put %s.groovy into %s)", name, name, getScriptsFolder()));
				}
				return readTextFile(script);
			};
		}

	},

	INLINE_GROOVY {

		@Override
		public String accept(String input) {
			return input.startsWith("g:") ? input.substring(2) : null;
		}

		@Override
		public ScriptProvider getScriptProvider(String input) throws Exception {
			return g -> g;
		}

	},

	REMOTE_URL {

		@Override
		public String accept(String input) {
			// absolute paths on Windows appear to be valid URIs so we need explicitly exclude them (e.g. C:\path\to\script.groovy)
			if (input.length() < 2 || input.charAt(1) == ':') {
				return null;
			}

			try {
				URI uri = new URI(input);
				if (uri.isAbsolute()) {
					return getName(new File(uri.getPath()));
				}
			} catch (Exception e) {
				debug.finest(e::toString);
			}
			return null;
		}

		@Override
		public ScriptProvider getScriptProvider(String input) throws Exception {
			URI parent = new URI(input).resolve(".");

			return n -> getCache().text(n, s -> parent.resolve(s + ".groovy").toURL()).expire(Duration.ZERO).get();
		}

	},

	LOCAL_FILE {

		@Override
		public String accept(String input) {
			try {
				File f = new File(input).getCanonicalFile();
				if (f.isFile()) {
					return getName(f);
				}
			} catch (Exception e) {
				debug.finest(e::toString);
			}
			return null;
		}

		@Override
		public ScriptProvider getScriptProvider(String input) throws Exception {
			File base = new File(input).getCanonicalFile().getParentFile();

			return f -> readTextFile(new File(base, f + ".groovy"));
		}

	};

	public abstract String accept(String input);

	public abstract ScriptProvider getScriptProvider(String input) throws Exception;

	public static File getScriptsFolder() {
		File folder = ApplicationFolder.AppData.resolve("scripts");
		folder.mkdirs();
		return folder;
	}

	public Cache getCache() {
		return Cache.getCache(name(), CacheType.Persistent);
	}

	public static ScriptSource findScriptProvider(String input) throws Exception {
		return stream(values()).filter(s -> s.accept(input) != null).findFirst().orElseThrow(() -> {
			return new CmdlineException("Bad script source: " + input);
		});
	}

}
