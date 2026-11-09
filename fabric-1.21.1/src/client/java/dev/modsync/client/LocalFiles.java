package dev.modsync.client;

import dev.modsync.plan.LocalFile;
import dev.modsync.util.Hashes;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Collects resource packs and shader packs already installed on the client. */
public final class LocalFiles {
	private LocalFiles() {
	}

	public static List<LocalFile> collect() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		List<LocalFile> result = new ArrayList<>();
		scan(result, gameDir.resolve("resourcepacks"), "resourcepacks");
		scan(result, gameDir.resolve("shaderpacks"), "shaderpacks");
		return result;
	}

	private static void scan(List<LocalFile> result, Path dir, String kind) {
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var stream = Files.list(dir)) {
			stream.filter(Files::isRegularFile)
					.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".zip"))
					.forEach(path -> {
						try {
							result.add(new LocalFile(kind, path.getFileName().toString(), path,
									Files.size(path), Hashes.sha256(path)));
						} catch (IOException ignored) {
						}
					});
		} catch (IOException ignored) {
		}
	}
}
