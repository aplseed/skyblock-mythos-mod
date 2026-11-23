package dev.aplseed.mythos.menu;

import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.util.Items;
import dev.aplseed.mythos.util.Scoreboard;
import dev.aplseed.mythos.util.TabList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/**
 * F8 with no menu open: the scoreboard, tab list, recent chat and name tags around you, written to
 * config/mythos/dumps/ and copied to the clipboard. Symbols are also written as U+XXXX so hidden characters show.
 */
public final class WorldDump {
	private WorldDump() {}

	private static final Deque<String> CHAT = new ArrayDeque<>();

	/** Remembers recent chat lines for the dump. */
	public static void chat(String line) {
		synchronized (CHAT) {
			CHAT.addLast(line);
			while (CHAT.size() > 60) CHAT.removeFirst();
		}
	}

	public static void dump() {
		Minecraft mc = Minecraft.getInstance();
		StringBuilder sb = new StringBuilder("title: (no menu) world dump\n--- scoreboard\n");
		for (String l : Scoreboard.lines()) sb.append(l).append("    | ").append(codes(l)).append('\n');
		sb.append("--- slayer tracker\n").append(dev.aplseed.mythos.magicgrind.SlayerTracker.debug()).append('\n');
		sb.append("--- tab list\n");
		for (String l : TabList.lines()) sb.append(l).append('\n');
		sb.append("--- chat (last 60)\n");
		List<String> chat;
		synchronized (CHAT) {
			chat = new ArrayList<>(CHAT);
		}
		for (String l : chat) sb.append(l).append("    | ").append(codes(l)).append('\n');
		sb.append("--- name tags within 12 blocks\n");
		if (mc.level != null && mc.player != null) {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e == mc.player || e.distanceTo(mc.player) > 12) continue;
				String type = EntityType.getKey(e.getType()).getPath();
				String name = e.hasCustomName() ? Items.plain(e.getCustomName()) : "";
				if (name.isEmpty() && "armor_stand".equals(type)) continue;
				sb.append(String.format("%s #%d d=%.1f y=%.1f  %s", type, e.getId(), e.distanceTo(mc.player), e.getY() - mc.player.getY(), name));
				if (!name.isEmpty()) sb.append("    | ").append(codes(name));
				sb.append('\n');
			}
		}
		String text = sb.toString();
		mc.keyboardHandler.setClipboard(text);
		try {
			Path dir = Mythos.configDir().resolve("dumps");
			Files.createDirectories(dir);
			Path file = dir.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + "-world.txt");
			Files.writeString(file, text);
			Mythos.toast("World dumped", "Copied to clipboard + dumps/" + file.getFileName());
		} catch (Exception ex) {
			Mythos.toast("World dumped", "Copied to clipboard (file write failed)");
		}
	}

	/** Non-ASCII characters as U+XXXX, so emoji or private-use glyphs hidden in a line show up. */
	static String codes(String s) {
		StringBuilder out = new StringBuilder();
		s.codePoints().forEach(cp -> {
			if (cp >= 0x20 && cp < 0x7F) out.appendCodePoint(cp);
			else out.append("<U+").append(Integer.toHexString(cp).toUpperCase()).append('>');
		});
		return out.toString();
	}
}
