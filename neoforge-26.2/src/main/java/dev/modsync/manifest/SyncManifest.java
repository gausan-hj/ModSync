package dev.modsync.manifest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Forge-side manifest. Unlike the Fabric manifest, each file carries its SHA-256. */
public record SyncManifest(int protocol, String serverName, String nonce, long generatedAt, List<ManifestFile> files) {
	public static final int CURRENT_PROTOCOL = 1;
	private static final int MAX_JSON = 2 * 1024 * 1024;
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	public SyncManifest {
		serverName = serverName == null ? "Minecraft Server" : serverName;
		nonce = nonce == null ? "" : nonce;
		files = files == null ? List.of() : List.copyOf(files);
	}

	public static SyncManifest create(String serverName, String nonce, List<ManifestFile> files) {
		return new SyncManifest(CURRENT_PROTOCOL, serverName, nonce, System.currentTimeMillis(), files);
	}

	public boolean compatible() {
		return protocol == CURRENT_PROTOCOL;
	}

	public long totalBytes() {
		long total = 0;
		for (ManifestFile file : files) {
			total += Math.max(0, file.size());
		}
		return total;
	}

	public ManifestFile find(String fileName) {
		for (ManifestFile file : files) {
			if (file.file().equals(fileName)) {
				return file;
			}
		}
		return null;
	}

	public byte[] toJsonBytes() {
		return GSON.toJson(this).getBytes(StandardCharsets.UTF_8);
	}

	public static SyncManifest fromJsonBytes(byte[] json) throws IOException {
		if (json == null || json.length == 0 || json.length > MAX_JSON) {
			throw new IOException("invalid manifest size");
		}
		try {
			SyncManifest manifest = GSON.fromJson(new String(json, StandardCharsets.UTF_8), SyncManifest.class);
			if (manifest == null) {
				throw new IOException("empty manifest");
			}
			return manifest;
		} catch (JsonSyntaxException e) {
			throw new IOException("invalid manifest json", e);
		}
	}

	public record ManifestFile(String file, long size, boolean required, String sha256, List<ManifestMod> mods) {
		public ManifestFile {
			file = Objects.requireNonNullElse(file, "");
			size = Math.max(0, size);
			sha256 = sha256 == null ? "" : sha256.toLowerCase();
			mods = mods == null ? List.of() : List.copyOf(mods);
		}

		public String displayName() {
			if (mods.isEmpty()) {
				return file;
			}
			if (mods.size() == 1) {
				return mods.get(0).name();
			}
			return mods.get(0).name() + " +" + (mods.size() - 1);
		}

		public String versions() {
			if (mods.isEmpty()) {
				return "";
			}
			if (mods.size() == 1) {
				return mods.get(0).version();
			}
			return mods.stream().map(ManifestMod::version).distinct().reduce((a, b) -> a + ", " + b).orElse("");
		}
	}

	public record ManifestMod(String id, String version, String name, String env) {
		public ManifestMod {
			id = Objects.requireNonNullElse(id, "");
			version = Objects.requireNonNullElse(version, "");
			name = name == null || name.isBlank() ? id : name;
			env = env == null || env.isBlank() ? "UNIVERSAL" : env;
		}
	}
}

