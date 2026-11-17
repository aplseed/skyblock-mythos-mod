package dev.aplseed.mythos.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.google.gson.JsonParser;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.acc.AccData;
import dev.aplseed.mythos.acc.Bag;
import dev.aplseed.mythos.acc.Catalog;
import dev.aplseed.mythos.acc.Prices;
import dev.aplseed.mythos.ui.MythosScreen;
import java.util.Map;
import dev.aplseed.mythos.feature.AbsorbSync;
import dev.aplseed.mythos.feature.ShardTracker;
import dev.aplseed.mythos.ui.Panel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/**
 * Boots the real client against .github/fake-backend (recorded server
 * responses), opens mock Hypixel menus and checks what each panel draws.
 * Panel text and every failure go to the log; screenshots to run/screenshots.
 */
public final class MythosClientGameTest implements FabricClientGameTest {
	private final List<String> failures = new ArrayList<>();

	@Override
	public void runTest(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			Mythos.config().onlyOnHypixel = false;
			Mythos.config().backendUrl = "http://127.0.0.1:5658";
			Mythos.config().sessionToken = "";
		});
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> {
				try {
					ClientCommands.getActiveDispatcher().execute("myth login tester password123",
							(FabricClientCommandSource) mc.getConnection().getSuggestionsProvider());
				} catch (CommandSyntaxException e) {
					throw new AssertionError("/myth login didn't parse", e);
				}
			});
			ctx.waitFor(mc -> !Mythos.config().sessionToken.isEmpty(), 200);

			// Hunting Box items as Hypixel sends them (from a real F8 dump): plain
			// shard name, "Owned: n Shards", "Attribute Maxed!", id on the last line
			check(ctx, "hunting-box", "Hunting Box", List.of(
					item("Hunting Box", "This is where all your Shards are", "stored!", "", "Shards: 3"),
					boxShard("Abyssal Lanternfish", "R23", 640, true),
					boxShard("Aero", "E3", 192, false),
					boxShard("Alligator", "E6", 256, false)),
					"Fusion plan", "3 shard types, 1088 shards", "Fusing adds", "Absorb sync", "Read 1 shard, 1 updated");
			// Attribute Menu items as Hypixel sends them (from a real F8 dump)
			check(ctx, "absorb", "(1/12) Attribute Menu", List.of(
					item("Attribute Menu", "Syphon Shards to unlock and level", "up your Attributes!"),
					attribute("Deep Light I", "Abyssal Lanternfish", "R23", "Attribute Level: 1",
							"Syphon 3 shards to level up!", "Syphon 47 shards to max!"),
					attribute("Air Elemental X", "Aero", "E3", "Attribute Level: 10 (MAX!)")),
					"Shard absorption", "To max everything", "Read 2 shards");
			absorbedArithmetic();
			absorbClick(ctx);
			check(ctx, "bazaar", "Diamond ➜ Enchanted Diamond", List.of(item("Buy Instantly", "Enchanted Diamond", "", "Price per unit: 1,340 coins")),
					"Enchanted Diamond", "Undervalued", "64% right", "Held 640", "Cole (now)");
			// Forge menus as Hypixel sends them (from real F8 dumps)
			check(ctx, "forge", "The Forge", List.of(
					withId(item("Refined Mithril", "Currently making: Refined Mithril", "Time Remaining: 41m 43s"), "REFINED_MITHRIL"),
					item("Slot #1", "View and start forge processes", "using the materials that you have.", "",
							"Click to select a process!"),
					item("Slot #2", "View and start forge processes", "using the materials that you have.", "",
							"Click to select a process!"),
					item("Forge Recipes", "View all Forge Recipes and their", "requirements to unlock.", "", "Click to view!")),
					"Forge (7 slots", "Forging: Refined Mithril  41m 43s", "2 free slots", "#1 Refined Mithril");
			// the running process isn't a suggestion
			ctx.runOnClient(mc -> {
				if (String.join("\n", Panel.lastDrawn()).contains("Best here")) failures.add("forge: running slot listed as a suggestion");
			});
			check(ctx, "forge-refining", "Refining", List.of(
					withId(item("Refined Diamond", "EPIC", "", "Items Required", "Enchanted Diamond Block x2", "",
							"Duration: 5h 36m", "", "Click to select!"), "REFINED_DIAMOND"),
					withId(item("Refined Mithril", "EPIC DWARVEN METAL", "", "Items Required", "Enchanted Mithril x160", "",
							"Duration: 4h 12m", "", "Click to select!"), "REFINED_MITHRIL")),
					"Best here:", "#1 Refined Mithril");
			// Select Process tiles carry recipe ids ("Refining" is REFINED_MITHRIL) but aren't recipes
			check(ctx, "forge-select", "Select Process (Slot #1)", List.of(
					withId(item("Refining", "Refine your ores into more valuable", "ores.", "", "Click to view!"), "REFINED_MITHRIL")),
					"Forge (7 slots");
			checkNoPanel(ctx, "pets-not-forge", "Pets", List.of(item("Golden Dragon", "Level 100", "Click to summon!")));
			// Fusion Box items (from a real F8 dump): "Hunting Box: n" and "(Max level reached)"
			check(ctx, "fusion-box", "Fusion Box", List.of(
					item("Fusion Box", "To begin a fusion you need to select", "", "Shards: 2"),
					fusionShard("Abyssal Lanternfish", "R23", 640, true),
					fusionShard("Aero", "E3", 192, false)),
					"Fusion plan", "2 shard types, 832 shards", "Absorb sync", "Read 1 shard");
			// booth items as Hypixel sends them (from a real F8 dump)
			check(ctx, "election", "Election, Year 521", List.of(
					item("Cole", "Year 521 Candidate", "", "Votes: 55k (3.8%)", "Last elected: 9y ago", "", "Click to vote for Cole!"),
					item("Diana", "Year 521 Candidate", "", "Votes: 47k (3.3%)", "", "Click to vote for Diana!")),
					"Election", "Cole  41.2%", "your items: \u25BC Enchanted Diamond", "Enchanted Mithril",
					"More Griffin drops, unmeasured", "no known market effect");

			if (System.getenv("MYTHOS_LIVE") != null) live(ctx);
			accessories(ctx);
			screens(ctx);
			badAddress(ctx);
			dump(ctx);
			shards(ctx);
			warnings(ctx);
		}
		if (!failures.isEmpty()) throw new AssertionError("Mythos game test failed:\n  " + String.join("\n  ", failures));
		Mythos.LOG.info("[gametest] all checks passed");
	}

	private static ItemStack named(net.minecraft.world.item.Item base, String name, int count, String... lore) {
		ItemStack s = new ItemStack(base, count);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		List<Component> lines = new ArrayList<>();
		for (String l : lore) lines.add(Component.literal(l));
		s.set(DataComponents.LORE, new ItemLore(lines));
		return s;
	}

	private static ItemStack withId(ItemStack s, String id) {
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return s;
	}

	private static ItemStack fusionShard(String name, String id, int inBox, boolean maxed) {
		return named(Items.PRISMARINE_SHARD, name, 1, "Some Family", "", "Source: The Garden",
				"Hunting Box: " + inBox, "Required to fuse: 5", "", "Some Attribute (Farming)",
				maxed ? "Your Attribute: Level 10" : "Your Attribute: Level 1", maxed ? "(Max level reached)" : "", "",
				"RARE FOREST SHARD (ID " + id + ")", "", "Click to fuse 5!");
	}

	private static ItemStack attribute(String name, String shard, String id, String... progress) {
		List<String> lore = new ArrayList<>(List.of("Global", "", "Grants something.", "",
				"Source: " + shard + " Shard (" + id + ")", "Rarity: RARE", "Enabled: Yes", ""));
		lore.addAll(List.of(progress));
		lore.addAll(List.of("", "Left-Click to open!"));
		return named(Items.PRISMARINE_SHARD, name, 1, lore.toArray(String[]::new));
	}

	/** Clicking a "Cheapest to finish" line copies the amount still needed (and runs /bz). */
	private void absorbClick(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> open(mc, "(1/12) Attribute Menu", List.of(item("Attribute Menu", "Syphon Shards"))));
		ctx.waitTicks(40);
		List<Panel.Hit> hits = ctx.computeOnClient(mc -> Panel.hits());
		if (hits.isEmpty()) {
			failures.add("absorb click: no clickable lines");
		} else {
			Panel.Hit h = hits.get(0);
			Mythos.LOG.info("[gametest] first absorb link: {}", h.hint());
			ctx.runOnClient(mc -> mc.keyboardHandler.setClipboard(""));
			boolean clicked = ctx.computeOnClient(mc -> Panel.click(h.x() + 2, h.y() + 2));
			String clip = ctx.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
			if (!clicked || !h.hint().startsWith("Click: /bz ") || !h.hint().contains("copies " + clip) || clip.isEmpty()) {
				failures.add("absorb click: clicked=" + clicked + " hint=" + h.hint() + " clipboard=" + clip);
			}
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	/** The count rules, on lore lines copied from a real Attribute Menu dump. */
	private void absorbedArithmetic() {
		record Case(List<String> lore, int cap, int want) {}
		for (Case c : List.of(
				new Case(List.of("Attribute Level: 1", "Syphon 3 shards to level up!", "Syphon 95 shards to max!"), 96, 1),
				new Case(List.of("Attribute Level: 10 (MAX!)", "", "Left-Click to open!"), 96, 96),
				new Case(List.of("Syphon 1 shard to unlock!", "Syphon 96 more to max!"), 96, 0),
				new Case(List.of("Owned: 9 Shards", "Attribute Maxed!"), 64, 64),
				new Case(List.of("Owned: 9 Shards", "Right-click to convert to an item!"), 64, -1))) {
			int got = AbsorbSync.absorbed(c.lore, c.cap);
			if (got != c.want) failures.add("absorbed " + c.lore + ": got " + got + ", want " + c.want);
		}
	}

	/** An accessory as an inventory shows it: SkyBlock id, rarity on the last lore line, recombobulated or not. */
	private static ItemStack accessory(String id, String name, String rarity, boolean recomb) {
		return accessory(id, name, rarity, recomb, false);
	}

	/** An accessory as it looks in the 0.27.3 bag (from a real dump); `stored` adds "Left-click to remove!". */
	private static ItemStack accessory(String id, String name, String rarity, boolean recomb, boolean stored) {
		List<String> lore = new ArrayList<>(List.of("Skilled Game Stage", "", "Accessory Power: +" + switch (rarity) {
			case "COMMON" -> 3;
			case "UNCOMMON" -> 5;
			case "RARE" -> 8;
			case "EPIC" -> 12;
			default -> id.equals("HEGEMONY_ARTIFACT") ? 32 : 16;
		}, "", "Grants something.", "", recomb ? "a " + rarity + " ACCESSORY a" : rarity + " ACCESSORY"));
		if (stored) lore.addAll(List.of("", "Left-click to remove!"));
		ItemStack s = named(Items.PLAYER_HEAD, name, 1, lore.toArray(String[]::new));
		CompoundTag tag = new CompoundTag();
		tag.putString("id", id);
		if (recomb) tag.putInt("rarity_upgrades", 1);
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
		return s;
	}

	/** A slot of the 0.27.3 bag for an accessory you don't have: no SkyBlock id, nothing to remove. */
	private static ItemStack missing(String name) {
		return named(Items.CLAY_BALL, name, 1, "Expert Game Stage", "", "Right-click to view recipes!");
	}

	/**
	 * A page of the 0.27.3 bag laid out as in a real dump: the bag's item (with the Accessory
	 * Power total) in the top row, accessories in the 7x4 block inside a blank border, and the
	 * bottom row with paging, "Need more room?" (slot use, `slots`), search and sort.
	 */
	private static void openBag(Minecraft mc, String title, List<ItemStack> items, String slots, int power) {
		SimpleContainer box = new SimpleContainer(54);
		for (int i = 0; i < 54; i++) box.setItem(i, named(Items.GLASS_PANE, "", 1));
		box.setItem(4, item("Accessory Bag", "A special bag which can hold", "Talismans, Rings, Artifacts, and Orbs", "",
				"Accessory Power: " + power, "", "Selected Power: Silky", "+718.05 Crit Damage"));
		int[] grid = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
		for (int i = 0; i < items.size(); i++) box.setItem(grid[i], items.get(i));
		box.setItem(45, item("Previous Page", "Page 1"));
		box.setItem(47, item("Need more room?", "You can expand your Accessory Bag", "by doing any of the following:", "",
				"Current Accessory Bag " + slots));
		box.setItem(48, item("Go Back", "To Your Bags"));
		box.setItem(49, item("Close"));
		box.setItem(50, item("Search", "Find accessories by name!", "", "Click to search!"));
		box.setItem(51, item("Sort", "", "Rarity", "Game Stage", "Alphabetical", "Upgrades First", "▶ Missing First", "", "Click to switch sort!"));
		box.setItem(53, item("Next Page", "Page 3"));
		ChestMenu menu = ChestMenu.sixRows(1000, mc.player.getInventory(), box);
		mc.gui.setScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal(title)));
	}

	private List<String> panelAfter(ClientGameTestContext ctx, String name, int ticks) {
		ctx.waitTicks(ticks);
		List<String> drawn = ctx.computeOnClient(mc -> Panel.lastDrawn());
		Mythos.LOG.info("[gametest] {} panel:\n    {}", name, String.join("\n    ", drawn));
		ctx.takeScreenshot("mythos-" + name);
		return drawn;
	}

	private void expect(String name, List<String> lines, String... want) {
		String all = String.join("\n", lines);
		for (String w : want) if (!all.contains(w)) failures.add(name + ": lacks \"" + w + "\"");
	}

	/**
	 * The accessory calculator on a hand-made item list (in Hypixel's and NEU's formats)
	 * and a two-page bag in the 0.27.3 layout: scan, spares, free slots, then /myth budget.
	 */
	private void accessories(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			Catalog cat = Catalog.build(JsonParser.parseString("""
					{"items": [
					  {"id": "FEATHER_TALISMAN", "name": "Feather Talisman", "category": "ACCESSORY"},
					  {"id": "FEATHER_RING", "name": "Feather Ring", "category": "ACCESSORY", "tier": "UNCOMMON"},
					  {"id": "FEATHER_ARTIFACT", "name": "Feather Artifact", "category": "ACCESSORY", "tier": "RARE"},
					  {"id": "WOLF_TALISMAN", "name": "Wolf Talisman", "category": "ACCESSORY", "tier": "UNCOMMON"},
					  {"id": "WOLF_RING", "name": "Wolf Ring", "category": "ACCESSORY", "tier": "RARE"},
					  {"id": "SPEED_TALISMAN", "name": "Speed Talisman", "category": "ACCESSORY", "tier": "COMMON"},
					  {"id": "SCARF_GRIMOIRE", "name": "Scarf's Grimoire", "category": "ACCESSORY", "tier": "EPIC"},
					  {"id": "RED_CLAW_ARTIFACT", "name": "Red Claw Artifact", "category": "ACCESSORY", "tier": "EPIC",
					   "requirements": [{"type": "SLAYER", "slayer_boss_type": "wolf", "level": 5}]},
					  {"id": "TITANIUM_RELIC", "name": "Titanium Relic", "category": "ACCESSORY", "tier": "LEGENDARY",
					   "requirements": [{"type": "HEART_OF_THE_MOUNTAIN", "tier": 5}]},
					  {"id": "HEGEMONY_ARTIFACT", "name": "Hegemony Artifact", "category": "ACCESSORY", "tier": "LEGENDARY"},
					  {"id": "CAMPFIRE_TALISMAN_1", "name": "Campfire Initiate Badge", "category": "ACCESSORY", "soulbound": "SOLO"}
					]}""").getAsJsonObject(), JsonParser.parseString("""
					{"talisman_upgrades": {"FEATHER_TALISMAN": ["FEATHER_RING", "FEATHER_ARTIFACT"], "FEATHER_RING": ["FEATHER_ARTIFACT"],
					  "WOLF_TALISMAN": ["WOLF_RING"]}, "ignored_talisman": []}""").getAsJsonObject());
			AccData.set(cat, new Prices(
					Map.of("FEATHER_ARTIFACT", 400_000.0, "SPEED_TALISMAN", 5_000.0, "SCARF_GRIMOIRE", 3_500_000.0,
							"RED_CLAW_ARTIFACT", 2_000_000.0, "TITANIUM_RELIC", 30_000_000.0),
					Map.of(Prices.RECOMB, 9_000_000.0, "ENCHANTED_FEATHER", 500.0),
					Map.of("FEATHER_RING", new Prices.Recipe(Map.of("ENCHANTED_FEATHER", 28, "FEATHER_TALISMAN", 1), "Feather VII", null, 0),
							"FEATHER_ARTIFACT", new Prices.Recipe(Map.of("ENCHANTED_FEATHER", 128, "FEATHER_RING", 1), "Feather IX", null, 0))));
			Bag.clear();
			Mythos.config().unlocks.clear();
			Mythos.config().unlocks.put("slayer_wolf", 7);
			Mythos.config().jacobusUpgrades = 3;
			Mythos.config().accFreeSlots = -1;
			Mythos.config().accCraft = true;
			Mythos.config().collections.clear();
			// spares in the inventory: a second feather talisman, and a wolf talisman under the bag's ring
			mc.player.getInventory().setItem(9, accessory("FEATHER_TALISMAN", "Feather Talisman", "COMMON", false));
			mc.player.getInventory().setItem(10, accessory("WOLF_TALISMAN", "Wolf Talisman", "UNCOMMON", false));
			// page 1 of 2: the feather talisman, the recombobulated wolf ring, two lines not in the bag
			// (one slot without an id, one showing the accessory but with nothing to remove) and 24 more
			List<ItemStack> page1 = new ArrayList<>(List.of(
					accessory("FEATHER_TALISMAN", "Feather Talisman", "COMMON", false, true),
					accessory("WOLF_RING", "Wolf Ring", "EPIC", true, true),
					missing("Speed Talisman"),
					accessory("SCARF_GRIMOIRE", "Scarf's Grimoire", "EPIC", false, false)));
			for (int i = 0; i < 24; i++) page1.add(accessory("FILLER_" + i, "Filler " + i, "COMMON", false, true));
			openBag(mc, "(1/2) Accessory Bag", page1, "Slots: 44/60", 170);
		});
		// 3 feather + 12 ring (epic once recombobulated) + 24 fillers x 3
		List<String> p1 = panelAfter(ctx, "accessory-bag", 30);
		expect("accessory bag page 1", p1, "Accessories  page 1/2", "Accessory Power  87", "Accessories  26", "Free slots  16",
				"Open page 2 to finish the scan", "Spares  2 not needed", "Feather Talisman  already in the bag", "Wolf Talisman  bag has Wolf Ring",
				"Cheapest power", "Speed Talisman  5k  +3");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);

		// page 2: the hegemony artifact (it counts twice), the titanium relic's empty slot, 17 more
		ctx.runOnClient(mc -> {
			List<ItemStack> page2 = new ArrayList<>(List.of(
					accessory("HEGEMONY_ARTIFACT", "Hegemony Artifact", "LEGENDARY", false, true), missing("Titanium Relic")));
			for (int i = 24; i < 41; i++) page2.add(accessory("FILLER_" + i, "Filler " + i, "COMMON", false, true));
			openBag(mc, "(2/2) Accessory Bag", page2, "Slots: 44/60", 170);
		});
		List<String> p2 = panelAfter(ctx, "accessory-bag-2", 30);
		expect("accessory bag page 2", p2, "Accessory Power  170", "Accessories  44", "Free slots  16");
		if (String.join("\n", p2).contains("the game says")) failures.add("accessory bag: Mythos' power differs from the bag's own");
		if (String.join("\n", p2).contains("finish the scan")) failures.add("accessory bag: still asks for a page after both were opened");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);

		// before the collection is seen, the feather upgrade is a 400k purchase, not a 78k craft
		command(ctx, "myth budget 100k");
		ctx.waitFor(mc -> chatContains(mc, "100k budget"), 200);
		expect("/myth budget before collections", ctx.computeOnClient(mc -> Mythos.sentChat()), "+3 Accessory Power", "2 recipes weren't considered");
		// a collection menu as the game shows it: the tier is in the item's name
		check(ctx, "collections", "Farming Collections", List.of(
				item("Feather IX", "View all your Feather Collection", "progress and rewards!", "", "Progress to Feather X: 12.5%"),
				item("Wheat", "View all your Wheat Collection", "progress and rewards!", "", "Progress to Wheat I: 0%")),
				"Collections  2 read", "Every accessory recipe is checked");
		ctx.runOnClient(mc -> {
			Map<String, Integer> seen = Mythos.config().collections;
			if (!Integer.valueOf(9).equals(seen.get("feather")) || !Integer.valueOf(0).equals(seen.get("wheat"))) failures.add("collections: read " + seen);
		});

		// HotM was never set: the titanium relic is locked, the red claw (wolf 7 >= 5) isn't.
		// 6M buys the 78k feather craft (+5), speed talisman (+3), red claw (+12) and the grimoire (+12)
		command(ctx, "myth budget 6m");
		ctx.waitFor(mc -> chatContains(mc, "budget"), 200);
		List<String> chat = ctx.computeOnClient(mc -> Mythos.sentChat());
		Mythos.LOG.info("[gametest] /myth budget 6m:\n    {}", String.join("\n    ", chat.subList(Math.max(0, chat.size() - 8), chat.size())));
		expect("/myth budget", chat, "+32 Accessory Power", "(170 -> 202)", "Craft Feather Artifact (replaces Feather Talisman)",
				"Buy Red Claw Artifact", "Buy Scarf's Grimoire", "Buy Speed Talisman", "1 accessory lines are locked");

		command(ctx, "myth acc dupes");
		expect("/myth acc dupes", ctx.computeOnClient(mc -> Mythos.sentChat()), "2 spares", "the bag has Wolf Ring");
		// a bad amount is refused, not planned
		command(ctx, "myth budget lots");
		expect("/myth budget lots", ctx.computeOnClient(mc -> Mythos.sentChat()), "That isn't an amount");
		// the slot count also comes from the Accessory Bag tab widget's text
		ctx.runOnClient(mc -> {
			Bag.readCounts(List.of("Accessory Bag:", " Accessory Power: 170", " Accessory Bag Slots: 44/62"));
			if (Bag.freeSlots() != 18 || Bag.gamePower() != 170) failures.add("tab widget: free " + Bag.freeSlots() + ", power " + Bag.gamePower());
			Bag.readCounts(List.of("Slots: 44/60"));
			mc.player.getInventory().setItem(9, ItemStack.EMPTY);
			mc.player.getInventory().setItem(10, ItemStack.EMPTY);
		});
	}

	/**
	 * Not part of CI: with MYTHOS_LIVE set, loads the real accessory list, prices and recipes
	 * from the public APIs and plans a budget for an empty bag, logging what came back.
	 */
	private void live(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			Bag.clear();
			Mythos.config().unlocks.clear();
			Mythos.config().jacobusUpgrades = 0;
			AccData.refresh(true);
		});
		ctx.waitFor(mc -> {
			AccData.refresh(false);
			return AccData.ready() && AccData.priced() && AccData.status() == null;
		}, 20 * 180);
		ctx.runOnClient(mc -> {
			Catalog cat = AccData.catalog();
			Prices p = AccData.prices();
			Mythos.LOG.info("[gametest] live: {} accessories in {} lines, {} lowest BINs, {} bazaar prices, {} recipes, requirements {}",
					cat.byId.size(), cat.families.size(), p.lbin().size(), p.bazaar().size(), p.recipes().size(), cat.reqKeys);
			if (cat.byId.size() < 300 || p.lbin().size() < 1000 || p.bazaar().size() < 500 || Double.isNaN(p.recomb())) {
				failures.add("live: data looks short - " + cat.byId.size() + " accessories, " + p.lbin().size() + " BINs, " + p.bazaar().size() + " bazaar");
			}
			// accessories are priced from Hypixel's open BIN auctions, not from the price list
			long stale = cat.byId.keySet().stream().filter(id -> !Double.isNaN(p.ah(id)) && Double.isNaN(p.bin(id))).count();
			Mythos.LOG.info("[gametest] live: {} accessories have an open BIN; the price list names {} more that have none (Runebook: list {}, open BIN {})",
					p.bins().size(), stale, p.ah("RUNEBOOK"), p.bin("RUNEBOOK"));
			if (p.bins().size() < 100) failures.add("live: only " + p.bins().size() + " accessories with an open BIN");
			long withRecipe = p.recipes().values().stream().filter(r -> !r.parts().isEmpty()).count();
			if (withRecipe < 50) failures.add("live: only " + withRecipe + " recipes loaded");
			openBag(mc, "Accessory Bag", List.of(accessory("FEATHER_TALISMAN", "Feather Talisman", "COMMON", false, true)), "Slots: 1/18", 3);
		});
		panelAfter(ctx, "live-bag", 30);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		command(ctx, "myth budget 25m");
		ctx.waitFor(mc -> chatContains(mc, "budget"), 400);
		List<String> chat = ctx.computeOnClient(mc -> Mythos.sentChat());
		Mythos.LOG.info("[gametest] live /myth budget 25m:\n    {}", String.join("\n    ", chat.subList(Math.max(0, chat.size() - 16), chat.size())));
		ctx.runOnClient(mc -> {
			MythosScreen.show(MythosScreen.Sub.MISSING);
			mc.gui.setScreen(new MythosScreen(MythosScreen.Tab.ACCESSORIES));
		});
		ctx.waitTicks(15);
		ctx.takeScreenshot("mythos-live-missing");
		ctx.runOnClient(mc -> {
			MythosScreen.show(MythosScreen.Sub.BUDGET);
			mc.gui.setScreen(new MythosScreen(MythosScreen.Tab.ACCESSORIES));
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("mythos-live-budget");
		ctx.runOnClient(mc -> {
			mc.gui.setScreen(null);
			Mythos.sentChat();
		});
		ctx.waitTicks(3);
	}

	private static void command(ClientGameTestContext ctx, String command) {
		ctx.runOnClient(mc -> {
			try {
				ClientCommands.getActiveDispatcher().execute(command, (FabricClientCommandSource) mc.getConnection().getSuggestionsProvider());
			} catch (CommandSyntaxException e) {
				throw new AssertionError("/" + command + " didn't parse", e);
			}
		});
		ctx.waitTicks(3);
	}

	/** Every tab of the Mythos window draws what it should. */
	private void screens(ClientGameTestContext ctx) {
		// "/myth" itself opens the window a tick later
		command(ctx, "myth");
		ctx.waitFor(mc -> mc.gui.screen() instanceof MythosScreen, 40);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		record Case(String name, MythosScreen.Tab tab, MythosScreen.Sub sub, String[] want) {}
		for (Case c : List.of(
				new Case("overview", MythosScreen.Tab.OVERVIEW, null, new String[] {"Logged in as tester", "Features", "Fusion planner", "Accessory bag", "[Log out]"}),
				new Case("settings", MythosScreen.Tab.SETTINGS, null, new String[] {"Forge slots", "Quick Forge level", "Pure Reptile level", "Only on Hypixel"}),
				new Case("acc-summary", MythosScreen.Tab.ACCESSORIES, MythosScreen.Sub.SUMMARY, new String[] {"170 AP", "Accessories", "44", "Spares", "16 of 60", "Power by rarity", "Legendary"}),
				new Case("acc-missing", MythosScreen.Tab.ACCESSORIES, MythosScreen.Sub.MISSING, new String[] {"Speed Talisman", "Red Claw Artifact", "needs Heart of the Mountain 5 (yours: not set)", "soulbound: earn it"}),
				new Case("acc-dupes", MythosScreen.Tab.ACCESSORIES, MythosScreen.Sub.DUPES, new String[] {"2 not needed", "already in the bag", "the bag has Wolf Ring"}),
				new Case("acc-budget", MythosScreen.Tab.ACCESSORIES, MythosScreen.Sub.BUDGET, new String[] {"+32 Accessory Power", "170 -> 202", "4 purchases", "craft from your Feather Talisman", "[Send to chat]"}),
				new Case("acc-unlocks", MythosScreen.Tab.ACCESSORIES, MythosScreen.Sub.UNLOCKS, new String[] {"Wolf Slayer", "Heart of the Mountain", "not set", "Jacobus upgrades", "3 of 99", "Next upgrade", "1M for +2 slots", "Collections for crafts", "Feather", "IX"}))) {
			ctx.runOnClient(mc -> {
				if (c.sub != null) MythosScreen.show(c.sub);
				mc.gui.setScreen(new MythosScreen(c.tab));
			});
			ctx.waitTicks(15);
			List<String> drawn = ctx.computeOnClient(mc -> MythosScreen.lastText());
			Mythos.LOG.info("[gametest] screen {}:\n    {}", c.name, String.join(" | ", drawn));
			expect("screen " + c.name, drawn, c.want);
			ctx.takeScreenshot("mythos-screen-" + c.name);
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(3);
		}
	}

	/** A server address that isn't one is refused; one that slipped into the config fails softly. */
	private void badAddress(ClientGameTestContext ctx) {
		command(ctx, "myth url not a url");
		String kept = ctx.computeOnClient(mc -> Mythos.config().backendUrl);
		if (!kept.equals("http://127.0.0.1:5658")) failures.add("bad address was stored: " + kept);
		ctx.runOnClient(mc -> {
			Mythos.config().backendUrl = "ht tp://nowhere";
			Mythos.backend().get("/api/status", j -> failures.add("bad address: request succeeded"));     // must not throw
			Mythos.config().backendUrl = "http://127.0.0.1:5658";
		});
		ctx.waitTicks(5);
		String err = ctx.computeOnClient(mc -> String.valueOf(Mythos.backend().lastError()));
		if (!err.contains("bad server address")) failures.add("bad address: last error was " + err);
	}

	private static ItemStack item(String name, String... lore) {
		return named(Items.PAPER, name, 1, lore);
	}

	private static ItemStack boxShard(String name, String id, int owned, boolean maxed) {
		return named(Items.PRISMARINE_SHARD, name, 1, "Some Family", "", "Owned: " + owned + " Shards",
				maxed ? "Attribute Maxed!" : "", "", "Right-click to convert to an item!", "",
				"RARE FOREST SHARD (ID " + id + ")");
	}

	private static void open(Minecraft mc, String title, List<ItemStack> items) {
		SimpleContainer box = new SimpleContainer(54);
		for (int i = 0; i < items.size(); i++) box.setItem(10 + i, items.get(i));
		ChestMenu menu = ChestMenu.sixRows(1000, mc.player.getInventory(), box);
		mc.gui.setScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal(title)));
	}

	private void check(ClientGameTestContext ctx, String name, String title, List<ItemStack> items, String... expect) {
		ctx.runOnClient(mc -> open(mc, title, items));
		// contents are scanned every few ticks; backend replies arrive asynchronously
		ctx.waitTicks(80);
		List<String> drawn = ctx.computeOnClient(mc -> Panel.lastDrawn());
		Mythos.LOG.info("[gametest] {} panel:\n    {}", name, String.join("\n    ", drawn));
		String all = String.join("\n", drawn);
		for (String e : expect) if (!all.contains(e)) failures.add(name + ": panel lacks \"" + e + "\"");
		ctx.takeScreenshot("mythos-" + name);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	private void checkNoPanel(ClientGameTestContext ctx, String name, String title, List<ItemStack> items) {
		ctx.runOnClient(mc -> open(mc, title, items));
		ctx.waitTicks(40);
		List<String> drawn = ctx.computeOnClient(mc -> Panel.lastDrawn());
		Mythos.LOG.info("[gametest] {} panel (should be empty): {}", name, drawn);
		if (!drawn.isEmpty()) failures.add(name + ": expected no panel, drew " + drawn);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	private void dump(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			open(mc, "Dump Test", List.of(item("Thing", "line one")));
		});
		ctx.waitTicks(10);
		ctx.getInput().pressKey(Mythos.DUMP_KEY);
		ctx.waitTicks(10);
		String clip = ctx.computeOnClient(mc -> mc.keyboardHandler.getClipboard());
		if (!clip.contains("title: Dump Test") || !clip.contains("Thing") || !clip.contains("line one")) {
			failures.add("dump: clipboard was " + clip);
		}
		try (Stream<Path> files = Files.list(Mythos.configDir().resolve("dumps"))) {
			if (files.noneMatch(p -> p.getFileName().toString().endsWith("Dump_Test.txt"))) failures.add("dump: no file written");
		} catch (Exception e) {
			failures.add("dump: " + e);
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private void shards(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			ShardTracker.onChat(Component.literal("You caught x2 Abyssal Lanternfish Shards!"), false);
			ShardTracker.onChat(Component.literal("You charmed an Aero and captured its Shard."), false);
			ShardTracker.onChat(Component.literal("You caught a Not A Real Shard!"), false);
		});
		// the first catch fetches the saved farms in the background
		ctx.waitFor(mc -> String.join("\n", ShardTracker.summary()).contains("vs saved"), 100);
		List<String> s = ctx.computeOnClient(mc -> ShardTracker.summary());
		Mythos.LOG.info("[gametest] shard session:\n    {}", String.join("\n    ", s));
		String all = String.join("\n", s);
		if (!all.contains("3 caught")) failures.add("shards: expected 3 caught, got " + all);
		if (!all.contains("Abyssal Lanternfish x2")) failures.add("shards: lanternfish not counted");
		if (!all.contains("vs saved")) failures.add("shards: no saved-farm comparison");
		ctx.takeScreenshot("mythos-shard-hud");
	}

	private void warnings(ClientGameTestContext ctx) {
		// SellWarnings checks 15 s after start, then every minute
		ctx.waitFor(mc -> chatContains(mc, "Sell warning"), 20 * 75);
		ctx.runOnClient(mc -> {
			if (!chatContains(mc, "Downtrend")) failures.add("warnings: no Downtrend line in chat");
		});
	}

	private static boolean chatContains(Minecraft mc, String text) {
		return Mythos.sentChat().stream().anyMatch(s -> s.contains(text));
	}
}
