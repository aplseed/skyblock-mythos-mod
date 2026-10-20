package dev.aplseed.mythos.menu;

import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.ui.Panel;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/** Hooks every container screen and hands it to the features whose title matches. */
public final class MenuOverlays {
	private static final int RESCAN_TICKS = 4;
	private static final List<MenuFeature> FEATURES = new ArrayList<>();

	private MenuOverlays() {}

	public static void add(MenuFeature f) {
		FEATURES.add(f);
	}

	public static void init() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof AbstractContainerScreen<?> acs)) return;
			Open open = new Open(acs);
			ScreenEvents.afterTick(screen).register(s -> open.tick());
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> open.render(g, mx, my));
			ScreenEvents.remove(screen).register(s -> open.close());
			// a left click on a panel link runs it instead of reaching the menu
			ScreenMouseEvents.allowMouseClick(screen).register((s, click) ->
					click.button() != 0 || !Mythos.config().overlays || !Panel.click(click.x(), click.y()));
			ScreenKeyboardEvents.afterKeyPress(screen).register((s, key) -> {
				if (Mythos.DUMP_KEY.matches(key)) MenuDump.dump(new Menu(acs));
				else if (Mythos.OVERLAY_KEY.matches(key)) Mythos.toggleOverlays();
			});
		});
	}

	private static final class Open {
		private final AbstractContainerScreen<?> screen;
		private final List<MenuFeature> active = new ArrayList<>();
		private Menu menu;
		private int ticks;

		Open(AbstractContainerScreen<?> screen) {
			this.screen = screen;
			if (!Mythos.active()) return;
			String title = new Menu(screen).title;
			for (MenuFeature f : FEATURES) if (f.enabled() && f.matches(title)) active.add(f);
		}

		void tick() {
			if (active.isEmpty() || ticks++ % RESCAN_TICKS != 0) return;
			Menu now = new Menu(screen);
			if (menu == null || now.signature != menu.signature) {
				menu = now;
				for (MenuFeature f : active) f.update(now);
			}
		}

		void render(GuiGraphicsExtractor g, int mx, int my) {
			if (menu == null || !Mythos.config().overlays) return;
			g.nextStratum();
			Panel panel = new Panel();
			Menu.Entry hovered = menu.at(mx, my);
			for (MenuFeature f : active) f.render(menu, hovered, g, panel);
			Screen s = screen;
			panel.draw(g, Minecraft.getInstance().font, menu.left(), menu.top(), menu.width(), s.width, mx, my);
		}

		void close() {
			Panel.clear();
			for (MenuFeature f : active) f.closed();
		}
	}
}
