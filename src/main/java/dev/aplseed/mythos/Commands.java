package dev.aplseed.mythos;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.aplseed.mythos.acc.AccData;
import dev.aplseed.mythos.acc.Accessories;
import dev.aplseed.mythos.acc.Analysis;
import dev.aplseed.mythos.acc.Bag;
import dev.aplseed.mythos.acc.Catalog;
import dev.aplseed.mythos.acc.Planner;
import dev.aplseed.mythos.data.ShardIndex;
import dev.aplseed.mythos.feature.ShardTracker;
import dev.aplseed.mythos.ui.MythosScreen;
import dev.aplseed.mythos.util.Fmt;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** /myth: the Mythos window, login, settings, feature toggles and the accessory calculator. */
final class Commands {
	private Commands() {}

	static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(ClientCommands.literal("myth")
				.executes(c -> {
					Mythos.open(null);
					return 1;
				})
				.then(ClientCommands.literal("status").executes(c -> status()))
				.then(ClientCommands.literal("login")
						.then(ClientCommands.argument("user", StringArgumentType.word())
								.then(ClientCommands.argument("password", StringArgumentType.greedyString())
										.executes(c -> login(StringArgumentType.getString(c, "user"), StringArgumentType.getString(c, "password"))))))
				.then(ClientCommands.literal("logout").executes(c -> {
					Mythos.backend().logout();
					Mythos.chat("Logged out");
					return 1;
				}))
				.then(ClientCommands.literal("url")
						.then(ClientCommands.argument("url", StringArgumentType.greedyString())
								.executes(c -> {
									String problem = Mythos.setBackendUrl(StringArgumentType.getString(c, "url"));
									Mythos.chat(problem != null ? "§c" + problem : "Server: " + Mythos.config().backendUrl);
									return problem == null ? 1 : 0;
								})))
				.then(ClientCommands.literal("toggle")
						.then(ClientCommands.argument("feature", StringArgumentType.word())
								.suggests((c, b) -> {
									for (Mythos.Feature f : Mythos.FEATURES) b.suggest(f.id());
									return b.buildFuture();
								})
								.executes(Commands::toggle)))
				.then(ClientCommands.literal("forge")
						.then(ClientCommands.argument("slots", IntegerArgumentType.integer(2, 7))
								.then(ClientCommands.argument("quickForge", IntegerArgumentType.integer(0, 20))
										.then(ClientCommands.argument("cole", BoolArgumentType.bool())
												.executes(c -> {
													Config cfg = Mythos.config();
													cfg.forgeSlots = IntegerArgumentType.getInteger(c, "slots");
													cfg.quickForgeLevel = IntegerArgumentType.getInteger(c, "quickForge");
													cfg.coleMayor = BoolArgumentType.getBool(c, "cole");
													cfg.save();
													Mythos.chat("Forge: " + cfg.forgeSlots + " slots, Quick Forge " + cfg.quickForgeLevel + (cfg.coleMayor ? ", Cole" : ""));
													return 1;
												})))))
				.then(ClientCommands.literal("fusion")
						.then(ClientCommands.argument("sell", StringArgumentType.word())
								.suggests((c, b) -> b.suggest("sell_order").suggest("instant_sell").buildFuture())
								.then(ClientCommands.argument("pureReptile", IntegerArgumentType.integer(0, 20))
										.executes(c -> {
											String sell = StringArgumentType.getString(c, "sell");
											if (!sell.equals("sell_order") && !sell.equals("instant_sell")) {
												Mythos.chat("sell must be sell_order or instant_sell");
												return 0;
											}
											Mythos.config().fusionSellMode = sell;
											Mythos.config().pureReptileLevel = IntegerArgumentType.getInteger(c, "pureReptile");
											Mythos.config().save();
											Mythos.chat("Fusion plans: " + sell + ", Pure Reptile " + Mythos.config().pureReptileLevel);
											return 1;
										}))))
				.then(ClientCommands.literal("session")
						.executes(c -> {
							ShardTracker.summary().forEach(Mythos::chat);
							return 1;
						})
						.then(ClientCommands.literal("stop").executes(c -> {
							ShardTracker.stop("stopped");
							return 1;
						})))
				.then(ClientCommands.literal("hud")
						.then(ClientCommands.argument("x", IntegerArgumentType.integer(0, 4000))
								.then(ClientCommands.argument("y", IntegerArgumentType.integer(0, 4000))
										.executes(c -> {
											Mythos.config().hudX = IntegerArgumentType.getInteger(c, "x");
											Mythos.config().hudY = IntegerArgumentType.getInteger(c, "y");
											Mythos.config().save();
											Mythos.chat("Shard HUD at " + Mythos.config().hudX + ", " + Mythos.config().hudY);
											return 1;
										}))))
				.then(ClientCommands.literal("budget")
						.then(ClientCommands.argument("coins", StringArgumentType.greedyString())
								.executes(c -> budget(StringArgumentType.getString(c, "coins")))))
				.then(accessories()));
	}

	/** /myth acc ...: the accessory calculator from chat. */
	private static LiteralArgumentBuilder<FabricClientCommandSource> accessories() {
		return ClientCommands.literal("acc")
				.executes(c -> {
					Mythos.open(MythosScreen.Tab.ACCESSORIES);
					return 1;
				})
				.then(ClientCommands.literal("budget")
						.then(ClientCommands.argument("coins", StringArgumentType.greedyString())
								.executes(c -> budget(StringArgumentType.getString(c, "coins")))))
				.then(ClientCommands.literal("dupes").executes(c -> dupes()))
				.then(ClientCommands.literal("missing").executes(c -> missing()))
				.then(ClientCommands.literal("unlock")
						.then(ClientCommands.argument("requirement", StringArgumentType.word())
								.suggests((c, b) -> {
									Catalog cat = AccData.catalog();
									if (cat != null) cat.reqKeys.keySet().forEach(b::suggest);
									return b.buildFuture();
								})
								.then(ClientCommands.argument("level", IntegerArgumentType.integer(0, 99))
										.executes(c -> {
											String key = StringArgumentType.getString(c, "requirement");
											int level = IntegerArgumentType.getInteger(c, "level");
											Mythos.config().unlocks.put(key, level);
											Mythos.config().save();
											Mythos.chat(Catalog.label(key) + ": " + Catalog.levelName(key, level));
											return 1;
										}))))
				.then(ClientCommands.literal("collection")
						.then(ClientCommands.argument("tier", IntegerArgumentType.integer(0, 20))
								.then(ClientCommands.argument("name", StringArgumentType.greedyString())
										.executes(c -> {
											String name = StringArgumentType.getString(c, "name").trim();
											int tier = IntegerArgumentType.getInteger(c, "tier");
											Mythos.config().collections.put(name.toLowerCase(java.util.Locale.ROOT), tier);
											Mythos.config().save();
											Mythos.chat(name + " collection: tier " + tier);
											return 1;
										}))))
				.then(ClientCommands.literal("jacobus")
						.then(ClientCommands.argument("upgradesBought", IntegerArgumentType.integer(-1, Planner.JACOBUS_MAX))
								.executes(c -> {
									int n = IntegerArgumentType.getInteger(c, "upgradesBought");
									Mythos.config().jacobusUpgrades = n;
									Mythos.config().save();
									Mythos.chat(n < 0 ? "Jacobus upgrades: unknown - budgets won't buy bag slots"
											: "Jacobus upgrades: " + n + " bought" + (n < Planner.JACOBUS_MAX ? ", the next costs " + Fmt.coins(Planner.jacobusPrice(n + 1)) : " (all of them)"));
									return 1;
								})))
				.then(ClientCommands.literal("slots")
						.then(ClientCommands.literal("auto").executes(c -> freeSlots(-1)))
						.then(ClientCommands.argument("free", IntegerArgumentType.integer(0, 300))
								.executes(c -> freeSlots(IntegerArgumentType.getInteger(c, "free")))))
				.then(ClientCommands.literal("refresh").executes(c -> {
					AccData.refresh(true);
					Mythos.chat("Reloading the accessory list and prices");
					return 1;
				}))
				.then(ClientCommands.literal("reset").executes(c -> {
					Bag.clear();
					Mythos.chat("Forgot the scanned bag - open your Accessory Bag to scan it again");
					return 1;
				}));
	}

	private static int freeSlots(int n) {
		Mythos.config().accFreeSlots = n;
		Mythos.config().save();
		Mythos.chat("Free bag slots: " + (n < 0 ? "counted from the scan (" + Bag.freeSlots() + ")" : n));
		return 1;
	}

	/** The scanned bag analysed, or null after telling the player what's missing. */
	private static Analysis scan() {
		Analysis a = Accessories.analysis();
		if (a == null) {
			Mythos.chat(AccData.status() + " - try again in a moment");
			return null;
		}
		if (!Bag.scanned()) {
			Mythos.chat("Open your Accessory Bag first (every page), so Mythos knows what you have");
			return null;
		}
		if (!Bag.complete()) Mythos.chat("§eOnly " + Bag.pagesSeen() + " of " + Bag.pages() + " bag pages scanned - open page " + Bag.pagesMissing());
		return a;
	}

	private static int budget(String text) {
		double coins = Fmt.parseCoins(text);
		if (coins < 0) {
			Mythos.chat("§cThat isn't an amount. Try /myth budget 50m");
			return 0;
		}
		Analysis a = scan();
		if (a == null) return 0;
		if (!AccData.priced()) {
			Mythos.chat(AccData.status() + " - try again in a moment");
			return 0;
		}
		Mythos.config().accBudget = text.trim();
		Mythos.config().save();
		MythosScreen.show(MythosScreen.Sub.BUDGET);
		pendingBudget = coins;
		pendingTicks = 0;
		Accessories.plan(coins);
		return 1;
	}

	private static double pendingBudget = -1;
	private static int pendingTicks;

	/** Prints the plan a /myth budget asked for once the planner is done. */
	static void tick() {
		if (pendingBudget < 0) return;
		Planner.Plan plan = Accessories.plan(pendingBudget);
		if (plan == null) {
			if (++pendingTicks > 20 * 60) pendingBudget = -1;
			return;
		}
		pendingBudget = -1;
		Analysis a = Accessories.analysis();
		if (a == null) return;
		for (String l : Accessories.lines(plan, a, 12)) Mythos.chat(l);
		if (a.locked > 0) Mythos.chat("§7 " + a.locked + " accessory lines are locked for you (/myth acc > Unlocks)");
		if (!a.unknownCollections.isEmpty()) {
			Mythos.chat("§7 " + a.unknownCollections.size() + " recipes weren't considered: open your Collections menus so Mythos can check them");
		}
	}

	private static int dupes() {
		Analysis a = scan();
		if (a == null) return 0;
		if (a.dupes.isEmpty()) {
			Mythos.chat("No spares: nothing you carry duplicates the bag (" + a.counted.size() + " accessories in it)");
			return 1;
		}
		Mythos.chat("§c" + a.dupes.size() + " spare" + (a.dupes.size() == 1 ? "" : "s") + "§r - the bag already counts their line:");
		for (Analysis.Dupe d : a.dupes) {
			Mythos.chat(" " + d.item().name() + " §7- " + (d.sameItem() ? "already in the bag" : "the bag has " + d.kept().name()) + (d.item().inBag() ? ", takes a slot" : ""));
		}
		return 1;
	}

	private static int missing() {
		Analysis a = scan();
		if (a == null) return 0;
		int shown = 0, buyable = 0;
		for (Analysis.Missing m : a.missing) {
			if (m.locked() != null || Double.isNaN(m.price())) continue;
			buyable++;
			if (shown++ < 10) Mythos.chat(" " + m.acc().name() + " §7- " + Fmt.coins(m.price()) + ", §d+" + m.gain() + " AP§7 (" + m.how() + ")");
		}
		Mythos.chat(a.magicPower + " Accessory Power. " + buyable + " lines to buy" + (buyable > 10 ? " (cheapest per AP above)" : "") + ", " + a.locked
				+ " locked, " + (a.missing.size() - buyable - a.locked) + " without a price - /myth acc lists them all");
		return 1;
	}

	private static int status() {
		Config c = Mythos.config();
		Mythos.chat("Server " + c.backendUrl + " - " + (Mythos.backend().loggedIn() ? "logged in as " + c.username : "not logged in (/myth login <user> <password>)"));
		String err = Mythos.backend().lastError();
		if (err != null) Mythos.chat("§cLast error: " + err);
		StringBuilder sb = new StringBuilder("Features:");
		for (Mythos.Feature f : Mythos.FEATURES) sb.append(f.get(c) ? " §a" : " §8").append(f.id());
		Mythos.chat(sb.toString());
		Mythos.chat("Forge " + c.forgeSlots + " slots, QF " + c.quickForgeLevel + (c.coleMayor ? ", Cole" : "")
				+ " - fusion " + c.fusionSellMode + ", PR " + c.pureReptileLevel + (Mythos.active() ? "" : " - §einactive (not on Hypixel)"));
		return 1;
	}

	private static int login(String user, String password) {
		Mythos.backend().login(user, password, name -> {
			Mythos.chat("Logged in as " + name);
			ShardIndex.refresh(true);
		}, err -> Mythos.chat("§cLogin failed: " + err));
		return 1;
	}

	private static int toggle(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "feature");
		Mythos.Feature f = Mythos.feature(name);
		if (f == null) {
			Mythos.chat("Unknown feature. One of: " + String.join(", ", Mythos.FEATURES.stream().map(Mythos.Feature::id).toList()));
			return 0;
		}
		Config c = Mythos.config();
		f.set(c, !f.get(c));
		c.save();
		Mythos.chat(f.label() + " " + (f.get(c) ? "§aon" : "§8off"));
		return 1;
	}
}
