package dev.aplseed.mythos.magicgrind;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.util.Items;
import dev.aplseed.mythos.util.TabList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Gemstone Gauntlet "Kinetic": kills on Mining Islands take 0.5 s per Perfect Gemstone off every Forge slot
 * (1 s cooldown since Jan 2026). To check how it really behaves (it once applied on every hit), this counts hits
 * and kills made with the Gauntlet, each also with the 1 s cooldown applied, and records the Forge's
 * "Time Remaining:" whenever The Forge is opened. MagicGrind compares the drop in Forge time with each way
 * Kinetic could work. Display-only.
 */
public final class KineticTracker {
	private static final long COOLDOWN_MS = 1000, NEW_SESSION_MS = 10 * 60_000;

	private static String id;
	private static long started, lastKill, lastHit, lastCdKill, lastCdHit, lastSent;
	private static int hits, kills, cdHits, cdKills, perfects = -1, ticks;
	private static boolean onIsland, dirty;
	private static final Map<Integer, Long> HIT = new HashMap<>();
	private static final JsonArray SNAPSHOTS = new JsonArray();

	private KineticTracker() {}

	private static boolean on() {
		return Mythos.config().magicgrind && Mythos.active();
	}

	private static boolean gauntlet(ItemStack s) {
		return "GEMSTONE_GAUNTLET".equals(Items.skyblockId(s));
	}

	/** Perfect Gemstones socketed in the Gauntlet: its SkyBlock data's "gems" ("JADE_0": "PERFECT" or {quality: PERFECT}). */
	static int perfectGems(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		if (d == null) return -1;
		CompoundTag gems = d.copyTag().getCompoundOrEmpty("gems");
		int n = 0;
		for (String k : gems.keySet()) {
			if (k.endsWith("_gem") || k.equals("unlocked_slots")) continue;
			String q = gems.getStringOr(k, "");
			if (q.isEmpty()) q = gems.getCompoundOrEmpty(k).getStringOr("quality", "");
			if (q.equals("PERFECT")) n++;
		}
		return n;
	}

	public static void onAttack(Entity target) {
		if (!on() || !onIsland || !(target instanceof LivingEntity)) return;
		var player = Minecraft.getInstance().player;
		if (player == null || !gauntlet(player.getMainHandItem())) return;
		long now = System.currentTimeMillis();
		if (id == null || now - Math.max(lastHit, lastKill) > NEW_SESSION_MS) start(now);
		perfects = perfectGems(player.getMainHandItem());
		hits++;
		if (now - lastCdHit >= COOLDOWN_MS) {
			cdHits++;
			lastCdHit = now;
		}
		lastHit = now;
		HIT.put(target.getId(), now);
		dirty = true;
	}

	private static void start(long now) {
		id = UUID.randomUUID().toString();
		started = now;
		hits = kills = cdHits = cdKills = 0;
		lastCdHit = lastCdKill = 0;
		HIT.clear();
		while (!SNAPSHOTS.isEmpty()) SNAPSHOTS.remove(0);
	}

	/** The Forge: every running slot's "Time Remaining:", with the counters at that moment. */
	public static void onMenu(Menu menu) {
		if (!on() || !menu.title.equals("The Forge")) return;
		JsonObject slots = new JsonObject();
		for (Menu.Entry e : menu.container) {
			if (e.lore().stream().noneMatch(l -> l.startsWith("Currently making:"))) continue;
			String left = e.lore().stream().filter(l -> l.startsWith("Time Remaining:")).findFirst().orElse("");
			long s = MagicGrindParse.seconds(left);
			if (s >= 0) slots.addProperty(Integer.toString(e.slot().index), s);
		}
		if (slots.isEmpty()) return;
		long now = System.currentTimeMillis();
		if (id == null) start(now);
		// one snapshot per opening: the menu updates every second while open, keep the first
		if (!SNAPSHOTS.isEmpty() && now - SNAPSHOTS.get(SNAPSHOTS.size() - 1).getAsJsonObject().get("ts").getAsDouble() * 1000 < 20_000) return;
		JsonObject snap = new JsonObject();
		snap.addProperty("ts", now / 1000.0);
		snap.addProperty("hits", hits);
		snap.addProperty("kills", kills);
		snap.addProperty("cd_hits", cdHits);
		snap.addProperty("cd_kills", cdKills);
		snap.add("slots", slots);
		SNAPSHOTS.add(snap);
		dirty = true;
		lastSent = 0;     // send now
	}

	public static void tick() {
		if (!on()) return;
		Minecraft mc = Minecraft.getInstance();
		if (++ticks % 20 == 0) onIsland = MagicGrindParse.onMiningIsland(TabList.lines());
		if (mc.level == null || id == null) return;
		long now = System.currentTimeMillis();
		for (Iterator<Map.Entry<Integer, Long>> it = HIT.entrySet().iterator(); it.hasNext(); ) {
			Map.Entry<Integer, Long> h = it.next();
			Entity e = mc.level.getEntity(h.getKey());
			if (e instanceof LivingEntity le && le.isDeadOrDying()) {
				kills++;
				if (now - lastCdKill >= COOLDOWN_MS) {
					cdKills++;
					lastCdKill = now;
				}
				lastKill = now;
				dirty = true;
				it.remove();
			} else if (e == null || e.isRemoved() || now - h.getValue() > 30_000) {
				it.remove();
			}
		}
		if (MagicGrindClient.configured() && dirty && now - lastSent > 15_000) {
			dirty = false;
			lastSent = now;
			MagicGrindClient.post("/api/mod/kinetic", payload());
		}
	}

	private static JsonObject payload() {
		JsonObject j = new JsonObject();
		j.addProperty("id", id);
		j.addProperty("started", started / 1000.0);
		j.addProperty("hits", hits);
		j.addProperty("kills", kills);
		j.addProperty("cd_hits", cdHits);
		j.addProperty("cd_kills", cdKills);
		j.addProperty("perfects", perfects);
		j.add("snapshots", SNAPSHOTS.deepCopy());
		return j;
	}
}
