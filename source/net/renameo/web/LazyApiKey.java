package net.renameo.web;

import java.util.function.Supplier;

/**
 * An API key that is only looked up (environment, keychain, settings) when the first request needs it, so starting the app doesn't wait for it.
 */
public final class LazyApiKey {

	private final Supplier<String> source;
	private volatile String value;
	private volatile boolean resolved;

	public LazyApiKey(Supplier<String> source) {
		this.source = source;
	}

	public static LazyApiKey of(String value) {
		LazyApiKey key = new LazyApiKey(null);
		key.set(value);
		return key;
	}

	public String get() {
		if (!resolved) {
			synchronized (this) {
				if (!resolved) {
					value = source == null ? null : source.get();
					resolved = true;
				}
			}
		}
		return value;
	}

	public void set(String value) {
		this.value = value;
		this.resolved = true;
	}

	public boolean isEmpty() {
		String key = get();
		return key == null || key.isEmpty();
	}

	@Override
	public String toString() {
		return String.valueOf(get());
	}

}
