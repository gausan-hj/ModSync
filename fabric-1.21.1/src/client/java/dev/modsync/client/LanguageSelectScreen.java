package dev.modsync.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/** A grid of every language shipped by ModSync. */
final class LanguageSelectScreen extends ModSyncScreen {
	private static final int COLUMNS = 2;
	private static final int ROWS = 8;
	private final Screen parent;

	LanguageSelectScreen(Screen parent) {
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
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		centered(graphics, Component.translatable("modsync.language.title"), 20, 0xFFFFFFFF);
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

	private void choose(String code) {
		Minecraft client = Minecraft.getInstance();
		client.getLanguageManager().setSelected(code);
		client.options.languageCode = code;
		client.options.save();
		client.reloadResourcePacks();
		client.setScreen(new TitleScreen());
	}
}
