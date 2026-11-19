package dev.aplseed.mythos.magicgrind;

import com.google.gson.JsonObject;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.menu.Menu;
import dev.aplseed.mythos.menu.MenuFeature;
import dev.aplseed.mythos.ui.Panel;
import dev.aplseed.mythos.util.TabList;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeMap;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Sends your Magic Find, Pet Luck, slayer levels and Bestiary tiers to MagicGrind.
 *
 * <p>Read from: the tab list's stats widget (when it shows Magic Find / Pet Luck), the SkyBlock Menu's
 * profile item and the stats menus, the Slayer menus ("Zombie Slayer: LVL 9") and the Bestiary menus
 * (mob items named with their tier, "Ghost XX"). Display-only: nothing is clicked for you.
 */
public final class MagicGrindSync implements MenuFeature {
	private static final long MIN_GAP_MS = 10_000, HEARTBEAT_MS = 5 * 60_000;

	private static Double magicFind, petLuck, riftMagicFind;
	private static final Map<String, Integer> SLAYERS = new TreeMap<>();
	private static final Map<String, Integer> BESTIARY = new TreeMap<>();
	private static boolean dirty;
	private static long lastSent;
	private static int ticks;

	private int readHere;

	@Override
	public boolean enabled() {
		return Mythos.config().magicgrind;
	}

	@Override
	public boolean matches(String title) {
		return title.equals("SkyBlock Menu") || title.contains("Stats") || title.contains("Profile")
				|| title.contains("Slayer") || title.startsWith("Bestiary");
	}

	@Override
	public void update(Menu menu) {
		readHere = 0;
		List<String> all = new ArrayList<>();
		for (Menu.Entry e : menu.container) {
			all.add(e.name());
			all.addAll(e.lore());
		}
		boolean rift = MagicGrindParse.inRift(TabList.lines());
		readHere += stat(all, "magic_find", rift);
		readHere += stat(all, "pet_luck", rift);
		if (menu.title.contains("Slayer")) {
			Map<String, Integer> found = new TreeMap<>();
			MagicGrindParse.slayers(all, found);
			readHere += merge(SLAYERS, found);
		}
		if (menu.title.startsWith("Bestiary")) {
			Map<String, Integer> found = new TreeMap<>();
			int area = menu.rows > 1 ? (menu.rows - 1) * 9 : Integer.MAX_VALUE;   // bottom row is navigation
			for (Menu.Entry e : menu.container) {
				if (e.slot().index >= area) continue;
				var t = MagicGrindParse.bestiaryTier(e.name());
				if (t != null) found.put(t.getKey(), t.getValue());
			}
			readHere += merge(BESTIARY, found);
		}
	}

	@Override
	public void render(Menu menu, Menu.Entry hovered, GuiGraphicsExtractor g, Panel panel) {
		if (readHere == 0 && !menu.title.startsWith("Bestiary")) return;
		panel.header("MagicGrind", MagicGrindClient.configured() ? MagicGrindClient.status() : "not set up");
		if (magicFind != null) panel.kv("Magic Find", dev.aplseed.mythos.util.Fmt.full(magicFind), Panel.PURPLE);
		if (petLuck != null) panel.kv("Pet Luck", dev.aplseed.mythos.util.Fmt.full(petLuck), Panel.PURPLE);
		if (!SLAYERS.isEmpty()) panel.kv("Slayer levels read", Integer.toString(SLAYERS.size()), Panel.WHITE);
		if (!BESTIARY.isEmpty()) panel.kv("Bestiary families read", Integer.toString(BESTIARY.size()), Panel.WHITE);
		if (menu.title.startsWith("Bestiary")) panel.line("Open each island's page once to send its tiers", Panel.DARK);
	}

	private static int stat(List<String> lines, String which, boolean rift) {
		OptionalDouble v = MagicGrindParse.stat(lines, which);
		if (v.isEmpty()) return 0;
		double d = v.getAsDouble();
		if (which.equals("pet_luck")) {
			if (petLuck == null || petLuck != d) dirty = true;
			petLuck = d;
		} else if (rift) {
			if (riftMagicFind == null || riftMagicFind != d) dirty = true;
			riftMagicFind = d;
		} else {
			if (magicFind == null || magicFind != d) dirty = true;
			magicFind = d;
		}
		return 1;
	}

	private static int merge(Map<String, Integer> into, Map<String, Integer> found) {
		for (var e : found.entrySet()) {
			Integer old = into.put(e.getKey(), e.getValue());
			if (!e.getValue().equals(old)) dirty = true;
		}
		return found.size();
	}

	/** Every client tick: read the tab list now and then, and send when something changed. */
	public static void tick() {
		if (!Mythos.config().magicgrind || !Mythos.active()) return;
		if (++ticks % 100 == 0) {
			List<String> tab = TabList.lines();
			boolean rift = MagicGrindParse.inRift(tab);
			stat(tab, "magic_find", rift);
			stat(tab, "pet_luck", rift);
		}
		long now = System.currentTimeMillis();
		boolean have = magicFind != null || petLuck != null || riftMagicFind != null || !SLAYERS.isEmpty() || !BESTIARY.isEmpty();
		if (!have || !MagicGrindClient.configured()) return;
		if ((dirty && now - lastSent > MIN_GAP_MS) || now - lastSent > HEARTBEAT_MS) {
			dirty = false;
			lastSent = now;
			MagicGrindClient.send(payload());
		}
	}

	/** Forces the next tick to send (after the address or key changed). */
	public static void resend() {
		dirty = true;
		lastSent = 0;
	}

	static JsonObject payload() {
		JsonObject j = new JsonObject();
		var player = Minecraft.getInstance().player;
		if (player != null) j.addProperty("name", player.getScoreboardName());
		if (magicFind != null) j.addProperty("base_mf", magicFind);
		if (petLuck != null) j.addProperty("pet_luck", petLuck);
		if (riftMagicFind != null) j.addProperty("rift_mf", riftMagicFind);
		if (!SLAYERS.isEmpty()) {
			JsonObject s = new JsonObject();
			SLAYERS.forEach(s::addProperty);
			j.add("slayers", s);
		}
		if (!BESTIARY.isEmpty()) {
			JsonObject b = new JsonObject();
			BESTIARY.forEach((fam, tier) -> {
				JsonObject t = new JsonObject();
				t.addProperty("tier", tier);
				b.add(fam, t);
			});
			j.add("bestiary", b);
		}
		j.addProperty("mod_version", FabricLoader.getInstance().getModContainer(Mythos.ID)
				.map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?"));
		return j;
	}
}
