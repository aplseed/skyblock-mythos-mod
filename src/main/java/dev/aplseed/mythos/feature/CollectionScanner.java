package dev.aplseed.mythos.feature;

import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.acc.Accessories;
import dev.aplseed.mythos.acc.Analysis;
import dev.aplseed.mythos.acc.Prices;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.menu.MenuFeature;
import dev.aplseed.mythos.ui.Panel;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Collection menus ("Farming Collections", "Mining Collections"...): records the tier of
 * every collection shown, so the accessory calculator only recommends crafts whose
 * recipe is unlocked.
 *
 * A collection is an item named with its tier ("Feather IX", "Sugar Cane VI"); one not
 * started yet has no numeral but a "Progress to Feather I" line.
 */
public final class CollectionScanner implements MenuFeature {
	private static final Pattern TIERED = Pattern.compile("^(.+?) ([IVXLCDM]+)$");
	private static final Pattern FIRST = Pattern.compile("Progress to (.+?) I\\b");

	private int read;

	@Override
	public boolean enabled() {
		return Mythos.config().accessories;
	}

	@Override
	public boolean matches(String title) {
		// the category pages only: a single collection's page lists every tier, reached or not
		return title.endsWith(" Collections");
	}

	@Override
	public void update(Menu menu) {
		Map<String, Integer> found = new TreeMap<>();
		int area = menu.rows > 1 ? (menu.rows - 1) * 9 : Integer.MAX_VALUE;     // the bottom row is navigation
		for (Menu.Entry e : menu.container) {
			if (e.slot().index >= area) continue;
			Matcher m = TIERED.matcher(e.name());
			int tier = m.matches() ? Prices.roman(m.group(2)) : 0;
			if (tier > 0) {
				found.put(m.group(1).toLowerCase(Locale.ROOT), tier);
				continue;
			}
			for (String line : e.lore()) {
				Matcher first = FIRST.matcher(line);
				if (first.find() && first.group(1).equalsIgnoreCase(e.name())) found.put(e.name().toLowerCase(Locale.ROOT), 0);
			}
		}
		read = found.size();
		if (found.isEmpty()) return;
		Map<String, Integer> known = Mythos.config().collections;
		boolean changed = false;
		for (Map.Entry<String, Integer> f : found.entrySet()) changed |= !f.getValue().equals(known.put(f.getKey(), f.getValue()));
		if (changed) Mythos.config().save();
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		if (read == 0) return;
		panel.header("Collections", read + " read");
		Analysis a = Accessories.analysis();
		if (a == null) return;
		if (a.unknownCollections.isEmpty()) {
			panel.line("Every accessory recipe is checked against your collections", Panel.GREEN);
		} else {
			panel.line("Not seen yet, so their crafts aren't recommended:", Panel.GRAY);
			int shown = 0;
			for (String name : a.unknownCollections) {
				if (shown++ == 6) {
					panel.line("+" + (a.unknownCollections.size() - 6) + " more", Panel.DARK);
					break;
				}
				panel.line("  " + name, Panel.YELLOW);
			}
		}
	}
}
