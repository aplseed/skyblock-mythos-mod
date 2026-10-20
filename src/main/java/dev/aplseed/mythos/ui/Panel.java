package dev.aplseed.mythos.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A text box drawn beside a container menu: left of it if there's room, else right. */
public final class Panel {
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int DARK = 0xFF777777;
	public static final int GOLD = 0xFFFFAA00;
	public static final int GREEN = 0xFF55FF55;
	public static final int RED = 0xFFFF5555;
	public static final int AQUA = 0xFF55FFFF;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int PURPLE = 0xFFFF55FF;

	private static final int PAD = 4;
	private static final int MAX_W = 220;

	private record Line(String text, int color, boolean header) {}

	private final List<Line> lines = new ArrayList<>();

	public Panel header(String text) {
		if (!lines.isEmpty()) lines.add(new Line("", 0, false));
		lines.add(new Line(text, GOLD, true));
		return this;
	}

	public Panel line(String text, int color) {
		lines.add(new Line(text, color, false));
		return this;
	}

	public Panel line(String text) {
		return line(text, WHITE);
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	public void draw(GuiGraphicsExtractor g, Font font, int menuLeft, int menuTop, int menuWidth, int screenWidth) {
		if (lines.isEmpty()) return;
		int w = 0;
		for (Line l : lines) w = Math.max(w, font.width(l.text));
		w = Math.min(w, MAX_W) + PAD * 2;
		int x = menuLeft - w - 4;
		if (x < 2) x = Math.min(menuLeft + menuWidth + 4, screenWidth - w - 2);
		int h = lines.size() * (font.lineHeight + 1) + PAD * 2 - 1;
		int y = Math.max(2, menuTop);
		g.fill(x, y, x + w, y + h, 0xE0100C18);
		g.outline(x, y, w, h, 0xFF5A3E8C);
		int ty = y + PAD;
		for (Line l : lines) {
			String t = l.text;
			if (font.width(t) > MAX_W) t = font.plainSubstrByWidth(t, MAX_W - font.width("...")) + "...";
			g.text(font, t, x + PAD, ty, l.color, true);
			ty += font.lineHeight + 1;
		}
	}
}
