package com.custombg.client;

import java.util.List;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class CustomBackgroundClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Кнопка «Фон» справа снизу на главном меню.
		ScreenEvents.AFTER_INIT.register((Minecraft client, Screen screen, int width, int height) -> {
			if (screen instanceof TitleScreen) {
				List<AbstractWidget> widgets = Screens.getWidgets(screen);
				widgets.add(Button.builder(Component.translatable("custombg.button"),
								button -> client.setScreen(new BackgroundSelectScreen(screen, 0)))
						.pos(width - 84, height - 34)
						.size(80, 20)
						.build());
			}
		});
	}
}
