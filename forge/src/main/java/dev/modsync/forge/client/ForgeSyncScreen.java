package dev.modsync.forge.client;

import dev.modsync.forge.ModSyncConfig;
import dev.modsync.forge.ModSyncForge;
import dev.modsync.forge.net.SyncHttpClient;
import dev.modsync.manifest.SyncManifest.ManifestFile;
import dev.modsync.plan.FileDecision;
import dev.modsync.plan.SyncPlan;
import dev.modsync.plan.SyncPlanner;
import dev.modsync.util.Human;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Pre-join review: fetch the host manifest, compare, and download after consent. */
public final class ForgeSyncScreen extends Screen {
	private static final int ROW_HEIGHT = 20;
	private final Screen parent;
	private final ServerData server;
	private SyncHttpClient.Remote remote;
	private SyncPlan plan;
	private String error = "";
	private boolean loading = true;
	private boolean started;
	private int page;
	private int pageSize = 6;

	public ForgeSyncScreen(Screen parent, ServerData server) {
		super(Component.translatable("modsync.name"));
		this.parent = parent;
		this.server = server;
	}

	@Override
	protected void init() {
		pageSize = Math.max(3, Math.min(8, (height - 170) / ROW_HEIGHT));
		if (!started) {
			started = true;
			load();
		}

		int y = height - 38;
		int center = width / 2;
		if (loading) {
			addRenderableWidget(Button.builder(Component.translatable("modsync.cancel"), b -> onClose())
					.bounds(center - 50, y, 100, 20).build());
		} else if (!error.isBlank()) {
			addRenderableWidget(Button.builder(Component.translatable("modsync.forge.retry"), b -> {
						load();
						rebuildWidgets();
					}).bounds(center - 110, y, 100, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("modsync.forge.close"), b -> onClose())
					.bounds(center + 10, y, 100, 20).build());
		} else if (plan != null) {
			Button download = addRenderableWidget(Button.builder(Component.translatable("modsync.download"), b -> {
						if (plan != null && plan.hasWork() && remote != null) {
							ForgeDownloadJob job = new ForgeDownloadJob(remote, plan, server);
							minecraft.setScreen(new ForgeProgressScreen(this, job, server));
							job.start();
						}
					}).bounds(center - 120, y, 150, 20).build());
			download.active = plan.hasWork();
			addRenderableWidget(Button.builder(Component.translatable(plan.hasWork() ? "modsync.cancel" : "modsync.forge.close"), b -> onClose())
					.bounds(center + 40, y, 80, 20).build());
		}
	}

	private void load() {
		started = true;
		loading = true;
		error = "";
		remote = null;
		plan = null;
		page = 0;
		CompletableFuture.supplyAsync(() -> {
			try {
				return SyncHttpClient.fetch(server);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}, ForgeClient.IO).whenComplete((result, throwable) -> Minecraft.getInstance().execute(() -> {
			loading = false;
			if (throwable != null) {
				Throwable cause = throwable.getCause() == null ? throwable : throwable.getCause();
				error = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
				ModSyncForge.LOGGER.warn("ModSync pre-join fetch failed for {}", server.ip, cause);
			} else {
				remote = result;
				plan = SyncPlanner.plan(result.manifest(), ForgeLocalMods.collect());
				if (plan.requiredBytes() > ModSyncConfig.get().client.maxTotalBytes()) {
					error = trs("modsync.forge.error_size");
					plan = null;
				}
			}
			rebuildWidgets();
		}));
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.fill(14, 14, width - 14, height - 14, 0xFF171717);
		graphics.drawCenteredString(font, trs("modsync.forge.review_title"), width / 2, 24, 0xFFFFFFFF);
		String serverText = (server.name == null || server.name.isBlank() ? "" : server.name + "  ") + server.ip;
		graphics.drawCenteredString(font, trim(serverText, width - 60), width / 2, 42, 0xFFB0B0B0);

		if (loading) {
			graphics.drawCenteredString(font, trs("modsync.forge.loading"), width / 2, 100, 0xFFFFD27F);
			graphics.drawCenteredString(font, trs("modsync.forge.port_hint"), width / 2, 120, 0xFF9A9A9A);
		} else if (!error.isBlank()) {
			drawWrapped(graphics, error, 30, 78, width - 60, 0xFFFF8080);
			drawWrapped(graphics, trs("modsync.forge.host_hint"),
					30, 128, width - 60, 0xFFFFAA00);
		} else if (plan != null) {
			graphics.drawCenteredString(font, trs("modsync.forge.need_download", plan.requiredDownloads(), Human.bytes(plan.requiredBytes())), width / 2, 64, 0xFFFFD27F);

			List<FileDecision> decisions = plan.decisions();
			int startY = 90;
			int end = Math.min(decisions.size(), (page + 1) * pageSize);
			for (int i = page * pageSize; i < end; i++) {
				FileDecision decision = decisions.get(i);
				int rowY = startY + (i - page * pageSize) * ROW_HEIGHT;
				int color = statusColor(decision.status());
				graphics.fill(30, rowY + 5, 35, rowY + 10, color);
				ManifestFile file = decision.file();
				String label = file.displayName() + "  " + file.versions();
				graphics.drawString(font, trim(label, width - 210), 42, rowY, 0xFFE8E8E8, false);
				graphics.drawString(font, Human.bytes(file.size()), width - 168, rowY, 0xFF9A9A9A, false);
				String status = statusText(decision.status());
				graphics.drawString(font, status, width - 30 - font.width(status), rowY, color, false);
			}
			if (decisions.size() > pageSize) {
				int pages = (decisions.size() + pageSize - 1) / pageSize;
				graphics.drawCenteredString(font, (page + 1) + " / " + pages, width / 2,
						startY + pageSize * ROW_HEIGHT + 2, 0xFF9A9A9A);
			}

			int warningY = Math.max(startY + pageSize * ROW_HEIGHT + 20, height - 94);
			drawWrapped(graphics, trs("modsync.consent"),
					30, warningY, width - 60, 0xFFFFAA00);
			if (!plan.extraLocalMods().isEmpty()) {
				String extras = trs("modsync.forge.extras", plan.extraLocalMods().size());
				graphics.drawString(font, trim(extras, width - 60), 30, height - 64, 0xFFB0B0B0, false);
			}
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
		if (plan != null && plan.decisions().size() > pageSize) {
			int pages = (plan.decisions().size() + pageSize - 1) / pageSize;
			if (delta < 0) {
				page = Math.min(pages - 1, page + 1);
			} else if (delta > 0) {
				page = Math.max(0, page - 1);
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, delta);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void drawWrapped(GuiGraphics graphics, String text, int x, int y, int width, int color) {
		List<FormattedCharSequence> lines = font.split(Component.literal(text), width);
		int yy = y;
		for (FormattedCharSequence line : lines) {
			graphics.drawString(font, line, x, yy, color, false);
			yy += 10;
		}
	}

	private String trim(String text, int maxWidth) {
		return font.plainSubstrByWidth(text == null ? "" : text, Math.max(20, maxWidth));
	}

	private static Component tr(String key, Object... args) {
		return Component.translatable(key, args);
	}

	private static String trs(String key, Object... args) {
		return tr(key, args).getString();
	}

	private static int statusColor(FileDecision.Status status) {
		return switch (status) {
			case OK -> 0xFF55FF55;
			case MISSING -> 0xFFFF5555;
			case VERSION_MISMATCH -> 0xFFFFAA00;
			case MIXED -> 0xFFFF8800;
			case OPTIONAL_MISSING -> 0xFF888888;
		};
	}

	private static String statusText(FileDecision.Status status) {
		return switch (status) {
			case OK -> trs("modsync.status.ok");
			case MISSING -> trs("modsync.status.missing");
			case VERSION_MISMATCH -> trs("modsync.status.mismatch");
			case MIXED -> trs("modsync.status.mixed");
			case OPTIONAL_MISSING -> trs("modsync.status.optional");
		};
	}
}
