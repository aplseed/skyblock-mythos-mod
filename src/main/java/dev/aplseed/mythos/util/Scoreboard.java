package dev.aplseed.mythos.util;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;

/** The sidebar scoreboard as plain text lines, top to bottom (Hypixel shows location and purse there). */
public final class Scoreboard {
	private Scoreboard() {}

	public static List<String> lines() {
		List<String> out = new ArrayList<>();
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return out;
		net.minecraft.world.scores.Scoreboard sb = mc.level.getScoreboard();
		Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (obj == null) return out;
		List<PlayerScoreEntry> entries = new ArrayList<>(sb.listPlayerScores(obj));
		entries.sort((a, b) -> Integer.compare(b.value(), a.value()));
		for (PlayerScoreEntry e : entries) {
			PlayerTeam team = sb.getPlayersTeam(e.owner());
			String line = Items.plain(PlayerTeam.formatNameForTeam(team, e.ownerName()));
			if (!line.isEmpty()) out.add(line);
		}
		return out;
	}
}
