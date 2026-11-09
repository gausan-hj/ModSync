package dev.modsync.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.modsync.ModSync;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Remembers which jars ModSync installed for which server, so the client can offer to
 * remove them when the player leaves that server.
 */
public final class SyncedMods {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path STATE = FabricLoader.getInstance().getConfigDir().resolve("modsync-synced.json");
	private static Data data;

	private SyncedMods() {
	}

	public static final class Entry {
		public String serverName = "";
		public List<String> files = new ArrayList<>();
		public boolean active;

		public Entry() {
		}

		public Entry(String serverName, List<String> files, boolean active) {
			this.serverName = serverName;
			this.files = new ArrayList<>(files);
			this.active = active;
		}

		public Entry copy() {
			return new Entry(serverName, files, active);
		}
	}

	private static final class Data {
		private Map<String, Entry> servers = new LinkedHashMap<>();
	}

	public static String currentServerKey(Minecraft client) {
		ServerData server = client.getCurrentServer();
		if (server != null && server.ip != null && !server.ip.isBlank()) {
			return server.ip;
		}
		return "unknown";
	}

	public static String currentServerKey(Minecraft client, net.minecraft.network.Connection connection) {
		String key = currentServerKey(client);
		if (!"unknown".equals(key)) {
			return key;
		}
		if (connection != null && connection.getRemoteAddress() instanceof InetSocketAddress remote) {
			String host = remote.getAddress() != null ? remote.getAddress().getHostAddress() : remote.getHostString();
			if (host != null && !host.isBlank()) {
				return host + ":" + remote.getPort();
			}
		}
		return "unknown";
	}

	public static synchronized void record(String key, String serverName, List<String> installed) {
		if (installed == null || installed.isEmpty()) {
			return;
		}
		Data current = load();
		Entry entry = current.servers.computeIfAbsent(key, ignored -> new Entry());
		entry.serverName = serverName == null ? "" : serverName;
		entry.files = new ArrayList<>(installed);
		entry.active = false;
		ModSync.LOGGER.info("Recorded {} synced file(s) for {} ({})", installed.size(), key, entry.serverName);
		save();
	}

	public static synchronized void activate(String key) {
		Data current = load();
		Entry entry = current.servers.get(key);
		if (entry == null || entry.files.isEmpty()) {
			return;
		}
		entry.active = true;
		ModSync.LOGGER.info("ModSync cleanup armed for {} ({} file(s))", key, entry.files.size());
		save();
	}

	public static synchronized Entry active(String key) {
		Data current = load();
		Entry entry = current.servers.get(key);
		if (entry == null || !entry.active || entry.files.isEmpty()) {
			return null;
		}
		return entry.copy();
	}

	public static synchronized void forget(String key) {
		Data current = load();
		if (current.servers.remove(key) != null) {
			save();
		}
	}

	/** Attempts to delete the recorded files immediately. Locked files are removed after the game exits. */
	public static void delete(Entry entry, String key) {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		List<Path> locked = new ArrayList<>();
		int deleted = 0;
		for (String name : entry.files) {
			Path path = safePath(gameDir, name);
			if (path == null) {
				continue;
			}
			try {
				if (Files.deleteIfExists(path)) {
					deleted++;
				}
			} catch (IOException e) {
				locked.add(path);
			}
		}
		forget(key);
		ModSync.LOGGER.info("ModSync cleanup deleted {} file(s); {} locked", deleted, locked.size());
		if (!locked.isEmpty()) {
			scheduleDelete(locked);
		}
	}

	private static Path safePath(Path gameDir, String name) {
		if (name == null || name.isBlank() || name.contains("\"") || name.contains("..")) {
			return null;
		}
		String normalized = name.replace('\\', '/');
		String[] parts = normalized.split("/");
		if (parts.length == 1) {
			return gameDir.resolve("mods").resolve(parts[0]);
		}
		if (parts.length != 2) {
			return null;
		}
		String kind = parts[0];
		if (!kind.equals("mods") && !kind.equals("resourcepacks") && !kind.equals("shaderpacks")) {
			return null;
		}
		if (parts[1].isBlank()) {
			return null;
		}
		return gameDir.resolve(kind).resolve(parts[1]);
	}

	private static void scheduleDelete(List<Path> paths) {
		Path script = FabricLoader.getInstance().getConfigDir().resolve("modsync-cleanup.bat");
		StringBuilder batch = new StringBuilder("@echo off\r\nsetlocal\r\nset /a tries=0\r\n:retry\r\n");
		batch.append("set /a tries+=1\r\nif %tries% GTR 120 goto end\r\nset remain=0\r\n");
		for (Path path : paths) {
			String escaped = path.toAbsolutePath().toString().replace("%", "%%");
			batch.append("if exist \"").append(escaped).append("\" (del /f /q \"")
					.append(escaped).append("\" >nul 2>&1 & if exist \"")
					.append(escaped).append("\" set remain=1)\r\n");
		}
		batch.append("if %remain%==1 (ping -n 2 127.0.0.1 >nul & goto retry)\r\n")
				.append(":end\r\ndel /f /q \"%~f0\" >nul 2>&1\r\n");
		try {
			Files.writeString(script, batch.toString(), StandardCharsets.ISO_8859_1);
			new ProcessBuilder("cmd.exe", "/c", "start", "", "/b", "cmd.exe", "/c", script.toString())
					.start();
			ModSync.LOGGER.info("Scheduled ModSync cleanup after exit: {}", script);
		} catch (IOException e) {
			ModSync.LOGGER.warn("Could not schedule ModSync cleanup", e);
		}
	}

	private static Data load() {
		if (data != null) {
			return data;
		}
		if (!Files.isRegularFile(STATE)) {
			data = new Data();
			return data;
		}
		try {
			data = GSON.fromJson(Files.readString(STATE, StandardCharsets.UTF_8), Data.class);
			if (data == null) {
				data = new Data();
			}
			if (data.servers == null) {
				data.servers = new LinkedHashMap<>();
			} else {
				Map<String, Entry> migrated = new LinkedHashMap<>();
				data.servers.forEach((key, entry) -> migrated.put(normalizeKey(key), entry));
				data.servers = migrated;
			}
		} catch (IOException | JsonSyntaxException e) {
			ModSync.LOGGER.warn("Could not read ModSync sync history", e);
			data = new Data();
		}
		return data;
	}

	private static String normalizeKey(String key) {
		if (key == null || key.isBlank()) {
			return "unknown";
		}
		int slash = key.lastIndexOf('/');
		if (slash >= 0 && slash + 1 < key.length()) {
			return key.substring(slash + 1);
		}
		return key;
	}

	private static void save() {
		try {
			Files.createDirectories(STATE.getParent());
			Path temp = STATE.resolveSibling(STATE.getFileName() + ".tmp");
			Files.writeString(temp, GSON.toJson(load()), StandardCharsets.UTF_8);
			try {
				Files.move(temp, STATE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (IOException e) {
				Files.move(temp, STATE, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			ModSync.LOGGER.warn("Could not save ModSync sync history", e);
		}
	}
}
