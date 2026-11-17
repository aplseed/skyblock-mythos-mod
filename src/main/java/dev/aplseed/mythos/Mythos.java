package dev.aplseed.mythos;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aplseed.mythos.data.ShardIndex;
import dev.aplseed.mythos.feature.AbsorbSync;
import dev.aplseed.mythos.feature.BagScanner;
import dev.aplseed.mythos.feature.BazaarPanel;
import dev.aplseed.mythos.feature.CollectionScanner;
import dev.aplseed.mythos.feature.ElectionPanel;
import dev.aplseed.mythos.feature.ForgeAdvisor;
import dev.aplseed.mythos.feature.FusionPlanner;
import dev.aplseed.mythos.feature.SellWarnings;
import dev.aplseed.mythos.feature.ShardTracker;
import dev.aplseed.mythos.menu.MenuOverlays;
import dev.aplseed.mythos.net.Backend;
import dev.aplseed.mythos.ui.MythosScreen;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Mythos implements ClientModInitializer {
	public static final String ID = "mythos";
	public static final Logger LOG = LoggerFactory.getLogger(ID);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
	// key codes come from InputConstants, not GLFW: Minecraft 26.3 numbers its keys differently
	public static final KeyMapping DUMP_KEY = new KeyMapping("key.mythos.dump", InputConstants.KEY_F8, CATEGORY);
	public static final KeyMapping OVERLAY_KEY = new KeyMapping("key.mythos.overlays", InputConstants.UNKNOWN.getValue(), CATEGORY);
	public static final KeyMapping SCREEN_KEY = new KeyMapping("key.mythos.screen", InputConstants.UNKNOWN.getValue(), CATEGORY);

	/** A switchable part of the mod: its /myth toggle name, and how the Mythos window lists it. */
	public record Feature(String id, String label, String about, Predicate<Config> getter, BiConsumer<Config, Boolean> setter) {
		public boolean get(Config c) {
			return getter.test(c);
		}

		public void set(Config c, boolean on) {
			setter.accept(c, on);
		}
	}

	public static final List<Feature> FEATURES = List.of(
			new Feature("panels", "Menu panels", "Everything drawn beside menus", c -> c.overlays, (c, v) -> c.overlays = v),
			new Feature("accessories", "Accessory bag", "Scan the bag and your collections", c -> c.accessories, (c, v) -> c.accessories = v),
			new Feature("fusion", "Fusion planner", "Best fusions for the shards you hold", c -> c.fusionPlanner, (c, v) -> c.fusionPlanner = v),
			new Feature("absorb", "Absorb sync", "Absorbed counts and cost to max", c -> c.absorbSync, (c, v) -> c.absorbSync = v),
			new Feature("bazaar", "Bazaar intel", "Signals, positions, mayor effects", c -> c.bazaarPanel, (c, v) -> c.bazaarPanel = v),
			new Feature("forge", "Forge advisor", "Recipes ranked by profit per slot-hour", c -> c.forgeAdvisor, (c, v) -> c.forgeAdvisor = v),
			new Feature("election", "Election panel", "What each candidate does to prices", c -> c.electionPanel, (c, v) -> c.electionPanel = v),
			new Feature("warnings", "Sell warnings", "Chat and toast when a holding is flagged", c -> c.sellWarnings, (c, v) -> c.sellWarnings = v),
			new Feature("shards", "Shard sessions", "Catch-rate HUD while hunting", c -> c.shardTracker, (c, v) -> c.shardTracker = v));

	public static Feature feature(String id) {
		for (Feature f : FEATURES) if (f.id().equals(id)) return f;
		return null;
	}

	private static Config config;
	private static Backend backend;

	public static Config config() {
		return config;
	}

	public static Backend backend() {
		return backend;
	}

	public static Path configDir() {
		return FabricLoader.getInstance().getConfigDir().resolve(ID);
	}

	/** On Hypixel (or anywhere, with onlyOnHypixel off). */
	public static boolean active() {
		if (!config.onlyOnHypixel) return true;
		ServerData s = Minecraft.getInstance().getCurrentServer();
		return s != null && s.ip != null && s.ip.toLowerCase(Locale.ROOT).contains("hypixel");
	}

	private static final Deque<String> SENT = new ArrayDeque<>();

	/** The mod's last chat lines, newest last (read by the client game test). */
	public static List<String> sentChat() {
		return List.copyOf(SENT);
	}

	public static void chat(String msg) {
		if (SENT.size() >= 50) SENT.removeFirst();
		SENT.addLast(msg);
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§5[Mythos]§r " + msg));
	}

	public static void toast(String title, String body) {
		SystemToast.addOrUpdate(Minecraft.getInstance().gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.literal(title), Component.literal(body));
	}

	/**
	 * Opens the bazaar searching for `query` ("/bz Solar Shard") and puts `amount` on the
	 * clipboard, ready to paste into the buy order. One click, one command - nothing automated.
	 */
	public static void bazaarSearch(String query, long amount) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return;
		if (amount > 0) mc.keyboardHandler.setClipboard(Long.toString(amount));
		mc.getConnection().sendCommand("bz " + query);
		if (amount > 0) toast("/bz " + query, "Copied " + amount + " to the clipboard");
	}

	/** Opens the auction house searching for `name` ("/ahs Feather Artifact"). One click, one command. */
	public static void auctionSearch(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return;
		if (mc.gui.screen() instanceof MythosScreen) mc.gui.setScreen(null);
		mc.getConnection().sendCommand("ahs " + name);
	}

	/** Opens the game's recipe view for `name` ("/recipe Feather Artifact"). One click, one command. */
	public static void recipe(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return;
		if (mc.gui.screen() instanceof MythosScreen) mc.gui.setScreen(null);
		mc.getConnection().sendCommand("recipe " + name);
	}

	private static int widgetTicks;
	private static MythosScreen.Tab pendingScreen;
	private static boolean screenRequested;

	/** Opens the Mythos window on the next tick (a command's chat screen is still closing now). */
	public static void open(MythosScreen.Tab tab) {
		pendingScreen = tab;
		screenRequested = true;
	}

	/** Stores a new server address; returns what's wrong with it, or null if it was saved. */
	public static String setBackendUrl(String url) {
		String u = url.trim().replaceAll("/+$", "");
		try {
			URI parsed = URI.create(u);
			String scheme = parsed.getScheme();
			if (scheme == null || !(scheme.equals("http") || scheme.equals("https")) || parsed.getHost() == null) {
				return "The address must look like http://127.0.0.1:5658";
			}
		} catch (IllegalArgumentException e) {
			return "The address must look like http://127.0.0.1:5658";
		}
		config.backendUrl = u;
		config.save();
		return null;
	}

	public static void toggleOverlays() {
		config.overlays = !config.overlays;
		config.save();
		toast("Mythos", "Menu panels " + (config.overlays ? "on" : "off"));
	}

	@Override
	public void onInitializeClient() {
		config = Config.load(FabricLoader.getInstance().getConfigDir().resolve(ID + ".json"));
		backend = new Backend();

		KeyMappingHelper.registerKeyMapping(DUMP_KEY);
		KeyMappingHelper.registerKeyMapping(OVERLAY_KEY);
		KeyMappingHelper.registerKeyMapping(SCREEN_KEY);

		MenuOverlays.add(new FusionPlanner());
		MenuOverlays.add(new AbsorbSync());
		MenuOverlays.add(new BazaarPanel());
		MenuOverlays.add(new ForgeAdvisor());
		MenuOverlays.add(new ElectionPanel());
		MenuOverlays.add(new BagScanner());
		MenuOverlays.add(new CollectionScanner());
		MenuOverlays.init();

		Commands.register();
		ClientReceiveMessageEvents.GAME.register(ShardTracker::onChat);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (OVERLAY_KEY.consumeClick()) toggleOverlays();
			while (SCREEN_KEY.consumeClick()) open(null);
			if (screenRequested) {
				screenRequested = false;
				mc.gui.setScreen(new MythosScreen(pendingScreen));
			}
			Commands.tick();
			// the Accessory Bag tab widget keeps the bag's slot count current between bag visits
			if (++widgetTicks % 100 == 0 && config.accessories && active()) dev.aplseed.mythos.acc.Bag.readCounts(dev.aplseed.mythos.util.TabList.lines());
			SellWarnings.tick();
			ShardTracker.tick();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			if (!active()) return;
			if (backend.loggedIn()) ShardIndex.refresh(false);
			SellWarnings.joined();
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "shard_session"), (g, delta) -> ShardTracker.renderHud(g));
	}
}
