package dev.aplseed.mythos.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aplseed.mythos.Mythos;
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
			check(ctx, "bazaar", "Mining ➜ Enchanted Diamond", List.of(item("Enchanted Diamond", "Buy price: 151.2")),
					"Enchanted Diamond", "Undervalued", "64% right", "Held 640", "Cole (now)");
			// Forge menus as Hypixel sends them (from real F8 dumps)
			check(ctx, "forge", "The Forge", List.of(
					item("Slot #1", "View and start forge processes", "using the materials that you have.", "",
							"Click to select a process!"),
					item("Slot #2", "View and start forge processes", "using the materials that you have.", "",
							"Click to select a process!"),
					item("Forge Recipes", "View all Forge Recipes and their", "requirements to unlock.", "", "Click to view!")),
					"Forge (7 slots", "2 free slots", "#1 Refined Mithril");
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
		mc.setScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal(title)));
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
		ctx.runOnClient(mc -> mc.setScreen(null));
		ctx.waitTicks(5);
	}

	private void checkNoPanel(ClientGameTestContext ctx, String name, String title, List<ItemStack> items) {
		ctx.runOnClient(mc -> open(mc, title, items));
		ctx.waitTicks(40);
		List<String> drawn = ctx.computeOnClient(mc -> Panel.lastDrawn());
		Mythos.LOG.info("[gametest] {} panel (should be empty): {}", name, drawn);
		if (!drawn.isEmpty()) failures.add(name + ": expected no panel, drew " + drawn);
		ctx.runOnClient(mc -> mc.setScreen(null));
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
		ctx.runOnClient(mc -> mc.setScreen(null));
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
