package dev.modsync.client;

import dev.modsync.plan.LocalMod;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModOrigin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LocalMods {
	private LocalMods() {
	}

	public static List<LocalMod> collect() {
		Path modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
		Path realModsDir = null;
		try {
			realModsDir = modsDir.toRealPath();
		} catch (Exception ignored) {
		}

		List<LocalMod> mods = new ArrayList<>();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			if (mod.getOrigin().getKind() != ModOrigin.Kind.PATH) {
				continue;
			}
			Path jar = null;
			for (Path root : mod.getOrigin().getPaths()) {
				if (!Files.isRegularFile(root) || !root.getFileName().toString().toLowerCase().endsWith(".jar")) {
					continue;
				}
				Path candidate = root.toAbsolutePath().normalize();
				try {
					if (realModsDir != null) {
						candidate = candidate.toRealPath();
						if (!candidate.startsWith(realModsDir)) {
							continue;
						}
					} else if (!candidate.startsWith(modsDir)) {
						continue;
					}
				} catch (Exception ignored) {
					continue;
				}
				jar = candidate;
				break;
			}
			mods.add(new LocalMod(
					mod.getMetadata().getId(),
					mod.getMetadata().getVersion().getFriendlyString(),
					mod.getMetadata().getName(),
					mod.getMetadata().getEnvironment().name(),
					jar
			));
		}
		mods.sort(Comparator.comparing(LocalMod::id, String.CASE_INSENSITIVE_ORDER));
		return List.copyOf(mods);
	}
}
