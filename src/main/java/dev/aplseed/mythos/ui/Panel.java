package dev.aplseed.mythos.ui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * The card drawn beside a container menu: left of it if there's room, else right.
 * Built from sections ({@link #header}), plain lines, label/value rows ({@link #kv}),
 * progress bars and clickable rows ({@link #link}: highlighted under the mouse, with a
 * tooltip, run on a left click). A panel taller than the screen scrolls with the wheel.
 */
public final class Panel {
	public static final int WHITE = Theme.TEXT;
	public static final int GRAY = Theme.MUTED;
	public static final int DARK = Theme.FAINT;
	public static final int GOLD = Theme.GOLD;
	public static final int GREEN = Theme.GREEN;
	public static final int RED = Theme.RED;
	public static final int AQUA = Theme.AQUA;
	public static final int YELLOW = Theme.YELLOW;
	public static final int PURPLE = Theme.PINK;

	private static final int PAD = 6;
	private static final int MAX_W = 216;
	private static final int MIN_W = 120;
	private static final int ROW = 10;
	private static final int GAP = 12;     // between a label and its value

	private enum Kind { HEADER, TEXT, KV, BAR }

	private record Line(Kind kind, String text, String value, int color, int valueColor, double frac, String hint, Runnable action) {
		String plain() {
			return value == null ? text : text + "  " + value;
		}
	}

	/** Where a clickable line was last drawn. */
	public record Hit(int x, int y, int w, int h, String hint, Runnable action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private final List<Line> lines = new ArrayList<>();
	private static List<String> lastDrawn = List.of();
	private static List<Hit> hits = List.of();
	// the last drawn panel's box and how far it is scrolled
	private static int boxX, boxY, boxW, boxH, scroll, overflow;

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

	/** Scrolls the last drawn panel if the mouse is over it and it overflows; true if it took the wheel. */
	public static boolean scroll(double mx, double my, double amount) {
		if (overflow <= 0 || mx < boxX || mx >= boxX + boxW || my < boxY || my >= boxY + boxH) return false;
		scroll = Math.max(0, Math.min(overflow, scroll - (int) Math.round(amount * ROW * 3)));
		return true;
	}

	/** Forget the clickable areas and scroll position (the menu closed). */
	public static void clear() {
		hits = List.of();
		lastDrawn = List.of();
		scroll = 0;
		overflow = 0;
		boxW = 0;
	}

	/** Starts a section. */
	public Panel header(String text) {
		return header(text, null);
	}

	/** Starts a section with a short note on the right ("3 slots", "12m ago"). */
	public Panel header(String text, String tag) {
		lines.add(new Line(Kind.HEADER, text, tag, Theme.ACCENT, Theme.FAINT, 0, null, null));
		return this;
	}

	public Panel line(String text, int color) {
		lines.add(new Line(Kind.TEXT, text, null, color, 0, 0, null, null));
		return this;
	}

	public Panel line(String text) {
		return line(text, WHITE);
	}

	/** A label on the left with its value right-aligned. */
	public Panel kv(String label, String value, int valueColor) {
		lines.add(new Line(Kind.KV, label, value, GRAY, valueColor, 0, null, null));
		return this;
	}

	public Panel kv(String label, String value) {
		return kv(label, value, WHITE);
	}

	/** A label/value row in the label's own colour (ranked lists). */
	public Panel row(String label, String value, int color) {
		lines.add(new Line(Kind.KV, label, value, color, color, 0, null, null));
		return this;
	}

	public Panel bar(double frac, int color) {
		lines.add(new Line(Kind.BAR, "", null, color, 0, frac, null, null));
		return this;
	}

	/** A clickable line: `hint` is its tooltip, `action` runs on a left click. */
	public Panel link(String text, int color, String hint, Runnable action) {
		return link(text, null, color, hint, action);
	}

	/** A clickable label/value row. */
	public Panel link(String text, String value, int color, String hint, Runnable action) {
		lines.add(new Line(value == null ? Kind.TEXT : Kind.KV, text, value, color, color, 0, hint, action));
		return this;
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}

	private static int height(Line l, boolean first) {
		return switch (l.kind) {
			case HEADER -> first ? ROW + 4 : ROW + 9;
			case BAR -> 6;
			default -> ROW;
		};
	}

	public void draw(GuiGraphicsExtractor g, Font font, int menuLeft, int menuTop, int menuWidth, int screenWidth,
			int mouseX, int mouseY) {
		lastDrawn = lines.stream().map(Line::plain).toList();
		List<Hit> newHits = new ArrayList<>();
		hits = newHits;
		if (lines.isEmpty()) {
			boxW = 0;
			overflow = 0;
			return;
		}
		// the widest side of the menu decides how wide the panel may be
		int roomLeft = menuLeft - 6, roomRight = screenWidth - (menuLeft + menuWidth) - 6;
		boolean left = roomLeft >= MIN_W + PAD * 2 || roomLeft >= roomRight;
		int inner = Math.max(60, Math.min(MAX_W, (left ? roomLeft : roomRight) - PAD * 2));

		List<Line> shown = new ArrayList<>();
		for (Line l : lines) wrap(font, l, inner, shown);
		int w = MIN_W;
		for (Line l : shown) {
			int need = font.width(l.text) + (l.value == null ? 0 : GAP + font.width(l.value)) + (l.action != null ? 8 : 0);
			w = Math.max(w, need);
		}
		w = Math.min(w, inner) + PAD * 2;
		int content = PAD * 2 - 1;
		for (int i = 0; i < shown.size(); i++) content += height(shown.get(i), i == 0);

		int screenHeight = g.guiHeight();
		int h = Math.min(content, screenHeight - 4);
		int x = left ? menuLeft - w - 4 : menuLeft + menuWidth + 4;
		x = Math.max(2, Math.min(x, screenWidth - w - 2));
		int y = Math.max(2, Math.min(menuTop, screenHeight - h - 2));
		overflow = content - h;
		scroll = Math.max(0, Math.min(scroll, overflow));
		boxX = x;
		boxY = y;
		boxW = w;
		boxH = h;

		Theme.frame(g, x, y, w, h);
		g.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
		int ty = y + PAD - scroll;
		Hit hovered = null;
		boolean mouseInside = mouseY >= y && mouseY < y + h;
		for (int i = 0; i < shown.size(); i++) {
			Line l = shown.get(i);
			int lh = height(l, i == 0);
			if (ty + lh >= y && ty <= y + h) {
				switch (l.kind) {
					case HEADER -> {
						int hy = ty + (i == 0 ? 0 : 5);
						g.text(font, l.text, x + PAD, hy, l.color, false);
						if (l.value != null) Theme.right(g, font, l.value, x + w - PAD, hy, l.valueColor);
						Theme.rule(g, x + PAD, hy + ROW, w - PAD * 2);
					}
					case BAR -> Theme.bar(g, x + PAD, ty + 1, w - PAD * 2, 3, l.frac, l.color);
					default -> {
						int tx = x + PAD;
						if (l.action != null) {
							Hit hit = new Hit(x + 2, ty - 1, w - 4, ROW, l.hint, l.action);
							if (hit.y >= y && hit.y + hit.h <= y + h) newHits.add(hit);
							boolean over = mouseInside && hit.contains(mouseX, mouseY);
							if (over) {
								hovered = hit;
								g.fill(hit.x, hit.y, hit.x + hit.w, hit.y + hit.h, Theme.BG_HOVER);
								g.fill(hit.x, hit.y, hit.x + 1, hit.y + hit.h, Theme.ACCENT);
							}
							if (!l.text.isEmpty() && !l.text.startsWith("  ")) g.text(font, "›", tx, ty, over ? Theme.ACCENT : Theme.FAINT, false);
							tx += 8;
						}
						g.text(font, l.text, tx, ty, l.color, false);
						if (l.value != null) Theme.right(g, font, l.value, x + w - PAD, ty, l.valueColor);
					}
				}
			}
			ty += lh;
		}
		g.disableScissor();
		if (overflow > 0) {
			// a thumb on the right edge shows there's more
			int track = h - 4, thumb = Math.max(8, track * h / content);
			int pos = y + 2 + (track - thumb) * scroll / overflow;
			g.fill(x + w - 3, pos, x + w - 1, pos + thumb, Theme.ACCENT_DIM);
		}
		if (hovered != null && hovered.hint != null) g.setTooltipForNextFrame(font, Component.literal(hovered.hint), mouseX, mouseY);
	}

	/** Splits a line wider than `max` at spaces; continuation lines are indented and keep the line's action. */
	private static void wrap(Font font, Line l, int max, List<Line> out) {
		int reserve = (l.action != null ? 8 : 0);
		if (l.kind == Kind.HEADER) {
			// a title that doesn't fit beside its note loses the note; alone it is cut
			boolean both = l.value != null && font.width(l.text) + GAP + font.width(l.value) <= max;
			out.add(new Line(Kind.HEADER, Theme.fit(font, l.text, max), both ? l.value : null, l.color, l.valueColor, 0, null, null));
			return;
		}
		if (l.kind == Kind.KV) {
			int valueW = l.value == null ? 0 : GAP + font.width(l.value);
			if (font.width(l.text) + valueW <= max - reserve) {
				out.add(l);
				return;
			}
			// too narrow for one row: the label wraps and the value goes under it, still on the right
			wrap(font, new Line(Kind.TEXT, l.text, null, l.color, 0, 0, l.hint, l.action), max, out);
			if (l.value != null) out.add(new Line(Kind.KV, "", Theme.fit(font, l.value, max - reserve), l.color, l.valueColor, 0, l.hint, l.action));
			return;
		}
		if (l.kind != Kind.TEXT) {
			out.add(l);
			return;
		}
		int width = max - reserve;
		String rest = l.text;
		while (font.width(rest) > width) {
			String head = font.plainSubstrByWidth(rest, width);
			int cut = head.lastIndexOf(' ');
			if (cut <= 2) cut = Math.max(1, head.length());     // no break after the indent: hard cut
			out.add(new Line(Kind.TEXT, rest.substring(0, cut), null, l.color, 0, 0, l.hint, l.action));
			rest = "  " + rest.substring(cut).trim();
		}
		out.add(new Line(Kind.TEXT, rest, null, l.color, 0, 0, l.hint, l.action));
	}
}
