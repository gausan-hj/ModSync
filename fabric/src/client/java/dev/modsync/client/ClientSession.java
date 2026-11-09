package dev.modsync.client;

import dev.modsync.ModSync;
import dev.modsync.ModSyncConfig;
import dev.modsync.manifest.SyncManifest;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.net.SyncPayload;
import dev.modsync.plan.SyncPlan;
import dev.modsync.protocol.Message;
import dev.modsync.protocol.Protocol;
import dev.modsync.util.Hashes;
import dev.modsync.util.SafeNames;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client state for one join attempt. The connection stays in the configuration phase while
 * the review screen is open, so the server's registry data never reaches a mismatched client.
 */
public final class ClientSession {
	public enum Phase {
		REVIEW,
		DOWNLOADING,
		VERIFYING,
		INSTALLING,
		DONE,
		FAILED,
		CANCELLED;

		public boolean terminal() {
			return this == DONE || this == FAILED || this == CANCELLED;
		}
	}

	private final Minecraft client;
	private final Connection connection;
	private final SyncManifest manifest;
	private final SyncPlan plan;
	private final Path modsDir;
	private final Path tempDir;
	private final String serverName;
	private final String serverKey;
	private final Map<Integer, IncomingFile> incoming = new ConcurrentHashMap<>();
	private final List<ModInstaller.VerifiedFile> cachedFiles = new ArrayList<>();

	private volatile Phase phase = Phase.REVIEW;
	private volatile int expectedFiles;
	private volatile long totalBytes;
	private volatile long receivedBytes;
	private volatile int completedFiles;
	private volatile String currentFile = "";
	private volatile long currentFileSize;
	private volatile long currentFileReceived;
	private volatile long lastPing = System.currentTimeMillis();
	private volatile long startedAt = System.currentTimeMillis();
	private volatile String error = "";
	private volatile int installedCount;
	private volatile Path backupDir;

	public ClientSession(Minecraft client, Connection connection, SyncManifest manifest, SyncPlan plan) {
		this.client = client;
		this.connection = connection;
		this.manifest = manifest;
		this.plan = plan;
		this.serverName = manifest.serverName();
		this.serverKey = SyncedMods.currentServerKey(client, connection);
		this.modsDir = FabricLoader.getInstance().getGameDir().resolve("mods");
		this.tempDir = modsDir.resolve(".modsync-tmp").resolve(UUID.randomUUID().toString());
		this.expectedFiles = plan.filesToDownload().size();
		this.totalBytes = plan.requiredBytes();
	}

	public SyncManifest manifest() {
		return manifest;
	}

	public SyncPlan plan() {
		return plan;
	}

	public String serverName() {
		return serverName;
	}

	public Phase phase() {
		return phase;
	}

	public int expectedFiles() {
		return expectedFiles;
	}

	public int completedFiles() {
		return completedFiles;
	}

	public long totalBytes() {
		return totalBytes;
	}

	public long receivedBytes() {
		return receivedBytes;
	}

	public String currentFile() {
		return currentFile;
	}

	public long currentFileSize() {
		return currentFileSize;
	}

	public long currentFileReceived() {
		return currentFileReceived;
	}

	public long elapsedMillis() {
		return Math.max(1, System.currentTimeMillis() - startedAt);
	}

	public String error() {
		return error;
	}

	public int installedCount() {
		return installedCount;
	}

	public Path backupDir() {
		return backupDir;
	}

	public synchronized void sendNeeded() {
		List<String> files = plan.filesToDownload().stream().map(ManifestFile::file).toList();
		send(new Message.Needed(manifest.nonce(), files));
	}

	public synchronized boolean requestDownload() {
		if (phase != Phase.REVIEW) {
			return false;
		}
		List<String> files = new ArrayList<>();
		cachedFiles.clear();
		try {
			Files.createDirectories(tempDir);
			for (ManifestFile file : plan.filesToDownload()) {
				Path cached = FabricCache.find(file.sha256());
				if (cached == null) {
					files.add(file.file());
					continue;
				}
				Path copy = tempDir.resolve("cached-" + SafeNames.jarName(file.file(), cachedFiles.size()));
				Files.copy(cached, copy, StandardCopyOption.REPLACE_EXISTING);
				cachedFiles.add(new ModInstaller.VerifiedFile(file, copy, file.sha256()));
			}
			if (files.isEmpty()) {
				// The server rejects an empty request; fall back to a normal transfer.
				for (ManifestFile file : plan.filesToDownload()) {
					files.add(file.file());
				}
				cachedFiles.clear();
			}
		} catch (IOException e) {
			fail("Could not prepare the download folder: " + e.getMessage());
			return false;
		}
		if (files.isEmpty()) {
			return false;
		}
		expectedFiles = files.size();
		totalBytes = files.stream().mapToLong(name -> {
			ManifestFile file = manifest.find(name);
			return file == null ? 0 : file.size();
		}).sum();
		receivedBytes = cachedFiles.stream().mapToLong(file -> file.manifest().size()).sum();
		completedFiles = cachedFiles.size();
		startedAt = System.currentTimeMillis();
		phase = Phase.DOWNLOADING;
		if (!send(new Message.Request(manifest.nonce(), files))) {
			fail("Could not send the download request.");
			return false;
		}
		return true;
	}

	public synchronized void handle(Message message) {
		if (phase.terminal()) {
			return;
		}
		switch (message) {
			case Message.FileStart start -> onFileStart(start);
			case Message.FileChunk chunk -> onFileChunk(chunk);
			case Message.FileEnd end -> onFileEnd(end);
			case Message.Complete complete -> onComplete(complete);
			case Message.Error errorMessage -> fail(errorMessage.message());
			case Message.Cancel cancel -> fail(cancel.reason());
			default -> {
			}
		}
	}

	public synchronized void tick() {
		if (phase == Phase.REVIEW || phase == Phase.DOWNLOADING) {
			if (!connection.isConnected()) {
				onDisconnected();
				return;
			}
			long now = System.currentTimeMillis();
			if (now - lastPing > 10_000L) {
				lastPing = now;
				send(new Message.Ping(manifest.nonce()));
			}
		}
	}

	public synchronized void onDisconnected() {
		if (phase == Phase.REVIEW || phase == Phase.DOWNLOADING) {
			fail("The server closed the connection before the sync finished.");
		}
	}

	public synchronized void cancel() {
		if (phase.terminal()) {
			return;
		}
		phase = Phase.CANCELLED;
		send(new Message.Cancel("cancelled by client"));
		closeAll();
		cleanup();
		disconnect(Component.translatable("modsync.cancelled"));
		ClientSyncController.clear(this);
	}

	public synchronized void fail(String message) {
		if (phase == Phase.DONE || phase == Phase.FAILED || phase == Phase.CANCELLED) {
			return;
		}
		phase = Phase.FAILED;
		error = message == null ? "unknown error" : message;
		ModSync.LOGGER.warn("ModSync failed: {}", error);
		closeAll();
		cleanup();
		client.execute(() -> {
			disconnect(Component.translatable("modsync.failed"));
			client.setScreenAndShow(new SyncResultScreen(false, installedCount, backupDir, error, serverName));
			ClientSyncController.clear(this);
		});
	}

	private void onFileStart(Message.FileStart start) {
		if (phase != Phase.DOWNLOADING) {
			return;
		}
		if (start.size() <= 0 || start.size() > ModSyncConfig.get().client.maxTotalBytes()) {
			fail("The server declared an invalid file size.");
			return;
		}
		String safeName = SafeNames.jarName(start.file(), start.index());
		Path path = tempDir.resolve(start.index() + "-" + safeName);
		try {
			Files.createDirectories(tempDir);
			FileChannel channel = FileChannel.open(path,
					StandardOpenOption.CREATE,
					StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING);
			IncomingFile file = new IncomingFile(start.index(), start.file(), start.size(), path, channel);
			incoming.put(start.index(), file);
			currentFile = start.file();
			currentFileSize = start.size();
			currentFileReceived = 0;
		} catch (IOException e) {
			fail("Could not create " + safeName + ": " + e.getMessage());
		}
	}

	private void onFileChunk(Message.FileChunk chunk) {
		if (phase != Phase.DOWNLOADING) {
			return;
		}
		IncomingFile file = incoming.get(chunk.index());
		if (file == null) {
			fail("Received data for an unknown file.");
			return;
		}
		if (chunk.offset() != file.written) {
			fail("Out-of-order file data.");
			return;
		}
		if (file.written + chunk.data().length > file.expectedSize) {
			fail("The server sent more data than declared.");
			return;
		}
		try {
			ByteBuffer buffer = ByteBuffer.wrap(chunk.data());
			long position = chunk.offset();
			while (buffer.hasRemaining()) {
				position += file.channel.write(buffer, position);
			}
			file.written += chunk.data().length;
			receivedBytes += chunk.data().length;
			currentFileReceived = file.written;
			long max = ModSyncConfig.get().client.maxTotalBytes();
			if (receivedBytes > max) {
				fail("Download exceeded the client's size limit.");
			}
		} catch (IOException e) {
			fail("Could not write " + file.name + ": " + e.getMessage());
		}
	}

	private void onFileEnd(Message.FileEnd end) {
		if (phase != Phase.DOWNLOADING) {
			return;
		}
		IncomingFile file = incoming.get(end.index());
		if (file == null) {
			fail("Received an end marker for an unknown file.");
			return;
		}
		file.close();
		if (!end.ok()) {
			fail(end.error().isBlank() ? "The server could not read " + file.name : end.error());
			return;
		}
		if (file.written != file.expectedSize) {
			fail("Incomplete file: " + file.name);
			return;
		}
		file.expectedSha256 = end.sha256();
		completedFiles = Math.max(completedFiles, end.index() + 1);
	}

	private void onComplete(Message.Complete complete) {
		if (phase != Phase.DOWNLOADING) {
			return;
		}
		closeAll();
		if (incoming.size() != expectedFiles) {
			fail("The server finished before all files arrived.");
			return;
		}
		for (IncomingFile file : incoming.values()) {
			if (file.written != file.expectedSize) {
				fail("Incomplete file: " + file.name);
				return;
			}
		}
		phase = Phase.VERIFYING;
		ModSyncClient.IO.execute(this::verifyAndInstall);
	}

	private void verifyAndInstall() {
		try {
			List<ModInstaller.VerifiedFile> verified = new ArrayList<>();
			for (IncomingFile file : incoming.values()) {
				String actual = Hashes.sha256(file.path);
				if (!actual.equalsIgnoreCase(file.expectedSha256)) {
					fail("Hash mismatch for " + file.name);
					return;
				}
				ManifestFile manifestFile = manifest.find(file.originalName);
				if (manifestFile == null) {
					fail("The server sent an unexpected file: " + file.name);
					return;
				}
				verified.add(new ModInstaller.VerifiedFile(manifestFile, file.path, actual));
				FabricCache.store(file.path, actual);
			}
			verified.addAll(cachedFiles);

			phase = Phase.INSTALLING;
			ModInstaller.InstallResult result = ModInstaller.install(
					modsDir.getParent(),
					verified,
					LocalMods.collect(),
					ModSyncConfig.get().client.backup
			);
			installedCount = result.installed().size();
			backupDir = result.backupDir();
			SyncedMods.record(serverKey, serverName, result.installed());
			phase = Phase.DONE;
			cleanup();
			if (!send(new Message.Installed(manifest.nonce(), installedCount, totalBytes))) {
				ModSync.LOGGER.warn("Could not send ModSync install acknowledgement");
			}
			// Give the acknowledgement time to leave the socket before the server
			// closes the configuration connection.
			ModSyncClient.IO.execute(() -> {
				try {
					Thread.sleep(1500L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				client.execute(this::showSuccess);
			});
		} catch (Exception e) {
			ModSync.LOGGER.warn("Could not verify/install ModSync files", e);
			fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
	}

	private void showSuccess() {
		disconnect(Component.translatable("modsync.result.restart"));
		client.setScreenAndShow(new SyncResultScreen(true, installedCount, backupDir, "", serverName));
		ClientSyncController.clear(this);
	}

	private synchronized boolean send(Message message) {
		try {
			ClientConfigurationNetworking.send(new SyncPayload(Protocol.encode(message)));
			return true;
		} catch (Exception e) {
			ModSync.LOGGER.warn("Could not send ModSync frame {}", message.type(), e);
			return false;
		}
	}

	private void closeAll() {
		for (IncomingFile file : incoming.values()) {
			file.close();
		}
	}

	private void cleanup() {
		for (IncomingFile file : incoming.values()) {
			file.close();
		}
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
		} catch (IOException ignored) {
		}
		try {
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

	private void disconnect(Component reason) {
		try {
			if (connection.isConnected()) {
				connection.disconnect(reason);
			}
		} catch (RuntimeException e) {
			ModSync.LOGGER.debug("Could not close the ModSync connection", e);
		}
	}

	private static final class IncomingFile {
		private final int index;
		private final String originalName;
		private final String name;
		private final long expectedSize;
		private final Path path;
		private final FileChannel channel;
		private long written;
		private String expectedSha256 = "";

		private IncomingFile(int index, String originalName, long expectedSize, Path path, FileChannel channel) {
			this.index = index;
			this.originalName = originalName;
			this.name = SafeNames.displayName(originalName);
			this.expectedSize = expectedSize;
			this.path = path;
			this.channel = channel;
		}

		private void close() {
			try {
				if (channel.isOpen()) {
					channel.close();
				}
			} catch (IOException ignored) {
			}
		}
	}
}
