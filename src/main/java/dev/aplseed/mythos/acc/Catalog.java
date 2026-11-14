package dev.aplseed.mythos.acc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.aplseed.mythos.net.Json;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Every accessory in the game: rarity and use requirements from Hypixel's item
 * list (api.hypixel.net/v2/resources/skyblock/items), upgrade lines from the
 * NotEnoughUpdates repo (constants/misc.json "talisman_upgrades"). Only one
 * accessory of a line counts in the bag, so lines are grouped into families.
 */
public final class Catalog {
	/** Something the player needs before an accessory works: "slayer_wolf" 5, "hotm" 4. */
	public record Req(String key, int level) {
		public String label() {
			return Catalog.label(key) + " " + levelName(key, level);
		}
	}

	public record Acc(String id, String name, Rarity tier, List<Req> reqs, boolean canRecomb, boolean soulbound,
			boolean obtainable, String family, int rank) {
		/** Magical Power at `rarity` (the Hegemony Artifact counts twice). */
		public int mp(Rarity rarity) {
			return mult(id) * rarity.mp;
		}
	}

	private static final String[] TROPHY = {"none", "Bronze", "Silver", "Gold", "Diamond"};

	public final Map<String, Acc> byId;
	/** family -> members, lowest tier first */
	public final Map<String, List<Acc>> families;
	/** every requirement key the data uses -> highest level asked for */
	public final Map<String, Integer> reqKeys;

	private Catalog(Map<String, Acc> byId, Map<String, List<Acc>> families, Map<String, Integer> reqKeys) {
		this.byId = byId;
		this.families = families;
		this.reqKeys = reqKeys;
	}

	public static int mult(String id) {
		return "HEGEMONY_ARTIFACT".equals(id) ? 2 : 1;
	}

	/** "slayer_wolf" -> "Wolf Slayer" */
	public static String label(String key) {
		if (key.startsWith("slayer_")) return cap(key.substring(7)) + " Slayer";
		if (key.startsWith("trophy_")) return cap(key.substring(7)) + " trophy fish";
		return switch (key) {
			case "hotm" -> "Heart of the Mountain";
			case "melody" -> "Melody's Harp";
			default -> cap(key.replace('_', ' '));
		};
	}

	public static String levelName(String key, int level) {
		if (key.startsWith("trophy_")) return TROPHY[Math.max(0, Math.min(level, 4))];
		if (key.equals("melody")) return level > 0 ? "completed" : "not done";
		return Integer.toString(level);
	}

	private static String cap(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.ROOT);
	}

	/** The family an id belongs to, for ids the item list doesn't know too. */
	public String familyOf(String id) {
		Acc a = byId.get(id);
		return a != null ? a.family : hatFamily(id);
	}

	/** Party, balloon and cake hats are one line: only one of them counts. */
	private static String hatFamily(String id) {
		if (id.startsWith("PARTY_HAT_") || id.startsWith("BALLOON_HAT_") || id.startsWith("CAKE_HAT_")) return "PARTY_HAT";
		if (id.startsWith("ABICASE")) return "ABICASE";
		return id;
	}

	public String name(String id) {
		Acc a = byId.get(id);
		return a != null ? a.name : id;
	}

	private static Req req(JsonObject r) {
		String type = Json.str(r, "type", "");
		return switch (type) {
			case "SLAYER" -> new Req("slayer_" + Json.str(r, "slayer_boss_type", "?").toLowerCase(Locale.ROOT), (int) Json.num(r, "level", 1));
			case "HEART_OF_THE_MOUNTAIN" -> new Req("hotm", (int) Json.num(r, "tier", 1));
			case "TROPHY_FISHING" -> new Req("trophy_" + Json.str(r, "trophy_type", "?").toLowerCase(Locale.ROOT),
					Math.max(1, List.of(TROPHY).indexOf(cap(Json.str(r, "reward", "Bronze")))));
			case "MELODY_HAIR" -> new Req("melody", 1);
			// anything newer: a yes/no unlock the player ticks off
			default -> type.isEmpty() ? null : new Req(type.toLowerCase(Locale.ROOT), 1);
		};
	}

	/** `items`: the Hypixel response; `misc`: NEU's constants/misc.json. */
	public static Catalog build(JsonObject items, JsonObject misc) {
		JsonObject upgrades = Json.obj(misc, "talisman_upgrades");
		Set<String> ignored = new HashSet<>(Json.strs(misc, "ignored_talisman"));

		// union the upgrade lists into families; an id's rank is how many ids upgrade into it
		Map<String, String> parent = new HashMap<>();
		Map<String, Integer> rank = new HashMap<>();
		for (Map.Entry<String, JsonElement> e : upgrades.entrySet()) {
			String from = hatFamily(e.getKey());
			for (String to : Json.strs(upgrades, e.getKey())) {
				String t = hatFamily(to);
				if (t.equals(from)) continue;
				union(parent, from, t);
				rank.merge(t, 1, Integer::sum);
			}
		}

		record Raw(String id, String name, Rarity tier, List<Req> reqs, boolean canRecomb, boolean soulbound, boolean obtainable) {}
		List<Raw> raws = new ArrayList<>();
		for (JsonObject it : Json.objs(items, "items")) {
			if (!"ACCESSORY".equals(Json.str(it, "category", ""))) continue;
			String id = Json.str(it, "id", "");
			if (id.isEmpty()) continue;
			Rarity tier = Rarity.parse(Json.str(it, "tier", "COMMON"));
			if (tier == null) tier = Rarity.COMMON;
			List<Req> reqs = new ArrayList<>();
			for (JsonObject r : Json.objs(it, "requirements")) {
				Req q = req(r);
				if (q != null) reqs.add(q);
			}
			boolean rift = "RIFT".equals(Json.str(it, "origin", "")) && !Json.bool(it, "rift_transferrable");
			boolean canRecomb = !it.has("can_recombobulate") || Json.bool(it, "can_recombobulate");
			raws.add(new Raw(id, Json.str(it, "name", id), tier, List.copyOf(reqs), canRecomb,
					it.has("soulbound"), !rift && !ignored.contains(id) && !id.startsWith("TEST_")));
		}

		Map<String, Acc> byId = new HashMap<>();
		Map<String, List<Acc>> families = new TreeMap<>();
		Map<String, Integer> reqKeys = new TreeMap<>();
		for (Raw r : raws) {
			String fam = find(parent, hatFamily(r.id));
			Acc a = new Acc(r.id, r.name, r.tier, r.reqs, r.canRecomb, r.soulbound, r.obtainable, fam,
					rank.getOrDefault(hatFamily(r.id), 0));
			byId.put(a.id, a);
			families.computeIfAbsent(fam, k -> new ArrayList<>()).add(a);
			for (Req q : a.reqs) reqKeys.merge(q.key, q.level, Math::max);
		}
		for (List<Acc> f : families.values()) {
			f.sort((a, b) -> a.rank != b.rank ? Integer.compare(a.rank, b.rank)
					: a.tier.mp != b.tier.mp ? Integer.compare(a.tier.mp, b.tier.mp) : a.id.compareTo(b.id));
		}
		return new Catalog(Collections.unmodifiableMap(byId), Collections.unmodifiableMap(families),
				Collections.unmodifiableMap(reqKeys));
	}

	private static String find(Map<String, String> parent, String x) {
		String p = parent.get(x);
		if (p == null || p.equals(x)) return x;
		String root = find(parent, p);
		parent.put(x, root);
		return root;
	}

	private static void union(Map<String, String> parent, String a, String b) {
		String ra = find(parent, a), rb = find(parent, b);
		if (ra.equals(rb)) return;
		// the alphabetically first id names the family, so it's stable between runs
		if (ra.compareTo(rb) < 0) parent.put(rb, ra);
		else parent.put(ra, rb);
	}
}
