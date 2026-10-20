package dev.aplseed.mythos.feature;

import com.google.gson.JsonObject;
import dev.aplseed.mythos.Config;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.menu.MenuFeature;
import dev.aplseed.mythos.net.Json;
import dev.aplseed.mythos.ui.Panel;
import dev.aplseed.mythos.util.Fmt;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The Forge: ranks recipes by profit per slot-hour with your slots, Quick Forge
 * and Cole (/api/forge?compact=1), badges recipes in the selection menus with
 * their rank, and says what to start in any free slot.
 */
public final class ForgeAdvisor implements MenuFeature {
	private static final long TTL_MS = 10 * 60_000;

	private List<JsonObject> rows = List.of();
	private final Map<String, Integer> rank = new HashMap<>();
	private long fetchedAt;
	private String settingsKey = "";
	private int freeSlots = -1;

	@Override
	public boolean enabled() {
		return Mythos.config().forgeAdvisor;
	}

	@Override
	public boolean matches(String title) {
		return title.contains("Forge") || title.contains("Select Process") || title.startsWith("Refin");
	}

	private void refresh() {
		Config c = Mythos.config();
		String key = c.forgeSlots + "/" + c.quickForgeLevel + "/" + c.coleMayor;
		if (key.equals(settingsKey) && System.currentTimeMillis() - fetchedAt < TTL_MS) return;
		settingsKey = key;
		fetchedAt = System.currentTimeMillis();
		Mythos.backend().get("/api/forge?compact=1&slots=" + c.forgeSlots + "&qf=" + c.quickForgeLevel
				+ "&cole=" + (c.coleMayor ? 1 : 0), j -> {
			rows = Json.objs(j, "data");
			rank.clear();
			for (int i = 0; i < rows.size(); i++) rank.put(norm(Json.str(rows.get(i), "name", "")), i);
		});
	}

	private static String norm(String s) {
		return s.toLowerCase(Locale.ROOT).trim();
	}

	@Override
	public void update(Menu menu) {
		int free = 0;
		for (Menu.Entry e : menu.container) {
			if (e.name().startsWith("Slot #") || e.name().contains("Empty")) {
				for (String l : e.lore()) {
					if (l.toLowerCase(Locale.ROOT).contains("click to select")) {
						free++;
						break;
					}
				}
			}
		}
		freeSlots = free;
	}

	private JsonObject rowFor(String name) {
		Integer i = rank.get(norm(name));
		return i == null ? null : rows.get(i);
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		refresh();
		Config c = Mythos.config();
		panel.header("Forge (" + c.forgeSlots + " slots, QF " + c.quickForgeLevel + (c.coleMayor ? ", Cole" : "") + ")");
		if (rows.isEmpty()) {
			panel.line("Loading recipes...", Panel.GRAY);
			return;
		}
		var font = Minecraft.getInstance().font;
		for (Menu.Entry e : menu.container) {
			Integer r = rank.get(norm(e.name()));
			if (r == null) continue;
			String badge = "#" + (r + 1);
			int x = menu.slotX(e.slot()), y = menu.slotY(e.slot());
			g.text(font, badge, x, y, r < 3 ? Panel.GOLD : Panel.GRAY, true);
		}
		if (freeSlots > 0) panel.line(freeSlots + " free slot" + (freeSlots > 1 ? "s" : "") + " - start:", Panel.AQUA);
		for (int i = 0; i < Math.min(6, rows.size()); i++) {
			JsonObject r = rows.get(i);
			panel.line("#" + (i + 1) + " " + Json.str(r, "name", "?") + "  " + Fmt.coins(Json.num(r, "opt_slot_pph", 0))
					+ "/slot-h  " + Fmt.hours(Json.num(r, "opt_time", 0)) + gradeOf(r), i < 3 ? Panel.GOLD : Panel.WHITE);
		}
		JsonObject h = hovered == null ? null : rowFor(hovered.name());
		if (h != null) {
			panel.header("#" + (rank.get(norm(hovered.name())) + 1) + " " + Json.str(h, "name", ""));
			panel.line("Profit " + Fmt.signed(Json.num(h, "opt_profit", 0)) + " on " + Fmt.coins(Json.num(h, "opt_cost", 0)), Panel.GREEN);
			panel.line(Fmt.coins(Json.num(h, "opt_slot_pph", 0)) + " per slot-hour, done in " + Fmt.hours(Json.num(h, "opt_time", 0)), Panel.WHITE);
			String hotm = Json.str(h, "hotm", "");
			if (!hotm.isEmpty()) panel.line(hotm, Panel.GRAY);
			List<String> parts = Json.strs(h, "forge_parts");
			if (!parts.isEmpty()) panel.line("Forge yourself: " + String.join(", ", parts), Panel.AQUA);
			List<JsonObject> buy = Json.objs(h, "shopping_list");
			if (!buy.isEmpty()) panel.line("Buy:", Panel.GRAY);
			for (JsonObject b : buy.subList(0, Math.min(5, buy.size()))) {
				panel.line("  " + (long) Json.num(b, "qty", 0) + "x " + Json.str(b, "name", "?") + "  " + Fmt.coins(Json.num(b, "cost", 0)), Panel.GRAY);
			}
		}
	}

	private static String gradeOf(JsonObject r) {
		String g = Json.str(r, "rec_grade", "");
		return g.isEmpty() ? "" : "  " + g;
	}
}
