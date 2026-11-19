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

	private static Double magicFind, petLuck, riftMagicFind, tracking;
	/** Magic Find on the weapon in hand while playing, and on whatever was in hand when the stats were read. */
	private static Double weaponMf, heldAtRead;
	private static String weaponName = "";
	private static final Map<String, Integer> SLAYERS = new TreeMap<>();
	private static final Map<String, Integer> BESTIARY = new TreeMap<>();
	private static List<MagicGrindParse.FlatEntry> flat = List.of();
	private static long bagVersion = -1;
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
		return title.equals("SkyBlock Menu") || title.contains("Stats") || title.contains("Profile") || title.startsWith("Magic Find")
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
		int mfRead = stat(all, "magic_find", rift);
		if (mfRead > 0 && !rift) {
			// the stats menu counts the item in your hand; remember it so the weapon isn't counted twice
			double held = heldMagicFind();
			if (heldAtRead == null || heldAtRead != held) dirty = true;
			heldAtRead = held;
		}
		readHere += mfRead;
		readHere += stat(all, "pet_luck", rift);
		readHere += stat(all, "tracking", rift);
		if (menu.title.startsWith("Magic Find") && menu.title.contains("Bonuses")) {
			List<MagicGrindParse.FlatEntry> found = new ArrayList<>();
			for (Menu.Entry e : menu.container) {
				MagicGrindParse.FlatEntry f = MagicGrindParse.flatBonus(e.name(), e.lore());
				if (f != null) found.add(f);
			}
			if (!found.isEmpty() && !found.equals(flat)) {
				flat = List.copyOf(found);
				dirty = true;
			}
			readHere += found.size();
		}
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
		if (heldAtRead != null && heldAtRead > 0) panel.kv("of which held item", dev.aplseed.mythos.util.Fmt.full(heldAtRead), Panel.GRAY);
		if (!SLAYERS.isEmpty()) panel.kv("Slayer levels read", Integer.toString(SLAYERS.size()), Panel.WHITE);
		if (!BESTIARY.isEmpty()) panel.kv("Bestiary families read", Integer.toString(BESTIARY.size()), Panel.WHITE);
		if (menu.title.startsWith("Bestiary")) panel.line("Open each island's page once to send its tiers", Panel.DARK);
	}

	private static int stat(List<String> lines, String which, boolean rift) {
		OptionalDouble v = MagicGrindParse.stat(lines, which);
		if (v.isEmpty()) return 0;
		double d = v.getAsDouble();
		if (which.equals("tracking")) {
			if (tracking == null || tracking != d) dirty = true;
			tracking = d;
		} else if (which.equals("pet_luck")) {
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
			stat(tab, "tracking", rift);
		}
		if (dev.aplseed.mythos.acc.Bag.scanned() && dev.aplseed.mythos.acc.Bag.version() != bagVersion) {
			bagVersion = dev.aplseed.mythos.acc.Bag.version();
			dirty = true;
		}
		long now = System.currentTimeMillis();
		boolean have = !flat.isEmpty() || bagVersion >= 0 || magicFind != null || petLuck != null || weaponMf != null || riftMagicFind != null || !SLAYERS.isEmpty() || !BESTIARY.isEmpty();
		if (!have || !MagicGrindClient.configured()) return;
		if ((dirty && now - lastSent > MIN_GAP_MS) || now - lastSent > HEARTBEAT_MS) {
			dirty = false;
			lastSent = now;
			MagicGrindClient.send(payload());
		}
	}

	private static double heldMagicFind() {
		var player = Minecraft.getInstance().player;
		if (player == null) return 0;
		return MagicGrindParse.itemMagicFind(dev.aplseed.mythos.util.Items.lore(player.getMainHandItem())).orElse(0);
	}

	/** A RARE DROP chat line: the game's own Magic Find for that drop. */
	public static void onChat(net.minecraft.network.chat.Component message, boolean overlay) {
		if (overlay || !Mythos.config().magicgrind || !Mythos.active()) return;
		String text = dev.aplseed.mythos.util.Items.plain(message);
		if (!text.contains("DROP!")) return;
		MagicGrindParse.RareDrop d = MagicGrindParse.rareDrop(text);
		if (d != null) MagicGrindClient.sendDrop(d.item(), d.magicFind());
	}

	/** Called when you hit a mob: the item you attack with is the weapon whose Magic Find counts. */
	public static void onAttack() {
		if (Mythos.config().magicgrind && Mythos.active()) readWeapon();
	}

	/** The weapon in hand: its own Magic Find line, 0 if it has none. */
	private static void readWeapon() {
		var player = Minecraft.getInstance().player;
		if (player == null) return;
		var stack = player.getMainHandItem();
		if (stack.isEmpty()) return;
		List<String> lore = dev.aplseed.mythos.util.Items.lore(stack);
		OptionalDouble mf = MagicGrindParse.itemMagicFind(lore);
		if (mf.isEmpty() && !MagicGrindParse.isWeapon(lore)) return;   // tools, menus, food: keep the last weapon
		double v = mf.orElse(0);
		String name = dev.aplseed.mythos.util.Items.name(stack);
		if (weaponMf == null || weaponMf != v || !name.equals(weaponName)) dirty = true;
		weaponMf = v;
		weaponName = name;
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
		if (tracking != null) j.addProperty("tracking", tracking);
		if (!flat.isEmpty()) {
			com.google.gson.JsonArray arr = new com.google.gson.JsonArray();
			for (MagicGrindParse.FlatEntry f : flat) {
				JsonObject o = new JsonObject();
				o.addProperty("name", f.name());
				o.addProperty("kind", f.kind());
				o.addProperty("value", f.value());
				com.google.gson.JsonArray ps = new com.google.gson.JsonArray();
				for (MagicGrindParse.FlatPart p : f.parts()) {
					JsonObject po = new JsonObject();
					po.addProperty("name", p.name());
					po.addProperty("value", p.value());
					ps.add(po);
				}
				o.add("parts", ps);
				arr.add(o);
			}
			j.add("mf_flat", arr);
		}
		if (dev.aplseed.mythos.acc.Bag.scanned()) {
			com.google.gson.JsonArray ids = new com.google.gson.JsonArray();
			for (var o : dev.aplseed.mythos.acc.Bag.owned()) if (o.inBag() && !o.id().isEmpty()) ids.add(o.id());
			j.add("accessories", ids);
		}
		if (heldAtRead != null) j.addProperty("held_mf_at_read", heldAtRead);
		if (weaponMf != null) {
			j.addProperty("weapon_mf", weaponMf);
			j.addProperty("weapon_name", weaponName.replaceAll("[^\\p{L}\\p{N} '-]", "").trim());
		}
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
