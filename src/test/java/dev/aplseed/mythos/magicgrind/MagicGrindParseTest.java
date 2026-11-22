package dev.aplseed.mythos.magicgrind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

class MagicGrindParseTest {
	@Test
	void readsStatTotals() {
		assertEquals(250.0, MagicGrindParse.stat(List.of("✯ Magic Find 250"), "magic_find").getAsDouble());
		assertEquals(123.5, MagicGrindParse.stat(List.of(" Magic Find: ✯123.5"), "magic_find").getAsDouble());
		assertEquals(1250.0, MagicGrindParse.stat(List.of("Magic Find 1,250"), "magic_find").getAsDouble());
		assertEquals(40.0, MagicGrindParse.stat(List.of("♣ Pet Luck 40"), "pet_luck").getAsDouble());
	}

	@Test
	void ignoresBonuses() {
		// gear lore and tooltips show signed bonuses, which aren't the total
		assertTrue(MagicGrindParse.stat(List.of("Magic Find: +5"), "magic_find").isEmpty());
		assertTrue(MagicGrindParse.stat(List.of("+3 ✯ Magic Find"), "magic_find").isEmpty());
		assertTrue(MagicGrindParse.stat(List.of("Grants +5 Magic Find on Sharks"), "magic_find").isEmpty());
		assertTrue(MagicGrindParse.stat(List.of("Increases Magic Find by 10"), "magic_find").isEmpty());
	}

	@Test
	void firstStatedLineWins() {
		assertEquals(77.0, MagicGrindParse.stat(List.of("Magic Find: +5", "✯ Magic Find 77", "✯ Magic Find 80"), "magic_find").getAsDouble());
	}

	@Test
	void readsSlayerLevels() {
		Map<String, Integer> out = new TreeMap<>();
		MagicGrindParse.slayers(List.of("Zombie Slayer: LVL 3", "Wolf Slayer: LVL VII", "Requires Spider Slayer 7", "Spider Slayer 7"), out);
		assertEquals(Map.of("zombie", 3, "wolf", 7), out);
	}

	@Test
	void slayerRewardsMenuIsNotALevel() {
		// "Zombie Slayer LVL Rewards" lists every level as an item name, reached or not (real dump)
		Map<String, Integer> out = new TreeMap<>();
		MagicGrindParse.slayers(List.of("Zombie Slayer LVL 1", "+1 Magic Find on Revenant Horror", "Rewards claimed!",
				"Zombie Slayer LVL 4", "Level not reached!", "Zombie Slayer LVL 7", "Zombie Slayer LVL", "Current LVL: 3"), out);
		assertTrue(out.isEmpty());
	}

	@Test
	void readsStatsBreakdownDump() {
		// "Your Stats Breakdown" (real dump): icons are stripped, lines keep a leading space
		List<String> lines = List.of("Miscellaneous Stats", " Speed 400", " Magic Find 45", " Pet Luck 72", " Tracking 5.5",
				"Flat: +45", "Stat Cap: 500 Magic Find", "You are 5.5% more likely to find ", "Elusive mobs.");
		assertEquals(45.0, MagicGrindParse.stat(lines, "magic_find").getAsDouble());
		assertEquals(72.0, MagicGrindParse.stat(lines, "pet_luck").getAsDouble());
		assertEquals(5.5, MagicGrindParse.stat(lines, "tracking").getAsDouble());
	}

	@Test
	void itemMagicFind() {
		List<String> midas = List.of("Gear Score: 1257 (3643)", "Damage: +570.36 (+30) (+90) (+1,852.71)", "Magic Find: +20 (+30.8)");
		assertEquals(20.0, MagicGrindParse.itemMagicFind(midas).getAsDouble());
		assertTrue(MagicGrindParse.isWeapon(midas));
		assertTrue(MagicGrindParse.itemMagicFind(List.of("Damage: +285", "Strength: +265")).isEmpty());
		assertFalse(MagicGrindParse.isWeapon(List.of("Use on a pet to consume an Ultimate")));
	}

	@Test
	void readsBestiaryTiers() {
		assertEquals(Map.entry("Ghost", 20), MagicGrindParse.bestiaryTier("Ghost XX"));
		assertEquals(Map.entry("Lord Jawbus", 4), MagicGrindParse.bestiaryTier("Lord Jawbus IV"));
		assertEquals(Map.entry("Goblin Raiders", 15), MagicGrindParse.bestiaryTier("Goblin Raiders XV"));
		assertNull(MagicGrindParse.bestiaryTier("Littlefoot"));   // tier 0: no numeral yet
		assertNull(MagicGrindParse.bestiaryTier("Bestiary Milestone CXXIII"));
		assertNull(MagicGrindParse.bestiaryTier("Dwarven Mines"));
		assertNull(MagicGrindParse.bestiaryTier("Bestiary Milestone X"));
	}

	@Test
	void rareDropLine() {
		// real chat line
		var d = MagicGrindParse.rareDrop("RARE DROP! Sorrow (+105 Magic Find)");
		assertEquals("Sorrow", d.item());
		assertEquals(105.0, d.magicFind());
		var v = MagicGrindParse.rareDrop("VERY RARE DROP! 2x Plasma (+88.5 ✯ Magic Find)");
		assertEquals("Plasma", v.item());
		assertEquals(88.5, v.magicFind());
		var w = MagicGrindParse.rareDrop("RARE DROP! (Hamster Wheel) (+150% \u272F Magic Find)");
		assertEquals("Hamster Wheel", w.item());
		assertEquals(150.0, w.magicFind());
		assertEquals("Spirit Rune I", MagicGrindParse.rareDrop("VERY RARE DROP!  (\u25C6 Spirit Rune I) (+212 \u272F Magic Find)").item());
		assertEquals("Hamster Wheel", MagicGrindParse.rareDrop("RARE DROP! (2x Hamster Wheel) (+150 Magic Find)").item());
		assertNull(MagicGrindParse.rareDrop("RARE DROP! Sorrow"));
		assertNull(MagicGrindParse.rareDrop("[MVP+] someone: RARE DROP! Sorrow (+999 Magic Find)"));
	}

	@Test
	void slayerScoreboard() {
		var q = MagicGrindParse.slayerQuest(List.of("Purse: 1,234", "", "Slayer Quest", "Sven Packmaster IV", "(1,234/2,000) Combat XP"));
		assertEquals("Sven Packmaster", q.boss());
		assertEquals(4, q.tier());
		assertEquals(false, q.bossUp());
		assertEquals(true, MagicGrindParse.slayerQuest(List.of("Slayer Quest", "Sven Packmaster III", "Slay the boss!")).bossUp());
		assertNull(MagicGrindParse.slayerQuest(List.of("Purse: 5", "\u23E3 Ruins")));
		// emoji padding inside the line
		assertEquals(4, MagicGrindParse.slayerQuest(List.of("Slayer Quest", "Sven Packmaster I\uD83C\uDF82V")).tier());
		assertEquals("Ruins", MagicGrindParse.location(List.of(" \u23E3 Ru\uD83D\uDC7Dins")));
		assertEquals(4, MagicGrindParse.bossTag("Sven Packmaster IV").tier());
		assertNull(MagicGrindParse.bossTag("Sven Follower"));
		// zombie slayer: the scoreboard names the tier, the T5 boss's tag doesn't
		var rev = MagicGrindParse.slayerQuest(List.of("Slayer Quest", "Revenant Horror IV", "Slay the boss!"));
		assertEquals("Revenant Horror", rev.boss());
		assertEquals(4, rev.tier());
		assertEquals("zombie", MagicGrindParse.slayerOf(rev.boss()));
		assertEquals(5, MagicGrindParse.bossTag("Atoned Horror").tier());
		assertEquals("Revenant Horror", MagicGrindParse.bossTag("Atoned Horror").boss());
		assertNull(MagicGrindParse.bossTag("Atoned Revenant"));
		assertNull(MagicGrindParse.bossTag("Atoned Champion"));
		assertNull(MagicGrindParse.bossTag("Revenant Sycophant"));
		assertEquals("wolf", MagicGrindParse.slayerOf(q.boss()));
		assertEquals("Ruins", MagicGrindParse.location(List.of("11/22/26", " \u23E3 Ruins", "Purse: 5")));
		assertEquals("Howling Cave", MagicGrindParse.location(List.of("\u23E3 Howling Cave")));
		assertEquals("", MagicGrindParse.location(List.of("Purse: 5")));
		// real scoreboard from the Crypts (world dump): the icon is a private-use glyph
		var crypts = List.of("Autumn 29th", "9:40am", " \uE067 Crypts", "Purse: 245,342,336 (+559)", "Bits: 103,814",
				"Slayer Quest", "Revenant Horror V", "Boss slain!", "Spooky Festival 51:52", "www.hypixel.net");
		assertEquals("Crypts", MagicGrindParse.location(crypts));
		assertEquals(5, MagicGrindParse.slayerQuest(crypts).tier());
		assertEquals("Revenant Horror", MagicGrindParse.slayerQuest(crypts).boss());
		assertEquals("Crypt Ghoul", MagicGrindParse.mobName("[Lv33] \uE084 Crypt Ghoul 1,600/2,000\u2764"));
		assertNull(MagicGrindParse.mobName("[Lv83] Ghoul"));    // your pet's tag has no health
		var drop = MagicGrindParse.rareDrop("VERY RARE DROP! (Undead Catalyst) (+125\uE01A Magic Find)");
		assertEquals("Undead Catalyst", drop.item());
		assertEquals(125, drop.magicFind(), 1e-9);
	}

	@Test
	void mobNameTags() {
		assertEquals("Old Wolf", MagicGrindParse.mobName("[Lv67] Old Wolf 15k/15k\u2764"));
		assertEquals("Pack Enforcer", MagicGrindParse.mobName("\u2620 Pack Enforcer 30,000/30,000\u2764"));
		assertEquals("Sven Packmaster", MagicGrindParse.mobName("\u2620 Sven Packmaster 2M\u2764"));
		assertEquals("Crypt Ghoul", MagicGrindParse.mobName("[Lv30] Crypt Ghoul 2,000/2,000\u2764"));
		assertEquals("Atoned Horror", MagicGrindParse.mobName("\u2620 Atoned Horror 10M\u2764"));
		assertEquals("Soul of the Alpha", MagicGrindParse.mobName("[Lv90] Soul of the Alpha 31k/31k\u2764"));
		assertEquals("Wolf", MagicGrindParse.mobName("[Lv15] Wolf 250/250\u2764"));
		assertNull(MagicGrindParse.mobName("Spawned by: aplseed"));
		assertNull(MagicGrindParse.mobName("03:59"));
		assertNull(MagicGrindParse.mobName("[Lv100] Wolf"));
	}

	@Test
	void flatBonusesMenu() {
		// "Magic Find > Flat Bonuses" (real dump)
		var potion = MagicGrindParse.flatBonus("Category: Potion Effect", List.of("Grouped", "", "Value: +11", "",
				"+10 Magic Find Effect", "+1 Celestial Mason Jar", "", "Stats from your active potion", "effects.", "", "Click to dig even deeper!"));
		assertEquals("Potion Effect", potion.name());
		assertEquals(11.0, potion.value());
		assertEquals(List.of(new MagicGrindParse.FlatPart("Magic Find Effect", 10), new MagicGrindParse.FlatPart("Celestial Mason Jar", 1)), potion.parts());
		var shop = MagicGrindParse.flatBonus("Magic Find", List.of("Community Shop", "", "Value: +5", "", "Stats from community shop upgrades."));
		assertEquals("Community Shop", shop.name());
		var attr = MagicGrindParse.flatBonus("Magic Find", List.of("Attribute", "", "Value: +5", "", "Stats from your equipped Attributes."));
		assertEquals("Attribute", attr.kind());
		var bag = MagicGrindParse.flatBonus("Category: Accessory Bag", List.of("Grouped", "", "Value: +1", "", "+0.5 Pulse Ring", "+0.5 Anguish Talisman"));
		assertEquals(2, bag.parts().size());
		assertEquals(0.5, bag.parts().get(1).value());
		assertNull(MagicGrindParse.flatBonus("Go Back", List.of("To Stats ➜ Magic Find")));
		assertNull(MagicGrindParse.flatBonus("Group By Category", List.of("Groups all modifiers from the same", "category into one.", "", "Enabled: ON!")));
	}

	@Test
	void trackerLines() {
		assertEquals(1234567.0, MagicGrindParse.purse(List.of("Early Spring 20th", "⏣ The Mist", "Purse: 1,234,567")).getAsDouble());
		assertEquals(12345.0, MagicGrindParse.purse(List.of("Piggy: 12,345 (+50)")).getAsDouble());
		assertTrue(MagicGrindParse.purse(List.of("Bits: 1,000")).isEmpty());
		var sacks = MagicGrindParse.sackItems("Added items:\n  +40 Rough Jade Gemstone (Gemstone Sack)\n  +1,280 Rough Amber Gemstone (Gemstone Sack)\n\nThis message can be disabled in the settings.");
		assertEquals(40, sacks.get("Rough Jade Gemstone"));
		assertEquals(1280, sacks.get("Rough Amber Gemstone"));
		// real names carry an icon glyph
		assertEquals(40, MagicGrindParse.sackItems("+40 \uE053 Rough Jade Gemstone (Gemstone Sack)").get("Rough Jade Gemstone"));
		// real Bestiary lore
		assertEquals(2664, MagicGrindParse.bestiaryKills(List.of("Mob Types:  Arcane,  Subterranean", "", "It is hard to find that which you", "cannot see.", "", "Kills: 2,664", "Deaths: 9")));
	}

	@Test
	void forgeTimesAndMiningIslands() {
		assertEquals(41 * 60 + 43, MagicGrindParse.seconds("41m 43s"));   // real "Time Remaining:" format
		assertEquals(86400 + 2 * 3600 + 5, MagicGrindParse.seconds("1d 2h 5s"));
		assertEquals(-1, MagicGrindParse.seconds("Ready!"));
		assertTrue(MagicGrindParse.onMiningIsland(List.of("Server: mini12A", "Area: Dwarven Mines")));
		assertTrue(MagicGrindParse.onMiningIsland(List.of("Area: Glacite Mineshafts")));
		assertFalse(MagicGrindParse.onMiningIsland(List.of("Area: Hub")));
	}

	@Test
	void romanNumerals() {
		assertEquals(4, MagicGrindParse.roman("IV"));
		assertEquals(25, MagicGrindParse.roman("XXV"));
		assertEquals(-1, MagicGrindParse.roman("XA"));
	}

	@Test
	void riftArea() {
		assertTrue(MagicGrindParse.inRift(List.of("Area: The Rift")));
		assertFalse(MagicGrindParse.inRift(List.of("Area: Hub", "Rift Time: 4m")));
	}
}
