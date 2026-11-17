package dev.aplseed.mythos.acc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.acc.Analysis.Owned;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The accessory bag as last seen, kept in config/mythos/accessories.json so the calculator
 * works without reopening the bag every session.
 *
 * Since SkyBlock 0.27.3 the bag has one slot per accessory line and can be sorted and
 * searched, so a page doesn't always show the same accessories. What's stored is per line:
 * the accessory last seen in it, or nothing once the line shows up empty.
 */
public final class Bag {
	public static final class Item {
		public String id = "", name = "", rarity = "COMMON";
		public boolean recomb;
	}

	/** One accessory line as a bag page shows it: what's in it, or null when it's empty. */
	public record Seen(String line, Item item) {}

	private static final class State {
		int version = 2;
		int pages;
		Set<Integer> pagesSeen = new TreeSet<>();
		Map<String, Item> lines = new TreeMap<>();
		/** bag slots in use and in total, from the game's "Accessory Bag Slots: 45/63"; -1 unknown */
		int used = -1, capacity = -1;
		/** the game's own Accessory Power total, when it shows one */
		int power = -1;
		long at;
	}

	// "Accessory Bag Slots: 45/63", "Slots: 45/63", "Slots Used: 1,045 / 1,063"
	private static final Pattern SLOTS = Pattern.compile("(?i)\\bslots?(?: used)?:\\s*([0-9,]+)\\s*/\\s*([0-9,]+)");
	// "Accessory Power: 1,234" (the old name, "Magical Power", is read too)
	private static final Pattern POWER = Pattern.compile("(?i)(?:total )?(?:accessory|magical) power:\\s*([0-9,]+)");

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static State state;
	private static List<Owned> inventory = List.of();
	private static long version;

	private Bag() {}

	private static Path file() {
		return Mythos.configDir().resolve("accessories.json");
	}

	private static State state() {
		if (state == null) {
			try {
				if (Files.exists(file())) state = GSON.fromJson(Files.readString(file()), State.class);
			} catch (Exception e) {
				Mythos.LOG.warn("Unreadable {}: {}", file(), e.toString());
			}
			// a scan from before the 0.27.3 bag stored pages, not lines: it doesn't carry over
			if (state == null || state.version != 2 || state.lines == null) state = new State();
			if (state.pagesSeen == null) state.pagesSeen = new TreeSet<>();
			state.lines.values().removeIf(i -> i == null || i.id == null);
		}
		return state;
	}

	private static void save() {
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(state));
		} catch (Exception e) {
			Mythos.LOG.warn("Couldn't save {}: {}", file(), e.toString());
		}
	}

	public static long version() {
		return version;
	}

	/** Stores what one page of the bag (1-based, out of `pages`) shows. */
	public static void record(int page, int pages, List<Seen> seen) {
		State s = state();
		if (pages != s.pages) s.pagesSeen.clear();     // a search or a bigger bag: count pages again
		s.pages = Math.max(1, pages);
		s.pagesSeen.add(page);
		for (Seen x : seen) {
			if (x.item() == null) s.lines.remove(x.line());
			else s.lines.put(x.line(), x.item());
		}
		s.at = System.currentTimeMillis();
		version++;
		save();
	}

	/**
	 * Reads slot use and the Accessory Power total from text the game shows: the bag's own
	 * items, or the "Accessory Bag" tab-list widget. Returns true if anything was found.
	 */
	public static boolean readCounts(List<String> lines) {
		State s = state();
		boolean found = false;
		for (String line : lines) {
			Matcher m = SLOTS.matcher(line);
			if (m.find()) {
				long used = Long.parseLong(m.group(1).replace(",", "")), cap = Long.parseLong(m.group(2).replace(",", ""));
				if (cap > 0 && cap < 10_000 && used <= cap && (used != s.used || cap != s.capacity)) {
					s.used = (int) used;
					s.capacity = (int) cap;
					found = true;
				}
			}
			Matcher p = POWER.matcher(line);
			if (p.find()) {
				long power = Long.parseLong(p.group(1).replace(",", ""));
				if (power < 100_000 && power != s.power) {
					s.power = (int) power;
					found = true;
				}
			}
		}
		if (found) {
			version++;
			save();
		}
		return found;
	}

	/** Accessories in the player's inventory (not stored: they move). */
	public static void inventory(List<Owned> items) {
		if (items.equals(inventory)) return;
		inventory = List.copyOf(items);
		version++;
	}

	public static void clear() {
		state = new State();
		inventory = List.of();
		version++;
		save();
	}

	public static boolean scanned() {
		return !state().pagesSeen.isEmpty();
	}

	public static int pages() {
		return state().pages;
	}

	public static int pagesSeen() {
		return state().pagesSeen.size();
	}

	/** Pages of the bag not opened yet: "2, 3". */
	public static String pagesMissing() {
		State s = state();
		List<String> out = new ArrayList<>();
		for (int i = 1; i <= s.pages; i++) if (!s.pagesSeen.contains(i)) out.add(Integer.toString(i));
		return String.join(", ", out);
	}

	public static boolean complete() {
		State s = state();
		return s.pages > 0 && s.pagesSeen.size() >= s.pages;
	}

	public static long scannedAt() {
		return state().at;
	}

	/** Whether the game has shown how many slots the bag has. */
	public static boolean slotsKnown() {
		return Mythos.config().accFreeSlots >= 0 || state().capacity >= 0;
	}

	/** Bag slots not holding an accessory: the `/myth acc slots` override, else the game's count, else 0. */
	public static int freeSlots() {
		int override = Mythos.config().accFreeSlots;
		if (override >= 0) return override;
		State s = state();
		return s.capacity >= 0 ? Math.max(0, s.capacity - s.used) : 0;
	}

	public static int capacity() {
		return state().capacity;
	}

	/** The game's own Accessory Power total, or -1 if it hasn't shown one. */
	public static int gamePower() {
		return state().power;
	}

	public static int size() {
		return state().lines.size();
	}

	/** Everything the player holds: the scanned bag, then accessories in the inventory. */
	public static List<Owned> owned() {
		List<Owned> out = new ArrayList<>();
		for (Item i : state().lines.values()) {
			Rarity r = Rarity.parse(i.rarity);
			out.add(new Owned(i.id, i.name, r == null ? Rarity.COMMON : r, i.recomb, 0, -1, true));
		}
		out.addAll(inventory);
		return out;
	}
}
