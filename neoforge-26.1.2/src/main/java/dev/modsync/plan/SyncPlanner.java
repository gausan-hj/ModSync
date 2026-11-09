package dev.modsync.plan;

import dev.modsync.manifest.SyncManifest;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.manifest.SyncManifest.ManifestMod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pure comparison logic; no Minecraft classes, so it is covered by unit tests. */
public final class SyncPlanner {
	/** Loader/platform mods that must never be auto-replaced. */
	public static final Set<String> PLATFORM_MODS = Set.of(
			"minecraft",
			"java",
			"fabricloader",
			"fabric-api",
			"forge",
			"neoforge",
			"mcp",
			"modsync"
	);

	private SyncPlanner() {
	}

	public static SyncPlan plan(SyncManifest manifest, Collection<LocalMod> localMods) {
		Map<String, LocalMod> localById = new HashMap<>();
		for (LocalMod mod : localMods) {
			if (mod == null || mod.id() == null || mod.id().isBlank()) {
				continue;
			}
			localById.putIfAbsent(mod.id(), mod);
		}

		Set<String> remoteIds = new HashSet<>();
		for (ManifestFile file : manifest.files()) {
			for (ManifestMod mod : file.mods()) {
				remoteIds.add(mod.id());
			}
		}

		List<FileDecision> decisions = new ArrayList<>();
		int downloads = 0;
		long bytes = 0;

		for (ManifestFile file : manifest.files()) {
			List<ManifestMod> missing = new ArrayList<>();
			List<ManifestMod> mismatched = new ArrayList<>();

			for (ManifestMod remote : file.mods()) {
				LocalMod local = localById.get(remote.id());
				if (local == null) {
					missing.add(remote);
				} else if (!local.version().equals(remote.version())) {
					mismatched.add(remote);
				}
			}

			FileDecision.Status status;
			if (missing.isEmpty() && mismatched.isEmpty()) {
				status = FileDecision.Status.OK;
			} else if (!file.required()) {
				status = FileDecision.Status.OPTIONAL_MISSING;
			} else if (missing.isEmpty()) {
				status = FileDecision.Status.VERSION_MISMATCH;
			} else if (mismatched.isEmpty()) {
				status = FileDecision.Status.MISSING;
			} else {
				status = FileDecision.Status.MIXED;
			}

			FileDecision decision = new FileDecision(file, status, missing, mismatched);
			decisions.add(decision);
			if (decision.needsDownload()) {
				downloads++;
				bytes += Math.max(0, file.size());
			}
		}

		Map<String, LocalMod> extras = new LinkedHashMap<>();
		for (LocalMod local : localById.values()) {
			if (PLATFORM_MODS.contains(local.id()) || remoteIds.contains(local.id()) || !local.hasJar()) {
				continue;
			}
			if (!"UNIVERSAL".equals(local.env()) && !"*".equals(local.env())) {
				continue;
			}
			extras.put(local.id(), local);
		}

		List<LocalMod> extraList = extras.values().stream()
				.sorted((a, b) -> a.id().compareToIgnoreCase(b.id()))
				.toList();

		return new SyncPlan(decisions, extraList, downloads, bytes);
	}
}
