package dev.aplseed.mythos.acc;

import dev.aplseed.mythos.Config;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.acc.Analysis.Option;
import dev.aplseed.mythos.util.Fmt;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;

/** The calculator's state: the current analysis of the scanned bag and the last budget plan. */
public final class Accessories {
	private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "mythos-accessory-planner");
		t.setDaemon(true);
		return t;
	});

	private static Analysis analysis;
	private static long analysisKey = Long.MIN_VALUE;
	private static Planner.Plan plan;
	private static long planKey = Long.MIN_VALUE;
	private static double planBudget = -1;
	private static volatile int generation;
	private static boolean planning;
	private static long runningKey;
	private static double runningBudget;

	private Accessories() {}

	public static Analysis.Player player() {
		Config c = Mythos.config();
		return new Analysis.Player(c.unlocks, Bag.freeSlots(), c.jacobusUpgrades, c.accCraft, c.collections);
	}

	/** Everything the analysis depends on, folded into one number. */
	private static long key() {
		Config c = Mythos.config();
		return Objects.hash(AccData.version(), Bag.version(), c.unlocks, c.jacobusUpgrades, c.accFreeSlots, c.accCraft, c.collections);
	}

	/** The scanned bag analysed with current prices, or null until the accessory list has loaded. */
	public static Analysis analysis() {
		AccData.refresh(false);
		if (!AccData.ready()) return null;
		long k = key();
		if (analysis == null || k != analysisKey) {
			analysis = Analysis.of(AccData.catalog(), AccData.prices(), Bag.owned(), player());
			analysisKey = k;
		}
		return analysis;
	}

	public static boolean planning() {
		return planning;
	}

	/**
	 * The best way to spend `budget`, computed in the background: null while the first one
	 * is running. Asking again after the bag, prices or settings changed starts a new one.
	 */
	public static Planner.Plan plan(double budget) {
		Analysis a = analysis();
		if (a == null) return null;
		long k = analysisKey;
		boolean current = plan != null && planKey == k && planBudget == budget;
		// already have it, or already working on exactly this
		if (current || (planning && runningKey == k && runningBudget == budget)) return current ? plan : null;
		planning = true;
		runningKey = k;
		runningBudget = budget;
		int gen = ++generation;     // a plan still running for other input stops at its next check
		Analysis.Player p = player();
		WORKER.execute(() -> {
			Planner.Plan result = null;
			try {
				result = Planner.plan(a, p, budget, () -> gen != generation);
			} catch (Throwable t) {
				Mythos.LOG.error("Accessory plan failed", t);
			}
			Planner.Plan done = result;
			Minecraft.getInstance().execute(() -> {
				if (gen != generation) return;
				planning = false;
				plan = done != null ? done : new Planner.Plan(List.of(), budget, 0, 0, 0, 0, 0, 0, 0, false);
				planKey = k;
				planBudget = budget;
			});
		});
		return null;
	}

	/** A step the player crafts rather than buys. */
	public static boolean crafted(Option o) {
		return (o.kind() == Analysis.Kind.NEW || o.kind() == Analysis.Kind.UPGRADE) && o.how().startsWith("craft");
	}

	/** One step of a plan: "Buy Feather Artifact (Epic)" / "Recombobulate Wolf Ring -> Epic". */
	public static String describe(Option o) {
		String rec = o.recomb() ? " + recombobulate" : "";
		return switch (o.kind()) {
			case RECOMB -> "Recombobulate " + o.name() + " -> " + o.result().label;
			case INVENTORY -> "Put " + o.name() + " in the bag";
			case UPGRADE -> (o.how().startsWith("craft") ? "Craft " : "Buy ") + o.name() + rec + " (replaces " + o.replaces() + ")";
			case NEW -> (o.how().startsWith("craft") ? "Craft " : "Buy ") + o.name() + rec;
		};
	}

	/** A plan as chat lines. */
	public static List<String> lines(Planner.Plan plan, Analysis a, int max) {
		List<String> out = new ArrayList<>();
		if (plan.picks().isEmpty()) {
			out.add("Nothing to buy for " + Fmt.coins(plan.budget()) + (a.groups.isEmpty() ? " - no priced accessory would add power" : ""));
			return out;
		}
		out.add("§6" + Fmt.coins(plan.budget()) + " budget§r: §d+" + plan.gain() + " Accessory Power§r (" + a.magicPower + " -> "
				+ (a.magicPower + plan.gain()) + ") for " + Fmt.coins(plan.total()) + ", " + Fmt.coins(plan.total() / plan.gain()) + " per AP");
		if (plan.dupesToRemove() > 0) out.add("§e Take " + plan.dupesToRemove() + " duplicate" + (plan.dupesToRemove() == 1 ? "" : "s") + " out of the bag first");
		if (plan.jacobusUpgrades() > 0) {
			out.add("§e Buy " + plan.jacobusUpgrades() + " bag upgrade" + (plan.jacobusUpgrades() == 1 ? "" : "s") + " from Jacobus: "
					+ Fmt.coins(plan.slotCost()) + " (+" + plan.jacobusUpgrades() * Planner.SLOTS_PER_UPGRADE + " slots)");
		}
		int n = 0;
		for (Option o : plan.picks()) {
			if (n++ >= max) {
				out.add("§7 +" + (plan.picks().size() - max) + " more - /myth acc shows them all");
				break;
			}
			out.add(" " + n + ". " + describe(o) + " §7- " + (o.cost() == 0 ? "free" : Fmt.coins(o.cost())) + ", §d+" + o.gain() + " AP§7 (" + o.how() + ")");
		}
		if (plan.limitedBySlots()) out.add("§e The bag is full - more slots would let this budget do more");
		return out;
	}
}
