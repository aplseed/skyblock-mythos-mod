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

	private static void effect(Panel panel, JsonObject e) {
		double mv = Json.num(e, "move", 0);
		panel.line("  " + (mv >= 0 ? "▲ " : "▼ ") + Fmt.pct(mv) + " " + Json.str(e, "name", Json.str(e, "item", "?"))
				+ " (" + (int) Json.num(e, "n", 0) + " terms)", mv >= 0 ? Panel.GREEN : Panel.RED);
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		refresh();
		panel.header("Election: past-term market effects");
		if (candidates.isEmpty()) {
			panel.line("No running election data", Panel.GRAY);
			return;
		}
		JsonObject focus = null;
		String hov = hovered == null ? "" : hovered.name().toLowerCase(Locale.ROOT);
		for (JsonObject c : candidates) {
			String name = Json.str(c, "mayor", "?");
			if (!hov.isEmpty() && hov.contains(name.toLowerCase(Locale.ROOT))) focus = c;
			double share = Json.num(c, "share", -1);
			panel.line(name + (share >= 0 ? "  " + share + "%" : ""), Panel.AQUA);
			List<JsonObject> eff = Json.objs(c, "effects");
			for (JsonObject e : eff.subList(0, Math.min(2, eff.size()))) effect(panel, e);
			if (eff.isEmpty()) {
				List<JsonObject> hyp = Json.objs(c, "hypotheses");
				if (hyp.isEmpty()) panel.line("  no measured effect", Panel.DARK);
				else panel.line("  " + hyp.size() + " expected effect" + (hyp.size() > 1 ? "s" : "") + " (unmeasured)", Panel.YELLOW);
			}
		}
		if (focus != null) {
			panel.header(Json.str(focus, "mayor", "?") + ": " + String.join(", ", Json.strs(focus, "perks")));
			List<JsonObject> eff = Json.objs(focus, "effects");
			for (JsonObject e : eff.subList(0, Math.min(10, eff.size()))) effect(panel, e);
			for (JsonObject h : Json.objs(focus, "hypotheses")) {
				panel.line((Json.num(h, "dir", 0) > 0 ? "▲ " : "▼ ") + String.join(", ", Json.strs(h, "items")), Panel.YELLOW);
				panel.line("  " + Json.str(h, "why", ""), Panel.DARK);
			}
		}
	}
}
