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
