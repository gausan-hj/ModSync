package dev.modsync.neoforge.client;

import dev.modsync.util.Human;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Lists saved multiplayer servers so the user can sync one before joining. */
public final class ServerPickerScreen extends Neo26Screen {
	private static final int ROW_HEIGHT = 24;
	private final Screen parent;
	private final ServerList servers;
	private int page;
	private int pageSize = 6;
	private int selected = -1;

	public ServerPickerScreen(Screen parent, ServerList servers) {
		super(Component.translatable("modsync.name"));
		this.parent = parent;
		this.servers = servers;
	}

	@Override
	protected void init() {
		pageSize = Math.max(3, Math.min(8, (height - 130) / ROW_HEIGHT));
		int count = servers.size();
		int pages = Math.max(1, (count + pageSize - 1) / pageSize);
		if (page >= pages) {
			page = pages - 1;
		}
		if (selected >= count) {
			selected = count - 1;
		}

		int y = height - 38;
		addRenderableWidget(Button.builder(Component.translatable("modsync.forge.sync_selected"), button -> {
					if (selected >= 0 && selected < servers.size()) {
						ServerData data = servers.get(selected);
						minecraft.setScreenAndShow(new ForgeSyncScreen(this, data));
					}
				}).bounds(width / 2 - 120, y, 150, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("modsync.forge.close"), button -> onClose())
				.bounds(width / 2 + 40, y, 80, 20).build());

		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), button -> {
						page = Math.max(0, page - 1);
						rebuildWidgets();
					}).bounds(20, y, 40, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), button -> {
						page = Math.min(pages - 1, page + 1);
						rebuildWidgets();
					}).bounds(width - 60, y, 40, 20).build());
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		graphics.fill(0, 0, width, height, 0xE0101010);
		graphics.fill(14, 14, width - 14, height - 14, 0xFF171717);
		centered(graphics, trs("modsync.forge.select_title"), width / 2, 24, 0xFFFFFFFF);
		centered(graphics, trs("modsync.forge.select_hint"), width / 2, 42, 0xFFB0B0B0);

		int count = servers.size();
		if (count == 0) {
			centered(graphics, trs("modsync.forge.no_servers"), width / 2, 90, 0xFFFFAA00);
		} else {
			int startY = 66;
			int end = Math.min(count, (page + 1) * pageSize);
			for (int i = page * pageSize; i < end; i++) {
				ServerData data = servers.get(i);
				int rowY = startY + (i - page * pageSize) * ROW_HEIGHT;
				if (i == selected) {
					graphics.fill(28, rowY - 3, width - 28, rowY + 19, 0xFF2C4A3A);
				}
				String name = data.name == null || data.name.isBlank() ? data.ip : data.name;
				text(graphics, trim(name, width - 80), 38, rowY, 0xFFFFFFFF);
				text(graphics, trim(data.ip, width - 80), 38, rowY + 10, 0xFF9A9A9A);
			}
			if (count > pageSize) {
				int pages = (count + pageSize - 1) / pageSize;
				centered(graphics, (page + 1) + " / " + pages, width / 2, height - 58, 0xFF9A9A9A);
			}
			if (selected >= 0 && selected < count) {
				ServerData data = servers.get(selected);
				centered(graphics, trs("modsync.forge.selected", data.name, data.ip), width / 2, height - 74, 0xFF55FF55);
			}
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (event.button() == 0) {
			int count = servers.size();
			int startY = 66;
			for (int i = page * pageSize; i < Math.min(count, (page + 1) * pageSize); i++) {
				int rowY = startY + (i - page * pageSize) * ROW_HEIGHT;
				if (mouseX >= 28 && mouseX <= width - 28 && mouseY >= rowY - 3 && mouseY <= rowY + 19) {
					selected = i;
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return true;
	}

	@Override
	public void onClose() {
		minecraft.setScreenAndShow(parent);
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
