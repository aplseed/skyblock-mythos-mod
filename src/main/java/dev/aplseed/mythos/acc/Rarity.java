package dev.aplseed.mythos.acc;

import java.util.Locale;

/**
 * Accessory rarities with the Accessory Power (formerly Magical Power) each one gives.
 * Since SkyBlock 0.27.3, SPECIAL accessories give none, and recombobulators can be reclaimed
 * from SPECIAL and VERY SPECIAL ones - so neither tier counts.
 */
public enum Rarity {
	// the game's rarity colours, lifted where needed to read on the dark panels
	COMMON(3, 0xFFECE8F5, "Common"),
	UNCOMMON(5, 0xFF6EE76E, "Uncommon"),
	RARE(8, 0xFF7D8CFF, "Rare"),
	EPIC(12, 0xFFC873F0, "Epic"),
	LEGENDARY(16, 0xFFFFB72E, "Legendary"),
	MYTHIC(22, 0xFFFF7DF2, "Mythic"),
	DIVINE(28, 0xFF6FE3F5, "Divine"),
	SPECIAL(0, 0xFFFF6B7A, "Special"),
	VERY_SPECIAL(0, 0xFFFF6B7A, "Very Special");

	public final int mp;
	public final int color;
	public final String label;

	Rarity(int mp, int color, String label) {
		this.mp = mp;
		this.color = color;
		this.label = label;
	}

	/** The rarity a Recombobulator 3000 turns this into, or null if it can't be raised. */
	public Rarity up() {
		return switch (this) {
			case COMMON -> UNCOMMON;
			case UNCOMMON -> RARE;
			case RARE -> EPIC;
			case EPIC -> LEGENDARY;
			case LEGENDARY -> MYTHIC;
			case MYTHIC -> DIVINE;
			case SPECIAL -> VERY_SPECIAL;
			default -> null;
		};
	}

	/** "LEGENDARY", "very special", "SUPREME" (the API's old name for Divine) -> rarity, or null. */
	public static Rarity parse(String s) {
		if (s == null) return null;
		String k = s.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
		if (k.equals("SUPREME")) return DIVINE;
		for (Rarity r : values()) if (r.name().equals(k)) return r;
		return null;
	}

	/**
	 * Rarity from an item's lore: the last line that starts with a rarity word
	 * ("RARE ACCESSORY", "a LEGENDARY DUNGEON ACCESSORY a" when recombobulated).
	 */
	public static Rarity fromLore(java.util.List<String> lore) {
		for (int i = lore.size() - 1; i >= 0; i--) {
			String line = lore.get(i).trim();
			if (line.startsWith("a ") && line.endsWith(" a")) line = line.substring(2, line.length() - 2).trim();
			if (line.startsWith("VERY SPECIAL")) return VERY_SPECIAL;
			int sp = line.indexOf(' ');
			Rarity r = parse(sp < 0 ? line : line.substring(0, sp));
			if (r != null && line.equals(line.toUpperCase(Locale.ROOT))) return r;
		}
		return null;
	}
}
