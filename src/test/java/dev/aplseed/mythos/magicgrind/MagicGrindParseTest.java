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
		MagicGrindParse.slayers(List.of("Zombie Slayer: LVL 9", "Wolf Slayer LVL VII", "Requires Spider Slayer 7", "Spider Slayer 7"), out);
		assertEquals(Map.of("zombie", 9, "wolf", 7), out);
	}

	@Test
	void readsBestiaryTiers() {
		assertEquals(Map.entry("Ghost", 20), MagicGrindParse.bestiaryTier("Ghost XX"));
		assertEquals(Map.entry("Lord Jawbus", 4), MagicGrindParse.bestiaryTier("Lord Jawbus IV"));
		assertNull(MagicGrindParse.bestiaryTier("Dwarven Mines"));
		assertNull(MagicGrindParse.bestiaryTier("Bestiary Milestone X"));
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
