package dev.modsync.client;

import dev.modsync.util.Human;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

public final class SyncProgressScreen extends ModSyncScreen {
	private final ClientSession session;

	public SyncProgressScreen(ClientSession session) {
		super(Component.translatable("modsync.progress.title"));
		this.session = session;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(
						Component.translatable("modsync.cancel"),
						button -> session.cancel())
				.bounds(width / 2 - 60, height - 42, 120, 20)
				.build());
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		centered(graphics, Component.translatable("modsync.progress.title"), 30, 0xFFFFFFFF);
		centered(graphics, Component.literal(trim(session.serverName(), width - 80)), 48, 0xFFB0B0B0);

		String file = session.currentFile().isBlank()
				? Component.translatable("modsync.progress.waiting").getString()
				: session.currentFile();
		text(graphics, trim(file, width - 80), 40, 80, 0xFFE8E8E8);

		long received = session.receivedBytes();
		long total = Math.max(1, session.totalBytes());
		int barX = 40;
		int barY = 110;
		int barWidth = width - 80;
		int barHeight = 14;
		graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + barHeight + 1, 0xFF3A3A3A);
		graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF242424);
		int filled = (int) Math.min(barWidth, received * barWidth / total);
		if (filled > 0) {
			graphics.fill(barX, barY, barX + filled, barY + barHeight, 0xFF55C878);
		}

		centered(graphics, Component.literal(Human.percent(received, total) + "  " + Human.bytes(received) + " / " + Human.bytes(total)), 132, 0xFFFFFFFF);

		long elapsed = session.elapsedMillis();
		long speed = elapsed <= 0 ? 0 : received * 1000L / elapsed;
		String stats = Component.translatable("modsync.progress.files",
				session.completedFiles(),
				session.expectedFiles(),
				Human.bytes(speed) + "/s").getString();
		centered(graphics, Component.literal(stats), 152, 0xFFB0B0B0);

		String hint = session.currentFile().isBlank()
				? Component.translatable("modsync.progress.connecting").getString()
				: Component.translatable("modsync.progress.hint").getString();
		centered(graphics, Component.literal(hint), 178, 0xFF9A9A9A);

		if (session.currentFileSize() > 0) {
			String current = Human.bytes(session.currentFileReceived()) + " / " + Human.bytes(session.currentFileSize());
			centered(graphics, Component.literal(current), 196, 0xFF9A9A9A);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
	}
}
