package dev.aplseed.mythos.data;

import com.google.gson.JsonElement;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.net.Json;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** In-game bazaar name -> product id (/api/mod/bazaar-names), since bazaar menu items carry no id. */
public final class BazaarNames {
	private static final long TTL_MS = 30 * 60_000;
	private static Map<String, String> ids = Map.of();
	private static long fetchedAt;

	private BazaarNames() {}

	public static void refresh() {
		if (System.currentTimeMillis() - fetchedAt < TTL_MS) return;
		fetchedAt = System.currentTimeMillis() - TTL_MS + 30_000;     // retry in 30 s on failure
		Mythos.backend().get("/api/mod/bazaar-names", j -> {
			Map<String, String> m = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : Json.obj(j, "names").entrySet()) {
				m.put(e.getKey(), e.getValue().getAsString());
			}
			ids = m;
			fetchedAt = System.currentTimeMillis();
		});
	}

	/** Product id for a menu item/title name, or null. */
	public static String id(String name) {
		refresh();
		if (name == null || name.isEmpty()) return null;
		return ids.get(name.toLowerCase(Locale.ROOT).trim());
	}
}
