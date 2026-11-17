package dev.aplseed.mythos.acc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aplseed.mythos.acc.Analysis.Kind;
import dev.aplseed.mythos.acc.Analysis.Option;
import dev.aplseed.mythos.acc.Analysis.Owned;
import dev.aplseed.mythos.acc.Analysis.Player;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The accessory calculator on a small hand-made item list, in Hypixel's and NEU's formats. */
class AccessoryCalculatorTest {
	static final JsonObject ITEMS = JsonParser.parseString("""
			{"items": [
			  {"id": "FEATHER_TALISMAN", "name": "Feather Talisman", "category": "ACCESSORY"},
			  {"id": "FEATHER_RING", "name": "Feather Ring", "category": "ACCESSORY", "tier": "UNCOMMON"},
			  {"id": "FEATHER_ARTIFACT", "name": "Feather Artifact", "category": "ACCESSORY", "tier": "RARE"},
			  {"id": "WOLF_TALISMAN", "name": "Wolf Talisman", "category": "ACCESSORY", "tier": "UNCOMMON"},
			  {"id": "WOLF_RING", "name": "Wolf Ring", "category": "ACCESSORY", "tier": "RARE"},
			  {"id": "SPEED_TALISMAN", "name": "Speed Talisman", "category": "ACCESSORY", "tier": "COMMON"},
			  {"id": "RED_CLAW_ARTIFACT", "name": "Red Claw Artifact", "category": "ACCESSORY", "tier": "EPIC",
			   "requirements": [{"type": "SLAYER", "slayer_boss_type": "wolf", "level": 5}]},
			  {"id": "TITANIUM_RELIC", "name": "Titanium Relic", "category": "ACCESSORY", "tier": "LEGENDARY",
			   "requirements": [{"type": "HEART_OF_THE_MOUNTAIN", "tier": 5}]},
			  {"id": "HEGEMONY_ARTIFACT", "name": "Hegemony Artifact", "category": "ACCESSORY", "tier": "LEGENDARY"},
			  {"id": "PARTY_HAT_CRAB", "name": "Crab Hat of Celebration", "category": "ACCESSORY", "tier": "SPECIAL"},
			  {"id": "PARTY_HAT_SLOTH", "name": "Sloth Hat of Celebration", "category": "ACCESSORY", "tier": "SPECIAL"},
			  {"id": "PANDORAS_BOX", "name": "Pandora's Box", "category": "ACCESSORY", "tier": "COMMON", "can_recombobulate": false},
			  {"id": "RIFT_ONLY", "name": "Rift Thing", "category": "ACCESSORY", "tier": "RARE", "origin": "RIFT"},
			  {"id": "OLD_BOOT", "name": "Old Boot", "category": "ACCESSORY", "tier": "RARE"},
			  {"id": "CAMPFIRE_TALISMAN_1", "name": "Campfire Badge", "category": "ACCESSORY", "soulbound": "SOLO"},
			  {"id": "DIAMOND_SWORD", "name": "Diamond Sword", "category": "SWORD"}
			]}""").getAsJsonObject();
	static final JsonObject MISC = JsonParser.parseString("""
			{"talisman_upgrades": {
			   "FEATHER_TALISMAN": ["FEATHER_RING", "FEATHER_ARTIFACT"], "FEATHER_RING": ["FEATHER_ARTIFACT"],
			   "WOLF_TALISMAN": ["WOLF_RING"]},
			 "ignored_talisman": ["OLD_BOOT"]}""").getAsJsonObject();
	static final Catalog CAT = Catalog.build(ITEMS, MISC);
	static final Prices PRICES = new Prices(
			Map.of("FEATHER_ARTIFACT", 400_000.0, "FEATHER_RING", 50_000.0, "SPEED_TALISMAN", 5_000.0, "RED_CLAW_ARTIFACT", 2_000_000.0,
					"TITANIUM_RELIC", 30_000_000.0, "HEGEMONY_ARTIFACT", 300_000_000.0, "PARTY_HAT_SLOTH", 900_000.0),
			Map.of(Prices.RECOMB, 9_000_000.0, "ENCHANTED_FEATHER", 500.0, "GOLDEN_TOOTH", 100_000.0),
			Map.of("FEATHER_RING", new Prices.Recipe(Map.of("ENCHANTED_FEATHER", 28, "FEATHER_TALISMAN", 1), "Feather VII", null, 0),
					"FEATHER_ARTIFACT", new Prices.Recipe(Map.of("ENCHANTED_FEATHER", 128, "FEATHER_RING", 1), "Feather IX", null, 0),
					// 4 x 100k teeth and a wolf ring nobody sells: only craftable, and only with the slayer level
					"RED_CLAW_ARTIFACT", new Prices.Recipe(Map.of("GOLDEN_TOOTH", 4), "Wolf Slayer 5", "slayer_wolf", 5)));

	static final Map<String, Integer> COLLECTIONS = Map.of("feather", 9);

	static Owned bag(String id, Rarity r, boolean recomb, int slot) {
		return new Owned(id, CAT.name(id), r, recomb, 1, slot, true);
	}

	static Player player(int free, int jacobus, Map<String, Integer> unlocks) {
		return new Player(unlocks, free, jacobus, true, COLLECTIONS);
	}

	static Option option(Analysis a, String id, boolean recomb) {
		for (List<Option> g : a.groups) for (Option o : g) if (o.id().equals(id) && o.recomb() == recomb) return o;
		return null;
	}

	@Test
	void catalogGroupsLinesAndReadsRequirements() {
		assertEquals(15, CAT.byId.size());
		assertEquals(CAT.familyOf("FEATHER_TALISMAN"), CAT.familyOf("FEATHER_ARTIFACT"));
		assertEquals(List.of("FEATHER_TALISMAN", "FEATHER_RING", "FEATHER_ARTIFACT"),
				CAT.families.get(CAT.familyOf("FEATHER_RING")).stream().map(Catalog.Acc::id).toList());
		assertEquals(CAT.familyOf("PARTY_HAT_CRAB"), CAT.familyOf("PARTY_HAT_SLOTH"));
		// ids the item list doesn't know still land in the right line
		assertEquals("PARTY_HAT", CAT.familyOf("BALLOON_HAT_2099"));
		assertEquals(Rarity.COMMON, CAT.byId.get("FEATHER_TALISMAN").tier());
		assertEquals(new Catalog.Req("slayer_wolf", 5), CAT.byId.get("RED_CLAW_ARTIFACT").reqs().get(0));
		assertEquals(Map.of("slayer_wolf", 5, "hotm", 5), CAT.reqKeys);
		assertFalse(CAT.byId.get("RIFT_ONLY").obtainable());
		assertFalse(CAT.byId.get("OLD_BOOT").obtainable());
		assertFalse(CAT.byId.get("PANDORAS_BOX").canRecomb());
	}

	@Test
	void rarityComesFromTheLastLoreLine() {
		assertEquals(Rarity.RARE, Rarity.fromLore(List.of("Grants +5 Speed.", "", "RARE ACCESSORY")));
		assertEquals(Rarity.MYTHIC, Rarity.fromLore(List.of("stats", "a MYTHIC DUNGEON ACCESSORY a")));
		assertEquals(Rarity.VERY_SPECIAL, Rarity.fromLore(List.of("VERY SPECIAL HATCESSORY")));
		assertEquals(Rarity.SPECIAL, Rarity.fromLore(List.of("SPECIAL HATCESSORY")));
		assertNull(Rarity.fromLore(List.of("Rare drop from wolves", "Click to view!")));
		assertEquals(Rarity.EPIC, Rarity.LEGENDARY.up() == Rarity.MYTHIC ? Rarity.RARE.up() : null);
		assertNull(Rarity.VERY_SPECIAL.up());
		// SkyBlock 0.27.3: DIVINE is worth 28, SPECIAL and VERY SPECIAL nothing
		assertEquals(28, Rarity.DIVINE.mp);
		assertEquals(6, Rarity.MYTHIC.up().mp - Rarity.MYTHIC.mp);
		assertEquals(0, Rarity.SPECIAL.mp);
		assertEquals(0, Rarity.VERY_SPECIAL.mp);
	}

	@Test
	void countsTheBestOfEachLineAndFindsDupes() {
		List<Owned> owned = List.of(
				bag("FEATHER_TALISMAN", Rarity.COMMON, false, 0), bag("FEATHER_TALISMAN", Rarity.COMMON, false, 1),
				bag("WOLF_TALISMAN", Rarity.UNCOMMON, false, 2), bag("WOLF_RING", Rarity.EPIC, true, 3),
				bag("HEGEMONY_ARTIFACT", Rarity.LEGENDARY, false, 4),
				bag("PARTY_HAT_CRAB", Rarity.SPECIAL, false, 5), bag("PARTY_HAT_SLOTH", Rarity.SPECIAL, false, 6),
				bag("SOMETHING_NEW", Rarity.RARE, false, 7));
		Analysis a = Analysis.of(CAT, PRICES, owned, player(0, -1, Map.of()));
		// 3 feather + 12 recombobulated wolf ring + 2x16 hegemony + 0 hat (SPECIAL gives none since 0.27.3) + 8 unknown
		assertEquals(3 + 12 + 32 + 0 + 8, a.magicPower);
		assertEquals(5, a.counted.size());
		assertEquals(3, a.dupes.size());
		assertTrue(a.dupes.stream().anyMatch(d -> d.item().id().equals("FEATHER_TALISMAN") && d.sameItem()));
		assertTrue(a.dupes.stream().anyMatch(d -> d.item().id().equals("WOLF_TALISMAN") && d.kept().id().equals("WOLF_RING") && !d.sameItem()));
		assertTrue(a.dupes.stream().anyMatch(d -> d.item().id().startsWith("PARTY_HAT")));
		assertEquals(3, a.bagDupes(), "a scan from before 0.27.3 can hold a line twice");

		// 0.27.3: one slot per line, so the spares are in the inventory and take no slot
		List<Owned> spares = List.of(bag("FEATHER_TALISMAN", Rarity.COMMON, false, 0), bag("WOLF_RING", Rarity.RARE, false, 1),
				new Owned("FEATHER_TALISMAN", "Feather Talisman", Rarity.COMMON, false, 0, 9, false),
				new Owned("WOLF_TALISMAN", "Wolf Talisman", Rarity.UNCOMMON, false, 0, 10, false),
				new Owned("SPEED_TALISMAN", "Speed Talisman", Rarity.COMMON, false, 0, 11, false));
		Analysis now = Analysis.of(CAT, PRICES, spares, player(0, -1, Map.of()));
		assertEquals(2, now.dupes.size());
		assertEquals(0, now.bagDupes());
		assertTrue(now.dupes.stream().allMatch(d -> !d.item().inBag()));
		assertTrue(now.dupes.stream().anyMatch(d -> d.item().id().equals("WOLF_TALISMAN") && d.kept().id().equals("WOLF_RING")));
	}

	@Test
	void optionsKnowUpgradesRecombsAndLocks() {
		List<Owned> owned = List.of(bag("FEATHER_TALISMAN", Rarity.COMMON, false, 0), bag("HEGEMONY_ARTIFACT", Rarity.LEGENDARY, false, 1),
				bag("WOLF_RING", Rarity.EPIC, true, 2), bag("PANDORAS_BOX", Rarity.COMMON, false, 3),
				new Owned("SPEED_TALISMAN", "Speed Talisman", Rarity.COMMON, false, 0, 9, false));
		Analysis a = Analysis.of(CAT, PRICES, owned, player(1, -1, Map.of("slayer_wolf", 4)));

		// crafting from the owned talisman beats the 50k / 400k auctions: 28 and 28+128 enchanted feathers
		Option ring = option(a, "FEATHER_RING", false), artifact = option(a, "FEATHER_ARTIFACT", false);
		assertEquals(14_000, ring.cost(), 1e-6);
		assertEquals(2, ring.gain());
		assertEquals(0, ring.slots());
		assertEquals(78_000, artifact.cost(), 1e-6);
		assertEquals(5, artifact.gain());
		assertEquals("craft from your Feather Talisman", artifact.how());
		assertEquals(Kind.UPGRADE, artifact.kind());
		assertEquals(9, option(a, "FEATHER_ARTIFACT", true).gain());     // epic 12 - 3

		// hegemony counts twice, so a recombobulator adds 2 x (22 - 16)
		Option heg = option(a, "HEGEMONY_ARTIFACT", true);
		assertEquals(Kind.RECOMB, heg.kind());
		assertEquals(12, heg.gain());
		assertEquals(9_000_000, heg.cost(), 1e-6);

		assertNull(option(a, "WOLF_RING", true), "already recombobulated");
		assertNull(option(a, "PANDORAS_BOX", true), "can't be recombobulated");
		assertNull(option(a, "RED_CLAW_ARTIFACT", false), "wolf slayer 4 < 5");
		assertNull(option(a, "TITANIUM_RELIC", false), "HotM level never given");
		assertNull(option(a, "RIFT_ONLY", false));
		assertNull(option(a, "OLD_BOOT", false));
		assertEquals(Set.of("hotm"), a.unknownUnlocks);
		assertEquals(2, a.locked);

		// the talisman in the inventory is free and only needs a slot
		Option speed = option(a, "SPEED_TALISMAN", false);
		assertEquals(Kind.INVENTORY, speed.kind());
		assertEquals(0, speed.cost(), 0);
		assertEquals(1, speed.slots());

		Analysis unlocked = Analysis.of(CAT, PRICES, owned, player(1, -1, Map.of("slayer_wolf", 5, "hotm", 7)));
		// 400k of teeth beats the 2M auction once the slayer level allows the recipe
		assertEquals(400_000, option(unlocked, "RED_CLAW_ARTIFACT", false).cost(), 1e-6);
		assertEquals("craft", option(unlocked, "RED_CLAW_ARTIFACT", false).how());
		// with crafting off, everything is priced at the auction
		Analysis buying = Analysis.of(CAT, PRICES, owned, new Player(Map.of("slayer_wolf", 5), 1, -1, false, COLLECTIONS));
		assertEquals(2_000_000, option(buying, "RED_CLAW_ARTIFACT", false).cost(), 1e-6);
		assertEquals(400_000, option(buying, "FEATHER_ARTIFACT", false).cost(), 1e-6);
		assertEquals("lowest BIN", option(buying, "FEATHER_ARTIFACT", false).how());
		assertEquals(16, option(unlocked, "TITANIUM_RELIC", false).gain());
		assertEquals(0, unlocked.locked);

		// an accessory is bought at a BIN or not at all: a bazaar-style price doesn't make it buyable
		Prices noBin = new Prices(Map.of(), Map.of(Prices.RECOMB, 9_000_000.0, "SPEED_TALISMAN", 5_000.0, "PARTY_HAT_SLOTH", 1.0), Map.of());
		Analysis unsold = Analysis.of(CAT, noBin, List.of(), player(5, -1, Map.of()));
		assertTrue(unsold.groups.isEmpty(), "nothing has a BIN, so nothing can be bought");
		assertTrue(unsold.missing.stream().allMatch(m -> Double.isNaN(m.price())));

		// the campfire badge has no price anywhere: listed as missing, never planned
		assertTrue(a.missing.stream().anyMatch(m -> m.acc().id().equals("CAMPFIRE_TALISMAN_1") && Double.isNaN(m.price())));
		assertNull(option(a, "CAMPFIRE_TALISMAN_1", false));
	}

	@Test
	void plansRespectBudgetSlotsAndJacobus() {
		List<Owned> owned = List.of(bag("FEATHER_TALISMAN", Rarity.COMMON, false, 0), bag("FEATHER_TALISMAN", Rarity.COMMON, false, 1));
		Map<String, Integer> unlocks = Map.of("slayer_wolf", 9, "hotm", 10);

		// 100k: the craft upgrade (78k, +5) and the 5k talisman (+3), which needs the dupe's slot
		Player full = player(0, -1, unlocks);
		Planner.Plan small = Planner.plan(Analysis.of(CAT, PRICES, owned, full), full, 100_000, () -> false);
		assertEquals(8, small.gain());
		assertEquals(83_000, small.total(), 1e-6);
		assertEquals(1, small.dupesToRemove());
		assertEquals(0, small.jacobusUpgrades());

		// 500k with a full bag and no Jacobus count: one new line only (the dupe's slot), so the
		// 400k red claw craft (+12) wins it and the feather line takes the rest
		Planner.Plan mid = Planner.plan(Analysis.of(CAT, PRICES, owned, full), full, 500_000, () -> false);
		assertEquals(17, mid.gain());
		assertEquals(1, mid.slotsUsed());
		assertTrue(mid.limitedBySlots());

		// same coins, Jacobus known (3 bought, the 4th costs 1M): slots are still too dear
		Player jac = player(0, 3, unlocks);
		assertEquals(17, Planner.plan(Analysis.of(CAT, PRICES, owned, jac), jac, 500_000, () -> false).gain());

		// 40M: one 1M upgrade (+2 slots) is worth it for the 30M relic and the rest
		Planner.Plan big = Planner.plan(Analysis.of(CAT, PRICES, owned, jac), jac, 40_000_000, () -> false);
		assertEquals(1, big.jacobusUpgrades());
		assertEquals(1_000_000, big.slotCost(), 1e-6);     // the 4th upgrade
		assertTrue(big.total() <= 40_000_000);
		assertTrue(big.picks().stream().anyMatch(o -> o.id().equals("TITANIUM_RELIC")));
		Set<String> lines = new HashSet<>();
		for (Option o : big.picks()) assertTrue(lines.add(o.family()), "one option per line");

		assertEquals(0, Planner.plan(Analysis.of(CAT, PRICES, owned, full), full, 0, () -> false).gain());
		assertNull(Planner.plan(Analysis.of(CAT, PRICES, owned, full), full, 1e9, () -> true), "cancelled");
	}

	@Test
	void craftsNeedTheCollection() {
		List<Owned> owned = List.of(bag("FEATHER_TALISMAN", Rarity.COMMON, false, 0));
		// Feather VIII: the ring's recipe (VII) is open, the artifact's (IX) isn't, so the
		// artifact is bought at its 400k BIN
		Analysis eight = Analysis.of(CAT, PRICES, owned, new Player(Map.of(), 1, -1, true, Map.of("feather", 8)));
		assertEquals("craft from your Feather Talisman", option(eight, "FEATHER_RING", false).how());
		assertEquals(14_000, option(eight, "FEATHER_RING", false).cost(), 1e-6);
		assertEquals("lowest BIN", option(eight, "FEATHER_ARTIFACT", false).how());
		assertEquals(400_000, option(eight, "FEATHER_ARTIFACT", false).cost(), 1e-6);
		assertEquals(Set.of("Feather IX (yours: VIII)"), eight.lowCollections);
		assertTrue(eight.unknownCollections.isEmpty());

		// never seen: no craft at all, and the analysis says which collections to show
		Analysis unseen = Analysis.of(CAT, PRICES, owned, new Player(Map.of(), 1, -1, true, Map.of()));
		assertEquals(50_000, option(unseen, "FEATHER_RING", false).cost(), 1e-6);
		assertEquals("lowest BIN", option(unseen, "FEATHER_RING", false).how());
		assertEquals(Set.of("Feather VII", "Feather IX"), unseen.unknownCollections);

		// the game and NEU don't always name a collection alike
		assertEquals(9, Prices.tier(Map.of("redstone", 9), "Redstone Dust"));
		assertEquals(9, Prices.tier(Map.of("redstone dust", 9), "Redstone"));
		assertNull(Prices.tier(Map.of("red sand", 9), "Redstone"));
		assertEquals(new Prices.Collection("Sugar Cane", 6), new Prices.Recipe(Map.of("X", 1), "Sugar Cane VI", null, 0).collection());
		assertNull(new Prices.Recipe(Map.of("X", 1), "Wolf Slayer 5", "slayer_wolf", 5).collection());
		assertNull(new Prices.Recipe(Map.of("X", 1), "Museum Milestone 10", null, 0).collection());
		assertEquals(14, Prices.roman("XIV"));
		assertEquals(0, Prices.roman("Dust"));
		assertEquals("XIV", Prices.roman(14));
	}

	/** An auction as Hypixel lists it; item_bytes is base64 of gzipped NBT holding the SkyBlock id. */
	static String auction(String id, String category, boolean bin, double price, double bid, long end) throws Exception {
		java.io.ByteArrayOutputStream nbt = new java.io.ByteArrayOutputStream();
		// a short tag also named "id" (the item's numeric id) comes first, as in the real data
		nbt.write(new byte[] {10, 0, 0, 2, 0, 2, 'i', 'd', 1, 5, 8, 0, 2, 'i', 'd', 0, (byte) id.length()});
		nbt.write(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		nbt.write(0);
		java.io.ByteArrayOutputStream zipped = new java.io.ByteArrayOutputStream();
		try (java.util.zip.GZIPOutputStream gz = new java.util.zip.GZIPOutputStream(zipped)) {
			gz.write(nbt.toByteArray());
		}
		return "{\"uuid\":\"x\",\"coop\":[\"a\"],\"item_lore\":\"lore\",\"category\":\"" + category + "\",\"starting_bid\":" + (long) price
				+ ",\"highest_bid_amount\":" + (long) bid + ",\"end\":" + end + ",\"claimed\":false,\"bin\":" + bin
				+ ",\"bids\":[],\"item_bytes\":\"" + java.util.Base64.getEncoder().encodeToString(zipped.toByteArray()) + "\"}";
	}

	@Test
	void onlyOpenBinAuctionsCount() throws Exception {
		long now = 1_000_000;
		String page = "{\"success\":true,\"page\":0,\"totalPages\":7,\"auctions\":[" + String.join(",",
				auction("FEATHER_RING", "accessories", true, 60_000, 0, now + 5_000),
				auction("FEATHER_RING", "accessories", true, 45_000, 0, now + 5_000),
				auction("RUNEBOOK", "accessories", false, 10_000, 0, now + 5_000),       // a bidding auction
				auction("WOLF_RING", "accessories", true, 70_000, 70_000, now + 5_000),  // bought, not claimed yet
				auction("SPEED_TALISMAN", "accessories", true, 5_000, 0, now - 1),       // ran out
				auction("DIAMOND_SWORD", "weapon", true, 1, 0, now + 5_000)) + "]}";
		Map<String, Double> lowest = new java.util.HashMap<>();
		assertEquals(7, Auctions.page(new java.io.StringReader(page), now, lowest));
		assertEquals(Map.of("FEATHER_RING", 45_000.0), lowest);

		// a price list may still carry the Runebook; without an open BIN it can't be bought
		Prices stale = new Prices(Map.of("PANDORAS_BOX", 10_000.0, "SPEED_TALISMAN", 5_000.0), Map.of(), Map.of(), Map.of("SPEED_TALISMAN", 5_000.0));
		Analysis a = Analysis.of(CAT, stale, List.of(), player(5, -1, Map.of()));
		assertNull(option(a, "PANDORAS_BOX", false));
		assertNotNull(option(a, "SPEED_TALISMAN", false));
		assertTrue(a.missing.stream().anyMatch(m -> m.acc().id().equals("PANDORAS_BOX") && Double.isNaN(m.price())));
	}

	@Test
	void jacobusPrices() {
		// SkyBlock 0.27.3's prices: the 5th upgrade (the Jacobus Register) at 3.35M in total
		assertEquals(100_000, Planner.jacobusCost(0, 1), 0);
		assertEquals(3_350_000, Planner.jacobusCost(0, 5), 0);
		assertEquals(18_350_000, Planner.jacobusCost(0, 10), 0);
		assertEquals(9_500_000, Planner.jacobusPrice(21), 0);
		assertEquals(10_000_000, Planner.jacobusPrice(22), 0);
		assertEquals(95_350_000, Planner.jacobusCost(0, 21), 0);
		assertEquals(875_350_000, Planner.jacobusCost(0, 99), 0);
		assertEquals(Double.POSITIVE_INFINITY, Planner.jacobusCost(98, 2), 0);
		assertEquals(Double.POSITIVE_INFINITY, Planner.jacobusCost(-1, 1), 0);
	}

	/** The planner against trying every combination, on random small problems. */
	@Test
	void planMatchesBruteForce() {
		Random r = new Random(7);
		for (int trial = 0; trial < 400; trial++) {
			List<List<Option>> groups = new ArrayList<>();
			int n = 1 + r.nextInt(7);
			for (int gi = 0; gi < n; gi++) {
				List<Option> g = new ArrayList<>();
				int slots = r.nextInt(3) == 0 ? 0 : 1, options = 1 + r.nextInt(3), gain = 0;
				double cost = 0;
				for (int k = 0; k < options; k++) {
					cost += 1 + r.nextInt(50);
					gain += 1 + r.nextInt(9);
					g.add(new Option("F" + gi, Kind.NEW, "I" + gi + "_" + k, "n", Rarity.RARE, false, cost, gain, slots, "x", null));
				}
				groups.add(g);
			}
			int free = r.nextInt(4), jacobus = r.nextBoolean() ? -1 : 97 + r.nextInt(3);
			double budget = r.nextInt(200) + (jacobus >= 0 && r.nextBoolean() ? 40_000_000 : 0);
			Player p = player(free, jacobus, Map.of());
			Analysis a = Analysis.of(CAT, Prices.empty(), List.of(), p);
			a.groups.clear();
			a.groups.addAll(groups);
			Planner.Plan plan = Planner.plan(a, p, budget, () -> false);
			assertEquals(brute(groups, 0, 0, 0, budget, free, jacobus), plan.gain(), "trial " + trial);
			assertTrue(plan.total() <= budget + 1e-6, "trial " + trial + " overspent");
			int gain = 0;
			for (Option o : plan.picks()) gain += o.gain();
			assertEquals(plan.gain(), gain);
		}
	}

	static int brute(List<List<Option>> groups, int i, int slots, double cost, double budget, int free, int jacobus) {
		if (i == groups.size()) {
			int extra = slots - free;
			double slotCost = extra <= 0 ? 0 : Planner.jacobusCost(jacobus, (extra + 1) / 2);
			return cost + slotCost <= budget ? 0 : Integer.MIN_VALUE / 2;
		}
		int best = brute(groups, i + 1, slots, cost, budget, free, jacobus);
		for (Option o : groups.get(i)) best = Math.max(best, o.gain() + brute(groups, i + 1, slots + o.slots(), cost + o.cost(), budget, free, jacobus));
		return best;
	}
}
