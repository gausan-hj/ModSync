package dev.modsync;

import dev.modsync.server.SyncCommands;
import dev.modsync.server.SyncServer;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ModSync implements ModInitializer {
	public static final String MOD_ID = "modsync";
	public static final String VERSION = "1.3.0";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		ModSyncConfig.get();
		SyncServer.init();
		SyncCommands.init();
		LOGGER.info("ModSync ready; server sharing={}, client downloads require confirmation", ModSyncConfig.get().server.enabled);
	}
}
