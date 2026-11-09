package dev.modsync.forge.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.modsync.forge.ModSyncConfig;
import dev.modsync.forge.ModSyncForge;
import dev.modsync.manifest.SyncManifest;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.manifest.SyncManifest.ManifestMod;
import dev.modsync.util.Hashes;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Forge host side: builds a manifest and serves it over a small HTTP endpoint. */
public final class SyncServer {
	private static final Set<String> PLATFORM = Set.of(
			"minecraft", "forge", "neoforge", "mcp", "fml", "modsync"
	);
	private static final String PREFIX = "/modsync/";

	private static volatile HttpServer http;
	private static volatile ExecutorService executor;
	private static volatile int port = -1;
	private static volatile SyncManifest manifest;
	private static volatile Map<String, Path> files = Map.of();

	private SyncServer() {
	}

	public static synchronized void start(MinecraftServer server) {
		ModSyncConfig.Server config = ModSyncConfig.get().server;
		if (!config.enabled) {
			ModSyncForge.LOGGER.info("ModSync sharing is disabled in config");
			return;
		}
		refresh(server);
		if (http != null) {
			return;
		}

		int preferred = config.syncPort > 0 ? config.syncPort : Math.min(65535, server.getPort() + 1);
		List<Integer> candidates = new ArrayList<>();
		candidates.add(preferred);
		if (!candidates.contains(8710)) {
			candidates.add(8710);
		}
		if (!candidates.contains(server.getPort() + 2)) {
			candidates.add(server.getPort() + 2);
		}

		HttpServer created = null;
		for (int candidate : candidates) {
			if (candidate <= 0 || candidate > 65535) {
				continue;
			}
			try {
				HttpServer serverCandidate = HttpServer.create(new InetSocketAddress("0.0.0.0", candidate), 0);
				serverCandidate.createContext(PREFIX + "manifest.json", SyncServer::handleManifest);
				serverCandidate.createContext(PREFIX + "files/", SyncServer::handleFile);
				serverCandidate.createContext(PREFIX + "health", SyncServer::handleHealth);
				ExecutorService pool = Executors.newFixedThreadPool(2, runnable -> {
					Thread thread = new Thread(runnable, "ModSync-http");
					thread.setDaemon(true);
					return thread;
				});
				serverCandidate.setExecutor(pool);
				serverCandidate.start();
				created = serverCandidate;
				executor = pool;
				port = candidate;
				break;
			} catch (BindException e) {
				ModSyncForge.LOGGER.warn("ModSync sync port {} is already in use", candidate);
			} catch (IOException e) {
				ModSyncForge.LOGGER.warn("Could not start ModSync sync server on {}", candidate, e);
			}
		}

		http = created;
		if (created == null) {
			ModSyncForge.LOGGER.error("ModSync could not bind any sync port; clients will not be able to download");
			return;
		}
		ModSyncForge.LOGGER.info("ModSync sync server listening on 0.0.0.0:{} ({} jar(s), {} bytes)",
				port, manifest.files().size(), manifest.totalBytes());
	}

	public static synchronized void stop() {
		HttpServer current = http;
		http = null;
		if (current != null) {
			current.stop(0);
		}
		ExecutorService pool = executor;
		executor = null;
		if (pool != null) {
			pool.shutdownNow();
		}
		port = -1;
		manifest = null;
		files = Map.of();
	}

	public static synchronized SyncManifest refresh(MinecraftServer server) {
		ModSyncConfig.Server config = ModSyncConfig.get().server;
		Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods").toAbsolutePath().normalize();
		Path realMods;
		try {
			Files.createDirectories(modsDir);
			realMods = modsDir.toRealPath();
		} catch (IOException e) {
			ModSyncForge.LOGGER.warn("Could not access the mods folder", e);
			realMods = modsDir;
		}

		Map<Path, List<ManifestMod>> grouped = new LinkedHashMap<>();
		for (IModFileInfo fileInfo : ModList.get().getModFiles()) {
			IModFile modFile = fileInfo.getFile();
			Path path;
			try {
				path = modFile.getFilePath();
			} catch (RuntimeException e) {
				continue;
			}
			if (path == null || !Files.isRegularFile(path)) {
				continue;
			}
			Path absolute = path.toAbsolutePath().normalize();
			Path real;
			try {
				real = absolute.toRealPath();
			} catch (IOException e) {
				continue;
			}
			if (!real.startsWith(realMods) || !real.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
				continue;
			}

			List<IModInfo> modInfos = fileInfo.getMods();
			boolean excluded = false;
			List<ManifestMod> mods = new ArrayList<>();
			for (IModInfo info : modInfos) {
				String id = info.getModId();
				if (PLATFORM.contains(id)) {
					continue;
				}
				if (config.excluded(id)) {
					excluded = true;
					break;
				}
				mods.add(new ManifestMod(
						id,
						info.getVersion().toString(),
						info.getDisplayName(),
						"UNIVERSAL"
				));
			}
			if (excluded || mods.isEmpty()) {
				continue;
			}
			grouped.computeIfAbsent(real, ignored -> new ArrayList<>()).addAll(mods);
		}

		List<ManifestFile> manifestFiles = new ArrayList<>();
		Map<String, Path> fileMap = new LinkedHashMap<>();
		grouped.entrySet().stream()
				.sorted(Comparator.comparing(entry -> entry.getKey().getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
				.forEach(entry -> {
					Path path = entry.getKey();
					String name = path.getFileName().toString();
					try {
						long size = Files.size(path);
						if (size <= 0 || size > config.maxFileBytes()) {
							ModSyncForge.LOGGER.warn("Skipping {}: size {} exceeds the configured limit", name, size);
							return;
						}
						String sha = Hashes.sha256(path);
						manifestFiles.add(new ManifestFile(name, size, true, sha, entry.getValue()));
						fileMap.put(name, path);
					} catch (IOException e) {
						ModSyncForge.LOGGER.warn("Could not hash {}", path, e);
					}
				});

		String serverName = server != null ? server.getMotd() : "Minecraft Server";
		SyncManifest built = SyncManifest.create(serverName, UUID.randomUUID().toString(), manifestFiles);
		manifest = built;
		files = Map.copyOf(fileMap);
		ModSyncForge.LOGGER.info("ModSync sharing {} jar(s), {} bytes", manifestFiles.size(), built.totalBytes());
		return built;
	}

	private static void handleManifest(HttpExchange exchange) throws IOException {
		SyncManifest current = manifest;
		if (current == null) {
			sendText(exchange, 503, "manifest not ready");
			return;
		}
		byte[] body = current.toJsonBytes();
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(200, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	private static void handleHealth(HttpExchange exchange) throws IOException {
		sendText(exchange, 200, "ok");
	}

	private static void handleFile(HttpExchange exchange) throws IOException {
		String rawPath = exchange.getRequestURI().getRawPath();
		String prefix = PREFIX + "files/";
		if (rawPath == null || !rawPath.startsWith(prefix)) {
			sendText(exchange, 404, "not found");
			return;
		}
		String name;
		try {
			name = URLDecoder.decode(rawPath.substring(prefix.length()), StandardCharsets.UTF_8);
		} catch (IllegalArgumentException e) {
			sendText(exchange, 400, "bad name");
			return;
		}
		Path path = files.get(name);
		if (path == null || !Files.isRegularFile(path)) {
			sendText(exchange, 404, "not found");
			return;
		}
		long size;
		try {
			size = Files.size(path);
		} catch (IOException e) {
			sendText(exchange, 500, "read error");
			return;
		}
		exchange.getResponseHeaders().set("Content-Type", "application/java-archive");
		exchange.getResponseHeaders().set("Content-Length", Long.toString(size));
		exchange.getResponseHeaders().set("Cache-Control", "no-store");
		exchange.sendResponseHeaders(200, size);
		try (InputStream in = Files.newInputStream(path); OutputStream out = exchange.getResponseBody()) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = in.read(buffer)) >= 0) {
				out.write(buffer, 0, read);
			}
		}
	}

	private static void sendText(HttpExchange exchange, int status, String text) throws IOException {
		byte[] body = text.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
		exchange.sendResponseHeaders(status, body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	public static int port() {
		return port;
	}

	public static SyncManifest manifest() {
		return manifest;
	}
}
