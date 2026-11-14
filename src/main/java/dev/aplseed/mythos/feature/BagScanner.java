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
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * Accessory Bag: records every accessory on the open page (the calculator in
 * /myth acc works from these scans), tints dupes, and shows Magical Power,
 * free slots and the cheapest power still to buy.
 *
 * The bag is "Accessory Bag" or "Accessory Bag (2/3)"; the bottom row is
 * navigation. In the rows above, an accessory carries a SkyBlock id and a
 * rarity line ending in ACCESSORY (or HATCESSORY); an empty slot is free, and
 * anything else (the panes over locked slots) is neither.
 */
public final class BagScanner implements MenuFeature {
	private static final Pattern TITLE = Pattern.compile("^Accessory Bag(?: \\((\\d+)/(\\d+)\\))?$");

	private int page = 1, pages = 1;

	@Override
	public boolean enabled() {
		return Mythos.config().accessories;
	}

	@Override
	public boolean matches(String title) {
		return TITLE.matcher(title).find();
	}

	/** An accessory by its lore ("RARE ACCESSORY", "a EPIC DUNGEON ACCESSORY a", "SPECIAL HATCESSORY"). */
	public static boolean isAccessory(Menu.Entry e) {
		if (e.id().isEmpty()) return false;
		for (int i = e.lore().size() - 1; i >= 0; i--) {
			String l = e.lore().get(i);
			if (l.contains("CESSORY") && Rarity.fromLore(List.of(l)) != null) return true;
		}
		Catalog c = AccData.catalog();
		return c != null && c.byId.containsKey(e.id());
	}

	private static Rarity rarity(Menu.Entry e) {
		Rarity r = Rarity.fromLore(e.lore());
		if (r != null) return r;
		Catalog c = AccData.catalog();
		Catalog.Acc a = c == null ? null : c.byId.get(e.id());
		return a == null ? Rarity.COMMON : a.tier();
	}

	@Override
	public void update(Menu menu) {
		Matcher m = TITLE.matcher(menu.title);
		if (!m.find() || menu.rows < 2) return;
		page = m.group(1) == null ? 1 : (int) Math.max(1, Items.parse(m.group(1)));
		pages = m.group(2) == null ? 1 : (int) Math.max(page, Items.parse(m.group(2)));
		int area = (menu.rows - 1) * 9;

		int free = 0;
		for (Slot s : menu.screen.getMenu().slots) {
			if (!(s.container instanceof Inventory) && s.index < area && s.getItem().isEmpty()) free++;
		}
		List<Bag.Item> items = new ArrayList<>();
		for (Menu.Entry e : menu.container) {
			if (e.slot().index >= area || !isAccessory(e)) continue;
			Bag.Item it = new Bag.Item();
			it.id = e.id();
			it.name = e.name();
			it.rarity = rarity(e).name();
			it.recomb = Items.customInt(e.stack(), "rarity_upgrades") > 0;
			it.slot = e.slot().index;
			items.add(it);
		}
		Bag.record(page, pages, items, free);

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
		for (Analysis.Dupe d : a.dupes) {
			if (d.item().page() != page) continue;
			for (Menu.Entry e : menu.container) {
				if (e.slot().index == d.item().slot()) Theme.slot(g, menu.slotX(e.slot()), menu.slotY(e.slot()), Theme.RED);
			}
		}

		panel.kv("Magical Power", Fmt.full(a.magicPower), Theme.PINK);
		panel.kv("Counted", a.counted.size() + " of " + Bag.size() + " in the bag");
		panel.kv("Free slots", Integer.toString(Bag.freeSlots()), Bag.freeSlots() > 0 ? Panel.GREEN : Panel.WHITE);
		if (!Bag.complete()) panel.line("Open page " + Bag.pagesMissing() + " to finish the scan", Panel.YELLOW);
		String status = AccData.status();
		if (status != null) panel.line(status, Panel.GRAY);

		if (!a.dupes.isEmpty()) {
			panel.header("Dupes", a.dupes.size() + " wasted slot" + (a.dupes.size() == 1 ? "" : "s"));
			int shown = 0;
			for (Analysis.Dupe d : a.dupes) {
				if (shown++ == 5) {
					panel.line("+" + (a.dupes.size() - 5) + " more", Panel.DARK);
					break;
				}
				panel.row(d.item().name(), d.sameItem() ? "twice" : "have " + d.kept().name(), Panel.RED);
			}
		}

		int listed = 0;
		for (Analysis.Missing m : a.missing) {
			if (m.locked() != null || Double.isNaN(m.price())) continue;
			if (listed == 0) panel.header("Cheapest power", "coins per MP");
			String value = Fmt.coins(m.price()) + "  +" + m.gain();
			String name = m.acc().name();
			if (m.how().startsWith("craft")) {
				panel.link(name, value, Panel.GREEN, "Click: /recipe " + name + " (" + m.how() + ")", () -> Mythos.recipe(name));
			} else {
				panel.link(name, value, Panel.GREEN, "Click: /ahs " + name + " (" + Fmt.coins(m.perMp()) + " per MP)", () -> Mythos.auctionSearch(name));
			}
			if (++listed == 5) break;
		}
		panel.header("Calculator");
		panel.link("Missing, dupes and budget plans", Panel.AQUA, "Opens the accessory calculator (/myth acc)",
				() -> Mythos.open(MythosScreen.Tab.ACCESSORIES));
	}
}
