package dev.aplseed.mythos.feature;

import com.google.gson.JsonObject;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.net.Json;
import dev.aplseed.mythos.util.Fmt;
import java.util.ArrayList;
import java.util.List;

/**
 * Sell warnings the engine raises on items you hold (/api/status
 * position_alerts), shown once in game and acknowledged for the mod only, so
 * the web toast is unaffected.
 */
public final class SellWarnings {
	private static final int INTERVAL_TICKS = 20 * 60;
	private static int ticks = INTERVAL_TICKS - 20 * 15;     // first check 15 s after joining

	private SellWarnings() {}

	public static void tick() {
		if (++ticks < INTERVAL_TICKS) return;
		ticks = 0;
		if (!Mythos.config().sellWarnings || !Mythos.active() || !Mythos.backend().loggedIn()) return;
		Mythos.backend().get("/api/status", SellWarnings::accept);
	}

	private static void accept(JsonObject status) {
		List<String> ids = new ArrayList<>();
		for (JsonObject a : Json.objs(status, "position_alerts")) {
			if (!Json.bool(Json.obj(a, "new"), "mod")) continue;
			ids.add(Json.str(a, "id", ""));
			String name = Json.str(a, "name", "?");
			String label = Json.str(a, "label", "?");
			List<String> ev = Json.strs(a, "evidence");
			Mythos.chat("§cSell warning§r " + name + ": §e" + label + "§r ("
					+ Math.round(Json.num(a, "confidence", 0) * 100) + "% conf, bought " + Fmt.coins(Json.num(a, "buy_price", 0))
					+ ", now " + Fmt.coins(Json.num(a, "price", 0)) + ")" + (ev.isEmpty() ? "" : " - " + ev.get(0)));
			if (ids.size() == 1) Mythos.toast("Sell warning: " + name, label);
		}
		if (ids.isEmpty()) return;
		JsonObject body = new JsonObject();
		body.add("ids", Json.array(ids));
		body.addProperty("client", "mod");
		Mythos.backend().post("/api/alerts/ack", body, j -> {});
	}
}
