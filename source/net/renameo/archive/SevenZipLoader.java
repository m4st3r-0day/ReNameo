package net.renameo.archive;

import net.sf.sevenzipjbinding.IArchiveOpenCallback;
import net.sf.sevenzipjbinding.IInArchive;
import net.sf.sevenzipjbinding.IInStream;
import net.sf.sevenzipjbinding.SevenZip;
import net.sf.sevenzipjbinding.SevenZipException;
import net.sf.sevenzipjbinding.SevenZipNativeInitializationException;

public class SevenZipLoader {

	private static boolean nativeLibrariesLoaded = false;

	private static synchronized void requireNativeLibraries() throws SevenZipNativeInitializationException {
		if (nativeLibrariesLoaded) {
			return;
		}

		// initialize 7z-JBinding native libs
		try {
			System.loadLibrary("7-Zip-JBinding");
			SevenZip.initLoadedLibraries(); // NATIVE LIBS MUST BE LOADED WITH SYSTEM CLASSLOADER
			nativeLibrariesLoaded = true;
		} catch (Throwable e) {
			throw new SevenZipNativeInitializationException("Failed to load 7z-JBinding: " + e.getMessage(), e);
		}
	}

	/**
	 * The native library exists for Windows, Linux x86 and Intel Macs, not for Apple Silicon.
	 */
	public static boolean isNativeAvailable() {
		try {
			requireNativeLibraries();
			return true;
		} catch (Throwable e) {
			return false;
		}
	}

	public static String getNativeVersion() throws SevenZipNativeInitializationException {
		requireNativeLibraries();

		return SevenZip.getSevenZipVersion().version;
	}

	public static IInArchive open(IInStream stream, IArchiveOpenCallback callback) throws SevenZipException, SevenZipNativeInitializationException {
		requireNativeLibraries();

		return (callback == null) ? SevenZip.openInArchive(null, stream) : SevenZip.openInArchive(null, stream, callback);
	}

}
