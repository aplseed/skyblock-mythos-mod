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
