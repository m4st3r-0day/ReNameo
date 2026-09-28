package net.renameo.archive;

import java.io.File;
import java.io.FileFilter;
import java.util.List;

import net.renameo.vfs.FileInfo;

public interface ArchiveExtractor {

	public List<FileInfo> listFiles() throws Exception;

	public void extract(File outputDir) throws Exception;

	public void extract(File outputDir, FileFilter filter) throws Exception;

}
