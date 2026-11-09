package dev.modsync.server;

import dev.modsync.ModSync;
import dev.modsync.ModSyncConfig;
import dev.modsync.manifest.SyncManifest;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.manifest.SyncManifest.ManifestMod;
import dev.modsync.mixin.ServerConfigurationPacketListenerAccessor;
import dev.modsync.net.SyncPayload;
import dev.modsync.protocol.Message;
import dev.modsync.protocol.Protocol;
import dev.modsync.protocol.Protocol.ProtocolException;
import dev.modsync.util.Hashes;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModEnvironment;
import net.fabricmc.loader.api.metadata.ModOrigin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Server side of ModSync. It runs entirely in the configuration phase, before vanilla sends
 * registry data, so a client with missing content mods can still receive the manifest and files.
 */
public final class SyncServer {
	private static final Set<String> PLATFORM = Set.of("minecraft", "java", "fabricloader", "fabric-api", "modsync");
	private static final Map<ServerConfigurationPacketListenerImpl, SyncTask> TASKS = new ConcurrentHashMap<>();
	private static final ScheduledExecutorService TIMEOUT = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "ModSync-timeout");
		thread.setDaemon(true);
		return thread;
	});

	private static volatile MinecraftServer server;
	private static volatile SyncManifest manifest;
	private static volatile Map<String, Path> fileMap = Map.of();
	private static volatile String nonce = "";

	private SyncServer() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundConfiguration().register(SyncPayload.TYPE, SyncPayload.CODEC);
		PayloadTypeRegistry.serverboundConfiguration().register(SyncPayload.TYPE, SyncPayload.CODEC);

		ServerConfigurationNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> {
			SyncTask task = TASKS.get(context.packetListener());
			if (task == null) {
				return;
			}
			try {
				task.handle(Protocol.decode(payload.data()));
			} catch (ProtocolException e) {
				ModSync.LOGGER.warn("Rejected malformed ModSync frame from {}", context.packetListener(), e);
				task.disconnect(Component.translatable("modsync.error.protocol"));
			}
		});

		ServerConfigurationConnectionEvents.BEFORE_CONFIGURE.register((listener, minecraftServer) -> {
			if (!ModSyncConfig.get().server.enabled) {
				return;
			}
			SyncManifest current = manifest;
			if (current == null) {
				current = refreshManifest(minecraftServer);
			}
			if (current.files().isEmpty()) {
				return;
			}
			SyncTask existing = TASKS.get(listener);
			if (existing != null) {
				return;
			}
			SyncTask task = new SyncTask(listener, current, Map.copyOf(fileMap));
			TASKS.put(listener, task);
			// Fabric API's registry sync also queues a task on BEFORE_CONFIGURE. It is
			// registered before mods, so a normal addTask would put registry sync first
			// and the client would be kicked before seeing our manifest. Prepend ours.
			Queue<ConfigurationTask> queue = ((ServerConfigurationPacketListenerAccessor) listener)
					.modsync$getConfigurationTasks();
			prepend(queue, task);
		});

		ServerConfigurationConnectionEvents.DISCONNECT.register((listener, minecraftServer) -> {
			SyncTask task = TASKS.remove(listener);
			if (task != null) {
				task.cancel();
			}
		});

		ServerLifecycleEvents.SERVER_STARTED.register(minecraftServer -> {
			server = minecraftServer;
			refreshManifest(minecraftServer);
		});

		ServerLifecycleEvents.SERVER_STOPPING.register(minecraftServer -> {
			for (SyncTask task : new ArrayList<>(TASKS.values())) {
				task.disconnect(Component.translatable("modsync.error.server_stopping"));
			}
			TASKS.clear();
			server = null;
			manifest = null;
			fileMap = Map.of();
			nonce = "";
		});
	}

	public static SyncManifest manifest() {
		return manifest;
	}

	public static synchronized SyncManifest refreshManifest(MinecraftServer minecraftServer) {
		MinecraftServer actual = minecraftServer != null ? minecraftServer : server;
		ModSyncConfig.Server config = ModSyncConfig.get().server;
		String serverName = actual != null ? actual.getMotd() : "Minecraft Server";
		String newNonce = UUID.randomUUID().toString();

		Path gameDir = FabricLoader.getInstance().getGameDir();
		Path modsDir = gameDir.resolve("mods");

		Map<Path, List<ManifestMod>> grouped = new LinkedHashMap<>();
		try {
			Files.createDirectories(modsDir);
			Path realMods = modsDir.toRealPath();
			for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
				String id = mod.getMetadata().getId();
				if (PLATFORM.contains(id) || config.excluded(id)) {
					continue;
				}
				ModEnvironment environment = mod.getMetadata().getEnvironment();
				if (environment == ModEnvironment.SERVER) {
					continue;
				}
				if (environment == ModEnvironment.CLIENT && !config.shareClientOnlyMods) {
					continue;
				}
				if (mod.getOrigin().getKind() != ModOrigin.Kind.PATH) {
					continue;
				}
				for (Path root : mod.getOrigin().getPaths()) {
					if (!isShareableJar(root, realMods)) {
						continue;
					}
					Path real = root.toRealPath();
					grouped.computeIfAbsent(real, ignored -> new ArrayList<>()).add(new ManifestMod(
							id,
							mod.getMetadata().getVersion().getFriendlyString(),
							mod.getMetadata().getName(),
							environment.name()
					));
				}
			}
		} catch (IOException e) {
			ModSync.LOGGER.warn("Could not scan the mods folder", e);
		}

		List<ManifestFile> files = new ArrayList<>();
		Map<String, Path> paths = new LinkedHashMap<>();
		grouped.entrySet().stream()
				.filter(entry -> !entry.getValue().isEmpty())
				.sorted(Comparator.comparing(entry -> entry.getKey().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
				.forEach(entry -> {
					Path path = entry.getKey();
					String fileName = path.getFileName().toString();
					try {
						long size = Files.size(path);
						if (size <= 0 || size > config.maxFileBytes()) {
							ModSync.LOGGER.warn("Skipping {} because its size ({} bytes) is outside the configured limit", fileName, size);
							return;
						}
						files.add(new ManifestFile(fileName, size, true, entry.getValue(),
								Hashes.sha256(path), "mods"));
						paths.put(fileName, path);
					} catch (IOException e) {
						ModSync.LOGGER.warn("Could not stat {}", path, e);
					}
				});

		addExtraFiles(files, paths, gameDir.resolve("resourcepacks"), "resourcepacks", config.maxFileBytes());
		addExtraFiles(files, paths, gameDir.resolve("shaderpacks"), "shaderpacks", config.maxFileBytes());

		SyncManifest built = SyncManifest.create(serverName, newNonce, files);
		manifest = built;
		fileMap = Map.copyOf(paths);
		nonce = newNonce;
		ModSync.LOGGER.info("ModSync sharing {} file(s), {} total", files.size(), built.totalBytes());
		return built;
	}

	private static void addExtraFiles(List<ManifestFile> files, Map<String, Path> paths, Path dir,
									  String kind, long maxFileBytes) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var stream = Files.list(dir)) {
			stream.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".zip"))
					.sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
					.forEach(path -> {
						String name = path.getFileName().toString();
						String key = kind + "/" + name;
						try {
							long size = Files.size(path);
							if (size <= 0 || size > maxFileBytes) {
								ModSync.LOGGER.warn("Skipping {} because its size ({} bytes) is outside the configured limit", key, size);
								return;
							}
							files.add(new ManifestFile(key, size, true, List.of(), Hashes.sha256(path), kind));
							paths.put(key, path);
						} catch (IOException e) {
							ModSync.LOGGER.warn("Could not stat {}", path, e);
						}
					});
		} catch (IOException e) {
			ModSync.LOGGER.warn("Could not scan {}", dir, e);
		}
	}

	private static boolean isShareableJar(Path path, Path realMods) {
		try {
			if (!Files.isRegularFile(path) || !path.getFileName().toString().toLowerCase().endsWith(".jar")) {
				return false;
			}
			return path.toRealPath().startsWith(realMods);
		} catch (IOException e) {
			return false;
		}
	}

	private static void send(ServerConfigurationPacketListenerImpl listener, Message message) {
		try {
			ServerConfigurationNetworking.send(listener, new SyncPayload(Protocol.encode(message)));
		} catch (ProtocolException e) {
			throw new IllegalStateException("Could not encode ModSync frame", e);
		}
	}

	static void prepend(Queue<ConfigurationTask> queue, ConfigurationTask task) {
		ModSync.LOGGER.info("ModSync task queue class: {}", queue.getClass().getName());
		try {
			List<ConfigurationTask> pending = new ArrayList<>(queue);
			queue.clear();
			queue.add(task);
			queue.addAll(pending);
			return;
		} catch (RuntimeException e) {
			ModSync.LOGGER.warn("Could not rebuild configuration task queue; falling back", e);
		}
		if (queue instanceof Deque<ConfigurationTask> deque) {
			deque.addFirst(task);
		} else {
			queue.add(task);
		}
	}

	/** One configuration task per connecting client. It blocks vanilla configuration until resolved. */
	private static final class SyncTask implements ConfigurationTask {
		private static final Type TYPE = new Type(ModSync.id("sync").toString());

		private final ServerConfigurationPacketListenerImpl listener;
		private final SyncManifest manifest;
		private final Map<String, Path> files;
		private volatile boolean completed;
		private volatile long lastActivity = System.currentTimeMillis();
		private volatile ScheduledFuture<?> timeout;
		private volatile TransferWorker worker;
		private int waitTicks;
		private boolean manifestSent;
		private volatile boolean installing;
		private volatile boolean readyToComplete;

		private SyncTask(ServerConfigurationPacketListenerImpl listener, SyncManifest manifest, Map<String, Path> files) {
			this.listener = listener;
			this.manifest = manifest;
			this.files = files;
		}

		@Override
		public void start(Consumer<Packet<?>> sender) {
			TASKS.put(listener, this);
			if (!ServerConfigurationNetworking.canSend(listener, SyncPayload.TYPE)) {
				// The client's channel declaration can arrive a few ticks after
				// BEFORE_CONFIGURE, especially through a proxy. Keep this task at the
				// front of the queue and retry briefly instead of silently letting
				// registry sync kick the client first.
				ModSync.LOGGER.info("Waiting for ModSync channel from {}", listener);
				return;
			}
			sendManifest();
		}

		@Override
		public boolean tick() {
			if (completed) {
				return false;
			}
			if (manifestSent) {
				return false;
			}
			if (ServerConfigurationNetworking.canSend(listener, SyncPayload.TYPE)) {
				sendManifest();
				return false;
			}
			waitTicks++;
			if (waitTicks >= 60) {
				ModSync.LOGGER.info("Client does not declare the ModSync channel; skipping sync for {}", listener);
				return true;
			}
			return false;
		}

		private void sendManifest() {
			if (completed || manifestSent) {
				return;
			}
			manifestSent = true;
			try {
				byte[] json = manifest.toJsonBytes();
				if (json.length > Protocol.MAX_MANIFEST_JSON) {
					ModSync.LOGGER.warn("ModSync manifest is too large ({} bytes); not sending it", json.length);
					complete();
					return;
				}
				send(listener, new Message.Manifest(manifest.nonce(), json));
				ModSync.LOGGER.info("Sending ModSync manifest to {} ({} file(s))", listener, manifest.files().size());
				scheduleTimeout();
			} catch (RuntimeException e) {
				ModSync.LOGGER.warn("Could not send ModSync manifest", e);
				disconnect(Component.translatable("modsync.error.server"));
			}
		}

		@Override
		public Type type() {
			return TYPE;
		}

		private void handle(Message message) {
			if (completed) {
				return;
			}
			if (!(message instanceof Message.Ping)) {
				ModSync.LOGGER.info("ModSync received {} from {}", message.type(), listener);
			}
			lastActivity = System.currentTimeMillis();
			scheduleTimeout();

			switch (message) {
				case Message.Ready ready -> {
					if (nonce.equals(ready.nonce())) {
						complete();
					}
				}
				case Message.Needed needed -> {
					ModSync.LOGGER.info("Client needs {} file(s); holding configuration until consent", needed.files().size());
				}
				case Message.Ping ping -> {
					// Activity already refreshed.
				}
				case Message.Request request -> {
					if (nonce.equals(request.nonce())) {
						ModSync.LOGGER.info("Client consented to download {} file(s)", request.files().size());
						startTransfer(request.files());
					}
				}
				case Message.Installed installed -> {
					if (installing && nonce.equals(installed.nonce())) {
						ModSync.LOGGER.info("Client installed {} file(s); closing sync session", installed.files());
						disconnect(Component.translatable("modsync.server.done"));
					}
				}
				case Message.Cancel cancel -> {
					complete();
				}
				default -> {
				}
			}
		}

		private void startTransfer(List<String> requested) {
			if (worker != null) {
				return;
			}
			ModSyncConfig.Server config = ModSyncConfig.get().server;
			if (requested.isEmpty() || requested.size() > manifest.files().size()) {
				fail(Component.translatable("modsync.error.request"));
				return;
			}

			List<Path> paths = new ArrayList<>();
			long total = 0;
			for (String name : requested) {
				Path path = files.get(name);
				if (path == null) {
					fail(Component.translatable("modsync.error.request"));
					return;
				}
				try {
					long size = Files.size(path);
					if (size <= 0 || size > config.maxFileBytes()) {
						fail(Component.translatable("modsync.error.file_too_large"));
						return;
					}
					total += size;
				} catch (IOException e) {
					fail(Component.translatable("modsync.error.read"));
					return;
				}
				if (total > config.maxTotalBytes()) {
					fail(Component.translatable("modsync.error.total_too_large"));
					return;
				}
				paths.add(path);
			}

			TransferWorker newWorker = new TransferWorker(this, requested, paths);
			worker = newWorker;
			newWorker.start();
		}

		private void scheduleTimeout() {
			ScheduledFuture<?> current = timeout;
			if (current != null) {
				current.cancel(false);
			}
			int seconds = ModSyncConfig.get().server.idleTimeoutSeconds;
			timeout = TIMEOUT.schedule(() -> {
				if (!completed && System.currentTimeMillis() - lastActivity >= seconds * 1000L) {
					disconnect(Component.translatable("modsync.error.timeout"));
				}
			}, seconds, TimeUnit.SECONDS);
		}

		private void complete() {
			if (completed) {
				return;
			}
			completed = true;
			ModSync.LOGGER.info("ModSync task completed for {}", listener);
			ScheduledFuture<?> current = timeout;
			if (current != null) {
				current.cancel(false);
			}
			TransferWorker currentWorker = worker;
			if (currentWorker != null) {
				currentWorker.stop();
			}
			TASKS.remove(listener);
			try {
				listener.completeTask(TYPE);
			} catch (RuntimeException e) {
				ModSync.LOGGER.warn("Could not complete ModSync configuration task", e);
			}
		}

		private void fail(Component reason) {
			if (completed) {
				return;
			}
			try {
				send(listener, new Message.Error(1, reason.getString()));
			} catch (RuntimeException ignored) {
			}
			disconnect(reason);
		}

		private void disconnect(Component reason) {
			if (completed) {
				return;
			}
			completed = true;
			ModSync.LOGGER.info("ModSync task completed for {}", listener);
			ScheduledFuture<?> current = timeout;
			if (current != null) {
				current.cancel(false);
			}
			TransferWorker currentWorker = worker;
			if (currentWorker != null) {
				currentWorker.stop();
			}
			TASKS.remove(listener);
			try {
				listener.disconnect(reason);
			} catch (RuntimeException e) {
				ModSync.LOGGER.debug("Could not disconnect ModSync client", e);
			}
		}

		private void onTransferDone(long bytes, int files) {
			if (completed) {
				return;
			}
			try {
				send(listener, new Message.Complete(files, bytes));
			} catch (RuntimeException e) {
				ModSync.LOGGER.warn("Could not send ModSync completion", e);
				disconnect(Component.translatable("modsync.error.server"));
				return;
			}
			// Do not close yet. The client verifies hashes and installs the jars, then
			// acknowledges. Closing immediately loses the tail of the transfer on a
			// high-latency/proxied connection.
			installing = true;
			lastActivity = System.currentTimeMillis();
			scheduleAckTimeout();
		}

		private void scheduleAckTimeout() {
			ScheduledFuture<?> current = timeout;
			if (current != null) {
				current.cancel(false);
			}
			timeout = TIMEOUT.schedule(() -> {
				if (!completed && installing) {
					ModSync.LOGGER.warn("Timed out waiting for ModSync install acknowledgement from {}", listener);
					disconnect(Component.translatable("modsync.error.install_ack"));
				}
			}, 120, TimeUnit.SECONDS);
		}

		private void onTransferError(String message) {
			if (completed) {
				return;
			}
			try {
				send(listener, new Message.Error(2, message));
			} catch (RuntimeException ignored) {
			}
			disconnect(Component.translatable("modsync.error.transfer"));
		}

		private void cancel() {
			completed = true;
			ModSync.LOGGER.info("ModSync task completed for {}", listener);
			ScheduledFuture<?> current = timeout;
			if (current != null) {
				current.cancel(false);
			}
			TransferWorker currentWorker = worker;
			if (currentWorker != null) {
				currentWorker.stop();
			}
		}
	}

	private static final class TransferWorker {
		private static final int CHUNK = 32 * 1024;

		private final SyncTask task;
		private final ServerConfigurationPacketListenerImpl listener;
		private final List<String> names;
		private final List<Path> paths;
		private volatile boolean running = true;
		private volatile Thread thread;

		private TransferWorker(SyncTask task, List<String> names, List<Path> paths) {
			this.task = task;
			this.listener = task.listener;
			this.names = List.copyOf(names);
			this.paths = List.copyOf(paths);
		}

		private void start() {
			thread = new Thread(this::run, "ModSync-transfer");
			thread.setDaemon(true);
			thread.start();
		}

		private void stop() {
			running = false;
			Thread current = thread;
			if (current != null) {
				current.interrupt();
			}
		}

		private void run() {
			int index = 0;
			long total = 0;
			try {
				for (int i = 0; i < names.size() && running; i++) {
					Path path = paths.get(i);
					long size = Files.size(path);
					send(new Message.FileStart(i, names.size(), names.get(i), size));

					MessageDigest digest = Hashes.newDigest();
					long offset = 0;
					byte[] buffer = new byte[CHUNK];
					try (InputStream in = Files.newInputStream(path)) {
						int read;
						while (running && (read = in.read(buffer)) >= 0) {
							if (read == 0) {
								continue;
							}
							byte[] chunk = new byte[read];
							System.arraycopy(buffer, 0, chunk, 0, read);
							digest.update(chunk);
							send(new Message.FileChunk(i, offset, chunk));
							offset += read;
							total += read;
							if (offset > size) {
								throw new IOException("file grew while sending: " + path);
							}
							Thread.sleep(1);
						}
					}

					if (!running) {
						return;
					}
					if (offset != size) {
						throw new IOException("file changed while sending: " + path);
					}
					send(new Message.FileEnd(i, Hashes.hex(digest.digest()), true, ""));
					index++;
				}

				if (running) {
					task.onTransferDone(total, index);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				if (running) {
					ModSync.LOGGER.warn("ModSync transfer failed", e);
					task.onTransferError(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
				}
			}
		}

		private void send(Message message) {
			SyncServer.send(listener, message);
		}
	}
}
