package net.renameo;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import net.renameo.format.ExpressionFormatTest;
import net.renameo.hash.VerificationFormatTest;
import net.renameo.media.MediaDetectionTest;
import net.renameo.media.ReleaseInfoTest;
import net.renameo.media.VideoFormatTest;
import net.renameo.mediainfo.MediaInfoTest;
import net.renameo.similarity.EpisodeMetricsTest;
import net.renameo.similarity.SimilarityTestSuite;
import net.renameo.subtitle.SubtitleReaderTestSuite;
import net.renameo.ui.rename.MatchModelTest;
import net.renameo.util.UtilTestSuite;
import net.renameo.web.WebTestSuite;

@RunWith(Suite.class)
@SuiteClasses({ ExpressionFormatTest.class, VerificationFormatTest.class, MatchModelTest.class, EpisodeMetricsTest.class, ReleaseInfoTest.class, VideoFormatTest.class, MediaDetectionTest.class, MediaInfoTest.class, SimilarityTestSuite.class, WebTestSuite.class, SubtitleReaderTestSuite.class, UtilTestSuite.class })
public class AllTests {

}
