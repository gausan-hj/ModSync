package dev.modsync.client;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

/**
 * Adds a language selection button to the title screen. The list matches the
 * translation files shipped with the mod.
 */
public final class LanguageToggle {
	private LanguageToggle() {
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
			if (!(screen instanceof TitleScreen)) {
				return;
			}
			Button button = Button.builder(label(client), pressed ->
							client.setScreen(new LanguageSelectScreen(screen)))
					.bounds(screen.width - 110, 8, 100, 20)
					.build();
			@SuppressWarnings("unchecked")
							var widgets = (java.util.List<net.minecraft.client.gui.components.events.GuiEventListener>) screen.children();
							widgets.add(button);
		});
	}

	private static Component label(Minecraft client) {
		return Component.literal(ModLanguages.currentName(client.options.languageCode));
	}
}
