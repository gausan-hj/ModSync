package dev.modsync.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared dark panel and text helpers for the sync screens. */
abstract class ModSyncScreen extends Screen {
	protected ModSyncScreen(Component title) {
		super(title);
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.fill(14, 14, width - 14, height - 14, 0xFF171717);
		graphics.renderOutline(14, 14, width - 28, height - 28, 0xFF3C3C3C);
	}

	protected void text(GuiGraphics graphics, Component text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color, false);
	}

	protected void text(GuiGraphics graphics, String text, int x, int y, int color) {
		graphics.drawString(font, text, x, y, color, false);
	}

	protected void centered(GuiGraphics graphics, Component text, int y, int color) {
		graphics.drawCenteredString(font, text, width / 2, y, color);
	}

	protected String trim(String value, int maxWidth) {
		return font.plainSubstrByWidth(value, Math.max(20, maxWidth));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
