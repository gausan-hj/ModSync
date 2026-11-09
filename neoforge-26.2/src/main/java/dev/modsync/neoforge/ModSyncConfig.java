package dev.modsync.neoforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ModSyncConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static volatile ModSyncConfig instance;

	public Server server = new Server();
	public Client client = new Client();

	public static ModSyncConfig get() {
		ModSyncConfig current = instance;
		if (current == null) {
			synchronized (ModSyncConfig.class) {
				current = instance;
				if (current == null) {
					current = load();
					instance = current;
				}
			}
		}
		return current;
	}

	public static Path path() {
		return FMLPaths.CONFIGDIR.get().resolve("modsync-forge.json");
	}

	public static synchronized ModSyncConfig load() {
		Path path = path();
		ModSyncConfig config = new ModSyncConfig();
		if (Files.isRegularFile(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				ModSyncConfig parsed = GSON.fromJson(reader, ModSyncConfig.class);
				if (parsed != null) {
					config = parsed;
				}
			} catch (IOException | JsonSyntaxException e) {
				ModSyncNeoForge.LOGGER.warn("Could not read {}; using defaults", path, e);
			}
		}
		config.validate();
		config.save();
		return config;
	}

	public synchronized void save() {
		Path path = path();
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException e) {
			ModSyncNeoForge.LOGGER.warn("Could not write {}", path, e);
		}
	}

	public synchronized void validate() {
		if (server == null) {
			server = new Server();
		}
		if (client == null) {
			client = new Client();
		}
		server.syncPort = Math.max(0, Math.min(65535, server.syncPort));
		server.maxFileSizeMb = clamp(server.maxFileSizeMb, 1, 4096, 200);
		server.maxTotalSizeMb = clamp(server.maxTotalSizeMb, 1, 8192, 1024);
		client.maxTotalSizeMb = clamp(client.maxTotalSizeMb, 1, 8192, 1024);
		if (server.excludeMods == null) {
			server.excludeMods = new ArrayList<>();
		}
		server.excludeMods = server.excludeMods.stream()
				.filter(value -> value != null && !value.isBlank())
				.map(value -> value.trim().toLowerCase(Locale.ROOT))
				.distinct()
				.toList();
	}

	private static int clamp(int value, int min, int max, int fallback) {
		if (value <= 0) {
			return fallback;
		}
		return Math.max(min, Math.min(max, value));
	}

	public static final class Server {
		public boolean enabled = true;
		/** 0 means game port + 1. */
		public int syncPort = 0;
		public List<String> excludeMods = new ArrayList<>();
		public int maxFileSizeMb = 200;
		public int maxTotalSizeMb = 1024;

		public boolean excluded(String id) {
			return id != null && excludeMods.contains(id.toLowerCase(Locale.ROOT));
		}

		public long maxFileBytes() {
			return maxFileSizeMb * 1024L * 1024L;
		}

		public long maxTotalBytes() {
			return maxTotalSizeMb * 1024L * 1024L;
		}
	}

	public static final class Client {
		public boolean enabled = true;
		public boolean backup = true;
		public int maxTotalSizeMb = 1024;

		public long maxTotalBytes() {
			return maxTotalSizeMb * 1024L * 1024L;
		}
	}
}
