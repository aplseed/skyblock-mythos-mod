package dev.aplseed.mythos.ui;

import dev.aplseed.mythos.Config;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.acc.AccData;
import dev.aplseed.mythos.acc.Accessories;
import dev.aplseed.mythos.acc.Analysis;
import dev.aplseed.mythos.acc.Bag;
import dev.aplseed.mythos.acc.Catalog;
import dev.aplseed.mythos.acc.Planner;
import dev.aplseed.mythos.acc.Prices;
import dev.aplseed.mythos.acc.Rarity;
import dev.aplseed.mythos.util.Fmt;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Mythos window (/myth, or its key): server login and feature switches,
 * the accessory calculator, and every setting the commands reach.
 * Drawn by hand in the shared {@link Theme}; each tab is one scrolling column
 * of rows, and everything clickable registers a {@link Zone} as it is drawn.
 */
public final class MythosScreen extends Screen {
	public enum Tab {
		OVERVIEW("Overview"), ACCESSORIES("Accessories"), SETTINGS("Settings");

		final String label;

		Tab(String label) {
			this.label = label;
		}
	}

	public enum Sub {
		SUMMARY("Summary"), MISSING("Missing"), DUPES("Dupes"), BUDGET("Budget"), UNLOCKS("Unlocks");

		final String label;

		Sub(String label) {
			this.label = label;
		}
	}

	private record Zone(int x, int y, int w, int h, Runnable action, String tip) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	private static final int HEAD = 22, PAD = 8, ROW = 12;
	private static final long[] QUICK = {10_000_000L, 50_000_000L, 100_000_000L, 500_000_000L, 1_000_000_000L};

	private static Tab tab = Tab.OVERVIEW;
	private static Sub sub = Sub.SUMMARY;
	private static boolean hideLocked;
	/** What the last frame drew, line by line (read by the client game test). */
	private static List<String> lastText = List.of();

	private final List<Zone> zones = new ArrayList<>();
	private final List<String> text = new ArrayList<>();
	private int left, top, w, h;
	private int clipTop, clipBottom;
	private int scroll, maxScroll;
	private boolean shift;
	private String tooltip;
	private String note;
	private EditBox user, pass, url, budget;

	// the frame being drawn
	private GuiGraphicsExtractor g;
	private int mx, my;

	public MythosScreen(Tab open) {
		super(Component.literal("Mythos"));
		if (open != null) tab = open;
	}

	public static List<String> lastText() {
		return lastText;
	}

	public static void show(Sub s) {
		tab = Tab.ACCESSORIES;
		sub = s;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		w = Math.min(width - 12, 440);
		h = Math.min(height - 12, 280);
		left = (width - w) / 2;
		top = (height - h) / 2;
		scroll = 0;
		user = pass = url = budget = null;
		Config c = Mythos.config();
		if (tab == Tab.OVERVIEW) {
			url = box("http://127.0.0.1:5658", c.backendUrl, 200);
			user = box("user", c.username, 32);
			pass = box("password", "", 128);
			pass.addFormatter((s, i) -> FormattedCharSequence.forward("*".repeat(s.length()), Style.EMPTY));
		} else if (tab == Tab.ACCESSORIES && sub == Sub.BUDGET) {
			budget = box("e.g. 50m", c.accBudget, 16);
			budget.moveCursorToEnd(false);
			setInitialFocus(budget);
		}
	}

	private EditBox box(String hint, String value, int max) {
		EditBox b = new EditBox(font, 0, 0, 100, 10, Component.literal(hint));
		b.setBordered(false);
		b.setMaxLength(max);
		b.setValue(value);
		b.moveCursorToStart(false);
		b.setHint(Component.literal(hint).withStyle(s -> s.withColor(Theme.FAINT & 0xFFFFFF)));
		b.setTextColor(Theme.TEXT);
		b.visible = false;
		return addRenderableWidget(b);
	}

	private void go(Tab t) {
		tab = t;
		rebuildWidgets();
	}

	private void go(Sub s) {
		sub = s;
		rebuildWidgets();
	}

	// ---- input ----

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		if (click.button() == 0) {
			shift = click.hasShiftDown();
			for (int i = zones.size() - 1; i >= 0; i--) {
				Zone z = zones.get(i);
				if (z.contains(click.x(), click.y())) {
					z.action.run();
					return true;
				}
			}
		}
		return super.mouseClicked(click, doubled);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double dx, double dy) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(dy * 24)));
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent key) {
		boolean enter = key.isConfirmation();
		if (enter && budget != null && budget.isFocused()) {
			applyBudget(budget.getValue());
			return true;
		}
		if (enter && pass != null && (pass.isFocused() || user.isFocused())) {
			login();
			return true;
		}
		if (enter && url != null && url.isFocused()) {
			saveUrl();
			return true;
		}
		return super.keyPressed(key);
	}

	private void login() {
		if (user.getValue().isBlank() || pass.getValue().isEmpty()) {
			note = "Enter a user and password";
			return;
		}
		saveUrl();
		note = "Logging in...";
		Mythos.backend().login(user.getValue().trim(), pass.getValue(), name -> {
			note = null;
			pass.setValue("");
			dev.aplseed.mythos.data.ShardIndex.refresh(true);
		}, err -> note = "Login failed: " + err);
	}

	private void saveUrl() {
		String v = url.getValue().trim();
		if (v.equals(Mythos.config().backendUrl)) return;
		String problem = Mythos.setBackendUrl(v);
		note = problem;
		if (problem != null) url.setValue(Mythos.config().backendUrl);
	}

	private void applyBudget(String value) {
		Mythos.config().accBudget = value.trim();
		Mythos.config().save();
		if (budget != null && !budget.getValue().equals(value)) budget.setValue(value);
	}

	// ---- drawing primitives ----

	private boolean over(int x, int y, int bw, int bh) {
		return mx >= x && mx < x + bw && my >= Math.max(y, clipTop) && my < Math.min(y + bh, clipBottom);
	}

	/** Registers a click area, cut to the visible part of the scrolling body. */
	private void zone(int x, int y, int bw, int bh, String tip, Runnable action) {
		int y0 = Math.max(y, clipTop), y1 = Math.min(y + bh, clipBottom);
		if (y1 <= y0) return;
		if (action != null) zones.add(new Zone(x, y0, bw, y1 - y0, action, tip));
		if (tip != null && over(x, y, bw, bh)) tooltip = tip;
	}

	private void put(String s, int x, int y, int color) {
		g.text(font, s, x, y, color, false);
		text.add(s);
	}

	private void putRight(String s, int rightX, int y, int color) {
		Theme.right(g, font, s, rightX, y, color);
		text.add(s);
	}

	private int section(String title, String tag, int x, int y, int bw) {
		put(title, x, y, Theme.ACCENT);
		if (tag != null && font.width(title) + 10 + font.width(tag) <= bw) putRight(tag, x + bw, y, Theme.FAINT);
		Theme.rule(g, x, y + 10, bw);
		return y + 15;
	}

	private int kv(String label, String value, int valueColor, int x, int y, int bw) {
		put(label, x, y, Theme.MUTED);
		putRight(value, x + bw, y, valueColor);
		return y + ROW;
	}

	/** Wrapped text; returns the y below it. */
	private int para(String s, int x, int y, int bw, int color) {
		for (FormattedCharSequence line : font.split(Component.literal(s), bw)) {
			g.text(font, line, x, y, color, false);
			y += 10;
		}
		text.add(s);
		return y + 2;
	}

	private void button(String label, int x, int y, int bw, boolean primary, boolean enabled, String tip, Runnable action) {
		boolean hover = enabled && over(x, y, bw, 14);
		g.fill(x, y, x + bw, y + 14, !enabled ? Theme.BG_RAISED : hover ? (primary ? Theme.ACCENT : Theme.BG_HOVER) : primary ? Theme.ACCENT_DIM : Theme.BG_RAISED);
		g.outline(x, y, bw, 14, enabled ? (primary || hover ? Theme.ACCENT : Theme.BORDER) : Theme.RULE);
		int color = !enabled ? Theme.FAINT : primary && hover ? 0xFF14101F : Theme.TEXT;
		g.text(font, label, x + (bw - font.width(label)) / 2, y + 3, color, false);
		text.add("[" + label + "]");
		zone(x, y, bw, 14, tip, enabled ? action : null);
	}

	/** A row with a switch on the right; returns the y below it. */
	private int toggle(String name, String desc, boolean on, int x, int y, int bw, Runnable action) {
		int rh = desc == null ? 14 : 23;
		boolean hover = over(x - 3, y - 2, bw + 6, rh);
		if (hover) g.fill(x - 3, y - 2, x + bw + 3, y - 2 + rh, Theme.BG_HOVER);
		put(name, x, y + 1, Theme.TEXT);
		if (desc != null) g.text(font, Theme.fit(font, desc, bw - 30), x, y + 11, Theme.FAINT, false);
		int sx = x + bw - 20, sy = y + 1;
		g.fill(sx, sy, sx + 20, sy + 9, on ? Theme.ACCENT_DIM : Theme.RULE);
		g.outline(sx, sy, 20, 9, on ? Theme.ACCENT : Theme.BORDER);
		int knob = on ? sx + 12 : sx + 2;
		g.fill(knob, sy + 2, knob + 6, sy + 7, on ? Theme.TEXT : Theme.FAINT);
		text.add(on ? "(on)" : "(off)");
		zone(x - 3, y - 2, bw + 6, rh, null, action);
		return y + rh + 1;
	}

	/** A row with "- value +"; `change` gets -1/+1 (x10 with Shift). Returns the y below it. */
	private int stepper(String name, String value, int valueColor, int x, int y, int bw, String tip, IntConsumer change) {
		put(name, x, y + 3, Theme.TEXT);
		int bx = x + bw - 76;
		button("-", bx, y, 14, false, true, "Shift-click: 10 at a time", () -> change.accept(shift ? -10 : -1));
		g.text(font, value, bx + 14 + (48 - font.width(value)) / 2, y + 3, valueColor, false);
		text.add(value);
		button("+", bx + 62, y, 14, false, true, "Shift-click: 10 at a time", () -> change.accept(shift ? 10 : 1));
		if (tip != null) zone(x, y, bw - 80, 14, tip, null);
		return y + 17;
	}

	private void place(EditBox b, int x, int y, int bw) {
		Theme.card(g, x, y, bw, 14, b.isFocused());
		boolean visible = y >= clipTop && y + 14 <= clipBottom;
		b.visible = visible;
		b.setX(x + 4);
		b.setY(y + 3);
		b.setWidth(bw - 8);
	}

	// ---- frame ----

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		super.extractBackground(g, mouseX, mouseY, delta);
		Theme.frame(g, left, top, w, h);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		// the tab can change from outside (a command); the input boxes must match it
		if ((tab == Tab.OVERVIEW) != (url != null) || (tab == Tab.ACCESSORIES && sub == Sub.BUDGET) != (budget != null)) rebuildWidgets();
		this.g = g;
		mx = mouseX;
		my = mouseY;
		zones.clear();
		text.clear();
		tooltip = null;
		clipTop = top;
		clipBottom = top + h;

		// title bar: name, tabs, close
		put("✦ Mythos", left + PAD, top + 7, Theme.ACCENT);
		int tx = left + PAD + font.width("✦ Mythos") + 14;
		for (Tab t : Tab.values()) {
			int tw = font.width(t.label) + 12;
			boolean on = t == tab, hover = over(tx, top + 2, tw, HEAD - 3);
			if (on || hover) g.fill(tx, top + 2, tx + tw, top + HEAD, on ? Theme.BG_RAISED : Theme.BG_HOVER);
			if (on) g.fill(tx, top + HEAD - 1, tx + tw, top + HEAD, Theme.ACCENT);
			put(t.label, tx + 6, top + 7, on ? Theme.TEXT : Theme.MUTED);
			zone(tx, top + 2, tw, HEAD - 3, null, () -> go(t));
			tx += tw + 2;
		}
		boolean closeHover = over(left + w - 18, top + 4, 14, 14);
		g.text(font, "x", left + w - 13, top + 7, closeHover ? Theme.RED : Theme.FAINT, false);
		zone(left + w - 18, top + 4, 14, 14, "Close (Esc)", this::onClose);
		Theme.rule(g, left + 1, top + HEAD, w - 2);

		int bodyTop = top + HEAD + 1;
		if (tab == Tab.ACCESSORIES) bodyTop = subTabs(bodyTop);
		clipTop = bodyTop;
		clipBottom = top + h - 1;
		g.enableScissor(left + 1, clipTop, left + w - 1, clipBottom);
		int x = left + PAD, y = clipTop + PAD - scroll, bw = w - PAD * 2 - 4;
		int end = switch (tab) {
			case OVERVIEW -> overview(x, y, bw);
			case SETTINGS -> settings(x, y, bw);
			case ACCESSORIES -> accessories(x, y, bw);
		};
		g.disableScissor();
		int content = end + scroll - clipTop + PAD, view = clipBottom - clipTop;
		maxScroll = Math.max(0, content - view);
		scroll = Math.min(scroll, maxScroll);
		if (maxScroll > 0) {
			int thumb = Math.max(12, view * view / content);
			int pos = clipTop + (view - thumb) * scroll / maxScroll;
			g.fill(left + w - 4, pos, left + w - 2, pos + thumb, Theme.ACCENT_DIM);
		}
		clipTop = top;
		clipBottom = top + h;

		super.extractRenderState(g, mouseX, mouseY, delta);
		if (tooltip != null) g.setTooltipForNextFrame(font, Component.literal(tooltip), mouseX, mouseY);
		lastText = List.copyOf(text);
	}

	// ---- overview ----

	private int overview(int x, int y, int bw) {
		Config c = Mythos.config();
		boolean wide = bw >= 330;
		int colW = wide ? (bw - 12) / 2 : bw;

		int ly = section("Server", Mythos.active() ? null : "inactive: not on Hypixel", x, y, colW);
		boolean in = Mythos.backend().loggedIn();
		g.fill(x, ly + 2, x + 5, ly + 7, in ? Theme.GREEN : Theme.RED);
		put(in ? "Logged in as " + c.username : "Not logged in", x + 9, ly, in ? Theme.TEXT : Theme.MUTED);
		ly += ROW + 2;
		put("Address", x, ly + 3, Theme.MUTED);
		place(url, x + 50, ly, colW - 50);
		ly += 17;
		put("User", x, ly + 3, Theme.MUTED);
		place(user, x + 50, ly, colW - 50);
		ly += 17;
		put("Password", x, ly + 3, Theme.MUTED);
		place(pass, x + 50, ly, colW - 50);
		ly += 19;
		button("Log in", x + 50, ly, 60, true, true, "Only the session token is kept, never the password", this::login);
		button("Log out", x + 114, ly, 60, false, in, null, () -> Mythos.backend().logout());
		ly += 19;
		String err = note != null ? note : Mythos.backend().lastError();
		if (err != null) ly = para(err, x, ly, colW, note != null && !note.startsWith("Login failed") ? Theme.MUTED : Theme.RED);
		ly = para("Panels for fusion, absorb, bazaar, forge and election need a skyblock-mythos server. The accessory calculator works without one.",
				x, ly + 2, colW, Theme.FAINT);

		int rx = wide ? x + colW + 12 : x, ry = wide ? y : ly + 6;
		ry = section("Features", null, rx, ry, colW);
		for (Mythos.Feature f : Mythos.FEATURES) {
			ry = toggle(f.label(), f.about(), f.get(c), rx, ry, colW, () -> {
				f.set(c, !f.get(c));
				c.save();
			});
		}
		return Math.max(ly, ry);
	}

	// ---- settings ----

	private int settings(int x, int y, int bw) {
		Config c = Mythos.config();
		boolean wide = bw >= 330;
		int colW = wide ? (bw - 12) / 2 : bw;

		int ly = section("Forge", "used to rank recipes", x, y, colW);
		ly = stepper("Forge slots", Integer.toString(c.forgeSlots), Theme.TEXT, x, ly, colW, null, d -> set(() -> c.forgeSlots = clamp(c.forgeSlots + d, 2, 7)));
		ly = stepper("Quick Forge level", Integer.toString(c.quickForgeLevel), Theme.TEXT, x, ly, colW, null, d -> set(() -> c.quickForgeLevel = clamp(c.quickForgeLevel + d, 0, 20)));
		ly = toggle("Cole is mayor", "Cole's forge time bonus", c.coleMayor, x, ly + 1, colW, () -> set(() -> c.coleMayor = !c.coleMayor));

		ly = section("Fusion", "how plans value output", x, ly + 6, colW);
		ly = toggle("Sell with orders", "Off: value at instant-sell prices", c.fusionSellMode.equals("sell_order"), x, ly, colW,
				() -> set(() -> c.fusionSellMode = c.fusionSellMode.equals("sell_order") ? "instant_sell" : "sell_order"));
		ly = stepper("Pure Reptile level", Integer.toString(c.pureReptileLevel), Theme.TEXT, x, ly, colW, null, d -> set(() -> c.pureReptileLevel = clamp(c.pureReptileLevel + d, 0, 20)));

		int rx = wide ? x + colW + 12 : x, ry = wide ? y : ly + 6;
		ry = section("Shard session HUD", "position on screen", rx, ry, colW);
		ry = stepper("Left", Integer.toString(c.hudX), Theme.TEXT, rx, ry, colW, null, d -> set(() -> c.hudX = clamp(c.hudX + d * 2, 0, 4000)));
		ry = stepper("Top", Integer.toString(c.hudY), Theme.TEXT, rx, ry, colW, null, d -> set(() -> c.hudY = clamp(c.hudY + d * 2, 0, 4000)));

		ry = section("General", null, rx, ry + 6, colW);
		ry = toggle("Only on Hypixel", "Stay idle on every other server", c.onlyOnHypixel, rx, ry, colW, () -> set(() -> c.onlyOnHypixel = !c.onlyOnHypixel));
		ry = para("Keys (Options > Controls > Mythos): open this window, dump the open menu (F8), toggle menu panels.", rx, ry + 2, colW, Theme.FAINT);
		return Math.max(ly, ry);
	}

	private static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	private static void set(Runnable change) {
		change.run();
		Mythos.config().save();
	}

	// ---- accessories ----

	private int subTabs(int y) {
		int tx = left + PAD;
		for (Sub s : Sub.values()) {
			int tw = font.width(s.label) + 10;
			boolean on = s == sub, hover = over(tx, y + 2, tw, 13);
			if (on || hover) g.fill(tx, y + 2, tx + tw, y + 15, on ? Theme.ACCENT_DIM : Theme.BG_HOVER);
			put(s.label, tx + 5, y + 5, on ? Theme.TEXT : Theme.MUTED);
			zone(tx, y + 2, tw, 13, null, () -> go(s));
			tx += tw + 2;
		}
		Analysis a = Accessories.analysis();
		if (a != null && Bag.scanned()) putRight(Fmt.full(a.magicPower) + " MP", left + w - PAD, y + 5, Theme.PINK);
		Theme.rule(g, left + 1, y + 17, w - 2);
		return y + 18;
	}

	private int accessories(int x, int y, int bw) {
		Analysis a = Accessories.analysis();
		if (sub == Sub.UNLOCKS) return unlocks(a, x, y, bw);
		String status = AccData.status();
		if (a == null) return para(status, x, y, bw, Theme.MUTED);
		if (!Bag.scanned()) {
			y = para("No scan yet. Open your Accessory Bag in SkyBlock (every page of it) and Mythos records what's inside.", x, y, bw, Theme.TEXT);
			y = para("After that this window lists what's missing, finds dupes and plans a budget - no need to reopen the bag.", x, y, bw, Theme.MUTED);
			if (status != null) y = para(status, x, y, bw, Theme.FAINT);
			return y;
		}
		if (!Bag.complete()) y = para("Only " + Bag.pagesSeen() + " of " + Bag.pages() + " bag pages scanned - open page " + Bag.pagesMissing() + " so nothing counts as missing by mistake.", x, y, bw, Theme.YELLOW);
		if (status != null) y = para(status, x, y, bw, Theme.FAINT);
		return switch (sub) {
			case MISSING -> missing(a, x, y, bw);
			case DUPES -> dupes(a, x, y, bw);
			case BUDGET -> budget(a, x, y, bw);
			default -> summary(a, x, y, bw);
		};
	}

	private static String ago(long at) {
		long min = (System.currentTimeMillis() - at) / 60_000;
		return min < 1 ? "just now" : min < 90 ? min + " min ago" : min < 48 * 60 ? min / 60 + " h ago" : min / 1440 + " d ago";
	}

	private int summary(Analysis a, int x, int y, int bw) {
		boolean wide = bw >= 330;
		int colW = wide ? (bw - 12) / 2 : bw;
		Config c = Mythos.config();

		int ly = section("Your bag", "scanned " + ago(Bag.scannedAt()), x, y, colW);
		ly = kv("Magical Power", Fmt.full(a.magicPower), Theme.PINK, x, ly, colW);
		ly = kv("Accessories counted", a.counted.size() + " of " + Bag.size(), Theme.TEXT, x, ly, colW);
		ly = kv("Dupes", Integer.toString(a.dupes.size()), a.dupes.isEmpty() ? Theme.GREEN : Theme.RED, x, ly, colW);
		ly = kv("Free slots", Bag.freeSlots() + (c.accFreeSlots >= 0 ? " (set by you)" : ""), Theme.TEXT, x, ly, colW);
		ly = kv("Pages scanned", Bag.pagesSeen() + " of " + Bag.pages(), Bag.complete() ? Theme.TEXT : Theme.YELLOW, x, ly, colW);
		if (c.riftPrism) ly = kv("Rift Prism", "+" + Analysis.RIFT_PRISM_MP, Theme.TEXT, x, ly, colW);
		if (!a.inventory.isEmpty()) ly = kv("In your inventory", a.inventory.size() + " not in the bag", Theme.YELLOW, x, ly, colW);

		ly = section("Still to get", null, x, ly + 6, colW);
		int priced = 0, gain = 0;
		double cost = 0;
		for (Analysis.Missing m : a.missing) {
			if (m.locked() == null && !Double.isNaN(m.price())) {
				priced++;
				gain += m.gain();
				cost += m.price();
			}
		}
		ly = kv("Buyable lines", priced + "  (+" + gain + " MP, " + Fmt.coins(cost) + ")", Theme.GREEN, x, ly, colW);
		ly = kv("Locked for you", Integer.toString(a.locked), a.locked > 0 ? Theme.YELLOW : Theme.TEXT, x, ly, colW);
		ly = kv("No market price", Integer.toString(a.missing.size() - priced - a.locked), Theme.MUTED, x, ly, colW);
		ly = kv("Can be recombobulated", a.recombable + "  (" + Fmt.coins(a.recombPrice) + " each)", Theme.TEXT, x, ly, colW);
		if (!a.unknownUnlocks.isEmpty()) {
			ly = para("Tell Mythos your slayer and other levels in Unlocks - " + a.unknownUnlocks.size() + " requirement"
					+ (a.unknownUnlocks.size() == 1 ? " is" : "s are") + " unset, so those accessories are treated as locked.", x, ly + 2, colW, Theme.YELLOW);
		}
		if (c.accCraft && !a.unknownCollections.isEmpty()) {
			ly = para("Open your Collections menus (each category) - " + a.unknownCollections.size() + " recipe"
					+ (a.unknownCollections.size() == 1 ? "" : "s") + " can't be checked yet, so those accessories are priced as purchases.",
					x, ly + 2, colW, Theme.YELLOW);
		}
		if (c.jacobusUpgrades < 0) ly = para("Set your Jacobus upgrades in Unlocks to let budgets buy more bag slots.", x, ly, colW, Theme.FAINT);

		int rx = wide ? x + colW + 12 : x, ry = wide ? y : ly + 6;
		ry = section("Power by rarity", null, rx, ry, colW);
		Map<Rarity, int[]> by = new EnumMap<>(Rarity.class);
		for (Analysis.Counted k : a.counted) {
			int[] v = by.computeIfAbsent(k.item().rarity(), r -> new int[2]);
			v[0]++;
			v[1] += k.mp();
		}
		int most = 1;
		for (int[] v : by.values()) most = Math.max(most, v[1]);
		for (Rarity r : Rarity.values()) {
			int[] v = by.get(r);
			if (v == null) continue;
			put(r.label, rx, ry, r.color);
			putRight(v[0] + " x  " + v[1] + " MP", rx + colW, ry, Theme.TEXT);
			Theme.bar(g, rx, ry + 10, colW, 2, v[1] / (double) most, r.color);
			ry += 16;
		}
		ry += 4;
		button("Refresh prices", rx, ry, 84, false, true, AccData.pricesAge() < 0 ? null : "Prices are " + AccData.pricesAge() / 60_000 + " min old",
				() -> AccData.refresh(true));
		button("Plan a budget", rx + 88, ry, 84, true, true, null, () -> go(Sub.BUDGET));
		return Math.max(ly, ry + 16);
	}

	/** Columns shared by the missing list and the plan: name | how | price | +MP. */
	private void itemRow(String name, int nameColor, String how, String price, String mp, int x, int y, int bw, String tip, Runnable click) {
		boolean hover = over(x - 3, y - 1, bw + 6, ROW);
		if (hover && click != null) g.fill(x - 3, y - 1, x + bw + 3, y - 1 + ROW, Theme.BG_HOVER);
		int mpX = x + bw, priceX = mpX - 44, howW = Math.min(104, bw / 4), howX = priceX - 46 - howW;
		put(Theme.fit(font, name, howX - x - 6), x, y + 1, nameColor);
		g.text(font, Theme.fit(font, how, howW), howX, y + 1, Theme.FAINT, false);
		text.add(how);
		putRight(price, priceX, y + 1, Theme.GOLD);
		putRight(mp, mpX, y + 1, Theme.PINK);
		zone(x - 3, y - 1, bw + 6, ROW, tip, click);
	}

	private int missing(Analysis a, int x, int y, int bw) {
		y = toggle("Hide locked and unpriced", null, hideLocked, x, y, bw, () -> hideLocked = !hideLocked);
		y = section("Missing accessories", "cheapest power first", x, y + 2, bw);
		int shown = 0;
		for (Analysis.Missing m : a.missing) {
			boolean buyable = m.locked() == null && !Double.isNaN(m.price());
			if (hideLocked && !buyable) continue;
			shown++;
			if (y > clipBottom || y + ROW < clipTop) {     // off screen: only its height matters
				y += ROW;
				continue;
			}
			Catalog.Acc acc = m.acc();
			String name = acc.name();
			String how = m.locked() != null ? m.locked() : buyable ? (m.upgrade() ? "upgrade, " : "") + m.how() : acc.soulbound() ? "soulbound: earn it" : "not sold";
			boolean ah = buyable && !m.how().startsWith("craft"), craft = buyable && !ah;
			itemRow(name, m.locked() != null ? Theme.FAINT : acc.tier().color, how, buyable ? Fmt.coins(m.price()) : "-", "+" + m.gain(), x, y, bw,
					buyable ? name + ": " + m.how() + ", " + Fmt.coins(m.perMp()) + " per MP - click: " + (ah ? "/ahs " : "/recipe ") + name : name + ": " + how,
					ah ? () -> Mythos.auctionSearch(name) : craft ? () -> Mythos.recipe(name) : null);
			y += ROW;
		}
		if (shown == 0) y = para(Bag.complete() ? "Nothing left to get. Impressive." : "Nothing to list yet.", x, y, bw, Theme.MUTED);
		return y;
	}

	private int dupes(Analysis a, int x, int y, int bw) {
		y = section("Dupes", a.dupes.isEmpty() ? null : a.dupes.size() + " slot" + (a.dupes.size() == 1 ? "" : "s") + " wasted", x, y, bw);
		if (a.dupes.isEmpty()) y = para("No dupes: every accessory in the bag counts.", x, y, bw, Theme.GREEN);
		for (Analysis.Dupe d : a.dupes) {
			put(Theme.fit(font, d.item().name(), bw / 2), x, y, d.item().rarity().color);
			putRight((d.sameItem() ? "you have it twice" : "lower tier of " + d.kept().name()) + "  -  page " + d.item().page(), x + bw, y, Theme.MUTED);
			y += ROW;
		}
		if (!a.dupes.isEmpty()) y = para("Only the best accessory of a line counts. Take these out to free their slots (or sell them).", x, y + 2, bw, Theme.FAINT);
		if (!a.inventory.isEmpty()) {
			y = section("In your inventory", "seen when the bag was last open", x, y + 6, bw);
			for (Analysis.Owned o : a.inventory) {
				put(o.name(), x, y, o.rarity().color);
				y += ROW;
			}
		}
		return y;
	}

	private int budget(Analysis a, int x, int y, int bw) {
		put("Budget", x, y + 3, Theme.MUTED);
		place(budget, x + 42, y, 86);
		int qx = x + 134;
		for (long q : QUICK) {
			String label = Fmt.coins(q);
			int qw = font.width(label) + 10;
			if (qx + qw > x + bw) break;
			button(label, qx, y, qw, false, true, null, () -> applyBudget(label));
			qx += qw + 3;
		}
		y += 20;
		double coins = Fmt.parseCoins(budget.getValue());
		if (budget.getValue().isBlank()) return para("Type what you want to spend (50m, 1.2b, 750k). The plan is the most Magical Power those coins can buy: new accessories, upgrades of the ones you have, recombobulators and - if it pays off - more bag slots.", x, y, bw, Theme.MUTED);
		if (coins < 0) return para("That isn't an amount. Try 50m, 1.2b or 750000.", x, y, bw, Theme.RED);
		if (!budget.getValue().trim().equals(Mythos.config().accBudget)) applyBudget(budget.getValue().trim());
		Planner.Plan plan = Accessories.plan(coins);
		if (plan == null) return para("Planning...", x, y, bw, Theme.MUTED);
		if (!AccData.priced()) y = para("Prices haven't loaded, so nothing can be bought yet.", x, y, bw, Theme.YELLOW);

		Theme.card(g, x, y, bw, 30, false);
		put("+" + plan.gain() + " Magical Power", x + 6, y + 5, Theme.PINK);
		put(a.magicPower + " -> " + (a.magicPower + plan.gain()), x + 6, y + 16, Theme.MUTED);
		putRight(Fmt.coins(plan.total()) + " of " + Fmt.coins(plan.budget()), x + bw - 6, y + 5, Theme.GOLD);
		putRight(plan.gain() > 0 ? Fmt.coins(plan.total() / plan.gain()) + " per MP" : "nothing to buy", x + bw - 6, y + 16, Theme.MUTED);
		y += 36;
		if (plan.picks().isEmpty()) return para(a.groups.isEmpty() ? "Nothing with a price would add power." : "Nothing fits this budget.", x, y, bw, Theme.MUTED);

		y = section("Steps", plan.picks().size() + " purchases, best value first", x, y, bw);
		int n = 0;
		if (plan.dupesToRemove() > 0) {
			itemRow(++n + ". Take " + plan.dupesToRemove() + " dupe" + (plan.dupesToRemove() == 1 ? "" : "s") + " out of the bag", Theme.YELLOW,
					"frees slots", "free", "", x, y, bw, "See the Dupes tab", () -> go(Sub.DUPES));
			y += ROW;
		}
		if (plan.jacobusUpgrades() > 0) {
			itemRow(++n + ". Buy " + plan.jacobusUpgrades() + " bag upgrade" + (plan.jacobusUpgrades() == 1 ? "" : "s") + " from Jacobus", Theme.YELLOW,
					"+" + plan.jacobusUpgrades() * Planner.SLOTS_PER_UPGRADE + " slots", Fmt.coins(plan.slotCost()), "", x, y, bw, null, null);
			y += ROW;
		}
		for (Analysis.Option o : plan.picks()) {
			n++;
			if (y > clipBottom || y + ROW < clipTop) {
				y += ROW;
				continue;
			}
			boolean ah = (o.kind() == Analysis.Kind.NEW || o.kind() == Analysis.Kind.UPGRADE) && !o.how().startsWith("craft");
			boolean craft = Accessories.crafted(o);
			String name = o.name();
			itemRow(n + ". " + Accessories.describe(o), o.result().color, o.how(), o.cost() == 0 ? "free" : Fmt.coins(o.cost()), "+" + o.gain(), x, y, bw,
					Accessories.describe(o) + ": " + o.how() + ", " + Fmt.coins(o.perMp()) + " per MP" + (ah ? " - click: /ahs " + name : craft ? " - click: /recipe " + name : ""),
					ah ? () -> Mythos.auctionSearch(name) : craft ? () -> Mythos.recipe(name) : null);
			y += ROW;
		}
		y += 4;
		if (plan.limitedBySlots()) {
			y = para(Mythos.config().jacobusUpgrades < 0 ? "The bag is full. Set your Jacobus upgrades in Unlocks and the plan can buy slots too."
					: "The bag is full and Jacobus has no upgrades left.", x, y, bw, Theme.YELLOW);
		}
		if (a.locked > 0) y = para(a.locked + " accessory line" + (a.locked == 1 ? " is" : "s are") + " left out as locked - see Unlocks.", x, y, bw, Theme.FAINT);
		button("Send to chat", x, y + 2, 80, false, true, "Prints this plan in chat", () -> {
			for (String l : Accessories.lines(plan, a, 40)) Mythos.chat(l);
			onClose();
		});
		button("Copy", x + 84, y + 2, 44, false, true, "Copies the plan as text", () -> {
			StringBuilder sb = new StringBuilder();
			for (String l : Accessories.lines(plan, a, 500)) sb.append(l.replaceAll("§.", "")).append('\n');
			minecraft.keyboardHandler.setClipboard(sb.toString());
			Mythos.toast("Mythos", "Plan copied");
		});
		return y + 18;
	}

	/** The collections accessory recipes need, with the tier Mythos has seen for each. */
	private int collections(Analysis a, int x, int y, int bw) {
		Config c = Mythos.config();
		y = section("Collections for crafts", "read from the Collections menus", x, y, bw);
		Map<String, Integer> needed = new java.util.TreeMap<>();
		for (Prices.Recipe r : AccData.prices().recipes().values()) {
			Prices.Collection need = r.collection();
			if (need != null && !r.parts().isEmpty()) needed.merge(need.name(), need.tier(), Math::max);
		}
		if (needed.isEmpty()) return para("Recipes haven't loaded yet.", x, y, bw, Theme.MUTED);
		for (Map.Entry<String, Integer> e : needed.entrySet()) {
			String key = e.getKey().toLowerCase(java.util.Locale.ROOT);
			Integer have = Prices.tier(c.collections, e.getKey());
			y = stepper(e.getKey(), have == null ? "not seen" : have == 0 ? "none" : Prices.roman(have),
					have == null ? Theme.YELLOW : have >= e.getValue() ? Theme.GREEN : Theme.TEXT, x, y, bw,
					"Recipes need up to " + e.getKey() + " " + Prices.roman(e.getValue()), d -> set(() -> {
						int v = (have == null ? -1 : have) + d;
						if (v < 0) c.collections.remove(key);
						else c.collections.put(key, Math.min(20, v));
					}));
		}
		return para("Open each Collections category in game and these fill in by themselves. A craft is only recommended once its collection is high enough; \"not seen\" counts as locked.",
				x, y + 2, bw, Theme.FAINT);
	}

	private int unlocks(Analysis a, int x, int y, int bw) {
		Config c = Mythos.config();
		boolean wide = bw >= 330;
		int colW = wide ? (bw - 12) / 2 : bw;
		Catalog cat = AccData.catalog();

		int ly = section("Your levels", "accessories above them are left out", x, y, colW);
		if (cat == null) {
			ly = para(AccData.status(), x, ly, colW, Theme.MUTED);
		} else {
			for (Map.Entry<String, Integer> e : cat.reqKeys.entrySet()) {
				String key = e.getKey();
				int max = Math.max(e.getValue(), key.startsWith("slayer_") ? 9 : key.equals("hotm") ? 10 : e.getValue());
				Integer have = c.unlocks.get(key);
				ly = stepper(Catalog.label(key), have == null ? "not set" : Catalog.levelName(key, have), have == null ? Theme.YELLOW : Theme.TEXT, x, ly, colW,
						null, d -> set(() -> {
							int v = (have == null ? -1 : have) + d;
							if (v < 0) c.unlocks.remove(key);
							else c.unlocks.put(key, Math.min(max, v));
						}));
			}
			ly = para("\"not set\" counts as locked. Mythos can't read these levels from the game.", x, ly + 2, colW, Theme.FAINT);
		}

		ly = collections(a, x, ly + 6, colW);

		int rx = wide ? x + colW + 12 : x, ry = wide ? y : ly + 6;
		ry = section("Bag slots", null, rx, ry, colW);
		ry = stepper("Jacobus upgrades", c.jacobusUpgrades < 0 ? "unknown" : c.jacobusUpgrades + " of " + Planner.JACOBUS_MAX, c.jacobusUpgrades < 0 ? Theme.YELLOW : Theme.TEXT,
				rx, ry, colW, "Bag upgrades you've bought from Jacobus. Known: budgets may buy more.",
				d -> set(() -> c.jacobusUpgrades = clamp(c.jacobusUpgrades + d, -1, Planner.JACOBUS_MAX)));
		if (c.jacobusUpgrades >= 0 && c.jacobusUpgrades < Planner.JACOBUS_MAX) {
			ry = kv("Next upgrade", Fmt.coins(Planner.jacobusPrice(c.jacobusUpgrades + 1)) + " for +" + Planner.SLOTS_PER_UPGRADE + " slots", Theme.GOLD, rx, ry, colW);
		}
		ry = stepper("Free slots", c.accFreeSlots < 0 ? "auto: " + Bag.freeSlots() : Integer.toString(c.accFreeSlots), Theme.TEXT, rx, ry + 2, colW,
				"Auto counts the empty slots seen in the bag. Set a number if that's wrong.",
				d -> set(() -> c.accFreeSlots = clamp(c.accFreeSlots + d, -1, 300)));

		ry = section("Other", null, rx, ry + 6, colW);
		ry = toggle("Craft when it's cheaper", "Off: plan with auction and bazaar prices only", c.accCraft, rx, ry, colW, () -> set(() -> c.accCraft = !c.accCraft));
		ry = toggle("Rift Prism consumed", "+" + Analysis.RIFT_PRISM_MP + " Magical Power, not an item in the bag", c.riftPrism, rx, ry, colW, () -> set(() -> c.riftPrism = !c.riftPrism));
		button("Forget the scan", rx, ry + 3, 90, false, Bag.scanned(), "Clears the stored bag (after switching profile)", Bag::clear);
		return Math.max(ly, ry + 20);
	}
}
