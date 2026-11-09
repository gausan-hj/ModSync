package dev.modsync.plan;

import dev.modsync.manifest.SyncManifest;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.manifest.SyncManifest.ManifestMod;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncPlannerTest {
	private static final Path MODS = Path.of("mods");

	private static ManifestFile file(String name, boolean required, String modId, String version) {
		return new ManifestFile(name, 100, required, List.of(new ManifestMod(modId, version, modId, "UNIVERSAL")));
	}

	private static LocalMod local(String id, String version) {
		return new LocalMod(id, version, id, "UNIVERSAL", MODS.resolve(id + ".jar"));
	}

	@Test
	void detectsMissingAndMismatched() {
		SyncManifest manifest = SyncManifest.create("server", "n", List.of(
				file("a.jar", true, "a", "1.0"),
				file("b.jar", true, "b", "2.0"),
				file("c.jar", true, "c", "3.0")
		));

		SyncPlan plan = SyncPlanner.plan(manifest, List.of(
				local("a", "1.0"),
				local("b", "1.9")
		));

		assertEquals(2, plan.requiredDownloads());
		assertEquals(FileDecision.Status.OK, plan.decisions().get(0).status());
		assertEquals(FileDecision.Status.VERSION_MISMATCH, plan.decisions().get(1).status());
		assertEquals(FileDecision.Status.MISSING, plan.decisions().get(2).status());
		assertTrue(plan.hasWork());
	}

	@Test
	void optionalFilesDoNotBlockJoin() {
		SyncManifest manifest = SyncManifest.create("server", "n", List.of(
				file("optional.jar", false, "optional", "1.0")
		));
		SyncPlan plan = SyncPlanner.plan(manifest, List.of());
		assertEquals(0, plan.requiredDownloads());
		assertFalse(plan.hasWork());
		assertEquals(FileDecision.Status.OPTIONAL_MISSING, plan.decisions().getFirst().status());
	}

	@Test
	void listsExtrasButIgnoresPlatformMods() {
		SyncManifest manifest = SyncManifest.create("server", "n", List.of(
				file("a.jar", true, "a", "1.0")
		));
		SyncPlan plan = SyncPlanner.plan(manifest, List.of(
				local("a", "1.0"),
				local("extra", "9.0"),
				local("fabric-api", "0.1"),
				local("minecraft", "26.3")
		));
		assertEquals(1, plan.extraLocalMods().size());
		assertEquals("extra", plan.extraLocalMods().getFirst().id());
	}

	@Test
	void mixedJarReportsMixed() {
		ManifestFile jar = new ManifestFile("both.jar", 100, true, List.of(
				new ManifestMod("a", "1.0", "A", "UNIVERSAL"),
				new ManifestMod("b", "1.0", "B", "UNIVERSAL")
		));
		SyncPlan plan = SyncPlanner.plan(SyncManifest.create("s", "n", List.of(jar)),
				List.of(local("a", "0.9")));
		assertEquals(FileDecision.Status.MIXED, plan.decisions().getFirst().status());
	}
}
