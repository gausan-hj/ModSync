package dev.modsync.neoforge.client;

import dev.modsync.neoforge.ModSyncNeoForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;

@EventBusSubscriber(modid = ModSyncNeoForge.MOD_ID, value = Dist.CLIENT)
public final class ForgeClientEvents {
	private ForgeClientEvents() {
	}

	@SubscribeEvent
	public static void onScreenInit(ScreenEvent.Init.Post event) {
		if (event.getScreen() instanceof JoinMultiplayerScreen screen) {
			Button button = Button.builder(Component.literal("ModSync"), ignored ->
							Minecraft.getInstance().setScreen(new ServerPickerScreen(screen, screen.getServers())))
					.bounds(screen.width - 110, 8, 100, 20)
					.build();
			event.addListener(button);
		} else if (event.getScreen() instanceof TitleScreen screen) {
			Button button = Button.builder(Component.literal("ModSync"), ignored -> {
						ServerList servers = new ServerList(Minecraft.getInstance());
						servers.load();
						Minecraft.getInstance().setScreen(new ServerPickerScreen(screen, servers));
					})
					.bounds(screen.width - 110, 8, 100, 20)
					.build();
			event.addListener(button);

			Button language = Button.builder(languageLabel(), pressed ->
							Minecraft.getInstance().setScreen(new LanguageSelectScreen(screen)))
					.bounds(screen.width - 220, 8, 100, 20)
					.build();
			event.addListener(language);
		}
	}

	private static Component languageLabel() {
		Minecraft client = Minecraft.getInstance();
		return Component.literal(ModLanguages.currentName(client.options.languageCode));
	}

}
