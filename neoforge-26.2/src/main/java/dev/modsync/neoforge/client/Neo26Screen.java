package dev.modsync.neoforge.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;

/** Shared drawing helpers for the 26.x screen API. */
abstract class Neo26Screen extends Screen {
	protected Neo26Screen(Component title) {
		super(title);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.fill(14, 14, width - 14, height - 14, 0xFF171717);
		graphics.outline(14, 14, width - 14, height - 14, 0xFF3C3C3C);
	}

	protected void text(GuiGraphicsExtractor graphics, String value, int x, int y, int color) {
		graphics.text(font, value, x, y, color, false);
	}

	protected void centered(GuiGraphicsExtractor graphics, String value, int y, int color) {
		graphics.centeredText(font, Component.literal(value), width / 2, y, color);
	}

	protected void centered(GuiGraphicsExtractor graphics, Component value, int y, int color) {
		graphics.centeredText(font, value, width / 2, y, color);
	}

	protected void drawCenteredString(GuiGraphicsExtractor graphics, Font font, String value, int centerX, int y, int color) {
		graphics.centeredText(font, Component.literal(value == null ? "" : value), centerX, y, color);
	}

	protected void drawString(GuiGraphicsExtractor graphics, Font font, String value, int x, int y, int color, boolean shadow) {
		graphics.text(font, value == null ? "" : value, x, y, color, shadow);
	}

	protected void drawString(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence value, int x, int y, int color, boolean shadow) {
		graphics.text(font, value, x, y, color, shadow);
	}

	protected void centered(GuiGraphicsExtractor graphics, String value, int centerX, int y, int color) {
		graphics.centeredText(font, Component.literal(value == null ? "" : value), centerX, y, color);
	}

	protected void centered(GuiGraphicsExtractor graphics, Component value, int centerX, int y, int color) {
		graphics.centeredText(font, value, centerX, y, color);
	}
}
