package dev.modsync.neoforge.client;

import dev.modsync.util.Human;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

public final class ForgeProgressScreen extends Neo26Screen {
	private final Screen parent;
	private final ForgeDownloadJob job;
	private final ServerData server;

	public ForgeProgressScreen(Screen parent, ForgeDownloadJob job, ServerData server) {
		super(Component.translatable("modsync.name"));
		this.parent = parent;
		this.job = job;
		this.server = server;
	}

	@Override
	protected void init() {
		addRenderableWidget(Button.builder(Component.translatable("modsync.cancel"), b -> {
			job.cancel();
			minecraft.setScreenAndShow(parent);
		}).bounds(width / 2 - 50, height - 38, 100, 20).build());
	}

	@Override
	public void tick() {
		if (job.phase() == ForgeDownloadJob.Phase.DONE) {
			minecraft.setScreenAndShow(new ForgeResultScreen(true, job.installed(), job.backupDir(), "", server, parent));
		} else if (job.phase() == ForgeDownloadJob.Phase.FAILED) {
			minecraft.setScreenAndShow(new ForgeResultScreen(false, 0, null, job.error(), server, parent));
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.fill(14, 14, width - 14, height - 14, 0xFF171717);
		centered(graphics, trs("modsync.forge.downloading"), width / 2, 30, 0xFFFFFFFF);
		String serverText = (server.name == null || server.name.isBlank() ? "" : server.name + "  ") + server.ip;
		centered(graphics, trim(serverText, width - 60), width / 2, 48, 0xFFB0B0B0);

		String file = job.currentFile().isBlank() ? trs("modsync.forge.preparing") : job.currentFile();
		text(graphics, trim(file, width - 80), 40, 82, 0xFFE8E8E8);

		long received = job.received();
		long total = Math.max(1, job.total());
		int barX = 40;
		int barY = 110;
		int barWidth = width - 80;
		graphics.fill(barX - 1, barY - 1, barX + barWidth + 1, barY + 15, 0xFF3A3A3A);
		graphics.fill(barX, barY, barX + barWidth, barY + 14, 0xFF242424);
		int filled = (int) Math.min(barWidth, received * barWidth / total);
		if (filled > 0) {
			graphics.fill(barX, barY, barX + filled, barY + 14, 0xFF55C878);
		}
		drawCenteredString(graphics, font, Human.percent(received, total) + "  " + Human.bytes(received) + " / " + Human.bytes(total),
				width / 2, 132, 0xFFFFFFFF);
		drawCenteredString(graphics, font, trs("modsync.forge.files", job.completedFiles(), Math.max(1, job.expectedFiles()),
				Human.bytes(received * 1000L / job.elapsedMillis()) + "/s"), width / 2, 152, 0xFFB0B0B0);

		if (job.currentSize() > 0) {
			drawCenteredString(graphics, font, Human.bytes(job.currentReceived()) + " / " + Human.bytes(job.currentSize()),
					width / 2, 172, 0xFF9A9A9A);
		}
		centered(graphics, trs("modsync.forge.keep_open"), width / 2, 198, 0xFFFFD27F);

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static String trs(String key, Object... args) {
		return Component.translatable(key, args).getString();
	}

	private String trim(String text, int maxWidth) {
		return font.plainSubstrByWidth(text == null ? "" : text, Math.max(20, maxWidth));
	}
}
