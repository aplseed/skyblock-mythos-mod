package dev.aplseed.mythos.data;

import com.google.gson.JsonObject;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.net.Json;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Every shard by in-game name with net sell prices and absorbed counts (/api/mod/shards). */
public final class ShardIndex {
	public record Shard(String id, String name, String rarity, double sellOrder, double instantSell,
			int absorbed, int cap) {
		public double value() {
			return sellOrder > 0 ? sellOrder : instantSell;
		}
	}

	private static final long TTL_MS = 5 * 60_000;
	private static Map<String, Shard> byName = Map.of();
	private static Map<String, Shard> byId = Map.of();
	private static long fetchedAt;

	private ShardIndex() {}

	public static void refresh(boolean force) {
		if (!force && System.currentTimeMillis() - fetchedAt < TTL_MS) return;
		fetchedAt = System.currentTimeMillis() - TTL_MS + 30_000;     // retry in 30 s on failure
		Mythos.backend().get("/api/mod/shards", ShardIndex::load);
	}

	private static void load(JsonObject j) {
		Map<String, Shard> n = new HashMap<>(), i = new HashMap<>();
		for (JsonObject o : Json.objs(j, "shards")) {
			Shard s = new Shard(Json.str(o, "id", ""), Json.str(o, "name", ""), Json.str(o, "rarity", ""),
					Json.num(o, "sell_order", 0), Json.num(o, "instant_sell", 0),
					(int) Json.num(o, "absorbed", 0), (int) Json.num(o, "cap", 0));
			n.put(s.name.toLowerCase(Locale.ROOT), s);
			i.put(s.id, s);
		}
		byName = n;
		byId = i;
		fetchedAt = System.currentTimeMillis();
	}

	public static boolean ready() {
		refresh(false);
		return !byName.isEmpty();
	}

	/** "Tadgang", "Tadgang Shard" or "Tadgang Shards" -> shard. */
	public static Shard byName(String name) {
		refresh(false);
		String k = name.toLowerCase(Locale.ROOT).trim();
		if (k.endsWith(" shards")) k = k.substring(0, k.length() - 7);
		else if (k.endsWith(" shard")) k = k.substring(0, k.length() - 6);
		return byName.get(k.trim());
	}

	public static Shard byId(String id) {
		refresh(false);
		return byId.get(id);
	}

	public static Iterable<Shard> all() {
		return byName.values();
	}
}
