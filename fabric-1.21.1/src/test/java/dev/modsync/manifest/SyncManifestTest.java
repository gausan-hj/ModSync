package dev.modsync.manifest;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncManifestTest {
	@Test
	void jsonRoundTrip() throws Exception {
		SyncManifest manifest = SyncManifest.create("My Server", "nonce", List.of(
				new SyncManifest.ManifestFile("a.jar", 1234, true,
						List.of(new SyncManifest.ManifestMod("a", "1.0", "A Mod", "UNIVERSAL")))
		));
		SyncManifest decoded = SyncManifest.fromJsonBytes(manifest.toJsonBytes());
		assertEquals("My Server", decoded.serverName());
		assertEquals(1, decoded.files().size());
		assertEquals(1234, decoded.totalBytes());
		assertNotNull(decoded.find("a.jar"));
		assertTrue(decoded.compatible());
	}

	@Test
	void toleratesMissingOptionalLists() throws Exception {
		SyncManifest decoded = SyncManifest.fromJsonBytes(
				"{\"protocol\":1,\"serverName\":\"s\",\"nonce\":\"n\",\"files\":[{\"file\":\"a.jar\",\"size\":1}]}"
						.getBytes());
		assertEquals(1, decoded.files().size());
		assertEquals(List.of(), decoded.files().getFirst().mods());
	}
}
