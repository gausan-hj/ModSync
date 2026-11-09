package dev.modsync.neoforge.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** A grid of every language shipped by ModSync. */
public final class LanguageSelectScreen extends Screen {
	private static final int COLUMNS = 2;
	private static final int ROWS = 8;
	private final Screen parent;

	public LanguageSelectScreen(Screen parent) {
		super(Component.translatable("modsync.language.title"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		int gap = 4;
		int buttonHeight = 18;
		int buttonWidth = Math.min(230, (width - 60 - gap) / COLUMNS);
		int totalWidth = COLUMNS * buttonWidth + gap;
		int startX = (width - totalWidth) / 2;
		int startY = Math.max(34, (height - ROWS * (buttonHeight + gap) - 30) / 2);
		String current = minecraft.options.languageCode;

		for (int i = 0; i < ModLanguages.CODES.length; i++) {
			String code = ModLanguages.CODES[i];
			String label = ModLanguages.NAMES[i];
			if (code.equals(current)) {
				label = "✓ " + label;
			}
			int x = startX + (i % COLUMNS) * (buttonWidth + gap);
			int y = startY + (i / COLUMNS) * (buttonHeight + gap);
			addRenderableWidget(Button.builder(Component.literal(label), button -> choose(code))
					.bounds(x, y, buttonWidth, buttonHeight)
					.build());
		}

		addRenderableWidget(Button.builder(Component.translatable("modsync.forge.close"), button -> onClose())
				.bounds(width / 2 - 60, height - 30, 120, 20)
				.build());
	}

	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		// Each screen paints its own panel in render(); calling super here would
		// apply 1.21.1's menu blur and then cover the custom content.
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.fill(14, 14, width - 14, height - 14, 0xFF171717);
		graphics.fill(14, 14, width - 14, 15, 0xFF3C3C3C);
		graphics.fill(14, height - 15, width - 14, height - 14, 0xFF3C3C3C);
		graphics.drawCenteredString(font, Component.translatable("modsync.language.title"), width / 2, 20, 0xFFFFFFFF);
		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void choose(String code) {
		Minecraft client = Minecraft.getInstance();
		client.getLanguageManager().setSelected(code);
		client.options.languageCode = code;
		client.options.save();
		client.reloadResourcePacks();
		client.setScreen(new TitleScreen());
	}
}
