package net.renameo.media;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class LibraryLayoutTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final List<String> NEAGLEY = singletonList("Neagley");

	private static File episode(String series, String name) {
		return new File("Shows/" + series + "/Season 01/" + name);
	}

	private File file(String path) throws Exception {
		File f = new File(tmp.getRoot(), path);
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), new byte[1]);
		return f;
	}

	private File path(String path) {
		return new File(tmp.getRoot(), path);
	}

	@Test
	public void releaseFolderIsRenamedInPlaceWithSeasonFolder() throws Exception {
		File original = file("Downloads/Neagley.S01.1080p.AMZN.WEB-DL.DDP5.1.Atmos.H265-TBK/Neagley.S01E01.1080p.mkv");
		File result = LibraryLayout.tidy(episode("Neagley (2026) [tmdbid-273207]", "Neagley (2026) - S01E01 - L Train.mkv"), original, NEAGLEY);
		assertEquals(path("Downloads/Neagley (2026)/Season 01/Neagley (2026) - S01E01 - L Train.mkv"), result);
	}

	@Test
	public void looseFileOnlyGetsItsNewName() throws Exception {
		File original = file("Downloads/Neagley.S01E01.1080p.mkv");
		File result = LibraryLayout.tidy(episode("Neagley (2026)", "Neagley (2026) - S01E01 - L Train.mkv"), original, NEAGLEY);
		assertEquals(path("Downloads/Neagley (2026) - S01E01 - L Train.mkv"), result);
	}

	@Test
	public void looseFileNextToItsSeriesFolderGoesIntoIt() throws Exception {
		file("Downloads/Neagley (2026)/Season 01/Neagley (2026) - S01E01 - L Train.mkv");
		File original = file("Downloads/Neagley.S01E03.1080p.WEB-DL.mkv");
		File result = LibraryLayout.tidy(episode("Neagley (2026) [tmdbid-273207]", "Neagley (2026) - S01E03 - Hammer Time.mkv"), original, NEAGLEY);
		assertEquals(path("Downloads/Neagley (2026)/Season 01/Neagley (2026) - S01E03 - Hammer Time.mkv"), result);
	}

	@Test
	public void looseFileNextToAnotherFolderOnlyGetsItsNewName() throws Exception {
		file("Downloads/Neagley Fans/notes.txt");
		File original = file("Downloads/Neagley.S01E03.1080p.WEB-DL.mkv");
		File result = LibraryLayout.tidy(episode("Neagley (2026)", "Neagley (2026) - S01E03 - Hammer Time.mkv"), original, NEAGLEY);
		assertEquals(path("Downloads/Neagley (2026) - S01E03 - Hammer Time.mkv"), result);
	}

	@Test
	public void cleanSeriesFolderAndExistingSeasonFolderStay() throws Exception {
		File original = file("Shows/Dark (2017)/Season 1/Dark - S01E02 - Lies.mkv");
		File result = LibraryLayout.tidy(episode("Dark (2017) [tmdbid-70523]", "Dark (2017) - S01E02 - Lies.mkv"), original, singletonList("Dark"));
		assertEquals(path("Shows/Dark (2017)/Season 1/Dark (2017) - S01E02 - Lies.mkv"), result);
	}

	@Test
	public void movieReleaseFolder() throws Exception {
		File original = file("Downloads/Inception.2010.1080p.BluRay.x264-GRP/Inception.2010.1080p.BluRay.x264-GRP.mkv");
		File result = LibraryLayout.tidy(new File("Movies/Inception (2010) [tmdbid-27205]/Inception (2010) [tmdbid-27205].mkv"), original, singletonList("Inception"));
		assertEquals(path("Downloads/Inception (2010)/Inception (2010) [tmdbid-27205].mkv"), result);
	}

	@Test
	public void episodeInReleaseFolderInsideSeriesFolderUsesTheSeriesFolder() throws Exception {
		file("Shows/Neagley/Season 1/Neagley - S01E01.mkv");
		File original = file("Shows/Neagley/Neagley.S02.1080p.WEB-DL-TBK/Neagley.S02E01.1080p.mkv");
		File result = LibraryLayout.tidy(new File("Shows/Neagley (2026)/Season 02/Neagley (2026) - S02E01 - Pilot.mkv"), original, NEAGLEY);
		assertEquals(path("Shows/Neagley (2026)/Season 02/Neagley (2026) - S02E01 - Pilot.mkv"), result);
	}

	@Test
	public void newSeasonNextToCleanSeriesFolderGoesIntoIt() throws Exception {
		file("Downloads/Neagley (2026)/Season 01/Neagley (2026) - S01E01 - L Train.mkv");
		File original = file("Downloads/Neagley.S02.1080p.WEB-DL-TBK/Neagley.S02E01.1080p.mkv");
		File result = LibraryLayout.tidy(new File("Shows/Neagley (2026)/Season 02/Neagley (2026) - S02E01 - Pilot.mkv"), original, NEAGLEY);
		assertEquals(path("Downloads/Neagley (2026)/Season 02/Neagley (2026) - S02E01 - Pilot.mkv"), result);
	}

	@Test
	public void folderNamedAfterAnAliasTitle() throws Exception {
		File original = file("Downloads/Il.Trono.di.Spade.S01.ITA.720p/Il.Trono.di.Spade.S01E01.mkv");
		File result = LibraryLayout.tidy(new File("Shows/Game of Thrones (2011)/Season 01/Game of Thrones (2011) - S01E01 - Winter Is Coming.mkv"), original, asList("Game of Thrones", "Il Trono di Spade"));
		assertEquals(path("Downloads/Game of Thrones (2011)/Season 01/Game of Thrones (2011) - S01E01 - Winter Is Coming.mkv"), result);
	}

	@Test
	public void withoutTitlesOnlyTheNameChanges() throws Exception {
		File original = file("Music/Album/01 track.mp3");
		File result = LibraryLayout.tidy(new File("Music/Artist/Album/01 - Title.mp3"), original, emptyList());
		assertEquals(path("Music/Album/01 - Title.mp3"), result);
	}

	@Test
	public void titleFolder() {
		assertTrue(LibraryLayout.isTitleFolder("Neagley.S01.1080p.AMZN.WEB-DL.DDP5.1.ENG.Atmos.ITA.H265-TBK", NEAGLEY));
		assertTrue(LibraryLayout.isTitleFolder("Neagley (2026)", NEAGLEY));
		assertTrue(LibraryLayout.isTitleFolder("Neagley", NEAGLEY));
		assertTrue(LibraryLayout.isTitleFolder("Dune.2021.2160p", singletonList("Dune (2021)")));
		assertTrue(LibraryLayout.isTitleFolder("The.Office.US.S01", singletonList("The Office")));

		assertFalse(LibraryLayout.isTitleFolder("Downloads", NEAGLEY));
		assertFalse(LibraryLayout.isTitleFolder("Upload.S01.1080p", singletonList("Up")));
		assertFalse(LibraryLayout.isTitleFolder("Star Trek", singletonList("Star Trek: The Next Generation")));
		assertFalse(LibraryLayout.isTitleFolder("Anything", singletonList("A")));
	}

	@Test
	public void restOfTheFolderFollowsAndOldFolderGoesAway() throws Exception {
		File original = file("Downloads/Neagley.S01.1080p-TBK/Neagley.S01E01.1080p.mkv");
		file("Downloads/Neagley.S01.1080p-TBK/Neagley.S01.1080p-TBK.nfo");
		file("Downloads/Neagley.S01.1080p-TBK/Sample/neagley.sample.mkv");
		file("Downloads/Neagley.S01.1080p-TBK/Empty/.DS_Store");
		file("Downloads/Neagley.S01.1080p-TBK/.DS_Store");

		File target = LibraryLayout.tidy(episode("Neagley (2026)", "Neagley (2026) - S01E01 - L Train.mkv"), original, NEAGLEY);
		target.getParentFile().mkdirs();
		Files.move(original.toPath(), target.toPath());

		Map<File, File> renamed = new LinkedHashMap<File, File>();
		renamed.put(original, target);
		Map<File, File> moves = LibraryLayout.finishRelocations(renamed);

		assertFalse(path("Downloads/Neagley.S01.1080p-TBK").exists());
		assertTrue(path("Downloads/Neagley (2026)/Neagley.S01.1080p-TBK.nfo").isFile());
		assertTrue(path("Downloads/Neagley (2026)/Sample/neagley.sample.mkv").isFile());
		assertFalse(path("Downloads/Neagley (2026)/Empty").exists());

		// every move is a file, so the history can undo it
		assertEquals(2, moves.size());
		for (File f : moves.keySet()) {
			assertFalse(f.getName(), moves.get(f).isDirectory());
		}

		// done once
		assertTrue(LibraryLayout.finishRelocations(renamed).isEmpty());
	}

	@Test
	public void fileOnBothSidesStaysWhereItIs() throws Exception {
		file("Downloads/Neagley (2026)/notes.txt");
		File original = file("Downloads/Neagley.S02.1080p-TBK/Neagley.S02E01.1080p.mkv");
		file("Downloads/Neagley.S02.1080p-TBK/notes.txt");

		File target = LibraryLayout.tidy(new File("Shows/Neagley (2026)/Season 02/Neagley (2026) - S02E01 - Pilot.mkv"), original, NEAGLEY);
		target.getParentFile().mkdirs();
		Files.move(original.toPath(), target.toPath());

		Map<File, File> renamed = new LinkedHashMap<File, File>();
		renamed.put(original, target);
		LibraryLayout.finishRelocations(renamed);

		assertTrue(path("Downloads/Neagley.S02.1080p-TBK/notes.txt").isFile());
		assertTrue(path("Downloads/Neagley (2026)/notes.txt").isFile());
	}

	@Test
	public void nothingHappensForFilesThatWereNotRenamed() throws Exception {
		File original = file("Downloads/Neagley.S03.1080p-TBK/Neagley.S03E01.1080p.mkv");
		LibraryLayout.tidy(new File("Shows/Neagley (2026)/Season 03/Neagley (2026) - S03E01 - X.mkv"), original, NEAGLEY);

		// e.g. only the preview was shown, nothing was renamed
		assertTrue(LibraryLayout.finishRelocations(emptyMap()).isEmpty());
		assertTrue(original.isFile());
	}

}
