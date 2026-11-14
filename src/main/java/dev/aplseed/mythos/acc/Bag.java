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
import java.util.TreeMap;

/**
 * The accessory bag as last seen, page by page (the game only shows one page
 * at a time). Kept in config/mythos/accessories.json so the calculator works
 * without reopening the bag every session.
 */
public final class Bag {
	public static final class Item {
		public String id = "", name = "", rarity = "COMMON";
		public boolean recomb;
		public int slot;
	}

	public static final class Page {
		public List<Item> items = new ArrayList<>();
		public int free;
		public long at;
	}

	private static final class State {
		int pages;
		Map<Integer, Page> seen = new TreeMap<>();
	}

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
			if (state == null) state = new State();
			if (state.seen == null) state.seen = new TreeMap<>();
			state.seen.values().removeIf(p -> p == null || p.items == null);
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

	/** Stores one page of the bag (1-based) out of `pages`. */
	public static void record(int page, int pages, List<Item> items, int free) {
		State s = state();
		s.pages = Math.max(1, pages);
		s.seen.keySet().removeIf(k -> k > s.pages);
		Page p = new Page();
		p.items = new ArrayList<>(items);
		p.free = free;
		p.at = System.currentTimeMillis();
		s.seen.put(page, p);
		version++;
		save();
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
		return !state().seen.isEmpty();
	}

	public static int pages() {
		return state().pages;
	}

	public static int pagesSeen() {
		return state().seen.size();
	}

	/** Pages of the bag not opened yet: "2, 3". */
	public static String pagesMissing() {
		State s = state();
		List<String> out = new ArrayList<>();
		for (int i = 1; i <= s.pages; i++) if (!s.seen.containsKey(i)) out.add(Integer.toString(i));
		return String.join(", ", out);
	}

	public static boolean complete() {
		State s = state();
		return s.pages > 0 && s.seen.size() >= s.pages;
	}

	public static long scannedAt() {
		long newest = 0;
		for (Page p : state().seen.values()) newest = Math.max(newest, p.at);
		return newest;
	}

	/** Empty slots on the pages seen, or the `/myth acc slots` override. */
	public static int freeSlots() {
		int override = Mythos.config().accFreeSlots;
		if (override >= 0) return override;
		int free = 0;
		for (Page p : state().seen.values()) free += p.free;
		return free;
	}

	public static int size() {
		int n = 0;
		for (Page p : state().seen.values()) n += p.items.size();
		return n;
	}

	/** Everything the player holds: the scanned bag, then accessories in the inventory. */
	public static List<Owned> owned() {
		List<Owned> out = new ArrayList<>();
		for (Map.Entry<Integer, Page> e : state().seen.entrySet()) {
			for (Item i : e.getValue().items) {
				Rarity r = Rarity.parse(i.rarity);
				out.add(new Owned(i.id, i.name, r == null ? Rarity.COMMON : r, i.recomb, e.getKey(), i.slot, true));
			}
		}
		out.addAll(inventory);
		return out;
	}
}
