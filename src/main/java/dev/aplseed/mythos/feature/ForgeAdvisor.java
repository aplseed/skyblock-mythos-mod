package dev.aplseed.mythos.feature;

import com.google.gson.JsonObject;
import dev.aplseed.mythos.Config;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.menu.MenuFeature;
import dev.aplseed.mythos.net.Json;
import dev.aplseed.mythos.ui.Panel;
import dev.aplseed.mythos.util.Fmt;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The Forge: ranks recipes by profit per slot-hour with your slots, Quick Forge
 * and Cole (/api/forge?compact=1), badges recipes in the selection menus with
 * their rank, and says what to start in any free slot.
 */
public final class ForgeAdvisor implements MenuFeature {
	private static final long TTL_MS = 10 * 60_000;

	// Recipe categories under Select Process; several ("Tools", "Pets", "Other") are also
	// titles of unrelated menus, so those only count once the menu shows recipes.
	private static final Set<String> CATEGORIES = Set.of("Refining", "Forging", "Tools", "Gear",
			"Reforge Stones", "Drill Parts", "Perfect Gemstones", "Pets", "Other");
	private static final Pattern PAGE = Pattern.compile("^\\(\\d+/\\d+\\) ");

	private List<JsonObject> rows = List.of();
	private final Map<String, Integer> rankById = new HashMap<>();
	private final Map<String, Integer> rankByName = new HashMap<>();
	private long fetchedAt;
	private String settingsKey = "";
	private int freeSlots = -1;
	private boolean showsRecipes;
	private List<String> running = List.of();

	@Override
	public boolean enabled() {
		return Mythos.config().forgeAdvisor;
	}

	@Override
	public boolean matches(String title) {
		return title.equals("The Forge") || title.startsWith("Select Process") || isCategory(title);
	}

	private static boolean isCategory(String title) {
		return CATEGORIES.contains(PAGE.matcher(title).replaceFirst(""));
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
			rankById.clear();
			rankByName.clear();
			for (int i = 0; i < rows.size(); i++) {
				rankById.putIfAbsent(Json.str(rows.get(i), "id", ""), i);
				rankByName.putIfAbsent(norm(Json.str(rows.get(i), "name", "")), i);
			}
		});
	}

	private static String norm(String s) {
		return s.toLowerCase(Locale.ROOT).trim();
	}

	/**
	 * The Forge: free slots read "Slot #1 ... Click to select a process!", busy ones are the
	 * product with "Currently making: X / Time Remaining: 41m 43s". Recipes: "Items Required ... Duration: 5h 36m".
	 */
	@Override
	public void update(Menu menu) {
		int free = 0;
		boolean recipes = false;
		List<String> busy = new ArrayList<>();
		for (Menu.Entry e : menu.container) {
			if (e.name().startsWith("Slot #") && e.lore().contains("Click to select a process!")) free++;
			if (e.lore().stream().anyMatch(l -> l.startsWith("Currently making:"))) {
				String left = e.lore().stream().filter(l -> l.startsWith("Time Remaining:")).findFirst()
						.map(l -> l.substring("Time Remaining:".length()).trim()).orElse("done");
				busy.add(e.name() + "  " + left);
			}
			if (e.lore().contains("Items Required") && e.lore().stream().anyMatch(l -> l.startsWith("Duration:"))) recipes = true;
		}
		freeSlots = free;
		showsRecipes = recipes;
		running = busy;
	}

	/**
	 * Rank of a menu item: by SkyBlock id (recipe items carry one), else by name.
	 * Category tiles ("Refining" is {REFINED_MITHRIL}, "Click to view!") and running
	 * processes (the product itself, "Currently making: ...") have none.
	 */
	private Integer rankOf(Menu.Entry e) {
		if (e.lore().contains("Click to view!") || e.lore().stream().anyMatch(l -> l.startsWith("Currently making:"))) return null;
		Integer r = e.id().isEmpty() ? null : rankById.get(e.id());
		return r != null ? r : rankByName.get(norm(e.name()));
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		if (isCategory(menu.title) && !showsRecipes) return;
		refresh();
		Config c = Mythos.config();
		panel.header("Forge (" + c.forgeSlots + " slots, QF " + c.quickForgeLevel + (c.coleMayor ? ", Cole" : "") + ")");
		if (rows.isEmpty()) {
			panel.line("Loading recipes...", Panel.GRAY);
			return;
		}
		var font = Minecraft.getInstance().font;
		List<Menu.Entry> here = new ArrayList<>();
		for (Menu.Entry e : menu.container) {
			Integer r = rankOf(e);
			if (r == null) continue;
			here.add(e);
			int x = menu.slotX(e.slot()), y = menu.slotY(e.slot());
			g.text(font, "#" + (r + 1), x, y, r < 3 ? Panel.GOLD : Panel.GRAY, true);
		}
		if (!here.isEmpty()) {
			here.sort((a, b) -> Integer.compare(rankOf(a), rankOf(b)));
			panel.line("Best here:", Panel.AQUA);
			for (Menu.Entry e : here.subList(0, Math.min(4, here.size()))) {
				JsonObject r = rows.get(rankOf(e));
				panel.line("#" + (rankOf(e) + 1) + " " + e.name() + "  " + Fmt.coins(Json.num(r, "opt_slot_pph", 0))
						+ "/slot-h" + gradeOf(r), Panel.AQUA);
			}
			panel.line("Overall:", Panel.GRAY);
		}
		for (String r : running) panel.line("Forging: " + r, Panel.GRAY);
		if (freeSlots > 0) panel.line(freeSlots + " free slot" + (freeSlots > 1 ? "s" : "") + " - start:", Panel.AQUA);
		for (int i = 0; i < Math.min(6, rows.size()); i++) {
			JsonObject r = rows.get(i);
			panel.line("#" + (i + 1) + " " + Json.str(r, "name", "?") + "  " + Fmt.coins(Json.num(r, "opt_slot_pph", 0))
					+ "/slot-h  " + Fmt.hours(Json.num(r, "opt_time", 0)) + gradeOf(r), i < 3 ? Panel.GOLD : Panel.WHITE);
		}
		Integer hr = hovered == null ? null : rankOf(hovered);
		if (hr != null) {
			JsonObject h = rows.get(hr);
			panel.header("#" + (hr + 1) + " " + Json.str(h, "name", ""));
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
