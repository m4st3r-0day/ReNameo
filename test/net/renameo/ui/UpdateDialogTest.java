package net.renameo.ui;

import static org.junit.Assert.*;

import org.junit.Test;

public class UpdateDialogTest {

	@Test
	public void releaseNotesAsPlainText() {
		String notes = "## Install\n\n1. Open **System Settings** and run:\n   ```\n   xattr -dr x\n   ```\n2. Enter the *API Key* from [TheMovieDB](https://x).\n- `brew install y`\n";
		assertEquals("Install\n\n1. Open System Settings and run:\n   xattr -dr x\n2. Enter the API Key from TheMovieDB.\n• brew install y", UpdateDialog.plainText(notes));
	}

}
