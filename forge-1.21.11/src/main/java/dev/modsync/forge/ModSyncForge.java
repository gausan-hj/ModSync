package dev.modsync.forge;

import dev.modsync.forge.server.SyncCommands;
import dev.modsync.forge.server.SyncServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ModSyncForge.MOD_ID)
public final class ModSyncForge {
	public static final String MOD_ID = "modsync";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public ModSyncForge() {
		ModSyncConfig.get();
		LOGGER.info("ModSync Forge loading");
	}

	@Mod.EventBusSubscriber(modid = MOD_ID)
	public static final class Events {
		@SubscribeEvent
		public static void onServerStarted(ServerStartedEvent event) {
			SyncServer.start(event.getServer());
		}

		@SubscribeEvent
		public static void onServerStopping(ServerStoppingEvent event) {
			SyncServer.stop();
		}

		@SubscribeEvent
		public static void onRegisterCommands(RegisterCommandsEvent event) {
			SyncCommands.register(event.getDispatcher());
		}
	}
}
