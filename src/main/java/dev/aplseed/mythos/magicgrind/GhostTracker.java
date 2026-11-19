package dev.aplseed.mythos.magicgrind;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.ui.Theme;
import dev.aplseed.mythos.util.Fmt;
import dev.aplseed.mythos.util.Items;
import dev.aplseed.mythos.util.Scoreboard;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * Ghost profit tracker: a session of Ghost farming in The Mist, sent to MagicGrind, which compares it with what it
 * expected. Display-only: nothing is clicked or automated.
 *
 * <ul>
 *   <li>kills: Ghosts (invisible creepers) you hit that then die</li>
 *   <li>coins: purse increases on the scoreboard within 3 s of a kill</li>
 *   <li>items: inventory gains while not in a menu, plus "[Sacks]" messages, within 10 s of a kill</li>
 *   <li>rare drops: RARE DROP lines; Bestiary "Kills:" for Ghost as a cross-check of the kill count</li>
 *   <li>time: only while in The Mist with a kill in the last 60 s; a new session after 10 minutes without a kill</li>
 * </ul>
 */
public final class GhostTracker {
	private static final long IDLE_MS = 60_000, NEW_SESSION_MS = 10 * 60_000, SEND_MS = 15_000;

	private static String id;
	private static long started, activeMs, lastTick, lastKill, lastSent, lastScreenOpen;
	private static int kills;
	private static double coins;
	private static Double lastPurse;
	private static final Map<String, Integer> ITEMS = new TreeMap<>();
	private static final Map<String, Integer> SACKS = new TreeMap<>();
	private static final List<JsonObject> RARE = new ArrayList<>();
	private static final Map<Integer, Long> HIT = new HashMap<>();
	private static Map<String, Integer> inventory;
	private static long bestiaryStart = -1, bestiaryNow = -1;
	private static boolean inMist, dirty;
	private static int ticks;
	private static String weapon = "";
	/** what MagicGrind answered last: shown on the HUD */
	private static volatile JsonObject summary;

	private GhostTracker() {}

	public static void accept(JsonObject answer) {
		if (answer.has("ended") && answer.get("ended").getAsBoolean()) {
			reset();
			return;
		}
		summary = answer;
	}

	private static void reset() {
		id = null;
		started = activeMs = lastKill = 0;
		kills = 0;
		coins = 0;
		ITEMS.clear();
		SACKS.clear();
		RARE.clear();
		HIT.clear();
		bestiaryStart = bestiaryNow = -1;
		summary = null;
	}

	private static boolean on() {
		return Mythos.config().magicgrind && Mythos.active();
	}

	/** You hit something: remember Ghosts so their death counts as your kill. */
	public static void onAttack(Entity target) {
		// Ghosts are invisible charged creepers; compare the registry id, which is stable across versions
		if (!on() || !inMist || target == null || !"creeper".equals(EntityType.getKey(target.getType()).getPath())) return;
		HIT.put(target.getId(), System.currentTimeMillis());
		var player = Minecraft.getInstance().player;
		if (player != null) weapon = Items.name(player.getMainHandItem());
	}

	public static void onChat(Component message) {
		if (!on() || !inMist || id == null) return;
		String text = Items.plain(message);
		long now = System.currentTimeMillis();
		if (text.startsWith("[Sacks]") && now - lastKill < 10_000) {
			for (Map.Entry<String, Integer> e : MagicGrindParse.sackItems(hover(message)).entrySet()) SACKS.merge(e.getKey(), e.getValue(), Integer::sum);
			dirty = true;
		}
		MagicGrindParse.RareDrop d = MagicGrindParse.rareDrop(text);
		if (d != null) {
			JsonObject o = new JsonObject();
			o.addProperty("item", d.item());
			o.addProperty("mf", d.magicFind());
			o.addProperty("ts", now / 1000.0);
			RARE.add(o);
			dirty = true;
		}
	}

	/** All hover text inside a chat message (the [Sacks] summary keeps its item list there). */
	private static String hover(Component c) {
		StringBuilder sb = new StringBuilder();
		HoverEvent h = c.getStyle().getHoverEvent();
		if (h instanceof HoverEvent.ShowText(Component value)) sb.append(value.getString()).append('\n');
		for (Component sib : c.getSiblings()) sb.append(hover(sib));
		return sb.toString();
	}

	/** Bestiary pages: Ghost's "Kills:" line, to check the kill count against the game's own. */
	public static void onMenu(Menu menu) {
		if (!menu.title.startsWith("Bestiary")) return;
		for (Menu.Entry e : menu.container) {
			var t = MagicGrindParse.bestiaryTier(e.name());
			if (t == null || !t.getKey().equals("Ghost")) continue;
			long k = MagicGrindParse.bestiaryKills(e.lore());
			if (k < 0) continue;
			bestiaryNow = k;
			if (bestiaryStart < 0) bestiaryStart = k - kills;     // opened mid-session: what it was when the session began
			dirty = true;
		}
	}

	public static void tick() {
		if (!on()) return;
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (mc.gui.screen() != null) lastScreenOpen = now;
		if (++ticks % 20 == 0) inMist = Scoreboard.lines().stream().anyMatch(l -> l.contains("The Mist"));
		long dt = lastTick == 0 ? 0 : Math.min(1000, now - lastTick);
		lastTick = now;
		if (!inMist || mc.level == null) return;
		if (id != null && now - lastKill > NEW_SESSION_MS) reset();

		// kills: Ghosts you hit that are now dying
		for (Iterator<Map.Entry<Integer, Long>> it = HIT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Long> h = it.next();
			Entity e = mc.level.getEntity(h.getKey());
			if (e instanceof LivingEntity le && le.isDeadOrDying()) {
				if (id == null) {
					id = UUID.randomUUID().toString();
					started = now;
				}
				kills++;
				lastKill = now;
				dirty = true;
				it.remove();
			} else if (e == null || e.isRemoved() || now - h.getValue() > 30_000) {
				it.remove();
			}
		}
		if (id != null && now - lastKill < IDLE_MS) activeMs += dt;

		// coins: purse gains right after a kill
		if (ticks % 5 == 0) {
			OptionalDouble p = MagicGrindParse.purse(Scoreboard.lines());
			if (p.isPresent()) {
				double v = p.getAsDouble();
				if (lastPurse != null && id != null) {
					double delta = v - lastPurse;
					if (delta > 0 && delta < 1_000_000 && now - lastKill < 3000) {
						coins += delta;
						dirty = true;
					}
				}
				lastPurse = v;
			}
		}

		// items: inventory gains outside menus, shortly after kills
		if (ticks % 10 == 0 && mc.player != null) {
			Map<String, Integer> inv = new HashMap<>();
			var pinv = mc.player.getInventory();
			for (int i = 0; i < 36; i++) {
				ItemStack s = pinv.getItem(i);
				if (s.isEmpty()) continue;
				String key = Items.skyblockId(s);
				if (key.isEmpty()) continue;
				inv.merge(key, s.getCount(), Integer::sum);
			}
			if (inventory != null && id != null && mc.gui.screen() == null && now - lastScreenOpen > 1500 && now - lastKill < 10_000) {
				for (Map.Entry<String, Integer> e : inv.entrySet()) {
					int gained = e.getValue() - inventory.getOrDefault(e.getKey(), 0);
					if (gained > 0) {
						ITEMS.merge(e.getKey(), gained, Integer::sum);
						dirty = true;
					}
				}
			}
			inventory = inv;
		}

		if (id != null && MagicGrindClient.configured() && (dirty && now - lastSent > SEND_MS || now - lastSent > 60_000)) {
			dirty = false;
			lastSent = now;
			MagicGrindClient.sendSession(payload());
		}
	}

	private static JsonObject payload() {
		JsonObject j = new JsonObject();
		j.addProperty("id", id);
		j.addProperty("grind", "Ghost");
		j.addProperty("started", started / 1000.0);
		j.addProperty("active_seconds", activeMs / 1000.0);
		j.addProperty("kills", kills);
		j.addProperty("coins", coins);
		j.addProperty("weapon", weapon.replaceAll("[^\\p{L}\\p{N} '-]", "").trim());
		JsonObject items = new JsonObject();
		ITEMS.forEach(items::addProperty);
		j.add("items", items);
		JsonObject sacks = new JsonObject();
		SACKS.forEach(sacks::addProperty);
		j.add("sack_items", sacks);
		JsonArray rare = new JsonArray();
		RARE.forEach(rare::add);
		j.add("rare_drops", rare);
		if (bestiaryStart >= 0) {
			JsonObject b0 = new JsonObject();
			b0.addProperty("kills", bestiaryStart);
			j.add("bestiary_start", b0);
			JsonObject b1 = new JsonObject();
			b1.addProperty("kills", bestiaryNow);
			j.add("bestiary_now", b1);
		}
		return j;
	}

	public static void renderHud(GuiGraphicsExtractor g) {
		if (!on() || id == null || !inMist) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden()) return;
		Font font = mc.font;
		List<String> lines = new ArrayList<>();
		long min = activeMs / 60_000;
		lines.add("Ghosts  " + kills + " kills · " + (min >= 60 ? min / 60 + "h " + min % 60 + "m" : min + "m"));
		JsonObject s = summary;
		if (s != null && !s.get("per_hour_actual").isJsonNull()) {
			lines.add("Actual " + Fmt.coins(s.get("per_hour_actual").getAsDouble()) + "/h");
			if (!s.get("per_hour_expected").isJsonNull()) lines.add("Expected " + Fmt.coins(s.get("per_hour_expected").getAsDouble()) + "/h");
			if (!s.get("ratio").isJsonNull()) lines.add(Math.round(s.get("ratio").getAsDouble() * 100) + "% of expected");
		} else {
			lines.add("Coins " + Fmt.coins(coins) + " · sending to MagicGrind");
		}
		int w = 0;
		for (String l : lines) w = Math.max(w, font.width(l));
		int h = lines.size() * (font.lineHeight + 1) + 7;
		int x = Math.max(0, Math.min(Mythos.config().hudX, g.guiWidth() - w - 10));
		int y = Math.max(0, Math.min(Mythos.config().hudY + 70, g.guiHeight() - h));
		Theme.frame(g, x, y, w + 10, h);
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), x + 5, y + 5 + i * (font.lineHeight + 1), i == 0 ? Theme.AQUA : Theme.TEXT, false);
		}
	}
}
