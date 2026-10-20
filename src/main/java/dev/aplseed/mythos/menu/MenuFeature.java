package dev.aplseed.mythos.menu;

import dev.aplseed.mythos.ui.Panel;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Something that adds information to particular Hypixel menus. */
public interface MenuFeature {
	boolean enabled();

	/** Decided once per menu, from its title. */
	boolean matches(String title);

	/** The menu's contents changed (including the first time they arrive). */
	default void update(Menu menu) {}

	/** Slot highlights/badges go on `g`; text goes in the shared side panel. */
	void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel);

	default void closed() {}
}
