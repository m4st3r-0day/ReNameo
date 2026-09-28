package net.renameo.web;

import java.nio.ByteBuffer;
import java.util.Map;

/**
 * Subtitle found through the REST API. It keeps the legacy descriptor properties (used by subtitle matching) but downloads through the API, which resolves the file id to a temporary link.
 */
public class OpenSubtitlesRestDescriptor extends OpenSubtitlesSubtitleDescriptor {

	private final transient OpenSubtitlesRestClient client;
	private final int fileId;

	public OpenSubtitlesRestDescriptor(Map<Property, String> properties, OpenSubtitlesRestClient client, int fileId) {
		super(properties);
		this.client = client;
		this.fileId = fileId;
	}

	@Override
	public long getLength() {
		try {
			return super.getLength();
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	@Override
	public ByteBuffer fetch() throws Exception {
		return client.download(fileId);
	}

}
