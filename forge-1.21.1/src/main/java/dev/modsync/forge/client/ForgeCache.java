package dev.modsync.forge.client;

import dev.modsync.util.Hashes;
import net.minecraftforge.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Content-addressed cache shared by every server. */
public final class ForgeCache {
	private static Path override;

	private ForgeCache() {
	}

	private static Path dir() {
		return override != null ? override : FMLPaths.GAMEDIR.get().resolve(".modsync-cache");
	}

	public static void useDirectoryForTests(Path directory) {
		override = directory;
	}

	public static Path pathFor(String sha256) {
		return dir().resolve(sha256.toLowerCase() + ".jar");
	}

	public static Path partialFor(String sha256) {
		return dir().resolve(".partial").resolve(sha256.toLowerCase() + ".part");
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
			Files.createDirectories(dir());
			Path target = pathFor(sha256);
			if (Files.isRegularFile(target)) {
				return;
			}
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
		}
	}
}
