
package net.renameo.web;


import java.nio.ByteBuffer;

import net.renameo.vfs.FileInfo;


public interface SubtitleDescriptor extends FileInfo {

	@Override
	String getName();


	String getLanguageName();


	@Override
	String getType();


	ByteBuffer fetch() throws Exception;

}
