package dev.modsync.neoforge.client;

import dev.modsync.util.Hashes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForgeCacheTest {
	@Test
	void storesAndFindsBySha(@TempDir Path temp) throws Exception {
		ForgeCache.useDirectoryForTests(temp.resolve("cache"));
		Path source = temp.resolve("a.jar");
		Files.writeString(source, "mod bytes");
		String sha = Hashes.sha256(source);

		assertNull(ForgeCache.find(sha));
		ForgeCache.store(source, sha);
		Path found = ForgeCache.find(sha);
		assertNotNull(found);
		assertEquals(sha, Hashes.sha256(found));
	}

	@Test
	void rejectsCorruptCache(@TempDir Path temp) throws Exception {
		ForgeCache.useDirectoryForTests(temp.resolve("cache"));
		Path source = temp.resolve("a.jar");
		Files.writeString(source, "mod bytes");
		String sha = Hashes.sha256(source);
		ForgeCache.store(source, sha);
		Files.writeString(ForgeCache.pathFor(sha), "corrupt");

		assertNull(ForgeCache.find(sha));
	}
}
