package dev.modsync.plan;

import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.manifest.SyncManifest.ManifestMod;

import java.util.List;

/** One manifest jar and what the client would have to do about it. */
public record FileDecision(ManifestFile file, Status status, List<ManifestMod> missing, List<ManifestMod> mismatched) {
	public FileDecision {
		missing = missing == null ? List.of() : List.copyOf(missing);
		mismatched = mismatched == null ? List.of() : List.copyOf(mismatched);
	}

	public boolean needsDownload() {
		return status != Status.OK && status != Status.OPTIONAL_MISSING;
	}

	public enum Status {
		OK,
		MISSING,
		VERSION_MISMATCH,
		MIXED,
		OPTIONAL_MISSING;

		public boolean needsDownload() {
			return this == MISSING || this == VERSION_MISMATCH || this == MIXED;
		}
	}
}
