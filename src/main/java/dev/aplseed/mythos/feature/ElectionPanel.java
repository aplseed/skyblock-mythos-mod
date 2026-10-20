package dev.aplseed.mythos.feature;

import com.google.gson.JsonObject;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.menu.MenuFeature;
import dev.aplseed.mythos.net.Json;
import dev.aplseed.mythos.ui.Panel;
import dev.aplseed.mythos.util.Fmt;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Election booth: for every candidate, what prices did in their past terms
 * (measured by the server) or what their perks should do (unmeasured).
 */
public final class ElectionPanel implements MenuFeature {
	private static final long TTL_MS = 5 * 60_000;

	private List<JsonObject> candidates = List.of();
	private long fetchedAt;

	@Override
	public boolean enabled() {
		return Mythos.config().electionPanel;
	}

	@Override
	public boolean matches(String title) {
		return title.contains("Election");
	}

	private void refresh() {
		if (System.currentTimeMillis() - fetchedAt < TTL_MS) return;
		fetchedAt = System.currentTimeMillis();
		Mythos.backend().get("/api/mod/election", j -> candidates = Json.objs(j, "candidates"));
	}

	private static String arrow(double dir) {
		return dir >= 0 ? "\u25B2 " : "\u25BC ";
	}

	private static void effect(Panel panel, JsonObject e) {
		double mv = Json.num(e, "move", 0);
		panel.line("  " + arrow(mv) + Fmt.pct(mv) + " " + Json.str(e, "name", Json.str(e, "item", "?"))
				+ " (" + (int) Json.num(e, "n", 0) + " terms)" + (Json.bool(e, "held") ? " - you hold" : ""),
				mv >= 0 ? Panel.GREEN : Panel.RED);
	}

	/** "Cobblestone, Enchanted Cobblestone +20 more" */
	private static String items(JsonObject h, int show) {
		List<String> names = Json.strs(h, "names");
		if (names.isEmpty()) names = Json.strs(h, "items");
		String head = String.join(", ", names.subList(0, Math.min(show, names.size())));
		return names.size() > show ? head + " +" + (names.size() - show) + " more" : head;
	}

	/** Your open positions this candidate is expected to move. */
	private static void held(Panel panel, JsonObject c) {
		List<JsonObject> held = Json.objs(c, "held");
		if (held.isEmpty()) return;
		StringBuilder sb = new StringBuilder("  your items: ");
		for (int i = 0; i < held.size(); i++) {
			if (i > 0) sb.append(", ");
			sb.append(arrow(Json.num(held.get(i), "dir", 0))).append(Json.str(held.get(i), "name", "?"));
		}
		panel.line(sb.toString(), Panel.GOLD);
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		refresh();
		panel.header("Election: market effects");
		if (candidates.isEmpty()) {
			panel.line("No running election data", Panel.GRAY);
			return;
		}
		JsonObject focus = null;
		String hov = hovered == null ? "" : hovered.name().toLowerCase(Locale.ROOT);
		for (JsonObject c : candidates) {
			String name = Json.str(c, "mayor", "?");
			if (!hov.isEmpty() && hov.equals(name.toLowerCase(Locale.ROOT))) focus = c;
			double share = Json.num(c, "share", -1);
			panel.line(name + (share >= 0 ? "  " + share + "%" : ""), Panel.AQUA);
			held(panel, c);
			List<JsonObject> eff = Json.objs(c, "effects");
			for (JsonObject e : eff.subList(0, Math.min(2, eff.size()))) effect(panel, e);
			if (eff.isEmpty()) {
				List<JsonObject> hyp = Json.objs(c, "hypotheses");
				if (hyp.isEmpty()) {
					panel.line("  no known market effect", Panel.DARK);
				} else {
					panel.line("  " + arrow(Json.num(hyp.get(0), "dir", 0)) + Json.str(hyp.get(0), "why", "")
							+ (hyp.size() > 1 ? " (+" + (hyp.size() - 1) + " more)" : "") + ", unmeasured", Panel.YELLOW);
				}
			}
		}
		if (focus != null) {
			panel.header(Json.str(focus, "mayor", "?") + ": " + String.join(", ", Json.strs(focus, "perks")));
			held(panel, focus);
			List<JsonObject> eff = Json.objs(focus, "effects");
			if (!eff.isEmpty()) panel.line("Measured in past terms:", Panel.GRAY);
			for (JsonObject e : eff.subList(0, Math.min(8, eff.size()))) effect(panel, e);
			List<JsonObject> hyp = Json.objs(focus, "hypotheses");
			if (!hyp.isEmpty()) panel.line("Expected from perks (unmeasured):", Panel.GRAY);
			for (JsonObject h : hyp) {
				panel.line(arrow(Json.num(h, "dir", 0)) + Json.str(h, "perk", "") + ": " + Json.str(h, "why", ""), Panel.YELLOW);
				panel.line("  " + items(h, 3), Panel.DARK);
			}
		}
	}
}
