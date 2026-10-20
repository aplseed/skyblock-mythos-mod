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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Fusion Machine / Hunting Box: counts the shards you hold and shows the
 * server's optimal fusion plan (/api/shards/optimize-inventory) beside the menu.
 * Shards the plan fuses are tinted green, shards it sells raw gold.
 */
public final class FusionPlanner implements MenuFeature {
	private static final Pattern OWNED = Pattern.compile("(?i)\\b(stored|owned|amount|you have|quantity)\\b");

	private Menu pending;
	private Map<String, Long> counts = Map.of();
	private int requestedFor;
	private JsonObject plan;
	private String status;
	private final Set<String> fuseNames = new HashSet<>();
	private final Map<String, JsonObject> sellByName = new HashMap<>();

	@Override
	public boolean enabled() {
		return Mythos.config().fusionPlanner;
	}

	@Override
	public boolean matches(String title) {
		return title.contains("Fusion") || title.contains("Hunting Box");
	}

	@Override
	public void update(Menu menu) {
		pending = menu;
	}

	@Override
	public void closed() {
		pending = null;
		plan = null;
		status = null;
		requestedFor = 0;
	}

	private void recount(Menu menu) {
		if (!ShardIndex.ready()) {
			status = "Loading shard list...";
			return;
		}
		pending = null;
		boolean box = menu.title.contains("Hunting Box");
		Map<String, Long> fromMenu = new HashMap<>(), c = new HashMap<>();
		for (Menu.Entry e : menu.container) {
			ShardIndex.Shard s = ShardIndex.byName(e.name());
			if (s == null) continue;
			long n = Items.loreNumber(e.lore(), OWNED);
			if (n < 0 && box) n = e.count();
			if (n > 0) fromMenu.merge(s.id(), n, Math::max);
		}
		for (Menu.Entry e : menu.inventory) {
			ShardIndex.Shard s = ShardIndex.byName(e.name());
			if (s != null) c.merge(s.id(), (long) e.count(), Long::sum);
		}
		fromMenu.forEach((k, v) -> c.merge(k, v, Long::sum));
		counts = c;
		if (c.isEmpty()) {
			status = "No shards found in this menu or your inventory";
			plan = null;
			return;
		}
		int key = c.hashCode();
		if (key == requestedFor) return;
		requestedFor = key;
		status = "Planning...";
		JsonObject inv = new JsonObject();
		c.forEach(inv::addProperty);
		JsonObject body = new JsonObject();
		body.add("inventory", inv);
		body.addProperty("sell", Mythos.config().fusionSellMode);
		body.addProperty("pr", Mythos.config().pureReptileLevel);
		Mythos.backend().post("/api/shards/optimize-inventory", body, this::accept,
				err -> status = "Plan failed: " + err);
	}

	private void accept(JsonObject j) {
		plan = j;
		status = null;
		fuseNames.clear();
		sellByName.clear();
		for (JsonObject f : Json.objs(j, "fusions")) {
			fuseNames.add(Json.str(f, "in1_name", ""));
			fuseNames.add(Json.str(f, "in2_name", ""));
		}
		for (JsonObject s : Json.objs(j, "sell_direct")) sellByName.put(Json.str(s, "name", ""), s);
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		if (pending != null) recount(pending);
		panel.header("Fusion plan");
		long total = counts.values().stream().mapToLong(Long::longValue).sum();
		if (!counts.isEmpty()) panel.line(counts.size() + " shard types, " + total + " shards", Panel.GRAY);
		if (status != null) panel.line(status, Panel.GRAY);
		if (plan == null) return;

		for (List<Menu.Entry> list : List.of(menu.container, menu.inventory)) {
			for (Menu.Entry e : list) {
				ShardIndex.Shard s = ShardIndex.byName(e.name());
				if (s == null) continue;
				int color = fuseNames.contains(s.name()) ? 0x6055FF55 : sellByName.containsKey(s.name()) ? 0x50FFAA00 : 0;
				if (color != 0) {
					int x = menu.slotX(e.slot()), y = menu.slotY(e.slot());
					g.fill(x, y, x + 16, y + 16, color);
				}
			}
		}

		double value = Json.num(plan, "total_value", 0), raw = Json.num(plan, "value_if_sold_raw", 0);
		panel.line("Worth " + Fmt.coins(value) + " (raw " + Fmt.coins(raw) + ")", Panel.WHITE);
		panel.line("Fusing adds " + Fmt.signed(Json.num(plan, "uplift", 0)), Panel.GREEN);
		List<JsonObject> fusions = Json.objs(plan, "fusions");
		if (!fusions.isEmpty()) {
			panel.line((int) Json.num(plan, "fusion_count", 0) + " fusions, ~"
					+ Fmt.hours(Json.num(plan, "fusion_hours", 0)) + ":", Panel.GRAY);
			for (JsonObject f : fusions.subList(0, Math.min(8, fusions.size()))) {
				panel.line((int) Json.num(f, "count", 0) + "x " + Json.str(f, "in1_name", "?") + " + "
						+ Json.str(f, "in2_name", "?") + " -> " + Json.str(f, "output_name", "?")
						+ "  " + Fmt.signed(Json.num(f, "total_profit", 0)), Panel.GREEN);
			}
			if (fusions.size() > 8) panel.line("+" + (fusions.size() - 8) + " more", Panel.DARK);
		}
		List<JsonObject> sell = Json.objs(plan, "sell_direct");
		if (!sell.isEmpty()) {
			panel.line("Sell raw:", Panel.GRAY);
			for (JsonObject s : sell.subList(0, Math.min(5, sell.size()))) {
				panel.line((int) Json.num(s, "qty", 0) + "x " + Json.str(s, "name", "?") + "  "
						+ Fmt.coins(Json.num(s, "total", 0)), Panel.GOLD);
			}
		}

		ShardIndex.Shard h = hovered == null ? null : ShardIndex.byName(hovered.name());
		if (h != null) {
			panel.header(h.name() + " (" + h.rarity() + ")");
			for (JsonObject f : fusions) {
				if (h.name().equals(Json.str(f, "in1_name", "")) || h.name().equals(Json.str(f, "in2_name", ""))) {
					panel.line("Fuse into " + Json.str(f, "output_name", "?") + " x" + (int) Json.num(f, "count", 0), Panel.GREEN);
				}
			}
			JsonObject s = sellByName.get(h.name());
			if (s != null) panel.line("Sell " + (int) Json.num(s, "qty", 0) + " for " + Fmt.coins(Json.num(s, "total", 0)), Panel.GOLD);
			panel.line("Net " + Fmt.coins(h.value()) + " each", Panel.GRAY);
		}
	}
}
