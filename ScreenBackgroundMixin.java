package com.custombg.client.mixin;

import com.custombg.client.BackgroundSelectScreen;
import com.custombg.client.Backgrounds;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Заменяет фон всех экранов, открытых вне мира (главное меню, выбор мира,
 * настройки, мультиплеер и т.д.), на выбранную картинку.
 */
@Mixin(Screen.class)
public abstract class ScreenBackgroundMixin {
	@Inject(method = "extractBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
			at = @At("HEAD"), cancellable = true)
	private void custombg$drawCustomBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (Minecraft.getInstance().level != null) {
			return; // в мире фон не трогаем
		}
		Backgrounds.Entry background = Backgrounds.current();
		if (background == null) {
			return; // выбран стандартный фон
		}
		Identifier texture = background.texture();

		int w = graphics.guiWidth();
		int h = graphics.guiHeight();

		// «Cover»: заполняем экран без искажений, лишнее обрезаем по центру.
		// Размер текстуры передаём «виртуальный» 10000x10000: blit нормирует UV по этим числам.
		final int t = 10000;
		double imageAspect = (double) background.width() / background.height();
		double screenAspect = (double) w / h;
		int regionW = t;
		int regionH = t;
		if (screenAspect > imageAspect) {
			regionH = (int) Math.round(t * imageAspect / screenAspect);
		} else {
			regionW = (int) Math.round(t * screenAspect / imageAspect);
		}
		int u = (t - regionW) / 2;
		int v = (t - regionH) / 2;
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, (float) u, (float) v, w, h, regionW, regionH, t, t);

		// Затемнение для читаемости на обычных экранах (не на главном меню и не на экране выбора фона).
		Screen self = (Screen) (Object) this;
		if (!(self instanceof TitleScreen) && !(self instanceof BackgroundSelectScreen)) {
			graphics.fill(0, 0, w, h, 0x80000000);
		}
		ci.cancel();
	}
}
