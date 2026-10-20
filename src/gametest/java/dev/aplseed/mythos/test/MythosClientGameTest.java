package dev.aplseed.mythos.test;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.aplseed.mythos.Mythos;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
			check(ctx, "absorb", "Attribute Menu", List.of(
					shard("Abyssal Lanternfish", 1, "Absorbed: 5/96"),
					shard("Aero", 1, "Absorbed: 0/64")),
					"Shard absorption", "To max everything", "Read 2 shards");
			check(ctx, "bazaar", "Mining ➜ Enchanted Diamond", List.of(item("Enchanted Diamond", "Buy price: 151.2")),
					"Enchanted Diamond", "Undervalued", "64% right", "Held 640", "Cole (now)");
			check(ctx, "forge", "The Forge", List.of(
					item("Slot #1", "Click to select an item to forge!"),
					item("Refined Mithril", "Forge time: 6h")),
					"Forge (7 slots", "1 free slot", "#1 Refined Mithril");
			check(ctx, "election", "Election, Year 512", List.of(item("Cole", "Mining Fiesta"), item("Diana", "Pet XP Buff")),
					"Election", "Cole  41.2%", "Enchanted Mithril", "expected effect");

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

	private static ItemStack shard(String name, int count, String lore) {
		return named(Items.PRISMARINE_SHARD, name + " Shard", count, lore);
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
