package dev.aplseed.mythos.acc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a scanned accessory bag is worth and every way to raise it: which items
 * count, which are dupes, what's missing, and - per accessory line - the
 * mutually exclusive things coins can buy (the {@link Planner}'s input).
 */
public final class Analysis {
	/** An accessory the player has. `inBag` false: seen in the inventory, not in the bag. */
	public record Owned(String id, String name, Rarity rarity, boolean recomb, int page, int slot, boolean inBag) {}

	/**
	 * `unlocks`: requirement key -> the player's level (missing = not told, treated as locked).
	 * `jacobusBought`: bag slot upgrades already bought from Jacobus (0-99), or -1 if unknown.
	 * `craft`: price accessories by their recipe when that's cheaper than buying them.
	 * `collections`: collection name (lower case) -> tier reached; a missing one was never seen,
	 * and recipes it unlocks are not crafted.
	 */
	public record Player(Map<String, Integer> unlocks, int freeSlots, int jacobusBought, boolean riftPrism, boolean craft,
			Map<String, Integer> collections) {}

	public record Counted(Owned item, int mp) {}

	/** An item in the bag that adds nothing because `kept` already counts for its line. */
	public record Dupe(Owned item, Owned kept, boolean sameItem) {}

	public enum Kind { RECOMB, UPGRADE, NEW, INVENTORY }

	/**
	 * One purchasable step. `gain` is Magical Power over what the line gives now; `slots` is 1
	 * for a line the bag doesn't hold yet. `recomb`: the cost includes a Recombobulator 3000.
	 */
	public record Option(String family, Kind kind, String id, String name, Rarity result, boolean recomb,
			double cost, int gain, int slots, String how, String replaces) {
		public double perMp() {
			return gain <= 0 ? Double.POSITIVE_INFINITY : cost / gain;
		}
	}

	/** The best step open for a line the bag lacks (or holds a lower tier of). */
	public record Missing(Catalog.Acc acc, double price, String how, int gain, String locked, boolean upgrade) {
		public double perMp() {
			return Double.isNaN(price) || gain <= 0 ? Double.POSITIVE_INFINITY : price / gain;
		}
	}

	public static final int RIFT_PRISM_MP = 11;

	public int magicPower;
	public final List<Counted> counted = new ArrayList<>();
	public final List<Dupe> dupes = new ArrayList<>();
	public final List<Owned> inventory = new ArrayList<>();
	/** one list per accessory line: pick at most one */
	public final List<List<Option>> groups = new ArrayList<>();
	public final List<Missing> missing = new ArrayList<>();
	/** lines that would add power but are locked behind a requirement */
	public int locked;
	/** requirement keys the player hasn't given a level for, that matter here */
	public final Set<String> unknownUnlocks = new HashSet<>();
	/** collections never seen whose recipes would have been used: "Feather IX" */
	public final Set<String> unknownCollections = new java.util.TreeSet<>();
	/** recipes the player's collections are too low for: "Feather IX (yours: VII)" */
	public final Set<String> lowCollections = new java.util.TreeSet<>();
	public double recombPrice;
	public int recombable;

	private Analysis() {}

	/** Why `acc` can't be used, or null: "needs Wolf Slayer 5 (yours: 3)". */
	public static String lockedBy(Catalog.Acc acc, Player p, Set<String> unknown) {
		for (Catalog.Req r : acc.reqs()) {
			Integer have = p.unlocks().get(r.key());
			if (have == null) {
				if (unknown != null) unknown.add(r.key());
				return "needs " + r.label() + " (yours: not set)";
			}
			if (have < r.level()) return "needs " + r.label() + " (yours: " + Catalog.levelName(r.key(), have) + ")";
		}
		return null;
	}

	/** `used`: the owned accessory a craft consumes; `needs`: what unlocks the recipe. */
	private record Source(double cost, boolean craft, String used, String needs) {
		String how() {
			if (!craft) return "lowest BIN";
			return (used != null ? "craft from your " + used : "craft") + (needs != null ? ", needs " + needs : "");
		}
	}

	/**
	 * Cheapest way to get `id` when the line's owned items in `usable` can be crafted into it:
	 * buying it, or (if the player crafts) its recipe with owned lower tiers free and the rest
	 * at market price. A recipe behind a slayer level or collection tier the player lacks (or
	 * hasn't shown) isn't an option.
	 */
	private Source acquire(Catalog cat, Prices prices, Player player, String id, Set<String> usable, int depth) {
		// an accessory can only be bought if a buy-it-now auction for it is up
		double ah = prices.ah(id);
		Source best = Double.isNaN(ah) ? null : new Source(ah, false, null, null);
		Prices.Recipe recipe = !player.craft() || depth > 4 ? null : prices.recipes().get(id);
		if (recipe == null || recipe.parts().isEmpty()) return best;
		if (recipe.slayerKey() != null && player.unlocks().getOrDefault(recipe.slayerKey(), -1) < recipe.slayerLevel()) return best;
		Prices.Collection need = recipe.collection();
		if (need != null) {
			Integer have = Prices.tier(player.collections(), need.name());
			if (have == null) {
				unknownCollections.add(recipe.needs());
				return best;
			}
			if (have < need.tier()) {
				lowCollections.add(recipe.needs() + " (yours: " + (have == 0 ? "none" : Prices.roman(have)) + ")");
				return best;
			}
		}
		// a requirement that was checked needn't be repeated in the step; one that can't be is
		double sum = 0;
		String used = null, needs = need != null || recipe.slayerKey() != null ? null : recipe.needs();
		for (Map.Entry<String, Integer> in : recipe.parts().entrySet()) {
			String part = in.getKey();
			int n = in.getValue();
			if (n == 1 && usable.contains(part)) {
				used = cat.name(part);
				continue;
			}
			boolean accessory = cat.byId.containsKey(part);
			double each = accessory ? prices.ah(part) : prices.market(part);
			if (accessory) {     // a lower tier: BIN only, or maybe cheaper to craft that too
				Source sub = acquire(cat, prices, player, part, usable, depth + 1);
				if (sub != null && sub.craft && (Double.isNaN(each) || sub.cost < each)) {
					each = sub.cost;
					if (sub.used != null) used = sub.used;
					if (needs == null) needs = sub.needs;
				}
			}
			sum += each * n;     // NaN if any part has no price
		}
		if (!Double.isNaN(sum) && (best == null || sum < best.cost)) best = new Source(sum, true, used, needs);
		return best;
	}

	public static Analysis of(Catalog cat, Prices prices, List<Owned> owned, Player player) {
		Analysis a = new Analysis();
		a.recombPrice = prices.recomb();

		Map<String, List<Owned>> bag = new LinkedHashMap<>(), inv = new HashMap<>();
		for (Owned o : owned) {
			(o.inBag() ? bag : inv).computeIfAbsent(cat.familyOf(o.id()), k -> new ArrayList<>()).add(o);
			if (!o.inBag()) a.inventory.add(o);
		}

		// what counts now: the strongest item of each line
		Map<String, Owned> best = new HashMap<>();
		for (Map.Entry<String, List<Owned>> e : bag.entrySet()) {
			Owned top = null;
			for (Owned o : e.getValue()) if (top == null || mp(o) > mp(top)) top = o;
			best.put(e.getKey(), top);
			a.counted.add(new Counted(top, mp(top)));
			a.magicPower += mp(top);
			for (Owned o : e.getValue()) if (o != top) a.dupes.add(new Dupe(o, top, o.id().equals(top.id())));
		}
		if (player.riftPrism()) a.magicPower += RIFT_PRISM_MP;
		a.counted.sort(Comparator.comparingInt((Counted c) -> -c.mp).thenComparing(c -> c.item.name()));

		Set<String> families = new HashSet<>(cat.families.keySet());
		families.addAll(bag.keySet());
		families.addAll(inv.keySet());
		List<String> sorted = new ArrayList<>(families);
		sorted.sort(null);
		for (String fam : sorted) a.line(cat, prices, player, fam, best.get(fam), bag.getOrDefault(fam, List.of()), inv.getOrDefault(fam, List.of()));

		a.missing.sort(Comparator.comparingDouble(Missing::perMp).thenComparing(m -> m.acc.name()));
		return a;
	}

	private static int mp(Owned o) {
		return Catalog.mult(o.id()) * o.rarity().mp;
	}

	private void line(Catalog cat, Prices prices, Player player, String fam, Owned cur, List<Owned> inBag, List<Owned> inInv) {
		int now = cur == null ? 0 : mp(cur);
		int slots = cur == null ? 1 : 0;
		List<Option> opts = new ArrayList<>();
		boolean haveRecomb = !Double.isNaN(recombPrice);

		if (cur != null && !cur.recomb() && cur.rarity().up() != null) {
			Catalog.Acc acc = cat.byId.get(cur.id());
			int gain = Catalog.mult(cur.id()) * cur.rarity().up().mp - now;
			if ((acc == null || acc.canRecomb()) && gain > 0) {
				recombable++;
				if (haveRecomb) {
					opts.add(new Option(fam, Kind.RECOMB, cur.id(), cur.name(), cur.rarity().up(), true, recombPrice, gain, 0,
							"recombobulate", null));
				}
			}
		}

		// accessories lying in the inventory: free, they only need a slot
		for (Owned o : inInv) {
			if (mp(o) > now) {
				opts.add(new Option(fam, Kind.INVENTORY, o.id(), o.name(), o.rarity(), false, 0, mp(o) - now, slots,
						"in your inventory", cur == null ? null : cur.name()));
			}
		}

		Set<String> usable = new HashSet<>();
		for (Owned o : inBag) usable.add(o.id());
		for (Owned o : inInv) usable.add(o.id());

		Missing lead = null;
		for (Catalog.Acc t : cat.families.getOrDefault(fam, List.of())) {
			if (!t.obtainable() || usable.contains(t.id())) continue;
			int plain = t.mp(t.tier()) - now;
			Rarity up = t.canRecomb() ? t.tier().up() : null;
			int boosted = up == null ? 0 : t.mp(up) - now;
			if (plain <= 0 && boosted <= 0) continue;
			String why = lockedBy(t, player, unknownUnlocks);
			Source src = why != null ? null : acquire(cat, prices, player, t.id(), usable, 0);
			double cost = src == null ? Double.NaN : src.cost;
			String how = src == null ? null : src.how();
			Missing m = new Missing(t, cost, how, Math.max(plain, 0), why, cur != null);
			if (plain > 0 && better(m, lead)) lead = m;
			if (src == null) continue;
			Kind kind = cur == null ? Kind.NEW : Kind.UPGRADE;
			String replaces = cur == null ? null : cur.name();
			if (plain > 0) opts.add(new Option(fam, kind, t.id(), t.name(), t.tier(), false, cost, plain, slots, how, replaces));
			if (boosted > plain && boosted > 0 && haveRecomb) {
				opts.add(new Option(fam, kind, t.id(), t.name(), up, true, cost + recombPrice, boosted, slots, how, replaces));
			}
		}
		if (lead != null) {
			missing.add(lead);
			if (lead.locked != null) locked++;
		}

		// drop options another one beats on both cost and gain
		opts.sort(Comparator.comparingDouble(Option::cost).thenComparingInt(o -> -o.gain));
		List<Option> kept = new ArrayList<>();
		int top = 0;
		for (Option o : opts) {
			if (o.gain > top) {
				kept.add(o);
				top = o.gain;
			}
		}
		if (!kept.isEmpty()) groups.add(kept);
	}

	/** Usable and priced beats locked or unpriced; then cheaper per Magical Power. */
	private static boolean better(Missing m, Missing than) {
		if (than == null) return true;
		boolean a = m.locked == null && !Double.isNaN(m.price), b = than.locked == null && !Double.isNaN(than.price);
		if (a != b) return a;
		if (a) return m.perMp() < than.perMp();
		boolean la = m.locked == null, lb = than.locked == null;
		if (la != lb) return la;
		return m.gain > than.gain;
	}
}
