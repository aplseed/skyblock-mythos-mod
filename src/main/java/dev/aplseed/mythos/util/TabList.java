package dev.aplseed.mythos.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/** The tab list as plain text lines (Hypixel puts its widgets there). */
public final class TabList {
	private TabList() {}

	public static List<String> lines() {
		List<String> out = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return out;
		for (PlayerInfo p : mc.getConnection().getListedOnlinePlayers()) {
			Component name = p.getTabListDisplayName();
			if (name != null) {
				String s = Items.plain(name);
				if (!s.isEmpty()) out.add(s);
			}
		}
		return out;
	}
}
