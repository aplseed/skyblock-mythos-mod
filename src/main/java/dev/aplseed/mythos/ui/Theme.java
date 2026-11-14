package dev.aplseed.mythos.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The one look every Mythos surface shares: side panels, the HUD and the Mythos screen. */
public final class Theme {
	// surfaces
	public static final int BG = 0xF00E0B16;
	public static final int BG_RAISED = 0xFF1A1526;
	public static final int BG_HOVER = 0xFF261E38;
	public static final int BORDER = 0xFF3A2D5C;
	public static final int RULE = 0xFF2A2140;
	public static final int ACCENT = 0xFFB48CFF;
	public static final int ACCENT_DIM = 0xFF6F52B8;
	public static final int SHADOW = 0x66000000;

	// text
	public static final int TEXT = 0xFFECE8F5;
	public static final int MUTED = 0xFFA79FBA;
	public static final int FAINT = 0xFF6E6684;
	public static final int GOLD = 0xFFFFC857;
	public static final int GREEN = 0xFF6EE7A0;
	public static final int RED = 0xFFFF6B7A;
	public static final int AQUA = 0xFF6FD8F5;
	public static final int YELLOW = 0xFFF5E06F;
	public static final int PINK = 0xFFF28BEB;

	private Theme() {}

	/** A panel body: soft shadow, dark fill, hairline border, accent line along the top. */
	public static void frame(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x + 1, y + h, x + w + 1, y + h + 1, SHADOW);
		g.fill(x + w, y + 1, x + w + 1, y + h, SHADOW);
		g.fill(x, y, x + w, y + h, BG);
		g.outline(x, y, w, h, BORDER);
		g.fill(x + 1, y, x + w - 1, y + 1, ACCENT);
	}

	/** A raised block inside a frame (cards, inputs). */
	public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean hover) {
		g.fill(x, y, x + w, y + h, hover ? BG_HOVER : BG_RAISED);
		g.outline(x, y, w, h, hover ? ACCENT_DIM : RULE);
	}

	public static void rule(GuiGraphicsExtractor g, int x, int y, int w) {
		g.fill(x, y, x + w, y + 1, RULE);
	}

	/** A progress bar; `frac` is clamped to 0-1. */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double frac, int color) {
		g.fill(x, y, x + w, y + h, RULE);
		int fill = (int) Math.round(w * Math.max(0, Math.min(1, frac)));
		if (fill > 0) g.fill(x, y, x + fill, y + h, color);
	}

	/** Tints a 16x16 inventory slot and outlines it. */
	public static void slot(GuiGraphicsExtractor g, int x, int y, int color) {
		g.fill(x, y, x + 16, y + 16, (color & 0x00FFFFFF) | 0x50000000);
		g.outline(x - 1, y - 1, 18, 18, (color & 0x00FFFFFF) | 0xC0000000);
	}

	/** A small label on a slot's top-left corner, readable over any item. */
	public static void badge(GuiGraphicsExtractor g, Font font, int x, int y, String text, int color) {
		int w = font.width(text) + 2;
		g.fill(x - 1, y - 1, x + w - 1, y + font.lineHeight - 1, 0xD00E0B16);
		g.text(font, text, x, y, color, false);
	}

	/** Text cut to `w` pixels with an ellipsis. */
	public static String fit(Font font, String s, int w) {
		if (font.width(s) <= w) return s;
		return font.plainSubstrByWidth(s, Math.max(0, w - font.width("…"))) + "…";
	}

	public static void right(GuiGraphicsExtractor g, Font font, String s, int rightX, int y, int color) {
		g.text(font, s, rightX - font.width(s), y, color, false);
	}
}
