package dev.modsync.manifest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import dev.modsync.protocol.Protocol;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * The server's shareable mod set. The manifest intentionally contains no absolute paths:
 * clients only see a file name, size, and the mod ids/versions inside that jar.
 */
public record SyncManifest(int protocol, String serverName, String nonce, long generatedAt, List<ManifestFile> files) {
	public static final int CURRENT_PROTOCOL = 3;
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

	public static SyncManifest fromJsonBytes(byte[] json) throws Protocol.ProtocolException {
		if (json == null || json.length == 0 || json.length > Protocol.MAX_MANIFEST_JSON) {
			throw new Protocol.ProtocolException("invalid manifest size");
		}
		try {
			SyncManifest manifest = GSON.fromJson(new String(json, StandardCharsets.UTF_8), SyncManifest.class);
			if (manifest == null) {
				throw new Protocol.ProtocolException("empty manifest");
			}
			return manifest;
		} catch (JsonSyntaxException e) {
			throw new Protocol.ProtocolException("invalid manifest json", e);
		}
	}

	public record ManifestFile(String file, long size, boolean required, List<ManifestMod> mods,
							   String sha256, String kind) {
		public ManifestFile {
			file = Objects.requireNonNullElse(file, "");
			size = Math.max(0, size);
			mods = mods == null ? List.of() : List.copyOf(mods);
			sha256 = Objects.requireNonNullElse(sha256, "");
			kind = kind == null || kind.isBlank() ? "mods" : kind;
		}

		public ManifestFile(String file, long size, boolean required, List<ManifestMod> mods) {
			this(file, size, required, mods, "", "mods");
		}

		public String fileName() {
			int slash = file.lastIndexOf('/');
			return slash >= 0 && slash + 1 < file.length() ? file.substring(slash + 1) : file;
		}

		public boolean isMod() {
			return "mods".equals(kind);
		}

		public String displayName() {
			if (mods.isEmpty()) {
				return fileName();
			}
			if (mods.size() == 1) {
				return mods.getFirst().name();
			}
			return mods.getFirst().name() + " +" + (mods.size() - 1);
		}

		public String versions() {
			if (mods.isEmpty()) {
				return "";
			}
			if (mods.size() == 1) {
				return mods.getFirst().version();
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
