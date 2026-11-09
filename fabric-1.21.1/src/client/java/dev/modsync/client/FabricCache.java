package dev.modsync.client;

import dev.modsync.util.Hashes;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Content-addressed cache shared by every server. */
public final class FabricCache {
	private static final Path DIR = FabricLoader.getInstance().getGameDir().resolve(".modsync-cache");

	private FabricCache() {
	}

	public static Path pathFor(String sha256) {
		return DIR.resolve(sha256.toLowerCase() + ".jar");
	}

	public static Path find(String sha256) {
		if (sha256 == null || sha256.isBlank()) {
			return null;
		}
		Path path = pathFor(sha256);
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try {
			return Hashes.sha256(path).equalsIgnoreCase(sha256) ? path : null;
		} catch (IOException e) {
			return null;
		}
	}

	public static void store(Path source, String sha256) {
		if (sha256 == null || sha256.isBlank()) {
			return;
		}
		try {
			Files.createDirectories(DIR);
			Path target = pathFor(sha256);
			if (!Files.isRegularFile(target)) {
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ignored) {
		}
	}
}
