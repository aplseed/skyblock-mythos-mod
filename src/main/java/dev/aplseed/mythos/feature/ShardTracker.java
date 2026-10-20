package dev.aplseed.mythos.feature;

import com.google.gson.JsonObject;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.data.ShardIndex;
import dev.aplseed.mythos.net.Json;
import dev.aplseed.mythos.util.Fmt;
import dev.aplseed.mythos.util.Items;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Shard farming session from chat: shards caught, their net value, coins per
 * hour, and how the main shard's catch rate compares with the rate saved for it
 * on the Shard Farm page (/api/shards/farms). Experimental - Hypixel words these messages several
 * ways; unmatched ones are simply not counted.
 */
public final class ShardTracker {
	private static final long IDLE_END_MS = 10 * 60_000;
	private static final String NAME = "([A-Z][A-Za-z' -]+?)";
	private static final Pattern[] PATTERNS = {
		// "You caught a Tadgang Shard!", "You caught x2 Shinyfish Shards!"
		Pattern.compile("(?i)\\byou (?:caught|captured|received|found|obtained|got)\\s+(?:an?\\s+)?(?:x?([0-9,]+)\\s*x?\\s+)?" + NAME + " Shards?\\b"),
		// "You charmed a Lapis Creeper and captured its Shard." / "... and captured 2 Shards"
		Pattern.compile("(?i)charmed (?:an? )?" + NAME + " and captured (?:its|([0-9,]+)) Shards?"),
		// "+3 Tadgang Shards"
		Pattern.compile("^\\+\\s*([0-9,]+)x?\\s+" + NAME + " Shards?\\b"),
	};

	private record Catch(String id, String name, long count, double value) {}

	private static long start, last;
	private static final Map<String, Catch> caught = new HashMap<>();
	private static List<JsonObject> farms = List.of();

	private ShardTracker() {}

	public static boolean running() {
		return start > 0;
	}

	public static void onChat(Component message, boolean overlay) {
		if (overlay || !Mythos.config().shardTracker || !Mythos.active()) return;
		String text = Items.plain(message);
		if (!text.contains("Shard")) return;
		for (int p = 0; p < PATTERNS.length; p++) {
			Matcher m = PATTERNS[p].matcher(text);
			if (!m.find()) continue;
			String name = p == 1 ? m.group(1) : m.group(2);
			String num = p == 1 ? m.group(2) : m.group(1);
			ShardIndex.Shard s = ShardIndex.byName(name);
			if (s == null) continue;
			long n = num == null ? 1 : Math.max(1, Items.parse(num));
			add(s, n);
			return;
		}
	}

	private static void add(ShardIndex.Shard s, long n) {
		long now = System.currentTimeMillis();
		if (start == 0) {
			start = now;
			caught.clear();
			Mythos.backend().get("/api/shards/farms", j -> farms = Json.objs(j, "farms"));
		}
		last = now;
		Catch c = caught.get(s.id());
		long count = (c == null ? 0 : c.count) + n;
		caught.put(s.id(), new Catch(s.id(), s.name(), count, count * s.value()));
	}

	public static void tick() {
		if (start > 0 && System.currentTimeMillis() - last > IDLE_END_MS) stop("ended after 10 min without a shard");
	}

	public static void stop(String why) {
		if (start == 0) return;
		for (String l : summary()) Mythos.chat(l);
		Mythos.chat("Shard session " + why);
		start = 0;
		caught.clear();
	}

	private static double hours() {
		return Math.max(last - start, 60_000) / 3_600_000.0;
	}

	public static List<String> summary() {
		List<String> out = new ArrayList<>();
		if (start == 0) {
			out.add("No shard session running");
			return out;
		}
		long total = caught.values().stream().mapToLong(Catch::count).sum();
		double value = caught.values().stream().mapToDouble(Catch::value).sum();
		long mins = (System.currentTimeMillis() - start) / 60_000;
		out.add("Shards " + mins + "m: " + total + " caught, " + Fmt.coins(value) + " (" + Fmt.coins(value / hours()) + "/h)");
		caught.values().stream().sorted((a, b) -> Double.compare(b.value, a.value)).limit(3).forEach(c ->
				out.add("  " + c.name + " x" + c.count + "  " + Fmt.coins(c.value)));
		String plan = planLine();
		if (plan != null) out.add(plan);
		return out;
	}

	/** Rate of the most-caught shard against the rate saved for it on the Shard Farm page. */
	private static String planLine() {
		Catch top = caught.values().stream().max((a, b) -> Long.compare(a.count, b.count)).orElse(null);
		if (top == null) return null;
		for (JsonObject f : farms) {
			if (!top.id.equals(Json.str(f, "shard_id", ""))) continue;
			double saved = Json.num(f, "rate", 0);
			if (saved <= 0) return null;
			double rate = top.count / hours();
			return "  " + top.name + ": " + Fmt.coins(rate) + "/h vs saved " + Fmt.coins(saved) + "/h ("
					+ Math.round(rate / saved * 100) + "%)";
		}
		return null;
	}

	public static void renderHud(GuiGraphicsExtractor g) {
		if (start == 0 || !Mythos.config().shardTracker) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.options.hideGui) return;
		Font font = mc.font;
		List<String> lines = summary();
		int w = 0;
		for (String l : lines) w = Math.max(w, font.width(l));
		int x = Mythos.config().hudX, y = Mythos.config().hudY;
		g.fill(x - 2, y - 2, x + w + 2, y + lines.size() * (font.lineHeight + 1) + 1, 0x90000000);
		for (int i = 0; i < lines.size(); i++) {
			g.text(font, lines.get(i), x, y + i * (font.lineHeight + 1), i == 0 ? 0xFF55FFFF : 0xFFDDDDDD, true);
		}
	}
}
