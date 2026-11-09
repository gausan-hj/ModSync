package dev.modsync.plan;

import dev.modsync.manifest.SyncManifest.ManifestFile;

import java.util.List;

/** Result of comparing a server manifest with the locally loaded mods. */
public record SyncPlan(List<FileDecision> decisions, List<LocalMod> extraLocalMods, int requiredDownloads, long requiredBytes) {
	public SyncPlan {
		decisions = decisions == null ? List.of() : List.copyOf(decisions);
		extraLocalMods = extraLocalMods == null ? List.of() : List.copyOf(extraLocalMods);
	}

	public boolean hasWork() {
		return requiredDownloads > 0;
	}

	public List<FileDecision> downloads() {
		return decisions.stream().filter(FileDecision::needsDownload).toList();
	}

	public List<FileDecision> ready() {
		return decisions.stream().filter(d -> d.status() == FileDecision.Status.OK).toList();
	}

	public List<ManifestFile> filesToDownload() {
		return downloads().stream().map(FileDecision::file).toList();
	}
}
