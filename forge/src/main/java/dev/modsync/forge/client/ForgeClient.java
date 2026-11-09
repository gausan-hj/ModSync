package dev.modsync.forge.client;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ForgeClient {
	public static final ExecutorService IO = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "ModSync-forge-io");
		thread.setDaemon(true);
		return thread;
	});

	private ForgeClient() {
	}
}
