package dev.aplseed.mythos.feature;

import com.google.gson.JsonObject;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.data.BazaarNames;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.menu.MenuFeature;
import dev.aplseed.mythos.net.Backend;
import dev.aplseed.mythos.net.Json;
import dev.aplseed.mythos.ui.Panel;
import dev.aplseed.mythos.util.Fmt;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Bazaar: for the product page (or the item under the mouse) shows what the
 * server knows that price mods don't - active signals with their backtested
 * record, your open position after tax, and how this/next mayor has moved it.
 */
public final class BazaarPanel implements MenuFeature {
	private static final long TTL_MS = 60_000;

	private record Cached(JsonObject data, long at) {}

	private final Map<String, Cached> cache = new HashMap<>();
	private final Set<String> loading = new HashSet<>();

	@Override
	public boolean enabled() {
		return Mythos.config().bazaarPanel;
	}

	@Override
	public boolean matches(String title) {
		return title.startsWith("Bazaar") || title.contains("➜");
	}

	/** Null while loading or after a failed fetch (retried after TTL_MS). */
	private JsonObject intel(String id) {
		Cached c = cache.get(id);
		if ((c == null || System.currentTimeMillis() - c.at > TTL_MS) && loading.add(id)) {
			Mythos.backend().get("/api/mod/item?ids=" + Backend.q(id), j -> {
				loading.remove(id);
				cache.put(id, new Cached(Json.obj(Json.obj(j, "items"), id), System.currentTimeMillis()));
			}, err -> {
				loading.remove(id);
				cache.put(id, new Cached(null, System.currentTimeMillis()));
			});
		}
		return c == null ? null : c.data;
	}

	/**
	 * The hovered product, else the product page in the title ("Mining ➜ Enchanted Diamond").
	 * Category tiles ("Click to view products!") stand for several products and are skipped.
	 */
	private static String productOf(Menu menu, Menu.Entry hovered) {
		if (hovered != null && !hovered.lore().contains("Click to view products!")) {
			String id = BazaarNames.id(hovered.name());
			if (id == null && !hovered.id().isEmpty()) id = hovered.id();
			if (id != null) return id;
		}
		int arrow = menu.title.lastIndexOf('➜');
		return arrow < 0 ? null : BazaarNames.id(menu.title.substring(arrow + 1));
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		String id = productOf(menu, hovered);
		if (id == null) return;
		JsonObject it = intel(id);
		panel.header(it == null ? id : Json.str(it, "name", id));
		if (it == null) {
			panel.line(loading.contains(id) ? "Loading..." : "Unavailable: " + Mythos.backend().lastError(), Panel.GRAY);
			return;
		}
		boolean any = false;
		for (JsonObject s : Json.objs(it, "signals")) {
			any = true;
			boolean buy = "buy".equals(Json.str(s, "side", ""));
			panel.line((buy ? "▲ " : "▼ ") + Json.str(s, "label", "?") + "  "
					+ Math.round(Json.num(s, "confidence", 0) * 100) + "% conf", buy ? Panel.GREEN : Panel.RED);
			JsonObject r = Json.obj(s, "record");
			int n = (int) Json.num(r, "n", 0);
			if ("terms".equals(Json.str(r, "unit", ""))) {
				panel.line("  moved this way in all " + n + " past terms", Panel.GRAY);
			} else if (n > 0) {
				String base = r.has("baseline_mean") ? " vs " + Fmt.pct(Json.num(r, "baseline_mean", 0)) + " any" : "";
				panel.line("  past: " + Math.round(Json.num(r, "hit", 0) * 100) + "% right, avg "
						+ Fmt.pct(Json.num(r, "mean", 0)) + base + " (n=" + n + ")", Panel.GRAY);
			} else {
				panel.line("  no track record yet", Panel.DARK);
			}
			for (String ev : Json.strs(s, "evidence").subList(0, Math.min(2, Json.strs(s, "evidence").size()))) {
				panel.line("  " + ev, Panel.DARK);
			}
		}
		for (JsonObject p : Json.objs(it, "positions")) {
			any = true;
			double pnl = Json.num(p, "pnl", 0);
			panel.line("Held " + (long) Json.num(p, "qty", 0) + " @ " + Fmt.coins(Json.num(p, "buy_price", 0))
					+ ": " + Fmt.signed(pnl) + " (" + Fmt.pct(Json.num(p, "pnl_pct", 0) / 100) + ") after tax",
					pnl >= 0 ? Panel.GREEN : Panel.RED);
			for (JsonObject a : Json.objs(p, "alerts")) panel.line("  sell warning: " + Json.str(a, "label", ""), Panel.RED);
		}
		JsonObject mayor = Json.obj(it, "mayor");
		for (String term : new String[] {"current", "next"}) {
			JsonObject m = Json.obj(mayor, term);
			if (m.size() == 0) continue;
			any = true;
			String who = Json.str(m, "mayor", "?") + (term.equals("current") ? " (now)" : " (next)");
			for (JsonObject e : Json.objs(m, "effects")) {
				double mv = Json.num(e, "move", 0);
				panel.line(who + ": " + Fmt.pct(mv) + " avg over " + (int) Json.num(e, "n", 0) + " terms",
						mv >= 0 ? Panel.GREEN : Panel.RED);
			}
			for (JsonObject h : Json.objs(m, "hypotheses")) {
				panel.line(who + " " + Json.str(h, "perk", "") + ": expect " + (Json.num(h, "dir", 0) > 0 ? "▲" : "▼")
						+ " (unmeasured)", Panel.YELLOW);
			}
		}
		if (!any) panel.line("No signal, not held, no mayor effect", Panel.GRAY);
	}
}
