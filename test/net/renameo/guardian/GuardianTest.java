package net.renameo.guardian;

import static java.util.Arrays.*;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GuardianTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private File file(String path) throws Exception {
		File f = new File(tmp.getRoot(), path);
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), new byte[1]);
		return f;
	}

	@Test
	public void tidyNamesNeedNoLookup() throws Exception {
		File root = tmp.getRoot();
		assertTrue(Guardian.looksTidy(file("Dark (2017)/Season 1/Dark (2017) - S01E01 - Secrets.mkv"), root));
		assertTrue(Guardian.looksTidy(file("Dark (2017) [tmdbid-70523]/Season 02/Dark (2017) - S02E01 - Beginnings.mkv"), root));
		assertTrue(Guardian.looksTidy(file("Inception (2010)/Inception (2010).mkv"), root));
		assertTrue(Guardian.looksTidy(file("Dune (2021) [tmdbid-438631]/Dune (2021) [tmdbid-438631].mkv"), root));
		assertTrue(Guardian.looksTidy(file("Heat (1995)/Heat (1995) - cd1.avi"), root));

		assertFalse(Guardian.looksTidy(file("Dark (2017)/Season 1/Dark.S01E02.720p.mkv"), root));
		assertFalse(Guardian.looksTidy(file("Dark (2017)/Dark - S02E01.mkv"), root));
		assertFalse(Guardian.looksTidy(file("Dark (2017)/Season 2/Dark (2017) - S01E03 - Wrong Season.mkv"), root));
		assertFalse(Guardian.looksTidy(file("Breaking Bad/Season 01/Breaking Bad - S01E01.mkv"), root));
		assertFalse(Guardian.looksTidy(file("Avatar.2009.1080p/Avatar.2009.1080p.mkv"), root));
		assertFalse(Guardian.looksTidy(file("Severance.S01E01.mkv"), root));
	}

	@Test
	public void groups() throws Exception {
		File root = tmp.getRoot();
		assertEquals(new File(root, "Dark (2017)"), Guardian.getGroup(file("Dark (2017)/Season 1/Dark.S01E02.mkv"), root));
		assertEquals(new File(root, "Dark (2017)"), Guardian.getGroup(file("Dark (2017)/Dark - S02E01.mkv"), root));
		assertEquals(new File(root, "Avatar.2009"), Guardian.getGroup(file("Avatar.2009/Avatar.2009.mkv"), root));
		File loose = file("Severance.S01E01.mkv");
		assertEquals(loose, Guardian.getGroup(loose, root));
	}

	@Test
	public void seriesTitle() throws Exception {
		File dark = file("Dark (2017)/Season 1/Dark.S01E02.mkv");
		assertEquals("Dark", Guardian.getSeriesTitle(new File(tmp.getRoot(), "Dark (2017)"), asList(dark)));
		assertEquals("Neagley", Guardian.getSeriesTitle(new File(tmp.getRoot(), "Neagley.S01.1080p.WEB-DL-TBK"), asList(file("Neagley.S01.1080p.WEB-DL-TBK/Neagley.S01E01.mkv"))));
		File loose = file("Severance.S01E01.mkv");
		assertEquals("Severance", Guardian.getSeriesTitle(loose, asList(loose)));
		assertNull(Guardian.getSeriesTitle(new File(tmp.getRoot(), "Avatar.2009"), asList(file("Avatar.2009/Avatar.2009.mkv"))));
	}

	@Test
	public void reasons() {
		assertEquals("Name doesn't follow the Jellyfin naming", Guardian.reason(new File("/L/Dark (2017)/Season 1/a.mkv"), new File("/L/Dark (2017)/Season 1/b.mkv"), "jellyfin"));
		assertEquals("Goes into its season folder", Guardian.reason(new File("/L/Dark (2017)/a.mkv"), new File("/L/Dark (2017)/Season 02/b.mkv"), "jellyfin"));
		assertEquals("Folder gets the proper name: Breaking Bad (2008)", Guardian.reason(new File("/L/Breaking Bad/Season 01/a.mkv"), new File("/L/Breaking Bad (2008)/Season 01/b.mkv"), "plex"));
		assertEquals("Folder gets the proper name: Avatar (2009)", Guardian.reason(new File("/L/Avatar.2009/a.mkv"), new File("/L/Avatar (2009)/b.mkv"), "plex"));
	}

}
