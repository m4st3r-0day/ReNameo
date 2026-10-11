package net.renameo.media;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.file.Files;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import net.renameo.media.LibraryStats.Kind;
import net.renameo.media.LibraryStats.Result;
import net.renameo.media.LibraryStats.Title;

public class LibraryStatsTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private void file(String path, int size) throws Exception {
		File f = new File(tmp.getRoot(), path);
		f.getParentFile().mkdirs();
		Files.write(f.toPath(), new byte[size]);
	}

	private Result library() throws Exception {
		// a clean series, with one subtitle
		file("Shows/Dark (2017)/Season 1/Dark (2017) - S01E01 - Secrets 1080p.mkv", 100);
		file("Shows/Dark (2017)/Season 1/Dark (2017) - S01E01 - Secrets 1080p.ita.srt", 1);
		file("Shows/Dark (2017)/Season 2/Dark (2017) - S02E01 - Beginnings 720p.mkv", 100);

		// a release folder: one episode twice, a sample
		file("Downloads/Neagley.S01.1080p.WEB-DL-TBK/Neagley.S01E01.1080p.WEB-DL-TBK.mkv", 100);
		file("Downloads/Neagley.S01.1080p.WEB-DL-TBK/Neagley.S01E01.720p.WEB-DL-TBK.mkv", 50);
		file("Downloads/Neagley.S01.1080p.WEB-DL-TBK/Neagley.S01E02.1080p.WEB-DL-TBK.mkv", 100);
		file("Downloads/Neagley.S01.1080p.WEB-DL-TBK/Sample/neagley.s01e01.sample.mkv", 5);

		// a loose episode
		file("The.Office.US.S01E01.720p.mkv", 100);

		// movies: two copies of one, two parts of another, an extra
		file("Movies/Avatar (2009)/Avatar (2009) 2160p.mkv", 400);
		file("Movies/Avatar (2009)/Avatar.2009.720p.mkv", 80);
		file("Movies/Avatar (2009)/Featurettes/Making Of.mkv", 10);
		file("Movies/Heat.1995.CD1.avi", 70);
		file("Movies/Heat.1995.CD2.avi", 70);

		// hidden and trash folders are left out
		file(".renameo-trash/Old.Movie.2001.mkv", 999);
		file("@eaDir/Thumb.2001.mkv", 999);

		return LibraryStats.scan(tmp.getRoot(), () -> false, (n, f) -> {
		});
	}

	@Test
	public void titles() throws Exception {
		Result r = library();
		Map<String, Title> titles = r.titles.stream().collect(Collectors.toMap(t -> t.name, Function.identity()));

		assertEquals(titles.keySet().toString(), 5, titles.size());
		assertEquals(3, r.count(Kind.SERIES));
		assertEquals(2, r.count(Kind.MOVIE));

		Title dark = titles.get("Dark (2017)");
		assertEquals(Kind.SERIES, dark.kind);
		assertEquals("[S01E01, S02E01]", dark.episodes.toString());
		assertEquals("[1, 2]", dark.seasons.toString());
		assertEquals(1, dark.withoutSubtitles);

		Title neagley = titles.get("Neagley");
		assertEquals("[S01E01, S01E02]", neagley.episodes.toString());
		assertEquals(1, neagley.duplicates);
		assertEquals(3, neagley.files);
		assertEquals("1080p", neagley.getResolution());

		assertTrue(titles.containsKey("The Office US"));

		Title avatar = titles.get("Avatar (2009)");
		assertEquals(Kind.MOVIE, avatar.kind);
		assertEquals(1, avatar.duplicates);
		assertEquals(480, avatar.size);

		Title heat = titles.get("Heat (1995)");
		assertEquals(0, heat.duplicates);
		assertEquals(2, heat.files);
	}

	@Test
	public void totals() throws Exception {
		Result r = library();
		assertEquals(5, r.getEpisodes());
		assertEquals(2, r.getDuplicates());
		assertEquals(1, r.extras);
		assertEquals(100 + 100 + 100 + 50 + 100 + 100 + 400 + 80 + 10 + 70 + 70, r.size);
		assertEquals(1, (int) r.resolutions.get("4K"));
		assertEquals(3, (int) r.resolutions.get("1080p"));
		assertEquals(4, (int) r.resolutions.get("720p"));
		assertEquals(3, (int) r.resolutions.get("Unknown"));
		assertEquals(-1, r.getMissing());
	}

	@Test
	public void names() {
		assertEquals("Neagley", LibraryStats.cleanName("Neagley.S01.1080p.AMZN.WEB-DL.DDP5.1.ENG.Atmos.ITA.H265-TBK"));
		assertEquals("Neagley (2026)", LibraryStats.cleanName("Neagley (2026)"));
		assertEquals("Il Trono di Spade", LibraryStats.cleanName("Il.Trono.di.Spade.Stagione.1.ITA"));
		assertEquals("Avatar (2009)", LibraryStats.movieName("Avatar.2009.1080p.BluRay.mkv"));
		assertEquals("2001 A Space Odyssey (1968)", LibraryStats.movieName("2001.A.Space.Odyssey.1968.mkv"));
		assertEquals("The Office US", LibraryStats.seriesNameFromFile("The.Office.US.S01E01.720p.mkv"));
		assertEquals(LibraryStats.key("Neagley"), LibraryStats.key("Neagley (2026)"));

		assertEquals("4K", LibraryStats.getResolution("Dune.2021.2160p.mkv"));
		assertEquals("1080p", LibraryStats.getResolution("Dune.2021.1080p.mkv"));
		assertEquals("SD", LibraryStats.getResolution("Dune.1984.DVDRip.XviD.avi"));
		assertEquals("Unknown", LibraryStats.getResolution("Dune.mkv"));
	}

}
