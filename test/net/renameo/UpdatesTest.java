package net.renameo;

import static org.junit.Assert.*;

import org.junit.Test;

public class UpdatesTest {

	@Test
	public void compareVersions() {
		assertTrue(Updates.compare("1.0.1", "1.0.0") > 0);
		assertTrue(Updates.compare("1.10.0", "1.9.2") > 0);
		assertTrue(Updates.compare("v2.0", "1.99.99") > 0);
		assertTrue(Updates.compare("1.0.0", "1.0.1") < 0);
		assertEquals(0, Updates.compare("v1.0.0", "1.0.0"));
		assertEquals(0, Updates.compare("1.0", "1.0.0"));
		assertEquals(0, Updates.compare("1.0.0-beta", "1.0.0"));
	}

}
