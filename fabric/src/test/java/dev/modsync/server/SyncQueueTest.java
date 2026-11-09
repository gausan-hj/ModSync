package dev.modsync.server;

import net.minecraft.network.protocol.Packet;
import net.minecraft.server.network.ConfigurationTask;
import org.junit.jupiter.api.Test;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SyncQueueTest {
	private static final class DummyTask implements ConfigurationTask {
		private final Type type;

		private DummyTask(String id) {
			this.type = new Type(id);
		}

		@Override
		public void start(Consumer<Packet<?>> sender) {
		}

		@Override
		public boolean tick() {
			return false;
		}

		@Override
		public Type type() {
			return type;
		}
	}

	@Test
	void prependMovesModSyncAheadOfRegistrySyncInConcurrentQueue() {
		// This is the queue implementation used by Minecraft 26.3 (it is not a Deque).
		Queue<ConfigurationTask> queue = new ConcurrentLinkedQueue<>();
		ConfigurationTask registrySync = new DummyTask("minecraft:registry_sync");
		ConfigurationTask another = new DummyTask("minecraft:another");
		queue.add(registrySync);
		queue.add(another);

		ConfigurationTask modsync = new DummyTask("modsync:sync");
		SyncServer.prepend(queue, modsync);

		assertSame(modsync, queue.poll());
		assertSame(registrySync, queue.poll());
		assertSame(another, queue.poll());
		assertEquals(0, queue.size());
	}

	@Test
	void prependKeepsAnEmptyQueueValid() {
		Queue<ConfigurationTask> queue = new ConcurrentLinkedQueue<>();
		ConfigurationTask modsync = new DummyTask("modsync:sync");

		SyncServer.prepend(queue, modsync);

		assertSame(modsync, queue.poll());
		assertEquals(0, queue.size());
	}
}
