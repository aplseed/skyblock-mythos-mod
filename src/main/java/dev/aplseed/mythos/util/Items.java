package dev.aplseed.mythos.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;

/** Plain-text views of SkyBlock items (names, lore, ids). */
public final class Items {
	private static final Pattern FORMAT = Pattern.compile("§.");
	private static final Pattern NUMBER = Pattern.compile("([0-9][0-9,]*)");

	private Items() {}

	public static String plain(Component c) {
		return c == null ? "" : strip(c.getString());
	}

	public static String strip(String s) {
		return FORMAT.matcher(s).replaceAll("").trim();
	}

	public static String name(ItemStack s) {
		return s.isEmpty() ? "" : plain(s.getHoverName());
	}

	public static List<String> lore(ItemStack s) {
		List<String> out = new ArrayList<>();
		ItemLore lore = s.get(DataComponents.LORE);
		if (lore != null) for (Component c : lore.lines()) out.add(plain(c));
		return out;
	}

	/** The SkyBlock item id ("ENCHANTED_DIAMOND"), or "" for menu-only items. */
	public static String skyblockId(ItemStack s) {
		CustomData d = s.get(DataComponents.CUSTOM_DATA);
		if (d == null) return "";
		return d.copyTag().getStringOr("id", "");
	}

	/** First integer after `label` in any lore line ("Stored: 1,234" -> 1234), or -1. */
	public static long loreNumber(List<String> lore, Pattern label) {
		for (String line : lore) {
			Matcher m = label.matcher(line);
			if (m.find()) {
				Matcher n = NUMBER.matcher(line.substring(m.end()));
				if (n.find()) return parse(n.group(1));
			}
		}
		return -1;
	}

	public static long parse(String digits) {
		try {
			return Long.parseLong(digits.replace(",", ""));
		} catch (NumberFormatException e) {
			return -1;
		}
	}
}
