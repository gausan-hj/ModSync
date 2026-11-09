package dev.modsync.neoforge.client;

import dev.modsync.neoforge.ModSyncNeoForge;
import dev.modsync.plan.LocalMod;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import net.neoforged.neoforgespi.locating.IModFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ForgeLocalMods {
	private ForgeLocalMods() {
	}

	public static List<LocalMod> collect() {
		Path modsDir = FMLPaths.GAMEDIR.get().resolve("mods").toAbsolutePath().normalize();
		Path realMods = null;
		try {
			realMods = modsDir.toRealPath();
		} catch (Exception ignored) {
		}

		List<LocalMod> result = new ArrayList<>();
		for (IModFileInfo fileInfo : ModList.get().getModFiles()) {
			IModFile file = fileInfo.getFile();
			Path path;
			try {
				path = file.getFilePath();
			} catch (RuntimeException e) {
				continue;
			}
			if (path == null || !Files.isRegularFile(path)) {
				continue;
			}
			Path absolute = path.toAbsolutePath().normalize();
			try {
				if (realMods != null) {
					absolute = absolute.toRealPath();
					if (!absolute.startsWith(realMods)) {
						continue;
					}
				} else if (!absolute.startsWith(modsDir)) {
					continue;
				}
			} catch (Exception e) {
				continue;
			}
			for (IModInfo info : fileInfo.getMods()) {
				result.add(new LocalMod(
						info.getModId(),
						info.getVersion().toString(),
						info.getDisplayName(),
						"UNIVERSAL",
						absolute
				));
			}
		}
		result.sort(Comparator.comparing(LocalMod::id, String.CASE_INSENSITIVE_ORDER));
		ModSyncNeoForge.LOGGER.debug("ModSync found {} local mod entries", result.size());
		return List.copyOf(result);
	}
}
