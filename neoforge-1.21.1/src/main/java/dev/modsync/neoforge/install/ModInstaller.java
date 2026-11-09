package dev.modsync.neoforge.install;

import dev.modsync.neoforge.ModSyncNeoForge;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.manifest.SyncManifest.ManifestMod;
import dev.modsync.plan.LocalMod;
import dev.modsync.util.SafeNames;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Verifies staged jars and moves them into the mods folder, backing up anything they replace. */
public final class ModInstaller {
	private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);

	private ModInstaller() {
	}

	public static InstallResult install(Path modsDir, List<VerifiedFile> files, List<LocalMod> localMods, boolean backup) throws IOException {
		Files.createDirectories(modsDir);
		Path normalizedMods = modsDir.toAbsolutePath().normalize();
		Map<String, Path> localById = new HashMap<>();
		for (LocalMod mod : localMods) {
			if (mod.path() != null) {
				localById.putIfAbsent(mod.id(), mod.path().toAbsolutePath().normalize());
			}
		}

		Path backupDir = null;
		Set<Path> backedUp = new HashSet<>();
		List<String> installed = new ArrayList<>();
		Set<String> usedNames = new HashSet<>();

		for (VerifiedFile verified : files) {
			ManifestFile manifestFile = verified.manifest();
			String name = uniqueName(SafeNames.jarName(manifestFile.file(), installed.size()), usedNames);
			Path target = normalizedMods.resolve(name).normalize();
			if (!target.getParent().equals(normalizedMods)) {
				throw new IOException("refusing target outside mods folder: " + target);
			}

			if (backup && Files.exists(target)) {
				backupDir = ensureBackupDir(modsDir, backupDir);
				backup(target, backupDir, backedUp);
			}

			for (ManifestMod mod : manifestFile.mods()) {
				Path local = localById.get(mod.id());
				if (local != null && !local.equals(target) && Files.exists(local) && backup) {
					backupDir = ensureBackupDir(modsDir, backupDir);
					backup(local, backupDir, backedUp);
				}
			}

			Files.move(verified.temp(), target, StandardCopyOption.REPLACE_EXISTING);
			installed.add(name);
			ModSyncNeoForge.LOGGER.info("Installed {}", target);
		}

		return new InstallResult(installed, backupDir);
	}

	private static String uniqueName(String name, Set<String> used) {
		String candidate = name;
		int counter = 2;
		while (!used.add(candidate.toLowerCase(Locale.ROOT))) {
			int dot = name.toLowerCase(Locale.ROOT).endsWith(".jar") ? name.length() - 4 : name.length();
			candidate = name.substring(0, dot) + "-" + counter + ".jar";
			counter++;
		}
		return candidate;
	}

	private static Path ensureBackupDir(Path modsDir, Path existing) throws IOException {
		if (existing != null) {
			return existing;
		}
		Path dir = modsDir.resolve(".modsync-backup").resolve(BACKUP_FORMAT.format(LocalDateTime.now()));
		Files.createDirectories(dir);
		return dir;
	}

	private static void backup(Path source, Path backupDir, Set<Path> backedUp) throws IOException {
		Path normalized = source.toAbsolutePath().normalize();
		if (!backedUp.add(normalized)) {
			return;
		}
		Path target = backupDir.resolve(source.getFileName().toString());
		int counter = 2;
		while (Files.exists(target)) {
			target = backupDir.resolve(source.getFileName() + "." + counter);
			counter++;
		}
		Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
	}

	public record VerifiedFile(ManifestFile manifest, Path temp, String sha256) {
	}

	public record InstallResult(List<String> installed, Path backupDir) {
		public InstallResult {
			installed = List.copyOf(installed);
		}
	}
}
