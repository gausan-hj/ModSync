package dev.modsync.neoforge.client;

import dev.modsync.neoforge.ModSyncConfig;
import dev.modsync.neoforge.ModSyncNeoForge;
import dev.modsync.neoforge.install.ModInstaller;
import dev.modsync.neoforge.net.SyncHttpClient;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.plan.FileDecision;
import dev.modsync.plan.SyncPlan;
import dev.modsync.util.Hashes;
import dev.modsync.util.SafeNames;
import net.minecraft.client.multiplayer.ServerData;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
				Path temp = file.sha256().isBlank()
						? ForgeCache.partialFor("unhashed-" + index)
						: ForgeCache.partialFor(file.sha256());
				Files.createDirectories(temp.getParent());
				Path cached = ForgeCache.find(file.sha256());
				String hash;
				if (cached != null) {
					Files.copy(cached, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					currentReceived = Files.size(temp);
					hash = file.sha256();
					ModSyncNeoForge.LOGGER.info("Reused cached {}", file.file());
				} else {
					hash = SyncHttpClient.download(remote, file.file(), temp, bytes -> currentReceived = bytes);
				}
				if (cancelled) {
					return;
				}
				long actualSize = Files.size(temp);
				if (actualSize != file.size()) {
					Files.deleteIfExists(temp);
					throw new IOException("size mismatch for " + file.file() + ": " + actualSize + " != " + file.size());
				}
				if (!file.sha256().isBlank() && !hash.equalsIgnoreCase(file.sha256())) {
					Files.deleteIfExists(temp);
					throw new IOException("SHA-256 mismatch for " + file.file());
				}
				ForgeCache.store(temp, file.sha256());
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
			for (ModInstaller.VerifiedFile file : verified) {
				Files.deleteIfExists(file.temp());
			}
			ModSyncNeoForge.LOGGER.info("ModSync installed {} files from {}", installed, server.ip);
		} catch (Exception e) {
			if (cancelled) {
				return;
			}
			phase = Phase.FAILED;
			error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
			ModSyncNeoForge.LOGGER.warn("ModSync download failed", e);
			cleanup();
		}
	}

	private void cleanup() {
		// Partial files are intentionally kept in .modsync-cache/.partial for Range resume.
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
