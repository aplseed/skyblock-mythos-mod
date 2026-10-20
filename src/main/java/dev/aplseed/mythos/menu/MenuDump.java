package dev.aplseed.mythos.menu;

import dev.aplseed.mythos.Mythos;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.minecraft.client.Minecraft;

/**
 * Writes the open menu's title and every item's name, id and lore to
 * config/mythos/dumps/ and copies it to the clipboard - chat can't be opened
 * over a menu, so this is how menu formats get reported.
 */
public final class MenuDump {
	private MenuDump() {}

	public static void dump(Menu menu) {
		StringBuilder sb = new StringBuilder();
		sb.append("title: ").append(menu.title).append('\n');
		sb.append("rows: ").append(menu.rows).append('\n');
		for (Menu.Entry e : menu.container) append(sb, e);
		sb.append("--- player inventory\n");
		for (Menu.Entry e : menu.inventory) append(sb, e);
		String text = sb.toString();

		Minecraft mc = Minecraft.getInstance();
		mc.keyboardHandler.setClipboard(text);
		try {
			Path dir = Mythos.configDir().resolve("dumps");
			Files.createDirectories(dir);
			String safe = menu.title.replaceAll("[^A-Za-z0-9]+", "_");
			String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
			Path file = dir.resolve(stamp + "-" + safe + ".txt");
			Files.writeString(file, text);
			Mythos.toast("Menu dumped", "Copied to clipboard + dumps/" + file.getFileName());
		} catch (Exception ex) {
			Mythos.toast("Menu dumped", "Copied to clipboard (file write failed)");
		}
	}

	private static void append(StringBuilder sb, Menu.Entry e) {
		sb.append("[").append(e.slot().index).append("] ").append(e.name());
		if (e.count() > 1) sb.append(" x").append(e.count());
		if (!e.id().isEmpty()) sb.append("  {").append(e.id()).append("}");
		sb.append('\n');
		for (String l : e.lore()) sb.append("    ").append(l).append('\n');
	}
}
