package dev.aplseed.mythos.magicgrind;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aplseed.mythos.Mythos;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Talks to a MagicGrind server (github.com/aplseed/skyblock-magicgrind) - separate from the skyblock-mythos
 * backend, with its own address and pairing key. Everything runs off the client thread.
 */
public final class MagicGrindClient {
	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

	private static volatile String status = "Not connected yet";
	private static volatile boolean ok;
	private static volatile String best = null;

	private MagicGrindClient() {}

	public static String status() {
		return status;
	}

	public static boolean ok() {
		return ok;
	}

	/** "Best at your MF: Spooky fishing - 304M/h", from the last successful ping. */
	public static String best() {
		return best;
	}

	public static boolean configured() {
		var c = Mythos.config();
		return c.magicgrind && !c.magicgrindUrl.isBlank() && !c.magicgrindKey.isBlank();
	}

	private static HttpRequest.Builder request(String path) {
		var c = Mythos.config();
		return HttpRequest.newBuilder(URI.create(c.magicgrindUrl.replaceAll("/+$", "") + path))
				.timeout(Duration.ofSeconds(10))
				.header("X-MagicGrind-Key", c.magicgrindKey.trim())
				.header("User-Agent", "mythos-mod");
	}

	public static void send(JsonObject stats) {
		if (!configured()) return;
		HttpRequest req;
		try {
			req = request("/api/mod/stats").header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(stats.toString())).build();
		} catch (IllegalArgumentException e) {
			fail("Bad address");
			return;
		}
		CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> {
			if (err != null) fail(reason(err));
			else if (resp.statusCode() == 401) fail("Wrong pairing key");
			else if (resp.statusCode() != 200) fail("HTTP " + resp.statusCode());
			else {
				ok = true;
				status = "Sent " + java.time.LocalTime.now().withNano(0);
			}
		});
	}

	/** Reports a rare drop and the Magic Find the game applied to it. */
	public static void sendDrop(String item, double magicFind) {
		if (!configured()) return;
		JsonObject j = new JsonObject();
		j.addProperty("item", item);
		j.addProperty("mf", magicFind);
		try {
			HttpRequest req = request("/api/mod/drop").header("Content-Type", "application/json")
					.POST(HttpRequest.BodyPublishers.ofString(j.toString())).build();
			CLIENT.sendAsync(req, HttpResponse.BodyHandlers.discarding());
		} catch (IllegalArgumentException ignored) {
			// bad address: the status line already says so
		}
	}

	/** Checks the address and key, and fetches the best grind for the window. */
	public static void ping() {
		if (!configured()) {
			fail("Set the address and pairing key first");
			return;
		}
		status = "Connecting...";
		HttpRequest req;
		try {
			req = request("/api/mod/ping").GET().build();
		} catch (IllegalArgumentException e) {
			fail("Bad address");
			return;
		}
		CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofString()).whenComplete((resp, err) -> {
			if (err != null) {
				fail(reason(err));
				return;
			}
			if (resp.statusCode() == 401) {
				fail("Wrong pairing key");
				return;
			}
			if (resp.statusCode() != 200) {
				fail("HTTP " + resp.statusCode());
				return;
			}
			try {
				JsonObject j = JsonParser.parseString(resp.body()).getAsJsonObject();
				var top = j.getAsJsonArray("top");
				if (top != null && !top.isEmpty()) {
					JsonObject t = top.get(0).getAsJsonObject();
					best = t.get("name").getAsString() + " - " + dev.aplseed.mythos.util.Fmt.coins(t.get("coins_hour").getAsDouble()) + "/h";
				}
				ok = true;
				status = "Connected";
			} catch (Exception e) {
				fail("Not a MagicGrind server");
			}
		});
	}

	private static void fail(String why) {
		ok = false;
		status = why;
	}

	private static String reason(Throwable t) {
		while (t.getCause() != null) t = t.getCause();
		String m = t.getMessage();
		return t instanceof java.net.ConnectException ? "Can't reach the server" : m != null ? m : t.getClass().getSimpleName();
	}
}
