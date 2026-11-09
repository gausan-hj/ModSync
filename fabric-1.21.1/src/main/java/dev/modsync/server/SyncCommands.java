package dev.modsync.server;

import com.mojang.brigadier.CommandDispatcher;
import dev.modsync.ModSyncConfig;
import dev.modsync.manifest.SyncManifest;
import dev.modsync.util.Human;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Small operator-facing command surface. */
public final class SyncCommands {
	private SyncCommands() {
	}

	public static void init() {
		CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("modsync")
				.then(Commands.literal("status").executes(SyncCommands::status))
				.then(Commands.literal("list").executes(SyncCommands::list))
				.then(Commands.literal("reload")
						.requires(source -> source.hasPermission(2))
						.executes(SyncCommands::reload)));
	}

	private static int status(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		ModSyncConfig.Server config = ModSyncConfig.get().server;
		SyncManifest manifest = SyncServer.manifest();
		String message;
		if (!config.enabled) {
			message = "ModSync sharing is disabled.";
		} else if (manifest == null) {
			message = "ModSync is enabled, but the manifest is not ready yet.";
		} else {
			message = "ModSync " + dev.modsync.ModSync.VERSION + ": " + manifest.files().size() + " jar(s), " + Human.bytes(manifest.totalBytes())
					+ ", idle timeout " + config.idleTimeoutSeconds + "s.";
		}
		source.sendSuccess(() -> Component.literal(message), false);
		return 1;
	}

	private static int list(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		SyncManifest manifest = SyncServer.manifest();
		if (manifest == null || manifest.files().isEmpty()) {
			source.sendSuccess(() -> Component.literal("ModSync has nothing to share."), false);
			return 0;
		}
		List<String> names = manifest.files().stream().map(SyncManifest.ManifestFile::file).toList();
		int limit = Math.min(20, names.size());
		String joined = String.join(", ", names.subList(0, limit));
		if (names.size() > limit) {
			joined += " ... (+" + (names.size() - limit) + ")";
		}
		String message = joined;
		source.sendSuccess(() -> Component.literal(message), false);
		return names.size();
	}

	private static int reload(com.mojang.brigadier.context.CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		SyncManifest manifest = SyncServer.refreshManifest(source.getServer());
		String message = "ModSync manifest refreshed: " + manifest.files().size() + " jar(s), "
				+ Human.bytes(manifest.totalBytes()) + ".";
		source.sendSuccess(() -> Component.literal(message), true);
		return manifest.files().size();
	}
}
