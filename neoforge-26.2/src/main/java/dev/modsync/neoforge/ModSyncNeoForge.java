package dev.modsync.neoforge;

import dev.modsync.neoforge.server.SyncCommands;
import dev.modsync.neoforge.server.SyncServer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ModSyncNeoForge.MOD_ID)
public final class ModSyncNeoForge {
	public static final String MOD_ID = "modsync";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public ModSyncNeoForge() {
		ModSyncConfig.get();
		LOGGER.info("ModSync NeoForge loading");
	}

	@EventBusSubscriber(modid = MOD_ID)
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
