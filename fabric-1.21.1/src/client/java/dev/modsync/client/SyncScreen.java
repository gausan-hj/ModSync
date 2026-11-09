package dev.modsync.client;

import dev.modsync.plan.FileDecision;
import dev.modsync.plan.SyncPlan;
import dev.modsync.util.Human;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Review screen: what the host has, what is missing, and the explicit download button. */
public final class SyncScreen extends ModSyncScreen {
	private static final int ROW_HEIGHT = 20;

	private final ClientSession session;
	private final SyncPlan plan;
	private int page;
	private int pageSize = 6;
	private Button downloadButton;

	public SyncScreen(ClientSession session) {
		super(Component.translatable("modsync.name"));
		this.session = session;
		this.plan = session.plan();
	}

	@Override
	protected void init() {
		pageSize = Math.max(2, Math.min(8, (height - 200) / ROW_HEIGHT));
		int pageCount = pageCount();
		if (page >= pageCount) {
			page = Math.max(0, pageCount - 1);
		}

		int buttonY = height - 42;
		int center = width / 2;
		downloadButton = addRenderableWidget(Button.builder(
						Component.translatable("modsync.download"),
						button -> {
							if (session.requestDownload()) {
								minecraft.setScreen(new SyncProgressScreen(session));
							}
						})
				.bounds(center - 140, buttonY, 170, 20)
				.build());
		downloadButton.active = plan.requiredDownloads() > 0;

		addRenderableWidget(Button.builder(
						Component.translatable("modsync.cancel"),
						button -> session.cancel())
				.bounds(center + 38, buttonY, 102, 20)
				.build());

		if (pageCount > 1) {
			addRenderableWidget(Button.builder(
							Component.literal("<"),
							button -> {
								page = Math.max(0, page - 1);
								rebuildWidgets();
							})
					.bounds(24, buttonY, 40, 20)
					.build());
			addRenderableWidget(Button.builder(
							Component.literal(">"),
							button -> {
								page = Math.min(pageCount() - 1, page + 1);
								rebuildWidgets();
							})
					.bounds(width - 64, buttonY, 40, 20)
					.build());
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
		centered(graphics, Component.translatable("modsync.review.title"), 24, 0xFFFFFFFF);
		centered(graphics, Component.literal(trim(session.serverName(), width - 80)), 42, 0xFFB0B0B0);

		String summary = Component.translatable("modsync.review.summary",
				plan.requiredDownloads(),
				Human.bytes(plan.requiredBytes())).getString();
		centered(graphics, Component.literal(summary), 60, 0xFFFFD27F);

		List<FileDecision> decisions = plan.decisions();
		int startY = 84;
		int end = Math.min(decisions.size(), (page + 1) * pageSize);
		for (int i = page * pageSize; i < end; i++) {
			FileDecision decision = decisions.get(i);
			int y = startY + (i - page * pageSize) * ROW_HEIGHT;
			int color = statusColor(decision.status());
			graphics.fill(30, y + 5, 35, y + 10, color);

			String label = decision.file().displayName();
			String version = decision.file().versions();
			if (!version.isBlank()) {
				label += "  " + version;
			}
			String trimmed = trim(label, width - 210);
			text(graphics, trimmed, 42, y, 0xFFE8E8E8);

			String size = Human.bytes(decision.file().size());
			text(graphics, size, width - 168, y, 0xFF9A9A9A);
			String status = Component.translatable(statusKey(decision.status())).getString();
			int statusWidth = font.width(status);
			text(graphics, status, width - 30 - statusWidth, y, color);
		}

		if (pageCount() > 1) {
			String pageLabel = (page + 1) + " / " + pageCount();
			centered(graphics, Component.literal(pageLabel), startY + pageSize * ROW_HEIGHT + 2, 0xFF9A9A9A);
		}

		int warningY = Math.max(startY + pageSize * ROW_HEIGHT + 18, height - 92);
		graphics.drawWordWrap(font, Component.translatable("modsync.consent"),
				30, warningY, width - 60, 0xFFFFAA00);

		if (!plan.extraLocalMods().isEmpty()) {
			String extras = Component.translatable("modsync.review.extras", plan.extraLocalMods().size()).getString();
			text(graphics, trim(extras, width - 60), 30, height - 64, 0xFFB0B0B0);
		}

		super.render(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void onClose() {
		session.cancel();
	}

	private int pageCount() {
		return Math.max(1, (plan.decisions().size() + pageSize - 1) / pageSize);
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

	private static String statusKey(FileDecision.Status status) {
		return switch (status) {
			case OK -> "modsync.status.ok";
			case MISSING -> "modsync.status.missing";
			case VERSION_MISMATCH -> "modsync.status.mismatch";
			case MIXED -> "modsync.status.mixed";
			case OPTIONAL_MISSING -> "modsync.status.optional";
		};
	}
}
