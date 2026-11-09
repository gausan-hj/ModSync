package dev.modsync.client;

import dev.modsync.util.Human;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Shown after leaving a server whose mods were installed by ModSync. */
public final class CleanupScreen extends ModSyncScreen {
	private final SyncedMods.Entry entry;
	private final String serverKey;

	public CleanupScreen(SyncedMods.Entry entry, String serverKey) {
		super(Component.translatable("modsync.cleanup.title"));
		this.entry = entry;
		this.serverKey = serverKey;
	}

	@Override
	protected void init() {
		int center = width / 2;
		int y = height - 68;
		addRenderableWidget(Button.builder(
						Component.translatable("modsync.cleanup.delete"),
						button -> {
							SyncedMods.delete(entry, serverKey);
							minecraft.setScreenAndShow(new TitleScreen());
						})
				.bounds(center - 180, y, 170, 20)
				.build());
		addRenderableWidget(Button.builder(
						Component.translatable("modsync.cleanup.keep"),
						button -> keep())
				.bounds(center + 10, y, 170, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		centered(graphics, Component.translatable("modsync.cleanup.title"), 36, 0xFFFFFFFF);
		centered(graphics, Component.literal(trim(entry.serverName, width - 80)), 56, 0xFFB0B0B0);

		long bytes = 0;
		Path gameDir = FabricLoader.getInstance().getGameDir();
		for (String name : entry.files) {
			try {
				Path path = resolve(gameDir, name);
				if (path != null && Files.isRegularFile(path)) {
					bytes += Files.size(path);
				}
			} catch (IOException ignored) {
			}
		}
		centered(graphics, Component.translatable("modsync.cleanup.summary",
				entry.files.size(), Human.bytes(bytes)), 84, 0xFFFFD27F);
		centered(graphics, Component.translatable("modsync.cleanup.warning"), 108, 0xFFFFAA00);

		int y = 140;
		int max = Math.min(entry.files.size(), 6);
		for (int i = 0; i < max; i++) {
			text(graphics, trim(display(entry.files.get(i)), width - 80), 40, y + i * 18, 0xFFE8E8E8);
		}
		if (entry.files.size() > max) {
			text(graphics, Component.translatable("modsync.cleanup.more",
					entry.files.size() - max), 40, y + max * 18, 0xFF9A9A9A);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void onClose() {
		keep();
	}

	private static Path resolve(Path gameDir, String key) {
		String normalized = key.replace('\\', '/');
		int slash = normalized.indexOf('/');
		if (slash < 0) {
			return gameDir.resolve("mods").resolve(normalized);
		}
		String kind = normalized.substring(0, slash);
		if (!kind.equals("mods") && !kind.equals("resourcepacks") && !kind.equals("shaderpacks")) {
			return null;
		}
		return gameDir.resolve(kind).resolve(normalized.substring(slash + 1));
	}

	private static String display(String key) {
		int slash = key.lastIndexOf('/');
		return slash >= 0 ? key.substring(slash + 1) : key;
	}

	private void keep() {
		SyncedMods.forget(serverKey);
		minecraft.setScreenAndShow(new TitleScreen());
	}
}
