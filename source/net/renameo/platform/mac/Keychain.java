package net.renameo.platform.mac;

import static net.renameo.Logging.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Secrets in the macOS login keychain, via the {@code security} command line tool.
 */
public final class Keychain {

	private Keychain() {
		throw new UnsupportedOperationException();
	}

	public static boolean isSupported() {
		return System.getProperty("os.name", "").toLowerCase().startsWith("mac");
	}

	public static String get(String service, String account) {
		String secret = run(Arrays.asList("security", "find-generic-password", "-s", service, "-a", account, "-w"));
		return secret == null ? null : secret.trim();
	}

	public static boolean set(String service, String account, String secret) {
		// -U updates an existing item instead of failing
		return run(Arrays.asList("security", "add-generic-password", "-U", "-s", service, "-a", account, "-w", secret)) != null;
	}

	public static void delete(String service, String account) {
		run(Arrays.asList("security", "delete-generic-password", "-s", service, "-a", account));
	}

	private static String run(List<String> command) {
		if (!isSupported()) {
			return null;
		}
		try {
			Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			try (InputStream in = process.getInputStream()) {
				byte[] buffer = new byte[4096];
				for (int n; (n = in.read(buffer)) > 0;) {
					out.write(buffer, 0, n);
				}
			}
			if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
				return null;
			}
			return new String(out.toByteArray(), StandardCharsets.UTF_8);
		} catch (Exception e) {
			debug.warning("Keychain access failed: " + e.getMessage());
			return null;
		}
	}

}
