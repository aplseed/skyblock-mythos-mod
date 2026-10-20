package dev.aplseed.mythos;

import com.mojang.blaze3d.platform.InputConstants;
import dev.aplseed.mythos.data.ShardIndex;
import dev.aplseed.mythos.feature.AbsorbSync;
import dev.aplseed.mythos.feature.BazaarPanel;
import dev.aplseed.mythos.feature.ElectionPanel;
import dev.aplseed.mythos.feature.ForgeAdvisor;
import dev.aplseed.mythos.feature.FusionPlanner;
import dev.aplseed.mythos.feature.SellWarnings;
import dev.aplseed.mythos.feature.ShardTracker;
import dev.aplseed.mythos.menu.MenuOverlays;
import dev.aplseed.mythos.net.Backend;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
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
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Mythos implements ClientModInitializer {
	public static final String ID = "mythos";
	public static final Logger LOG = LoggerFactory.getLogger(ID);

	private static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath(ID, "main"));
	public static final KeyMapping DUMP_KEY = new KeyMapping("key.mythos.dump", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY);
	public static final KeyMapping OVERLAY_KEY = new KeyMapping("key.mythos.overlays", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, CATEGORY);

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
		SystemToast.addOrUpdate(Minecraft.getInstance().getToastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
				Component.literal(title), Component.literal(body));
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

		MenuOverlays.add(new FusionPlanner());
		MenuOverlays.add(new AbsorbSync());
		MenuOverlays.add(new BazaarPanel());
		MenuOverlays.add(new ForgeAdvisor());
		MenuOverlays.add(new ElectionPanel());
		MenuOverlays.init();

		Commands.register();
		ClientReceiveMessageEvents.GAME.register(ShardTracker::onChat);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (OVERLAY_KEY.consumeClick()) toggleOverlays();
			SellWarnings.tick();
			ShardTracker.tick();
		});
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			if (active() && backend.loggedIn()) ShardIndex.refresh(false);
		});
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(ID, "shard_session"), (g, delta) -> ShardTracker.renderHud(g));
	}
}
