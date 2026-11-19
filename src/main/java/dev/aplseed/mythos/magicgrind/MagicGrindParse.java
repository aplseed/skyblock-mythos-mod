package dev.aplseed.mythos.magicgrind;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the stats MagicGrind needs out of plain text (menu lore, item names, tab-list lines).
 * No game classes here, so it is unit tested in src/test.
 *
 * <p>A stat's own value is written without a sign ("✯ Magic Find 250", "Magic Find: ✯250"); bonuses on
 * gear and in tooltips carry one ("Magic Find: +5", "+3 ✯ Magic Find"), so a sign means "not the total".
 */
public final class MagicGrindParse {
	private MagicGrindParse() {}

	private static final String NUM = "([0-9][0-9,]*(?:\\.[0-9]+)?)";
	private static final Map<String, Pattern> STAT = Map.of(
			"magic_find", stat("Magic Find"),
			"pet_luck", stat("Pet Luck"),
			"tracking", stat("Tracking"));
	// only the colon form "Zombie Slayer: LVL 3" (Slayer menu); the rewards menu names its items
	// "Zombie Slayer LVL 1" ... "LVL 7" for every level, reached or not
	private static final Pattern SLAYER = Pattern.compile(
			"\\b(Zombie|Spider|Wolf|Enderman|Blaze|Vampire) Slayer:\\s*(?:LVL|Lvl|Level)\\s*([0-9]+|[IVX]+)\\b");
	// an item's own Magic Find line: "Magic Find: +20 (+30.8)" - the bracket is the dungeon value
	private static final Pattern ITEM_MF = Pattern.compile("^Magic Find:\\s*\\+" + NUM);
	private static final Pattern TIERED = Pattern.compile("^(.+?) ([IVXLC]+)$");
	// "RARE DROP! Sorrow (+105 Magic Find)", "VERY RARE DROP! 2x Foo (+88 ✯ Magic Find)"
	private static final Pattern RARE_DROP = Pattern.compile(
			"^[A-Z][A-Z ]*DROP!\\s+(?:([0-9]+)x\\s+)?(.+?)\\s*\\(\\+" + NUM + "%?\\s*[^A-Za-z0-9\\s]?\\s*Magic Find\\)");

	private static final Pattern VALUE = Pattern.compile("^Value:\\s*\\+" + NUM);
	private static final Pattern PART = Pattern.compile("^\\+" + NUM + "\\s+(.+)$");

	/** One entry of the "Magic Find > Flat Bonuses" menu: what gives it, its group, its value and grouped parts. */
	public record FlatPart(String name, double value) {}

	public record FlatEntry(String name, String kind, double value, List<FlatPart> parts) {}

	/**
	 * "Booster Cookie" / lore "Booster Cookie, , Value: +10" -> (Booster Cookie, Booster Cookie, 10, []);
	 * "Category: Accessory Bag" / "Grouped, , Value: +1, , +0.5 Pulse Ring, +0.5 Anguish Talisman" -> parts.
	 * Items without a "Value:" line (navigation) give null. A generic name ("Magic Find") takes its kind instead.
	 */
	public static FlatEntry flatBonus(String name, List<String> lore) {
		Double value = null;
		List<FlatPart> parts = new java.util.ArrayList<>();
		for (String raw : lore) {
			String line = raw.trim();
			Matcher v = VALUE.matcher(line);
			if (v.find()) {
				value = Double.parseDouble(v.group(1).replace(",", ""));
				continue;
			}
			Matcher p = PART.matcher(line);
			if (value != null && p.matches()) parts.add(new FlatPart(p.group(2).trim(), Double.parseDouble(p.group(1).replace(",", ""))));
		}
		if (value == null) return null;
		String n = name.trim().replaceFirst("^Category:\\s*", "");
		String kind = lore.isEmpty() ? "" : lore.get(0).trim();
		if (n.equals("Magic Find") && !kind.isEmpty()) n = kind;
		return new FlatEntry(n, kind, value, parts);
	}

	private static final Pattern PURSE = Pattern.compile("^(?:Purse|Piggy):\\s*" + NUM);
	private static final Pattern SACK_LINE = Pattern.compile("^\\+([0-9][0-9,]*)\\s+(.+?)\\s*\\(([^)]*Sack)\\)\\s*$");
	private static final Pattern KILLS = Pattern.compile("^Kills:\\s*" + NUM);

	/** Coins in the purse from the scoreboard ("Purse: 1,234,567", "Piggy: 12,345 (+50)"). */
	public static OptionalDouble purse(List<String> scoreboard) {
		for (String l : scoreboard) {
			Matcher m = PURSE.matcher(l.trim());
			if (m.find()) return OptionalDouble.of(Double.parseDouble(m.group(1).replace(",", "")));
		}
		return OptionalDouble.empty();
	}

	/** A "[Sacks]" message's hover text: "+40 Rough Jade Gemstone (Gemstone Sack)" lines -> name -> count. */
	public static Map<String, Integer> sackItems(String hover) {
		Map<String, Integer> out = new java.util.LinkedHashMap<>();
		for (String raw : hover.split("\\n")) {
			Matcher m = SACK_LINE.matcher(raw.trim());
			// Hypixel puts a private-use icon glyph before gemstone names ("\uE053 Rough Jade Gemstone")
			if (m.matches()) out.merge(m.group(2).replaceAll("[\\uE000-\\uF8FF]", "").trim(), Integer.parseInt(m.group(1).replace(",", "")), Integer::sum);
		}
		return out;
	}

	/** "Kills: 2,664" in a Bestiary entry's lore. */
	public static long bestiaryKills(List<String> lore) {
		for (String l : lore) {
			Matcher m = KILLS.matcher(l.trim());
			if (m.find()) return (long) Double.parseDouble(m.group(1).replace(",", ""));
		}
		return -1;
	}

	/** A rare-drop chat line's item and the Magic Find the game applied, or null. */
	public record RareDrop(String item, double magicFind) {}

	public static RareDrop rareDrop(String chat) {
		Matcher m = RARE_DROP.matcher(chat.trim());
		if (!m.find()) return null;
		try {
			return new RareDrop(m.group(2).trim(), Double.parseDouble(m.group(3).replace(",", "")));
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Pattern stat(String name) {
		// optional leading icon/space, the stat name, optional colon, optional icon, then an unsigned number ending the line
		return Pattern.compile("^[^A-Za-z0-9+\\-]*" + Pattern.quote(name) + ":?\\s*[^A-Za-z0-9+\\-\\s]?\\s*" + NUM + "\\s*$");
	}

	/** The total of `stat` ("magic_find", "pet_luck") from the first line that states it, if any. */
	public static OptionalDouble stat(List<String> lines, String stat) {
		Pattern p = STAT.get(stat);
		for (String line : lines) {
			Matcher m = p.matcher(line.trim());
			if (m.find()) {
				try {
					return OptionalDouble.of(Double.parseDouble(m.group(1).replace(",", "")));
				} catch (NumberFormatException ignored) {
					// keep looking
				}
			}
		}
		return OptionalDouble.empty();
	}

	/** "Zombie Slayer: LVL 9" -> ("zombie", 9) into `out`. Requirement lines ("Requires Zombie Slayer 7") are skipped. */
	public static void slayers(List<String> lines, Map<String, Integer> out) {
		for (String line : lines) {
			if (line.contains("Requires") || line.contains("Required")) continue;
			Matcher m = SLAYER.matcher(line);
			while (m.find()) {
				int lvl = level(m.group(2));
				if (lvl >= 0 && lvl <= 12) out.put(m.group(1).toLowerCase(Locale.ROOT), lvl);
			}
		}
	}

	/** The Magic Find an item itself gives (its "Magic Find: +20" lore line), if any. */
	public static OptionalDouble itemMagicFind(List<String> lore) {
		for (String line : lore) {
			Matcher m = ITEM_MF.matcher(line.trim());
			if (m.find()) {
				try {
					return OptionalDouble.of(Double.parseDouble(m.group(1).replace(",", "")));
				} catch (NumberFormatException ignored) {
					return OptionalDouble.empty();
				}
			}
		}
		return OptionalDouble.empty();
	}

	/** A weapon or tool you hit with: has a "Damage:" stat line. */
	public static boolean isWeapon(List<String> lore) {
		for (String line : lore) if (line.trim().startsWith("Damage:")) return true;
		return false;
	}

	/** A Bestiary mob item named "Ghost XX" -> ("Ghost", 20); null for anything else. */
	public static Map.Entry<String, Integer> bestiaryTier(String itemName) {
		Matcher m = TIERED.matcher(itemName.trim());
		if (!m.matches()) return null;
		String family = m.group(1).trim();
		if (family.isEmpty() || family.contains("Milestone") || family.contains("Bestiary") || family.contains("Page")) return null;
		int tier = roman(m.group(2));
		return tier > 0 ? Map.entry(family, tier) : null;
	}

	/** Tab-list "Area: The Rift" style check. */
	public static boolean inRift(List<String> tabLines) {
		for (String l : tabLines) {
			String t = l.trim();
			if ((t.startsWith("Area:") || t.startsWith("Area ")) && t.contains("Rift")) return true;
		}
		return false;
	}

	static int level(String s) {
		if (s.chars().allMatch(Character::isDigit)) return Integer.parseInt(s);
		return roman(s);
	}

	static int roman(String s) {
		int total = 0, prev = 0;
		for (int i = s.length() - 1; i >= 0; i--) {
			int v = switch (s.charAt(i)) {
				case 'I' -> 1;
				case 'V' -> 5;
				case 'X' -> 10;
				case 'L' -> 50;
				case 'C' -> 100;
				default -> -1;
			};
			if (v < 0) return -1;
			total += v < prev ? -v : v;
			prev = Math.max(prev, v);
		}
		return total;
	}
}
