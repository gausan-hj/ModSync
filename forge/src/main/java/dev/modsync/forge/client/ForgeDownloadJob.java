package dev.modsync.forge.client;

import dev.modsync.forge.ModSyncConfig;
import dev.modsync.forge.ModSyncForge;
import dev.modsync.forge.install.ModInstaller;
import dev.modsync.forge.net.SyncHttpClient;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.plan.FileDecision;
import dev.modsync.plan.SyncPlan;
import dev.modsync.util.Hashes;
import dev.modsync.util.SafeNames;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Sequential HTTP download into a staging folder, then verify and install. */
public final class ForgeDownloadJob {
	public enum Phase {
		DOWNLOADING,
		INSTALLING,
		DONE,
		FAILED,
		CANCELLED
	}

	private final SyncHttpClient.Remote remote;
	private final SyncPlan plan;
	private final ServerData server;
	private final Path modsDir;
	private final Path tempDir;

	private volatile Phase phase = Phase.DOWNLOADING;
	private volatile String currentFile = "";
	private volatile long currentSize;
	private volatile long currentReceived;
	private volatile long received;
	private volatile long total;
	private volatile int completedFiles;
	private volatile int expectedFiles;
	private volatile int installed;
	private volatile Path backupDir;
	private volatile String error = "";
	private volatile boolean cancelled;
	private volatile long startedAt = System.currentTimeMillis();
	private Thread thread;

	public ForgeDownloadJob(SyncHttpClient.Remote remote, SyncPlan plan, ServerData server) {
		this.remote = remote;
		this.plan = plan;
		this.server = server;
		this.modsDir = FMLPaths.GAMEDIR.get().resolve("mods");
		this.tempDir = modsDir.resolve(".modsync-tmp").resolve(UUID.randomUUID().toString());
		this.total = plan.requiredBytes();
		this.expectedFiles = plan.downloads().size();
	}

	public void start() {
		thread = new Thread(this::run, "ModSync-forge-download");
		thread.setDaemon(true);
		thread.start();
	}

	public void cancel() {
		cancelled = true;
		phase = Phase.CANCELLED;
		Thread current = thread;
		if (current != null) {
			current.interrupt();
		}
	}

	private void run() {
		try {
			Files.createDirectories(tempDir);
			List<ModInstaller.VerifiedFile> verified = new ArrayList<>();
			int index = 0;
			for (FileDecision decision : plan.downloads()) {
				if (cancelled) {
					return;
				}
				ManifestFile file = decision.file();
				currentFile = file.file();
				currentSize = file.size();
				currentReceived = 0;
				Path temp = tempDir.resolve(index + "-" + SafeNames.jarName(file.file(), index));
				String hash = SyncHttpClient.download(remote, file.file(), temp, bytes -> currentReceived = bytes);
				if (cancelled) {
					return;
				}
				long actualSize = Files.size(temp);
				if (actualSize != file.size()) {
					throw new IOException("size mismatch for " + file.file() + ": " + actualSize + " != " + file.size());
				}
				if (!file.sha256().isBlank() && !hash.equalsIgnoreCase(file.sha256())) {
					throw new IOException("SHA-256 mismatch for " + file.file());
				}
				verified.add(new ModInstaller.VerifiedFile(file, temp, hash));
				received += file.size();
				completedFiles++;
				index++;
			}

			if (cancelled) {
				return;
			}
			phase = Phase.INSTALLING;
			ModInstaller.InstallResult result = ModInstaller.install(
					modsDir,
					verified,
					ForgeLocalMods.collect(),
					ModSyncConfig.get().client.backup
			);
			installed = result.installed().size();
			backupDir = result.backupDir();
			phase = Phase.DONE;
			cleanup();
			ModSyncForge.LOGGER.info("ModSync installed {} files from {}", installed, server.ip);
		} catch (Exception e) {
			if (cancelled) {
				return;
			}
			phase = Phase.FAILED;
			error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			ModSyncForge.LOGGER.warn("ModSync download failed", e);
			cleanup();
		}
	}

	private void cleanup() {
		try {
			if (Files.isDirectory(tempDir)) {
				try (var stream = Files.walk(tempDir)) {
					stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
						try {
							Files.deleteIfExists(path);
						} catch (IOException ignored) {
						}
					});
				}
			}
			Path parent = tempDir.getParent();
			if (parent != null && Files.isDirectory(parent)) {
				try (var children = Files.list(parent)) {
					if (children.findAny().isEmpty()) {
						Files.deleteIfExists(parent);
					}
				}
			}
		} catch (IOException ignored) {
		}
	}

	public Phase phase() {
		return phase;
	}

	public String currentFile() {
		return currentFile;
	}

	public long currentSize() {
		return currentSize;
	}

	public long currentReceived() {
		return currentReceived;
	}

	public long received() {
		return received;
	}

	public long total() {
		return total;
	}

	public int completedFiles() {
		return completedFiles;
	}

	public int expectedFiles() {
		return expectedFiles;
	}

	public int installed() {
		return installed;
	}

	public Path backupDir() {
		return backupDir;
	}

	public String error() {
		return error;
	}

	public long elapsedMillis() {
		return Math.max(1, System.currentTimeMillis() - startedAt);
	}

	public ServerData server() {
		return server;
	}
}
