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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Slayer profit tracker (Sven first): a session of slayer quests, sent to MagicGrind, which compares it with what
 * it expected. Display-only: nothing is clicked or automated.
 *
 * <ul>
 *   <li>bosses: "NICE! SLAYER BOSS SLAIN!", tier from the scoreboard's "Slayer Quest" lines</li>
 *   <li>quests: "SLAYER QUEST STARTED!"; their cost is the purse drop that goes with it</li>
 *   <li>mobs and minibosses: by their name tag; "hit" = you hit it and it died, "near" = it died within 10 blocks
 *       while your quest was active (AoE kills, but also other players')</li>
 *   <li>coins: purse gains within 3 s of a kill; items: inventory gains and "[Sacks]" messages within 10 s</li>
 *   <li>time: while hitting or killing (paused after 60 s without); a new session after 10 minutes, or for
 *       another slayer</li>
 * </ul>
 */
public final class SlayerTracker {
	private static final long IDLE_MS = 60_000, NEW_SESSION_MS = 10 * 60_000, SEND_MS = 15_000;
	private static final double NEAR = 10, SCAN = 16;
	private static final Set<String> BOSSES = Set.of("Revenant Horror", "Tarantula Broodfather", "Sven Packmaster",
			"Voidgloom Seraph", "Inferno Demonlord", "Riftstalker Bloodfiend", "Atoned Horror");

	private static String id, slayer, area = "";
	private static long started, activeMs, lastTick, lastActivity, lastKill, lastSent, lastScreenOpen, questStartAt, purseDropAt;
	private static int ticks, failed, costSeen;
	private static double coins, cost, purseDrop;
	private static boolean dirty, questPending, costPending;
	private static Double lastPurse;
	private static MagicGrindParse.SlayerQuest quest, lastQuest, bossTag;
	private static final Map<String, Integer> BOSS_KILLS = new TreeMap<>(), QUESTS = new TreeMap<>();
	private static final Map<String, Integer> ITEMS = new TreeMap<>(), SACKS = new TreeMap<>();
	private static final Map<String, int[]> MOBS = new TreeMap<>();
	private static final Map<String, Double> AREAS = new TreeMap<>();
	private static final Map<String, Long> BEST_START = new TreeMap<>(), BEST_NOW = new TreeMap<>();
	private static final List<JsonObject> RARE = new ArrayList<>();
	private static final Map<Integer, Long> HIT = new HashMap<>();
	private static final Map<Integer, String> NAMES = new HashMap<>();
	private static final Set<Integer> COUNTED = new HashSet<>();
	private static Map<String, Integer> inventory;
	private static String weapon = "";
	private static volatile JsonObject summary;

	private SlayerTracker() {}

	public static void accept(JsonObject answer) {
		if (answer.has("ended") && answer.get("ended").getAsBoolean()) {
			reset();
			return;
		}
		summary = answer;
	}

	private static void reset() {
		id = slayer = null;
		started = activeMs = lastActivity = lastKill = 0;
		failed = costSeen = 0;
		coins = cost = 0;
		questPending = costPending = false;
		BOSS_KILLS.clear();
		QUESTS.clear();
		ITEMS.clear();
		SACKS.clear();
		MOBS.clear();
		AREAS.clear();
		BEST_START.clear();
		BEST_NOW.clear();
		RARE.clear();
		HIT.clear();
		COUNTED.clear();
		summary = null;
	}

	private static void start(long now, String which) {
		reset();
		id = UUID.randomUUID().toString();
		started = lastActivity = now;
		slayer = which;
	}

	private static boolean on() {
		return Mythos.config().magicgrind && Mythos.active();
	}

	/** The mob's name from its own custom name or the name-tag armor stand just above it. */
	private static String nameOf(Entity mob) {
		if (mob.hasCustomName()) {
			String n = MagicGrindParse.mobName(Items.plain(mob.getCustomName()));
			if (n != null) return n;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		String best = null;
		double bestDy = Double.MAX_VALUE;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!e.hasCustomName() || !"armor_stand".equals(EntityType.getKey(e.getType()).getPath())) continue;
			double dx = e.getX() - mob.getX(), dz = e.getZ() - mob.getZ(), dy = e.getY() - mob.getY();
			if (dx * dx + dz * dz > 1.0 || dy < -0.5 || dy > 3.5) continue;
			String n = MagicGrindParse.mobName(Items.plain(e.getCustomName()));
			if (n != null && dy < bestDy) {
				best = n;
				bestDy = dy;
			}
		}
		return best;
	}

	private static boolean isMob(Entity e) {
		return e instanceof LivingEntity && !(e instanceof Player) && !"armor_stand".equals(EntityType.getKey(e.getType()).getPath());
	}

	/** You hit something: remember it so its death counts as your kill. */
	public static void onAttack(Entity target) {
		if (!on() || target == null || !isMob(target) || (quest == null && id == null)) return;
		long now = System.currentTimeMillis();
		HIT.put(target.getId(), now);
		lastActivity = now;
		String n = nameOf(target);
		if (n != null) {
			NAMES.put(target.getId(), n);
			// your boss's tag ("Sven Packmaster IV") gives the tier when the scoreboard can't be read
			MagicGrindParse.SlayerQuest tag = MagicGrindParse.bossTag(n);
			if (tag != null) {
				bossTag = tag;
				String which = MagicGrindParse.slayerOf(tag.boss());
				if (id != null && slayer == null && !which.equals("vampire")) slayer = which;
				if (questPending && quest == null) {
					QUESTS.merge(Integer.toString(tag.tier()), 1, Integer::sum);
					questPending = false;
				}
				dirty = true;
			}
		}
		var player = Minecraft.getInstance().player;
		if (player != null) weapon = Items.name(player.getMainHandItem());
	}

	public static void onChat(Component message) {
		if (!on()) return;
		String text = Items.plain(message).trim();
		long now = System.currentTimeMillis();
		// the quest messages come as one block with separator lines: look at every line
		boolean started = false, slain = false, failedNow = false;
		for (String raw : text.split("\\R")) {
			String l = raw.trim();
			started |= l.startsWith("SLAYER QUEST STARTED");
			slain |= l.contains("SLAYER BOSS SLAIN");
			failedNow |= l.startsWith("SLAYER QUEST FAILED");
		}
		if (slain && id != null) {
			MagicGrindParse.SlayerQuest q = quest != null ? quest : bossTag != null ? bossTag : lastQuest;
			if (q != null) BOSS_KILLS.merge(Integer.toString(q.tier()), 1, Integer::sum);
			else BOSS_KILLS.merge("0", 1, Integer::sum);      // tier unknown: MagicGrind uses the session's main tier
			lastActivity = lastKill = now;
			dirty = true;
		}
		if (failedNow && id != null) {
			failed++;
			dirty = true;
		}
		if (started) {
			if (id == null || now - lastActivity > NEW_SESSION_MS) start(now, slayer);
			questPending = true;
			questStartAt = now;
			lastActivity = now;
			if (now - purseDropAt < 3000 && purseDrop > 0) {
				// the purse went down just before the message
				cost += purseDrop;
				costSeen++;
				purseDrop = 0;
			} else {
				costPending = true;
			}
			dirty = true;
		}
		if (started || slain || failedNow || id == null || now - lastActivity > IDLE_MS) return;
		if (text.startsWith("[Sacks]") && now - lastKill < 10_000) {
			Set<String> hovers = new LinkedHashSet<>();
			hovers(message, hovers);
			for (String h : hovers)
				for (Map.Entry<String, Integer> e : MagicGrindParse.sackItems(h).entrySet()) SACKS.merge(e.getKey(), e.getValue(), Integer::sum);
			dirty = true;
		}
		for (String raw : text.split("\\R")) {
			MagicGrindParse.RareDrop d = MagicGrindParse.rareDrop(raw);
			if (d == null) continue;
			JsonObject o = new JsonObject();
			o.addProperty("item", d.item());
			o.addProperty("mf", d.magicFind());
			o.addProperty("ts", now / 1000.0);
			RARE.add(o);
			dirty = true;
		}
	}

	private static void hovers(Component c, Set<String> out) {
		HoverEvent h = c.getStyle().getHoverEvent();
		if (h instanceof HoverEvent.ShowText(Component value)) out.add(value.getString());
		for (Component sib : c.getSiblings()) hovers(sib, out);
	}

	/** Bestiary pages: "Kills:" per mob, to check the kill counts against the game's own. */
	public static void onMenu(Menu menu) {
		if (id == null || !menu.title.startsWith("Bestiary")) return;
		for (Menu.Entry e : menu.container) {
			var t = MagicGrindParse.bestiaryTier(e.name());
			if (t == null) continue;
			long k = MagicGrindParse.bestiaryKills(e.lore());
			if (k < 0) continue;
			int[] c = MOBS.get(t.getKey());
			BEST_NOW.put(t.getKey(), k);
			BEST_START.putIfAbsent(t.getKey(), k - (c == null ? 0 : c[0] + c[1]));
			dirty = true;
		}
	}

	public static void tick() {
		if (!on()) return;
		Minecraft mc = Minecraft.getInstance();
		long now = System.currentTimeMillis();
		if (mc.gui.screen() != null) lastScreenOpen = now;
		long dt = lastTick == 0 ? 0 : Math.min(1000, now - lastTick);
		lastTick = now;
		if (mc.level == null || mc.player == null) return;

		if (++ticks % 20 == 0) {
			List<String> sb = Scoreboard.lines();
			quest = MagicGrindParse.slayerQuest(sb);
			// the Rift's slayer isn't tracked (MagicGrind leaves the Rift out)
			if (quest != null && MagicGrindParse.slayerOf(quest.boss()).equals("vampire")) quest = null;
			area = MagicGrindParse.location(sb);
			if (area.isEmpty()) {
				for (String t : dev.aplseed.mythos.util.TabList.lines()) {
					String c = MagicGrindParse.clean(t);
					if (c.startsWith("Area:")) area = c.substring(5).trim();
				}
			}
			if (quest != null) {
				String which = MagicGrindParse.slayerOf(quest.boss());
				if (id == null || now - lastActivity > NEW_SESSION_MS) start(now, which);
				else if (slayer == null) slayer = which;
				else if (!slayer.equals(which)) {
					// another slayer: its own session (the quest just started belongs to it)
					boolean pending = questPending, paying = costPending;
					long startedAt = questStartAt;
					start(now, which);
					questPending = pending;
					costPending = paying;
					questStartAt = startedAt;
				}
				if (questPending) {
					QUESTS.merge(Integer.toString(quest.tier()), 1, Integer::sum);
					questPending = false;
					dirty = true;
				}
				lastQuest = quest;
			}
		}
		if (id == null) return;
		if (now - lastActivity > NEW_SESSION_MS && quest == null) {
			reset();
			return;
		}
		boolean active = now - lastActivity < IDLE_MS;
		if (active) {
			activeMs += dt;
			if (!area.isEmpty()) AREAS.merge(area, dt / 1000.0, Double::sum);
		}

		// name tags of mobs around you, so deaths can be told apart (Old Wolf, Pack Enforcer...)
		if (ticks % 10 == 0 && (quest != null || active)) {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (!isMob(e) || NAMES.containsKey(e.getId()) || e.distanceTo(mc.player) > SCAN) continue;
				String n = nameOf(e);
				if (n != null) NAMES.put(e.getId(), n);
			}
		}
		// deaths: yours if you hit it, "nearby" if it died close to you during your quest
		for (Iterator<Map.Entry<Integer, String>> it = NAMES.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, String> n = it.next();
			Entity e = mc.level.getEntity(n.getKey());
			if (e == null || e.isRemoved()) {
				it.remove();
				HIT.remove(n.getKey());
				continue;
			}
			if (!(e instanceof LivingEntity le) || !le.isDeadOrDying() || !COUNTED.add(e.getId())) continue;
			it.remove();
			Long hit = HIT.remove(e.getId());
			String name = n.getValue();
			if (MagicGrindParse.bossTag(name) != null || BOSSES.contains(name)) continue;   // bosses are counted from chat
			boolean mine = hit != null && now - hit < 30_000;
			boolean near = !mine && quest != null && e.distanceTo(mc.player) <= NEAR;
			if (!mine && !near) continue;
			MOBS.computeIfAbsent(name, k -> new int[2])[mine ? 0 : 1]++;
			lastActivity = lastKill = now;
			dirty = true;
		}
		if (COUNTED.size() > 2000) COUNTED.clear();
		HIT.values().removeIf(t -> now - t > 30_000);

		// coins: purse gains right after a kill; quest costs: the drop that goes with "SLAYER QUEST STARTED!"
		if (ticks % 5 == 0) {
			OptionalDouble p = MagicGrindParse.purse(Scoreboard.lines());
			if (p.isPresent()) {
				double v = p.getAsDouble();
				if (lastPurse != null) {
					double delta = v - lastPurse;
					if (delta > 0 && delta < 1_000_000 && now - lastKill < 3000) {
						coins += delta;
						dirty = true;
					} else if (delta < 0) {
						if (costPending && now - questStartAt < 3000) {
							cost += -delta;
							costSeen++;
							costPending = false;
							dirty = true;
						} else {
							purseDrop = -delta;
							purseDropAt = now;
						}
					}
				}
				lastPurse = v;
			}
			if (costPending && now - questStartAt > 3000) costPending = false;   // MagicGrind uses the list price
		}

		// items: inventory gains outside menus, shortly after kills
		if (ticks % 10 == 0) {
			Map<String, Integer> inv = new HashMap<>();
			var pinv = mc.player.getInventory();
			for (int i = 0; i < 36; i++) {
				ItemStack s = pinv.getItem(i);
				if (s.isEmpty()) continue;
				String key = Items.skyblockId(s);
				if (key.isEmpty()) continue;
				inv.merge(key, s.getCount(), Integer::sum);
			}
			if (inventory != null && mc.gui.screen() == null && now - lastScreenOpen > 1500 && now - lastKill < 10_000) {
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

		if (slayer != null && MagicGrindClient.configured() && (dirty && now - lastSent > SEND_MS || now - lastSent > 60_000)) {
			dirty = false;
			lastSent = now;
			MagicGrindClient.sendSession(payload(), SlayerTracker::accept);
		}
	}

	private static JsonObject payload() {
		JsonObject j = new JsonObject();
		j.addProperty("id", id);
		j.addProperty("kind", "slayer");
		j.addProperty("slayer", slayer);
		j.addProperty("grind", "Slayer:" + slayer);
		j.addProperty("sacks_deduped", true);
		j.addProperty("started", started / 1000.0);
		j.addProperty("active_seconds", activeMs / 1000.0);
		j.addProperty("coins", coins);
		j.addProperty("cost", cost);
		j.addProperty("cost_seen", costSeen);
		j.addProperty("failed", failed);
		j.addProperty("weapon", weapon.replaceAll("[^\\p{L}\\p{N} '-]", "").trim());
		j.add("bosses", counts(BOSS_KILLS));
		j.add("quests", counts(QUESTS));
		j.add("items", counts(ITEMS));
		j.add("sack_items", counts(SACKS));
		JsonObject mobs = new JsonObject();
		MOBS.forEach((k, v) -> {
			JsonObject o = new JsonObject();
			o.addProperty("hit", v[0]);
			o.addProperty("near", v[1]);
			mobs.add(k, o);
		});
		j.add("mobs", mobs);
		JsonObject areas = new JsonObject();
		AREAS.forEach(areas::addProperty);
		j.add("areas", areas);
		JsonArray rare = new JsonArray();
		RARE.forEach(rare::add);
		j.add("rare_drops", rare);
		if (!BEST_NOW.isEmpty()) {
			JsonObject b0 = new JsonObject(), b1 = new JsonObject();
			BEST_START.forEach(b0::addProperty);
			BEST_NOW.forEach(b1::addProperty);
			j.add("bestiary_start", b0);
			j.add("bestiary_now", b1);
		}
		return j;
	}

	private static JsonObject counts(Map<String, Integer> m) {
		JsonObject o = new JsonObject();
		m.forEach(o::addProperty);
		return o;
	}

	public static void renderHud(GuiGraphicsExtractor g) {
		if (!on() || id == null || slayer == null || (quest == null && System.currentTimeMillis() - lastActivity > IDLE_MS)) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden()) return;
		Font font = mc.font;
		List<String> lines = new ArrayList<>();
		long min = activeMs / 60_000;
		int bosses = BOSS_KILLS.values().stream().mapToInt(Integer::intValue).sum();
		MagicGrindParse.SlayerQuest shown = lastQuest != null ? lastQuest : bossTag;
		String boss = shown != null ? shown.boss() + " " + roman(shown.tier()) : "Slayer";
		lines.add(boss + "  " + bosses + (bosses == 1 ? " boss · " : " bosses · ") + (min >= 60 ? min / 60 + "h " + min % 60 + "m" : min + "m"));
		JsonObject s = summary;
		if (s != null && s.has("per_hour_actual") && !s.get("per_hour_actual").isJsonNull()) {
			lines.add("Profit " + Fmt.coins(s.get("per_hour_actual").getAsDouble()) + "/h");
			if (!s.get("per_hour_expected").isJsonNull()) lines.add("Expected " + Fmt.coins(s.get("per_hour_expected").getAsDouble()) + "/h");
			if (s.has("per_boss_actual") && !s.get("per_boss_actual").isJsonNull()) lines.add("Per boss " + Fmt.coins(s.get("per_boss_actual").getAsDouble()));
			if (!s.get("ratio").isJsonNull()) lines.add("Drops " + Math.round(s.get("ratio").getAsDouble() * 100) + "% of expected");
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

	private static String roman(int n) {
		return switch (n) {
			case 1 -> "I";
			case 2 -> "II";
			case 3 -> "III";
			case 4 -> "IV";
			case 5 -> "V";
			default -> Integer.toString(n);
		};
	}
}
