package dev.modsync.neoforge.client;

import dev.modsync.neoforge.ModSyncNeoForge;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.loading.FMLPaths;

import java.awt.Desktop;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ForgeResultScreen extends Neo26Screen {
	private final boolean success;
	private final int installed;
	private final Path backupDir;
	private final String error;
	private final ServerData server;
	private final Screen parent;

	public ForgeResultScreen(boolean success, int installed, Path backupDir, String error, ServerData server, Screen parent) {
		super(Component.translatable("modsync.name"));
		this.success = success;
		this.installed = installed;
		this.backupDir = backupDir;
		this.error = error == null ? "" : error;
		this.server = server;
		this.parent = parent;
	}

	@Override
	protected void init() {
		int y = height - 38;
		int center = width / 2;
		if (success) {
			addRenderableWidget(Button.builder(Component.translatable("modsync.quit"), b -> minecraft.stop())
					.bounds(center - 155, y, 150, 20).build());
		} else {
			addRenderableWidget(Button.builder(Component.translatable("modsync.forge.back"), b -> minecraft.setScreenAndShow(parent))
					.bounds(center - 155, y, 150, 20).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("modsync.result.open_folder"), b -> openModsFolder())
				.bounds(center + 5, y, 150, 20).build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.fill(14, 14, width - 14, height - 14, 0xFF171717);
		drawCenteredString(graphics, font, trs(success ? "modsync.result.success" : "modsync.result.failed"), width / 2, 40,
				success ? 0xFF55FF55 : 0xFFFF5555);
		String serverText = (server.name == null || server.name.isBlank() ? "" : server.name + "  ") + server.ip;
		centered(graphics, trim(serverText, width - 60), width / 2, 60, 0xFFB0B0B0);

		if (success) {
			centered(graphics, trs("modsync.forge.installed", installed), width / 2, 96, 0xFFFFFFFF);
			if (backupDir != null) {
				text(graphics, trim(trs("modsync.forge.backup", backupDir), width - 60), 30, 122, 0xFF9A9A9A);
			}
			centered(graphics, trs("modsync.result.restart"), width / 2, 160, 0xFFFFD27F);
		} else {
			centered(graphics, trim(error, width - 60), width / 2, 96, 0xFFFF8080);
			centered(graphics, trs("modsync.forge.failed_hint"), width / 2, 124, 0xFFB0B0B0);
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

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static String trs(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private void openModsFolder() {
		try {
			Path mods = FMLPaths.GAMEDIR.get().resolve("mods");
			Files.createDirectories(mods);
			if (Desktop.isDesktopSupported()) {
				Desktop.getDesktop().open(mods.toFile());
			}
		} catch (Exception e) {
			ModSyncNeoForge.LOGGER.warn("Could not open mods folder", e);
		}
	}

	private String trim(String text, int maxWidth) {
		return font.plainSubstrByWidth(text == null ? "" : text, Math.max(20, maxWidth));
	}
}
