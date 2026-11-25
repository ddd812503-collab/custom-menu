package com.custombg.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/**
 * Экран выбора фона. Фон за ним меняется сразу при нажатии.
 * Свои картинки: положить в папку custom_backgrounds или перетащить файл в окно игры.
 */
public class BackgroundSelectScreen extends Screen {
	private static final int BUTTON_WIDTH = 150;
	private static final int BUTTON_HEIGHT = 20;
	private static final int GAP = 4;
	private static final int GRID_TOP = 40;

	private final Screen parent;
	private int page;
	private final List<Button> entryButtons = new ArrayList<>();
	private List<Backgrounds.Entry> pageEntries = List.of();

	public BackgroundSelectScreen(Screen parent, int page) {
		super(Component.translatable("custombg.screen.title"));
		this.parent = parent;
		this.page = page;
	}

	@Override
	protected void init() {
		this.entryButtons.clear();
		List<Backgrounds.Entry> all = Backgrounds.entries();

		int left = this.width / 2 - BUTTON_WIDTH - GAP / 2;

		// Заголовок: неактивная кнопка (так не нужен вызов рисования текста).
		Button header = Button.builder(this.title, button -> { })
				.pos(left, 12)
				.size(BUTTON_WIDTH * 2 + GAP, BUTTON_HEIGHT)
				.build();
		header.active = false;
		this.addRenderableWidget(header);

		// Сколько строк влезает: снизу занято 3 ряда кнопок.
		int bottomReserved = 84;
		int rows = Math.max(1, (this.height - GRID_TOP - bottomReserved) / (BUTTON_HEIGHT + GAP));
		int perPage = rows * 2;
		int pageCount = Math.max(1, (all.size() + perPage - 1) / perPage);
		this.page = Math.max(0, Math.min(this.page, pageCount - 1));

		int from = this.page * perPage;
		int to = Math.min(all.size(), from + perPage);
		this.pageEntries = all.subList(from, to);

		for (int i = 0; i < this.pageEntries.size(); i++) {
			Backgrounds.Entry entry = this.pageEntries.get(i);
			int col = i % 2;
			int row = i / 2;
			Button button = Button.builder(label(entry), b -> {
						Backgrounds.select(entry);
						refreshLabels();
					})
					.pos(left + col * (BUTTON_WIDTH + GAP), GRID_TOP + row * (BUTTON_HEIGHT + GAP))
					.size(BUTTON_WIDTH, BUTTON_HEIGHT)
					.build();
			this.entryButtons.add(button);
			this.addRenderableWidget(button);
		}

		// Переключение страниц.
		int navY = this.height - 76;
		Button prev = Button.builder(Component.literal("<"), b -> this.openPage(this.page - 1))
				.pos(this.width / 2 - 70, navY).size(20, BUTTON_HEIGHT).build();
		prev.active = this.page > 0;
		Button label = Button.builder(Component.literal((this.page + 1) + " / " + pageCount), b -> { })
				.pos(this.width / 2 - 48, navY).size(96, BUTTON_HEIGHT).build();
		label.active = false;
		Button next = Button.builder(Component.literal(">"), b -> this.openPage(this.page + 1))
				.pos(this.width / 2 + 50, navY).size(20, BUTTON_HEIGHT).build();
		next.active = this.page < pageCount - 1;
		this.addRenderableWidget(prev);
		this.addRenderableWidget(label);
		this.addRenderableWidget(next);

		// Свои картинки.
		int rowY = this.height - 52;
		this.addRenderableWidget(Button.builder(Component.translatable("custombg.open_folder"), b -> this.openFolder())
				.pos(left, rowY)
				.size(BUTTON_WIDTH, BUTTON_HEIGHT)
				.tooltip(Tooltip.create(Component.translatable("custombg.open_folder.tooltip")))
				.build());
		this.addRenderableWidget(Button.builder(Component.translatable("custombg.refresh"), b -> {
					Backgrounds.refresh();
					this.openPage(this.page);
				})
				.pos(left + BUTTON_WIDTH + GAP, rowY)
				.size(BUTTON_WIDTH, BUTTON_HEIGHT)
				.build());

		this.addRenderableWidget(Button.builder(Component.translatable("custombg.done"), b -> this.onClose())
				.pos(this.width / 2 - 100, this.height - 28)
				.size(200, BUTTON_HEIGHT)
				.build());
	}

	private void openPage(int newPage) {
		this.minecraft.setScreen(new BackgroundSelectScreen(this.parent, newPage));
	}

	private Component label(Backgrounds.Entry entry) {
		if (Backgrounds.isSelected(entry)) {
			return Component.literal("> ").append(entry.name()).withStyle(ChatFormatting.GREEN);
		}
		return entry.name();
	}

	private void refreshLabels() {
		for (int i = 0; i < this.entryButtons.size(); i++) {
			this.entryButtons.get(i).setMessage(label(this.pageEntries.get(i)));
		}
	}

	private void openFolder() {
		Path folder = Backgrounds.customFolder();
		try {
			Files.createDirectories(folder);
		} catch (IOException ignored) {
		}
		Util.getPlatform().openUri(folder.toUri());
	}

	/** Перетащили файл(ы) в окно игры: копируем в папку и сразу выбираем. */
	@Override
	public void onFilesDrop(List<Path> paths) {
		String firstId = null;
		try {
			Files.createDirectories(Backgrounds.customFolder());
			for (Path path : paths) {
				if (!Backgrounds.isImageFile(path)) {
					continue;
				}
				Path target = Backgrounds.customFolder().resolve(path.getFileName().toString());
				Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
				if (firstId == null) {
					firstId = "custom:" + target.getFileName();
				}
			}
		} catch (IOException ignored) {
		}
		if (firstId != null) {
			Backgrounds.refresh();
			Backgrounds.selectById(firstId);
			this.openPage(this.page);
		}
	}

	@Override
	public void onClose() {
		this.minecraft.setScreen(this.parent);
	}
}
