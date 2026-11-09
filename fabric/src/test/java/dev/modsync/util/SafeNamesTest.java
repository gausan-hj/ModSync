package dev.modsync.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeNamesTest {
	@Test
	void stripsDirectories() {
		assertEquals("evil.jar", SafeNames.jarName("../../evil.jar", 0));
		assertEquals("evil.jar", SafeNames.jarName("C:\\temp\\evil.jar", 0));
	}

	@Test
	void removesUnsafeCharacters() {
		String name = SafeNames.jarName("my mod <script>.jar", 0);
		assertEquals("my_mod__script_.jar", name);
		assertFalse(name.contains(" "));
		assertFalse(name.contains("<"));
	}

	@Test
	void keepsJarSuffix() {
		assertEquals("mod.jar", SafeNames.jarName("mod", 0));
		assertTrue(SafeNames.jarName("CON", 0).endsWith(".jar"));
		assertEquals("mod-CON.jar", SafeNames.jarName("CON", 0));
	}

	@Test
	void handlesBlank() {
		assertEquals("mod-7.jar", SafeNames.jarName("", 7));
		assertEquals("mod-8.jar", SafeNames.jarName("...", 8));
	}
}
