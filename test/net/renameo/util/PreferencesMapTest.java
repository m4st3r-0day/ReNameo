
package net.renameo.util;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.prefs.Preferences;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import net.renameo.util.PreferencesMap.JsonAdapter;
import net.renameo.util.PreferencesMap.SimpleAdapter;

public class PreferencesMapTest {

	private static Preferences root;
	private static Preferences strings;
	private static Preferences numbers;
	private static Preferences temp;
	private static Preferences sequence;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		root = Preferences.userRoot().node("junit-test");

		strings = root.node("strings");
		strings.put("1", "Firefly");
		strings.put("2", "Roswell");
		strings.put("3", "Angel");
		strings.put("4", "Dead like me");
		strings.put("5", "Babylon");

		numbers = root.node("numbers");
		numbers.putInt("M", 4);
		numbers.putInt("A", 5);
		numbers.putInt("X", 2);

		sequence = root.node("sequence");
		sequence.putInt("1", 1);
		sequence.putInt("2", 2);
		sequence.putInt("3", 3);

		temp = root.node("temp");
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		root.removeNode();
	}

	@Test
	public void get() {
		Map<String, String> stringMap = PreferencesMap.map(strings);

		assertEquals("Firefly", stringMap.get("1"));
	}

	@Test
	public void put() {
		Map<String, String> stringMap = PreferencesMap.map(temp);

		stringMap.put("key", "snake");

		assertEquals("snake", temp.get("key", null));
	}

	@Test
	public void remove() throws Exception {
		Map<String, Integer> map = PreferencesMap.map(numbers, SimpleAdapter.forClass(Integer.class));

		map.remove("A");

		assertFalse(Arrays.asList(numbers.keys()).contains("A"));
	}

	@Test
	public void clear() throws Exception {
		Map<String, Integer> map = PreferencesMap.map(temp, SimpleAdapter.forClass(Integer.class));

		map.put("X", 42);

		map.clear();

		assertTrue(temp.keys().length == 0);
	}

	@Test
	public void containsKey() {
		temp.put("name", "kaya");

		Map<String, String> map = PreferencesMap.map(temp);

		assertTrue(map.containsKey("name"));
	}

	@Test
	public void values() {

		Map<String, Integer> map = PreferencesMap.map(sequence, SimpleAdapter.forClass(Integer.class));

		Collection<Integer> list = map.values();

		assertTrue(list.contains(1));
		assertTrue(list.contains(2));
		assertTrue(list.contains(3));
	}

	@Test
	public void containsValue() {
		Map<String, String> map = PreferencesMap.map(strings);

		assertTrue(map.containsValue("Firefly"));
	}

	@Test
	public void entrySet() {
		Map<String, Integer> map = PreferencesMap.map(numbers, SimpleAdapter.forClass(Integer.class));

		for (Entry<String, Integer> entry : map.entrySet()) {
			Integer v = entry.getValue();
			entry.setValue(v + 1);
		}

		assertEquals(5, numbers.getInt("M", -1));
	}

	@Test
	public void containsKeyWithObjectKey() throws Exception {
		Map<String, String> map = PreferencesMap.map(strings);

		assertFalse(map.containsKey(new Object()));
	}

	public void getWithObjectKey() throws Exception {
		Map<String, String> map = PreferencesMap.map(strings);

		assertEquals(null, map.get(new Object()));
	}

	@Test
	public void jsonAdapter() {
		// the adapter stores the application's own settings objects (e.g. rename presets); JDK classes can't be read reflectively on Java 17+
		Map<String, Setting> map = PreferencesMap.map(temp, new JsonAdapter<Setting>(Setting.class));
		Setting entry = new Setting("Plex", "{plex.name}", 3);

		map.put("preset", entry);
		assertEquals(entry, map.get("preset"));

		// stored without Java class names
		assertFalse(temp.get("preset", "").contains("@type"));

		// values written by earlier versions name an old class, e.g. from before a package rename
		temp.put("legacy", "{\"@type\":\"net.filebot.ui.rename.Preset\",\"name\":\"Plex\",\"format\":\"{plex.name}\",\"priority\":3}");
		assertEquals(entry, map.get("legacy"));
	}

	public static class Setting {

		public String name;
		public String format;
		public int priority;

		public Setting() {
		}

		public Setting(String name, String format, int priority) {
			this.name = name;
			this.format = format;
			this.priority = priority;
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Setting && ((Setting) other).name.equals(name) && ((Setting) other).format.equals(format) && ((Setting) other).priority == priority;
		}

		@Override
		public int hashCode() {
			return name.hashCode();
		}
	}

}
