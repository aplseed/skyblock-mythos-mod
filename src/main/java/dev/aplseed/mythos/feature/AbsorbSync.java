package dev.aplseed.mythos.feature;

import com.google.gson.JsonObject;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.data.ShardIndex;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.menu.MenuFeature;
import dev.aplseed.mythos.net.Json;
import dev.aplseed.mythos.ui.Panel;
import dev.aplseed.mythos.util.Fmt;
import dev.aplseed.mythos.util.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Attribute Menu and Hunting Box: works out how many of each shard you've
 * absorbed and stores it on the server (/api/mod/absorb), so the web absorb
 * tracker is never stale. The game shows no count directly: "Attribute Level:
 * 10 (MAX!)" / "Attribute Maxed!" mean the cap, otherwise it's the cap minus
 * "Syphon n shards to max!" (or "Syphon n more to max!" while locked). In the
 * Attribute Menu it also shows what finishing each one costs (/api/shards/absorb).
 */
public final class AbsorbSync implements MenuFeature {
	private static final Pattern TO_MAX = Pattern.compile("Syphon ([0-9,]+) (?:more|shards?) to max");
	private static final Pattern SHARD_IN_LORE = Pattern.compile("([A-Z][A-Za-z' -]+?) Shards?\\b");
	private static final long COST_TTL_MS = 5 * 60_000;

	private Menu pending;
	private final Map<String, Integer> pushed = new HashMap<>();
	private String status;
	private Map<String, JsonObject> costs = Map.of();
	private JsonObject costSummary;
	private long costsAt;

	@Override
	public boolean enabled() {
		return Mythos.config().absorbSync;
	}

	@Override
	public boolean matches(String title) {
		return title.contains("Attribute") || title.contains("Hunting Box");
	}

	@Override
	public void update(Menu menu) {
		pending = menu;
	}

	@Override
	public void closed() {
		pending = null;
		status = null;
	}

	private static ShardIndex.Shard shardOf(Menu.Entry e) {
		ShardIndex.Shard s = ShardIndex.of(e);
		if (s != null) return s;
		for (String line : e.lore()) {
			Matcher m = SHARD_IN_LORE.matcher(line);
			while (m.find()) {
				s = ShardIndex.byName(m.group(1));
				if (s != null) return s;
			}
		}
		return null;
	}

	/** Absorbed count from an item's lore, or -1 if it doesn't say. */
	public static int absorbed(List<String> lore, int cap) {
		if (cap <= 0) return -1;
		for (String line : lore) {
			if (line.equals("Attribute Maxed!") || (line.startsWith("Attribute Level:") && line.contains("(MAX!)"))) return cap;
			Matcher m = TO_MAX.matcher(line);
			if (m.find()) {
				long toMax = Items.parse(m.group(1));
				return toMax < 0 ? -1 : (int) Math.max(0, cap - Math.min(toMax, cap));
			}
		}
		return -1;
	}

	private void read(Menu menu) {
		if (!ShardIndex.ready()) {
			status = ShardIndex.status();
			return;
		}
		pending = null;
		Map<String, Integer> found = new HashMap<>();
		for (Menu.Entry e : menu.container) {
			ShardIndex.Shard s = shardOf(e);
			if (s == null) continue;
			int n = absorbed(e.lore(), s.cap());
			if (n >= 0) found.put(s.id(), n);
		}
		Map<String, Integer> fresh = new HashMap<>();
		found.forEach((k, v) -> {
			if (!v.equals(pushed.get(k))) fresh.put(k, v);
		});
		if (found.isEmpty()) {
			status = menu.title.contains("Hunting Box") ? null : "No absorbed counts on this page";
			return;
		}
		if (fresh.isEmpty()) return;
		JsonObject counts = new JsonObject();
		fresh.forEach(counts::addProperty);
		JsonObject body = new JsonObject();
		body.add("counts", counts);
		Mythos.backend().post("/api/mod/absorb", body, j -> {
			pushed.putAll(fresh);
			int changed = Json.obj(j, "changed").size();
			status = "Read " + found.size() + " shard" + (found.size() == 1 ? "" : "s")
					+ (changed > 0 ? ", " + changed + " updated" : ", all up to date");
			if (changed > 0) {
				costsAt = 0;
				ShardIndex.refresh(true);
			}
		}, err -> status = "Sync failed: " + err);
	}

	private void refreshCosts() {
		if (System.currentTimeMillis() - costsAt < COST_TTL_MS) return;
		costsAt = System.currentTimeMillis();
		Mythos.backend().get("/api/shards/absorb?buy=instant_buy", j -> {
			Map<String, JsonObject> m = new HashMap<>();
			for (JsonObject o : Json.objs(j, "shards")) m.put(Json.str(o, "id", ""), o);
			costs = m;
			costSummary = j;
		});
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		if (pending != null) read(pending);
		if (menu.title.contains("Hunting Box")) {
			// the fusion plan owns this panel; just report the sync
			if (status != null) panel.header("Absorb sync").line(status, Panel.GRAY);
			return;
		}
		refreshCosts();
		panel.header("Shard absorption");
		if (status != null) panel.line(status, Panel.GRAY);
		if (costSummary != null) {
			panel.line("To max everything: " + Fmt.coins(Json.num(costSummary, "total_cost", 0)), Panel.WHITE);
			List<JsonObject> open = new ArrayList<>();
			for (JsonObject o : costs.values()) {
				if (!Json.bool(o, "done") && Json.num(o, "cost_remaining", -1) >= 0) open.add(o);
			}
			open.sort((a, b) -> Double.compare(Json.num(a, "cost_remaining", 0), Json.num(b, "cost_remaining", 0)));
			if (!open.isEmpty()) panel.line("Cheapest to finish:", Panel.GRAY);
			for (JsonObject o : open.subList(0, Math.min(6, open.size()))) {
				panel.line(Json.str(o, "name", "?") + "  " + (int) Json.num(o, "remaining", 0) + " left  "
						+ Fmt.coins(Json.num(o, "cost_remaining", 0)) + " (" + Json.str(o, "method", "") + ")", Panel.GREEN);
			}
		}
		ShardIndex.Shard h = hovered == null ? null : shardOf(hovered);
		JsonObject c = h == null ? null : costs.get(h.id());
		if (c != null) {
			panel.header(h.name());
			panel.line((int) Json.num(c, "absorbed", 0) + " / " + (int) Json.num(c, "cap", 0) + " absorbed", Panel.WHITE);
			if (Json.bool(c, "done")) {
				panel.line("Maxed", Panel.GREEN);
			} else if (Json.num(c, "unit_cost", -1) >= 0) {
				panel.line(Fmt.coins(Json.num(c, "unit_cost", 0)) + " each by " + Json.str(c, "method", "?"), Panel.GRAY);
				panel.line("Finish: " + Fmt.coins(Json.num(c, "cost_remaining", 0)), Panel.GOLD);
			} else {
				panel.line("Not obtainable on the bazaar", Panel.DARK);
			}
		}
	}
}
