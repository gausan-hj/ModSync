package dev.modsync.mixin;

import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Queue;

/** Exposes the early configuration task queue so ModSync can run before Fabric registry sync. */
@Mixin(ServerConfigurationPacketListenerImpl.class)
public interface ServerConfigurationPacketListenerAccessor {
	@Accessor("configurationTasks")
	Queue<ConfigurationTask> modsync$getConfigurationTasks();

}
