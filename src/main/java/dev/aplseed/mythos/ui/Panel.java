package dev.aplseed.mythos.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A text box drawn beside a container menu: left of it if there's room, else right.
 * Lines added with {@link #link} are clickable: they highlight under the mouse, show
 * their hint as a tooltip, and run their action on a left click.
 */
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

	private record Line(String text, int color, boolean header, String hint, Runnable action) {}

	/** Where a clickable line was last drawn. */
	public record Hit(int x, int y, int w, int h, String hint, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Line> lines = new ArrayList<>();
	private static List<String> lastDrawn = List.of();
	private static List<Hit> hits = List.of();

	/** Text of the most recently drawn panel (read by the client game test). */
	public static List<String> lastDrawn() {
		return lastDrawn;
	}

	/** Clickable lines of the most recently drawn panel. */
	public static List<Hit> hits() {
		return hits;
	}

	/** Runs the clickable line at (mx, my) of the last drawn panel; true if there was one. */
	public static boolean click(double mx, double my) {
		for (Hit h : hits) {
			if (h.contains(mx, my)) {
				h.action.run();
				return true;
			}
		}
		return false;
	}

	/** Forget the clickable areas (the menu closed). */
	public static void clear() {
		hits = List.of();
	}

	public Panel header(String text) {
		if (!lines.isEmpty()) lines.add(new Line("", 0, false, null, null));
		lines.add(new Line(text, GOLD, true, null, null));
		return this;
	}

	public Panel line(String text, int color) {
		lines.add(new Line(text, color, false, null, null));
		return this;
	}

	public Panel line(String text) {
		return line(text, WHITE);
	}

	/** A clickable line: `hint` is its tooltip, `action` runs on a left click. */
	public Panel link(String text, int color, String hint, Runnable action) {
		lines.add(new Line(text, color, false, hint, action));
		return this;
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	public void draw(GuiGraphicsExtractor g, Font font, int menuLeft, int menuTop, int menuWidth, int screenWidth,
			int mouseX, int mouseY) {
		lastDrawn = lines.stream().map(Line::text).toList();
		List<Hit> newHits = new ArrayList<>();
		hits = newHits;
		if (lines.isEmpty()) return;
		List<Line> shown = new ArrayList<>();
		for (Line l : lines) wrap(font, l, shown);
		int w = 0;
		for (Line l : shown) w = Math.max(w, font.width(l.text));
		w += PAD * 2;
		int x = menuLeft - w - 4;
		if (x < 2) x = Math.min(menuLeft + menuWidth + 4, screenWidth - w - 2);
		int lh = font.lineHeight + 1;
		int h = shown.size() * lh + PAD * 2 - 1;
		int y = Math.max(2, menuTop);
		g.fill(x, y, x + w, y + h, 0xE0100C18);
		g.outline(x, y, w, h, 0xFF5A3E8C);
		int ty = y + PAD;
		Hit hovered = null;
		for (Line l : shown) {
			if (l.action != null) {
				Hit hit = new Hit(x + 1, ty - 1, w - 2, lh, l.hint, l.action);
				newHits.add(hit);
				if (hit.contains(mouseX, mouseY)) {
					hovered = hit;
					g.fill(hit.x, hit.y, hit.x + hit.w, hit.y + hit.h, 0x40FFFFFF);
				}
			}
			g.text(font, l.text, x + PAD, ty, l.color, true);
			ty += lh;
		}
		if (hovered != null && hovered.hint != null) g.setTooltipForNextFrame(font, Component.literal(hovered.hint), mouseX, mouseY);
	}

	/** Splits a line wider than MAX_W at spaces; continuation lines are indented and keep the line's action. */
	private static void wrap(Font font, Line l, List<Line> out) {
		String rest = l.text;
		while (font.width(rest) > MAX_W) {
			String head = font.plainSubstrByWidth(rest, MAX_W);
			int cut = head.lastIndexOf(' ');
			if (cut <= 2) cut = head.length();     // no break after the indent: hard cut
			out.add(new Line(rest.substring(0, cut), l.color, l.header, l.hint, l.action));
			rest = "  " + rest.substring(cut).trim();
		}
		out.add(new Line(rest, l.color, l.header, l.hint, l.action));
	}
}
