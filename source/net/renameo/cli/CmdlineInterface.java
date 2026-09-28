package net.renameo.cli;

import java.io.File;
import java.io.FileFilter;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import net.renameo.Language;
import net.renameo.RenameAction;
import net.renameo.format.ExpressionFileFormat;
import net.renameo.format.ExpressionFilter;
import net.renameo.format.ExpressionFormat;
import net.renameo.hash.HashType;
import net.renameo.subtitle.SubtitleFormat;
import net.renameo.subtitle.SubtitleNaming;
import net.renameo.web.Datasource;
import net.renameo.web.EpisodeListProvider;
import net.renameo.web.SortOrder;

public interface CmdlineInterface {

	List<File> rename(Collection<File> files, RenameAction action, ConflictAction conflict, File output, ExpressionFileFormat format, Datasource db, String query, SortOrder order, ExpressionFilter filter, Locale locale, boolean strict, ExecCommand exec) throws Exception;

	List<File> rename(EpisodeListProvider db, String query, ExpressionFileFormat format, ExpressionFilter filter, SortOrder order, Locale locale, boolean strict, List<File> files, RenameAction action, ConflictAction conflict, File output, ExecCommand exec) throws Exception;

	List<File> rename(Map<File, File> rename, RenameAction action, ConflictAction conflict) throws Exception;

	List<File> revert(Collection<File> files, FileFilter filter, RenameAction action) throws Exception;

	List<File> getSubtitles(Collection<File> files, String query, Language language, SubtitleFormat output, Charset encoding, SubtitleNaming format, boolean strict) throws Exception;

	List<File> getMissingSubtitles(Collection<File> files, String query, Language language, SubtitleFormat output, Charset encoding, SubtitleNaming format, boolean strict) throws Exception;

	boolean check(Collection<File> files) throws Exception;

	File compute(Collection<File> files, File output, HashType hash, Charset encoding) throws Exception;

	Stream<String> fetchEpisodeList(EpisodeListProvider db, String query, ExpressionFormat format, ExpressionFilter filter, SortOrder order, Locale locale, boolean strict) throws Exception;

	Stream<String> getMediaInfo(Collection<File> files, FileFilter filter, ExpressionFormat format) throws Exception;

	boolean execute(Collection<File> files, FileFilter filter, ExecCommand exec) throws Exception;

	List<File> extract(Collection<File> files, File output, ConflictAction conflict, FileFilter filter, boolean forceExtractAll) throws Exception;

	List<File> apply(Collection<File> files, String postProcessingTask, File outputDir) throws Exception;

}
