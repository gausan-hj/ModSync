package dev.modsync.client;

import dev.modsync.ModSync;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SyncResultScreen extends ModSyncScreen {
	private final boolean success;
	private final int installed;
	private final Path backupDir;
	private final String error;
	private final String serverName;

	public SyncResultScreen(boolean success, int installed, Path backupDir, String error, String serverName) {
		super(Component.translatable(success ? "modsync.result.success" : "modsync.result.failed"));
		this.success = success;
		this.installed = installed;
		this.backupDir = backupDir;
		this.error = error == null ? "" : error;
		this.serverName = serverName == null ? "" : serverName;
	}

	@Override
	protected void init() {
		int y = height - 42;
		addRenderableWidget(Button.builder(
						Component.translatable("modsync.quit"),
						button -> minecraft.stop())
				.bounds(width / 2 - 155, y, 150, 20)
				.build());

		addRenderableWidget(Button.builder(
						Component.translatable("modsync.result.open_folder"),
						button -> openModsFolder())
				.bounds(width / 2 + 5, y, 150, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		centered(graphics, Component.translatable(success ? "modsync.result.success" : "modsync.result.failed"), 40,
				success ? 0xFF55FF55 : 0xFFFF5555);
		centered(graphics, Component.literal(trim(serverName, width - 80)), 60, 0xFFB0B0B0);

		if (success) {
			String line = Component.translatable("modsync.result.installed_short", installed).getString();
			centered(graphics, Component.literal(line), 96, 0xFFFFFFFF);
			if (backupDir != null) {
				text(graphics, trim(Component.translatable("modsync.result.backup", backupDir.toString()).getString(), width - 60),
						30, 122, 0xFF9A9A9A);
			}
			graphics.textWithWordWrap(font, Component.translatable("modsync.result.restart"),
					30, 156, width - 60, 0xFFFFD27F);
		} else {
			graphics.textWithWordWrap(font, Component.literal(error),
					30, 92, width - 60, 0xFFFF8080);
			graphics.textWithWordWrap(font, Component.translatable("modsync.result.failed_hint"),
					30, 150, width - 60, 0xFFB0B0B0);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
	}

	private void openModsFolder() {
		try {
			Path mods = FabricLoader.getInstance().getGameDir().resolve("mods");
			Files.createDirectories(mods);
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(mods.toFile());
			}
		} catch (Exception e) {
			ModSync.LOGGER.warn("Could not open the mods folder", e);
		}
	}
}
