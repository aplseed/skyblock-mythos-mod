package dev.aplseed.mythos.feature;

import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.acc.AccData;
import dev.aplseed.mythos.acc.Accessories;
import dev.aplseed.mythos.acc.Analysis;
import dev.aplseed.mythos.acc.Bag;
import dev.aplseed.mythos.acc.Catalog;
import dev.aplseed.mythos.acc.Rarity;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.menu.MenuFeature;
import dev.aplseed.mythos.ui.MythosScreen;
import dev.aplseed.mythos.ui.Panel;
import dev.aplseed.mythos.ui.Theme;
import dev.aplseed.mythos.util.Fmt;
import dev.aplseed.mythos.util.Items;
import dev.aplseed.mythos.util.TabList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Accessory Bag: records what's in the bag (the calculator in /myth acc works from these
 * scans), tints spare accessories in the inventory, and shows Accessory Power, free slots
 * and the cheapest power still to buy.
 *
 * Since SkyBlock 0.27.3 the bag has a slot for every accessory line, filled or not, and can
 * be sorted and searched. A filled slot holds the accessory itself: its SkyBlock id and a
 * rarity line ending in ACCESSORY. An empty one stands for the line without being the item -
 * it carries no id, or says in its lore that the accessory is missing. Slot use comes from
 * "Slots: 45/63" text in the menu or the Accessory Bag tab-list widget.
 */
public final class BagScanner implements MenuFeature {
	private static final Pattern TITLE = Pattern.compile("^Accessory Bag(?: \\((\\d+)/(\\d+)\\))?");
	/** lore that marks a slot as a placeholder for an accessory you don't have in the bag */
	private static final String[] MISSING = {"missing", "not in your accessory bag", "not in your bag", "you don't have",
		"you do not have", "not obtained", "not collected", "click to place", "not unlocked"};

	private int page = 1, pages = 1;

	@Override
	public boolean enabled() {
		return Mythos.config().accessories;
	}

	@Override
	public boolean matches(String title) {
		// The Rift's bag is separate and its accessories don't count outside it
		return TITLE.matcher(title).find() && !title.contains("Rift");
	}

	/** An accessory by its lore ("RARE ACCESSORY", "a EPIC DUNGEON ACCESSORY a") or a known id. */
	public static boolean isAccessory(Menu.Entry e) {
		if (e.id().isEmpty()) return false;
		for (int i = e.lore().size() - 1; i >= 0; i--) {
			String l = e.lore().get(i);
			if (l.contains("CESSORY") && Rarity.fromLore(List.of(l)) != null) return true;
		}
		Catalog c = AccData.catalog();
		return c != null && c.byId.containsKey(e.id());
	}

	/** A slot standing for an accessory the bag doesn't hold. */
	static boolean placeholder(Menu.Entry e) {
		for (String l : e.lore()) {
			String low = l.toLowerCase(Locale.ROOT);
			for (String m : MISSING) if (low.contains(m)) return true;
		}
		return false;
	}

	private static Rarity rarity(Menu.Entry e) {
		Rarity r = Rarity.fromLore(e.lore());
		if (r != null) return r;
		Catalog c = AccData.catalog();
		Catalog.Acc a = c == null ? null : c.byId.get(e.id());
		return a == null ? Rarity.COMMON : a.tier();
	}

	/** The line an accessory belongs to, by id; by name for a placeholder without one. */
	private static String line(Catalog cat, String id, String name, Map<String, String> byName) {
		if (!id.isEmpty()) return cat == null ? id : cat.familyOf(id);
		return byName.get(name.toLowerCase(Locale.ROOT));
	}

	@Override
	public void update(Menu menu) {
		Matcher m = TITLE.matcher(menu.title);
		if (!m.find() || menu.rows < 2) return;
		page = m.group(1) == null ? 1 : (int) Math.max(1, Items.parse(m.group(1)));
		pages = m.group(2) == null ? 1 : (int) Math.max(page, Items.parse(m.group(2)));
		int area = (menu.rows - 1) * 9;     // the bottom row is navigation, sorting and search

		Catalog cat = AccData.catalog();
		Map<String, String> byName = new HashMap<>();
		if (cat != null) for (Catalog.Acc a : cat.byId.values()) byName.put(a.name().toLowerCase(Locale.ROOT), a.family());

		List<String> text = new ArrayList<>();
		List<Bag.Seen> seen = new ArrayList<>();
		for (Menu.Entry e : menu.container) {
			text.add(e.name());
			text.addAll(e.lore());
			if (e.slot().index >= area) continue;
			boolean real = isAccessory(e) && !placeholder(e);
			String line = line(cat, e.id(), e.name(), byName);
			if (line == null) continue;     // not an accessory slot (filler, buttons)
			if (!real) {
				// an empty slot names its line by id or by the accessory's name
				if (!e.id().isEmpty() || byName.containsKey(e.name().toLowerCase(Locale.ROOT))) seen.add(new Bag.Seen(line, null));
				continue;
			}
			Bag.Item it = new Bag.Item();
			it.id = e.id();
			it.name = e.name();
			it.rarity = rarity(e).name();
			it.recomb = Items.customInt(e.stack(), "rarity_upgrades") > 0;
			seen.add(new Bag.Seen(line, it));
		}
		if (!Bag.readCounts(text)) Bag.readCounts(TabList.lines());
		Bag.record(page, pages, seen);

		List<Analysis.Owned> carried = new ArrayList<>();
		for (Menu.Entry e : menu.inventory) {
			if (isAccessory(e)) {
				carried.add(new Analysis.Owned(e.id(), e.name(), rarity(e), Items.customInt(e.stack(), "rarity_upgrades") > 0, 0, e.slot().index, false));
			}
		}
		Bag.inventory(carried);
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		panel.header("Accessories", pages > 1 ? "page " + page + "/" + pages : null);
		Analysis a = Accessories.analysis();
		if (a == null) {
			panel.kv("In the bag", Integer.toString(Bag.size()));
			panel.line(AccData.status(), Panel.GRAY);
			return;
		}
		// spares in the inventory: the bag already holds their line as well or better
		for (Analysis.Dupe d : a.dupes) {
			if (d.item().inBag()) continue;
			for (Menu.Entry e : menu.inventory) {
				if (e.slot().index == d.item().slot()) Theme.slot(g, menu.slotX(e.slot()), menu.slotY(e.slot()), Theme.RED);
			}
		}

		panel.kv("Accessory Power", Fmt.full(a.magicPower), Theme.PINK);
		int game = Bag.gamePower();
		if (game >= 0 && game != a.magicPower && Bag.complete()) panel.kv("  the game says", Fmt.full(game), Panel.GRAY);
		panel.kv("Accessories", Integer.toString(a.counted.size()));
		if (Bag.slotsKnown()) {
			panel.kv("Free slots", Integer.toString(Bag.freeSlots()), Bag.freeSlots() > 0 ? Panel.GREEN : Panel.WHITE);
		} else {
			panel.line("Free slots unknown - turn on the Accessory Bag tab widget or use /myth acc slots", Panel.YELLOW);
		}
		if (!Bag.complete()) panel.line("Open page " + Bag.pagesMissing() + " to finish the scan", Panel.YELLOW);
		String status = AccData.status();
		if (status != null) panel.line(status, Panel.GRAY);

		if (!a.dupes.isEmpty()) {
			panel.header("Spares", a.dupes.size() + " not needed");
			int shown = 0;
			for (Analysis.Dupe d : a.dupes) {
				if (shown++ == 5) {
					panel.line("+" + (a.dupes.size() - 5) + " more", Panel.DARK);
					break;
				}
				panel.row(d.item().name(), d.sameItem() ? "already in the bag" : "bag has " + d.kept().name(), Panel.RED);
			}
		}

		int listed = 0;
		for (Analysis.Missing m : a.missing) {
			if (m.locked() != null || Double.isNaN(m.price())) continue;
			if (listed == 0) panel.header("Cheapest power", "coins per AP");
			String value = Fmt.coins(m.price()) + "  +" + m.gain();
			String name = m.acc().name();
			if (m.how().startsWith("craft")) {
				panel.link(name, value, Panel.GREEN, "Click: /recipe " + name + " (" + m.how() + ")", () -> Mythos.recipe(name));
			} else {
				panel.link(name, value, Panel.GREEN, "Click: /ahs " + name + " (" + Fmt.coins(m.perMp()) + " per AP)", () -> Mythos.auctionSearch(name));
			}
			if (++listed == 5) break;
		}
		panel.header("Calculator");
		panel.link("Missing, spares and budget plans", Panel.AQUA, "Opens the accessory calculator (/myth acc)",
				() -> Mythos.open(MythosScreen.Tab.ACCESSORIES));
	}
}
