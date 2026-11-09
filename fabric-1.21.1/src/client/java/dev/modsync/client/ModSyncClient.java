package dev.modsync.client;

import net.fabricmc.api.ClientModInitializer;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ModSyncClient implements ClientModInitializer {
	public static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "ModSync-io");
		thread.setDaemon(true);
		return thread;
	});
	@Override
	public void onInitializeClient() {
		LanguageToggle.init();
		ClientSyncController.init();
	}
}
