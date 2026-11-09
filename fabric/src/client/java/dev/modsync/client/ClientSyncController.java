package dev.modsync.client;

import dev.modsync.ModSync;
import dev.modsync.ModSyncConfig;
import dev.modsync.manifest.SyncManifest;
import dev.modsync.net.SyncPayload;
import dev.modsync.plan.SyncPlan;
import dev.modsync.plan.SyncPlanner;
import dev.modsync.protocol.Message;
import dev.modsync.protocol.Protocol;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.context.PacketContext;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;

public final class ClientSyncController {
	private static volatile ClientSession session;
	private static SyncedMods.Entry cleanupEntry;
	private static String cleanupKey;
	private static int cleanupTicks;
	private static String joinedServerKey;

	private ClientSyncController() {
	}

	public static void init() {
		ClientConfigurationNetworking.registerGlobalReceiver(SyncPayload.TYPE, (payload, context) -> {
			Message message;
			try {
				message = Protocol.decode(payload.data());
			} catch (Protocol.ProtocolException e) {
				ModSync.LOGGER.warn("Ignoring malformed ModSync frame", e);
				return;
			}

			if (message instanceof Message.Manifest manifest) {
				onManifest(manifest, context);
			} else {
				ClientSession current = session;
				if (current != null) {
					current.handle(message);
				}
			}
		});

		ClientConfigurationConnectionEvents.DISCONNECT.register((listener, client) -> {
			ClientSession current = session;
			if (current != null) {
				current.onDisconnected();
				if (current.phase().terminal()) {
					clear(current);
				}
			}
		});

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			joinedServerKey = SyncedMods.currentServerKey(client);
			SyncedMods.activate(joinedServerKey);
		});

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			String key = joinedServerKey != null ? joinedServerKey : SyncedMods.currentServerKey(client);
			joinedServerKey = null;
			SyncedMods.Entry entry = SyncedMods.active(key);
			if (entry != null) {
				cleanupEntry = entry;
				cleanupKey = key;
				cleanupTicks = 5;
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			ClientSession current = session;
			if (current != null) {
				current.tick();
			}
			if (cleanupTicks > 0 && --cleanupTicks == 0 && cleanupEntry != null) {
				SyncedMods.Entry entry = cleanupEntry;
				String key = cleanupKey;
				cleanupEntry = null;
				cleanupKey = null;
				ModSync.LOGGER.info("Showing ModSync cleanup for {}", key);
				client.setScreenAndShow(new CleanupScreen(entry, key));
			}
		});
	}

	private static void onManifest(Message.Manifest payload, ClientConfigurationNetworking.Context context) {
		Minecraft client = context.client();
		Connection connection = context.packetContext().orElseThrow(PacketContext.CONNECTION);

		SyncManifest manifest;
		try {
			manifest = SyncManifest.fromJsonBytes(payload.json());
		} catch (Protocol.ProtocolException e) {
			ModSync.LOGGER.warn("Server sent an invalid manifest", e);
			connection.disconnect(Component.translatable("modsync.error.manifest"));
			return;
		}

		if (!manifest.compatible()) {
			connection.disconnect(Component.translatable("modsync.error.protocol"));
			return;
		}

		if (!ModSyncConfig.get().client.enabled) {
			send(connection, new Message.Ready(manifest.nonce()));
			return;
		}

		SyncPlan plan = SyncPlanner.plan(manifest, LocalMods.collect(), LocalFiles.collect());
		ModSync.LOGGER.info("ModSync manifest from {}: {} file(s), {} download(s), {} byte(s)",
				manifest.serverName(), manifest.files().size(), plan.requiredDownloads(), plan.requiredBytes());
		if (!plan.hasWork()) {
			ModSync.LOGGER.info("ModSync has no work; sending Ready");
			send(connection, new Message.Ready(manifest.nonce()));
			return;
		}

		ClientSession newSession = new ClientSession(client, connection, manifest, plan);
		session = newSession;

		if (plan.requiredBytes() > ModSyncConfig.get().client.maxTotalBytes()) {
			newSession.fail("This sync is larger than the client's configured limit.");
			return;
		}

		newSession.sendNeeded();
		client.execute(() -> client.setScreenAndShow(new SyncScreen(newSession)));
	}

	private static void send(Connection connection, Message message) {
		try {
			ClientConfigurationNetworking.send(new SyncPayload(Protocol.encode(message)));
		} catch (Exception e) {
			ModSync.LOGGER.warn("Could not send ModSync frame {}", message.type(), e);
			connection.disconnect(Component.translatable("modsync.error.protocol"));
		}
	}

	static void clear(ClientSession expected) {
		if (session == expected) {
			session = null;
		}
	}

	public static ClientSession session() {
		return session;
	}
}
