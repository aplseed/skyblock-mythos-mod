package dev.aplseed.mythos.acc;

import java.util.Map;

/** Market data the calculator prices with. All maps are read-only snapshots. */
public record Prices(Map<String, Double> lbin, Map<String, Double> bazaar, Map<String, Recipe> recipes) {
	public static final String RECOMB = "RECOMBOBULATOR_3000";

	/**
	 * A crafting recipe: ingredient id -> count. `needs` is what unlocks it ("Feather IX",
	 * "Wolf Slayer 5"), or null; a slayer requirement is also given as `slayerKey`/`slayerLevel`
	 * ("slayer_wolf", 5) so it can be checked against the player's levels.
	 */
	public record Recipe(Map<String, Integer> parts, String needs, String slayerKey, int slayerLevel) {
		public static Recipe of(Map<String, Integer> parts) {
			return new Recipe(parts, null, null, 0);
		}

		/** The collection tier that unlocks the recipe ("Feather IX" -> Feather, 9), or null. */
		public Collection collection() {
			if (needs == null || slayerKey != null) return null;
			int cut = needs.lastIndexOf(' ');
			int tier = cut < 1 ? 0 : roman(needs.substring(cut + 1));
			return tier == 0 ? null : new Collection(needs.substring(0, cut).trim(), tier);
		}
	}

	public record Collection(String name, int tier) {}

	/** "IX" -> 9; 0 if it isn't a roman numeral. */
	public static int roman(String s) {
		int total = 0, prev = 0;
		for (int i = s.length() - 1; i >= 0; i--) {
			int v = switch (s.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				case 'D' -> 500;
				case 'M' -> 1000;
				default -> -1;
			};
			if (v < 0) return 0;
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
	}

	public static String roman(int n) {
		String[] r = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < v.length; i++) {
			while (n >= v[i]) {
				sb.append(r[i]);
				n -= v[i];
			}
		}
		return sb.toString();
	}

	/**
	 * The player's tier in `collection`, or null if it was never seen. Names are matched
	 * loosely ("Redstone" and "Redstone Dust" are the same collection under two names).
	 */
	public static Integer tier(Map<String, Integer> collections, String collection) {
		String k = collection.toLowerCase(java.util.Locale.ROOT);
		Integer exact = collections.get(k);
		if (exact != null) return exact;
		for (Map.Entry<String, Integer> e : collections.entrySet()) {
			if (e.getKey().startsWith(k + " ") || k.startsWith(e.getKey() + " ")) return e.getValue();
		}
		return null;
	}

	public static Prices empty() {
		return new Prices(Map.of(), Map.of(), Map.of());
	}

	/** Lowest BIN, or NaN when none is listed. */
	public double ah(String id) {
		Double v = lbin.get(id);
		return v == null || v <= 0 ? Double.NaN : v;
	}

	/** What one costs right now: bazaar instant-buy, else lowest BIN, else NaN. */
	public double market(String id) {
		Double v = bazaar.get(id);
		return v != null && v > 0 ? v : ah(id);
	}

	public double recomb() {
		return market(RECOMB);
	}
}
