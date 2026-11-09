package dev.modsync.plan;

import dev.modsync.manifest.SyncManifest;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.manifest.SyncManifest.ManifestMod;
import dev.modsync.util.Hashes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncPlannerHashTest {
	private static final ManifestMod MOD = new ManifestMod("example", "1.0.0", "Example", "UNIVERSAL");

	@Test
	void sameVersionButDifferentContentsIsReplaced(@TempDir Path dir) throws Exception {
		Path localJar = dir.resolve("example-1.0.0.jar");
		Files.writeString(localJar, "official build");
		LocalMod local = new LocalMod("example", "1.0.0", "Example", "UNIVERSAL", localJar);

		ManifestFile remote = new ManifestFile("example-1.0.0.jar",
				Files.size(localJar), true, "0000000000000000000000000000000000000000000000000000000000000000",
				List.of(MOD));
		SyncPlan plan = SyncPlanner.plan(
				SyncManifest.create("test", "n", List.of(remote)), List.of(local));

		assertTrue(plan.decisions().getFirst().needsDownload());
	}

	@Test
	void identicalContentsIsNotDownloaded(@TempDir Path dir) throws Exception {
		Path localJar = dir.resolve("example-1.0.0.jar");
		Files.writeString(localJar, "same build");
		LocalMod local = new LocalMod("example", "1.0.0", "Example", "UNIVERSAL", localJar);

		ManifestFile remote = new ManifestFile("example-1.0.0.jar",
				Files.size(localJar), true, Hashes.sha256(localJar), List.of(MOD));
		SyncPlan plan = SyncPlanner.plan(
				SyncManifest.create("test", "n", List.of(remote)), List.of(local));

		assertFalse(plan.decisions().getFirst().needsDownload());
	}
}
