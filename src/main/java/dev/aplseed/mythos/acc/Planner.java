package dev.aplseed.mythos.acc;

import dev.aplseed.mythos.acc.Analysis.Option;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Spends a budget for the most Magical Power. Every accessory line offers a few
 * exclusive options (buy it, buy a higher tier, add a recombobulator); new lines
 * also take a bag slot, and slots beyond the free ones cost coins at Jacobus.
 * That is a multiple-choice knapsack over coins and slots, solved exactly:
 * gains are small integers, so the tables hold the cheapest way to reach each
 * (slots, gain), and the picks are recovered by divide and conquer.
 */
public final class Planner {
	public static final int JACOBUS_MAX = 99;
	public static final int SLOTS_PER_UPGRADE = 2;

	/**
	 * `dupesToRemove`: bag slots the plan frees by taking dupes out; `jacobusUpgrades`: slot
	 * upgrades to buy (`slotCost` coins). `total` = picks + slots; `limitedBySlots`: more
	 * coins wouldn't help without more slots (and Jacobus' count isn't known or is maxed).
	 */
	public record Plan(List<Option> picks, double budget, double itemCost, double slotCost, double total, int gain,
			int slotsUsed, int dupesToRemove, int jacobusUpgrades, boolean limitedBySlots) {}

	private Planner() {}

	/** Price of Jacobus' n-th accessory bag upgrade (1-based): 1.5M, 4x 5M, 5x 8M, 10x 12M, then 20M. */
	public static double jacobusPrice(int n) {
		if (n <= 1) return 1_500_000;
		if (n <= 5) return 5_000_000;
		if (n <= 10) return 8_000_000;
		if (n <= 20) return 12_000_000;
		return 20_000_000;
	}

	/** Cost of `more` upgrades after `bought`; infinite past the 99th. */
	public static double jacobusCost(int bought, int more) {
		if (more <= 0) return 0;
		if (bought < 0 || bought + more > JACOBUS_MAX) return Double.POSITIVE_INFINITY;
		double sum = 0;
		for (int i = 1; i <= more; i++) sum += jacobusPrice(bought + i);
		return sum;
	}

	private static final double INF = Double.POSITIVE_INFINITY;

	/** cost[g] = cheapest way to gain exactly g from `groups`, with the option picked per group. */
	private record Line(double[] cost, byte[][] choice) {}

	private static Line oneD(List<List<Option>> groups, int cap) {
		double[] cost = new double[cap + 1];
		Arrays.fill(cost, INF);
		cost[0] = 0;
		byte[][] choice = new byte[groups.size()][cap + 1];
		int reach = 0;
		for (int i = 0; i < groups.size(); i++) {
			List<Option> g = groups.get(i);
			int top = 0;
			for (Option o : g) top = Math.max(top, o.gain());
			int hi = Math.min(cap, reach + top);
			for (int v = hi; v >= 0; v--) {
				double bestCost = cost[v];
				byte pick = 0;
				for (int k = 0; k < g.size(); k++) {
					Option o = g.get(k);
					if (o.gain() > v) continue;
					double c = cost[v - o.gain()] + o.cost();
					if (c < bestCost) {
						bestCost = c;
						pick = (byte) (k + 1);
					}
				}
				cost[v] = bestCost;
				choice[i][v] = pick;
			}
			reach = hi;
		}
		return new Line(cost, choice);
	}

	private static List<Option> trace(List<List<Option>> groups, Line line, int gain) {
		List<Option> out = new ArrayList<>();
		for (int i = groups.size() - 1; i >= 0 && gain > 0; i--) {
			int pick = line.choice[i][gain];
			if (pick == 0) continue;
			Option o = groups.get(i).get(pick - 1);
			out.add(o);
			gain -= o.gain();
		}
		return out;
	}

	/** t[s][g] = cheapest way to take exactly s options (one per group at most) gaining exactly g. */
	private static double[][] twoD(List<List<Option>> groups, int from, int to, int maxS, int maxG, BooleanSupplier cancelled) {
		double[][] t = new double[maxS + 1][maxG + 1];
		for (double[] row : t) Arrays.fill(row, INF);
		t[0][0] = 0;
		int reachS = 0, reachG = 0;
		for (int i = from; i < to; i++) {
			if (cancelled.getAsBoolean()) return t;
			List<Option> g = groups.get(i);
			int top = 0;
			for (Option o : g) top = Math.max(top, o.gain());
			int hiS = Math.min(maxS, reachS + 1), hiG = Math.min(maxG, reachG + top);
			for (int s = hiS; s >= 1; s--) {     // row s-1 is still the table without this group
				double[] row = t[s], prev = t[s - 1];
				for (Option o : g) {
					int gain = o.gain();
					double c = o.cost();
					for (int v = hiG; v >= gain; v--) {
						double x = prev[v - gain] + c;
						if (x < row[v]) row[v] = x;
					}
				}
			}
			reachS = hiS;
			reachG = hiG;
		}
		return t;
	}

	/** The options (from groups[from, to)) that reach exactly (s, g) at the table's cost. */
	private static void recover(List<List<Option>> groups, int from, int to, int s, int g, List<Option> out, BooleanSupplier cancelled) {
		if (s == 0 || to <= from || cancelled.getAsBoolean()) return;
		if (to - from == 1) {
			Option best = null;
			for (Option o : groups.get(from)) if (o.gain() == g && (best == null || o.cost() < best.cost())) best = o;
			if (best != null) out.add(best);
			return;
		}
		int mid = (from + to) >>> 1;
		double[][] a = twoD(groups, from, mid, s, g, cancelled), b = twoD(groups, mid, to, s, g, cancelled);
		double best = INF;
		int bs = 0, bg = 0;
		for (int s1 = 0; s1 <= s; s1++) {
			for (int g1 = 0; g1 <= g; g1++) {
				double x = a[s1][g1] + b[s - s1][g - g1];
				if (x < best) {
					best = x;
					bs = s1;
					bg = g1;
				}
			}
		}
		a = null;
		b = null;
		recover(groups, from, mid, bs, bg, out, cancelled);
		recover(groups, mid, to, s - bs, g - bg, out, cancelled);
	}

	/** Coins for the bag to take `s` new lines: free slots first, then dupes come out, then Jacobus. */
	private static double slotCost(int s, int free, int dupes, int jacobus) {
		int extra = s - free - dupes;
		return extra <= 0 ? 0 : jacobusCost(jacobus, (extra + SLOTS_PER_UPGRADE - 1) / SLOTS_PER_UPGRADE);
	}

	public static Plan plan(Analysis a, Analysis.Player p, double budget, BooleanSupplier cancelled) {
		budget = Math.max(0, budget);
		int free = Math.max(0, p.freeSlots()), dupes = a.dupes.size(), jac = p.jacobusBought();

		// options the budget can pay for, split by whether they need a slot
		List<List<Option>> upgrades = new ArrayList<>(), fresh = new ArrayList<>();
		int capUp = 0, capNew = 0;
		for (List<Option> group : a.groups) {
			List<Option> ok = new ArrayList<>();
			int top = 0;
			for (Option o : group) {
				if (o.cost() <= budget && o.gain() > 0) {
					ok.add(o);
					top = Math.max(top, o.gain());
				}
			}
			if (ok.isEmpty()) continue;
			if (ok.get(0).slots() > 0) {
				fresh.add(ok);
				capNew += top;
			} else {
				upgrades.add(ok);
				capUp += top;
			}
		}
		// cheapest lines first keeps the tables' reachable corner small for longer
		fresh.sort(Comparator.comparingDouble(g -> g.get(0).cost()));

		Line up = oneD(upgrades, capUp);
		// best[g] = cheapest way to gain at least g from upgrades
		double[] upAtLeast = up.cost.clone();
		for (int g = capUp - 1; g >= 0; g--) upAtLeast[g] = Math.min(upAtLeast[g], upAtLeast[g + 1]);

		// how many new lines could ever fit: slots at hand plus what the whole budget buys at Jacobus
		int maxS = free + dupes;
		if (jac >= 0) {
			int more = 0;
			while (jac + more < JACOBUS_MAX && jacobusCost(jac, more + 1) <= budget) more++;
			maxS += more * SLOTS_PER_UPGRADE;
		}
		maxS = Math.min(maxS, fresh.size());

		// no solution gains more from new lines than ignoring slots allows
		Line loose = oneD(fresh, capNew);
		int capG = 0;
		for (int g = capNew; g > 0; g--) {
			if (loose.cost[g] <= budget) {
				capG = g;
				break;
			}
		}

		double[][] t = twoD(fresh, 0, fresh.size(), maxS, capG, cancelled);
		if (cancelled.getAsBoolean()) return null;

		int bestS = 0, bestNew = 0, bestUp = 0;
		double bestCost = 0;
		for (int s = 0; s <= maxS; s++) {
			double slots = slotCost(s, free, dupes, jac);
			if (slots > budget) break;
			for (int g = 0; g <= capG; g++) {
				double c = t[s][g] + slots;
				if (c > budget) continue;
				double left = budget - c;
				int u = 0, hi = capUp;     // upAtLeast rises with u: largest u within what's left
				while (u < hi) {
					int mid = (u + hi + 1) >>> 1;
					if (upAtLeast[mid] <= left) u = mid;
					else hi = mid - 1;
				}
				double total = c + upAtLeast[u];
				int gain = g + u, bestGain = bestNew + bestUp;
				if (gain > bestGain || (gain == bestGain && total < bestCost - 1e-6)) {
					bestS = s;
					bestNew = g;
					bestUp = u;
					bestCost = total;
				}
			}
		}
		t = null;

		List<Option> picks = new ArrayList<>();
		recover(fresh, 0, fresh.size(), bestS, bestNew, picks, cancelled);
		if (cancelled.getAsBoolean()) return null;
		// upAtLeast[u] may be reached by a larger exact gain: take the cheapest one at or above u
		int exact = bestUp;
		for (int g = bestUp; g <= capUp; g++) if (up.cost[g] < up.cost[exact]) exact = g;
		if (bestUp > 0 || exact > 0) picks.addAll(trace(upgrades, up, exact));

		double items = 0;
		int gain = 0;
		for (Option o : picks) {
			items += o.cost();
			gain += o.gain();
		}
		picks.sort(Comparator.comparingDouble(Option::perMp));
		int overFree = Math.max(0, bestS - free);
		int remove = Math.min(overFree, dupes);
		int upgradesBought = (overFree - remove + SLOTS_PER_UPGRADE - 1) / SLOTS_PER_UPGRADE;
		double slots = jacobusCost(jac, upgradesBought);
		// slots are what stops the plan if one more line was affordable but had nowhere to go
		boolean limited = false;
		if (bestS >= maxS && (jac < 0 || jac + upgradesBought >= JACOBUS_MAX)) {
			double left = budget - items - slots;
			java.util.Set<String> taken = new java.util.HashSet<>();
			for (Option o : picks) taken.add(o.family());
			for (List<Option> g : fresh) {
				if (g.get(0).cost() <= left && !taken.contains(g.get(0).family())) {
					limited = true;
					break;
				}
			}
		}
		return new Plan(List.copyOf(picks), budget, items, slots, items + slots, gain, bestS, remove, upgradesBought, limited);
	}
}
