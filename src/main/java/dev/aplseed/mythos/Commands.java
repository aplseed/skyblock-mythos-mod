package dev.aplseed.mythos;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import dev.aplseed.mythos.data.ShardIndex;
import dev.aplseed.mythos.feature.ShardTracker;
import dev.aplseed.mythos.net.Json;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

/** /myth: login, settings and feature toggles. */
final class Commands {
	private Commands() {}

	private interface Toggle {
		boolean get(Config c);

		void set(Config c, boolean v);
	}

	private static final String[] FEATURES = {"fusion", "absorb", "bazaar", "forge", "election", "warnings", "shards", "panels"};

	private static Toggle toggle(String name) {
		return switch (name) {
			case "fusion" -> of(c -> c.fusionPlanner, (c, v) -> c.fusionPlanner = v);
			case "absorb" -> of(c -> c.absorbSync, (c, v) -> c.absorbSync = v);
			case "bazaar" -> of(c -> c.bazaarPanel, (c, v) -> c.bazaarPanel = v);
			case "forge" -> of(c -> c.forgeAdvisor, (c, v) -> c.forgeAdvisor = v);
			case "election" -> of(c -> c.electionPanel, (c, v) -> c.electionPanel = v);
			case "warnings" -> of(c -> c.sellWarnings, (c, v) -> c.sellWarnings = v);
			case "shards" -> of(c -> c.shardTracker, (c, v) -> c.shardTracker = v);
			case "panels" -> of(c -> c.overlays, (c, v) -> c.overlays = v);
			default -> null;
		};
	}

	private static Toggle of(java.util.function.Predicate<Config> get, java.util.function.BiConsumer<Config, Boolean> set) {
		return new Toggle() {
			public boolean get(Config c) {
				return get.test(c);
			}

			public void set(Config c, boolean v) {
				set.accept(c, v);
			}
		};
	}

	static void register() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> register(dispatcher));
	}

	private static void register(CommandDispatcher<FabricClientCommandSource> d) {
		d.register(ClientCommands.literal("myth")
				.executes(c -> status())
				.then(ClientCommands.literal("login")
						.then(ClientCommands.argument("user", StringArgumentType.word())
								.then(ClientCommands.argument("password", StringArgumentType.greedyString())
										.executes(c -> login(StringArgumentType.getString(c, "user"), StringArgumentType.getString(c, "password"))))))
				.then(ClientCommands.literal("logout").executes(c -> logout()))
				.then(ClientCommands.literal("url")
						.then(ClientCommands.argument("url", StringArgumentType.greedyString())
								.executes(c -> {
									Mythos.config().backendUrl = StringArgumentType.getString(c, "url").trim();
									Mythos.config().save();
									Mythos.chat("Backend: " + Mythos.config().backendUrl);
									return 1;
								})))
				.then(ClientCommands.literal("toggle")
						.then(ClientCommands.argument("feature", StringArgumentType.word())
								.suggests((c, b) -> {
									for (String f : FEATURES) b.suggest(f);
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
						.then(ClientCommands.argument("x", IntegerArgumentType.integer(0))
								.then(ClientCommands.argument("y", IntegerArgumentType.integer(0))
										.executes(c -> {
											Mythos.config().hudX = IntegerArgumentType.getInteger(c, "x");
											Mythos.config().hudY = IntegerArgumentType.getInteger(c, "y");
											Mythos.config().save();
											return 1;
										})))));
	}

	private static int status() {
		Config c = Mythos.config();
		Mythos.chat("Backend " + c.backendUrl + " - " + (Mythos.backend().loggedIn() ? "logged in as " + c.username : "not logged in (/myth login <user> <password>)"));
		String err = Mythos.backend().lastError();
		if (err != null) Mythos.chat("§cLast error: " + err);
		StringBuilder sb = new StringBuilder("Features:");
		for (String f : FEATURES) sb.append(toggle(f).get(c) ? " §a" : " §8").append(f);
		Mythos.chat(sb.toString());
		Mythos.chat("Forge " + c.forgeSlots + " slots, QF " + c.quickForgeLevel + (c.coleMayor ? ", Cole" : "")
				+ " - fusion " + c.fusionSellMode + ", PR " + c.pureReptileLevel + (Mythos.active() ? "" : " - §einactive (not on Hypixel)"));
		return 1;
	}

	private static int login(String user, String password) {
		JsonObject body = new JsonObject();
		body.addProperty("username", user);
		body.addProperty("password", password);
		body.addProperty("client", "mod");
		Mythos.config().sessionToken = "";
		Mythos.backend().post("/api/auth/login", body, j -> {
			Config c = Mythos.config();
			c.sessionToken = Json.str(j, "token", "");
			c.username = Json.str(Json.obj(j, "user"), "name", user);
			c.save();
			Mythos.chat("Logged in as " + c.username + " (session lasts " + (int) Json.num(j, "expires_days", 30) + " days unused)");
			ShardIndex.refresh(true);
		}, err -> Mythos.chat("§cLogin failed: " + err));
		return 1;
	}

	private static int logout() {
		Consumer<Object> clear = x -> {
			Mythos.config().sessionToken = "";
			Mythos.config().save();
			Mythos.chat("Logged out");
		};
		Mythos.backend().post("/api/auth/logout", new JsonObject(), clear::accept, clear::accept);
		return 1;
	}

	private static int toggle(CommandContext<FabricClientCommandSource> ctx) {
		String name = StringArgumentType.getString(ctx, "feature");
		Toggle t = toggle(name);
		if (t == null) {
			Mythos.chat("Unknown feature. One of: " + String.join(", ", FEATURES));
			return 0;
		}
		Config c = Mythos.config();
		t.set(c, !t.get(c));
		c.save();
		Mythos.chat(name + " " + (t.get(c) ? "§aon" : "§8off"));
		return 1;
	}
}
