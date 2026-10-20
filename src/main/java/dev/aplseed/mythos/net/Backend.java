package dev.aplseed.mythos.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aplseed.mythos.Mythos;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * The skyblock-mythos server. Every call is async; callbacks run on the
 * client thread. A 401 clears the session so the status line asks for /myth login.
 */
public final class Backend {
	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(5))
			.build();
	private volatile String lastError;

	public String lastError() {
		return lastError;
	}

	public boolean loggedIn() {
		return !Mythos.config().sessionToken.isEmpty();
	}

	public static String q(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	public void get(String path, Consumer<JsonObject> ok) {
		get(path, ok, null);
	}

	public void get(String path, Consumer<JsonObject> ok, Consumer<String> fail) {
		send(HttpRequest.newBuilder(uri(path)).GET(), ok, fail);
	}

	public void post(String path, JsonObject body, Consumer<JsonObject> ok) {
		post(path, body, ok, null);
	}

	public void post(String path, JsonObject body, Consumer<JsonObject> ok, Consumer<String> fail) {
		send(HttpRequest.newBuilder(uri(path))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())), ok, fail);
	}

	private URI uri(String path) {
		String base = Mythos.config().backendUrl.replaceAll("/+$", "");
		return URI.create(base + path);
	}

	private void send(HttpRequest.Builder req, Consumer<JsonObject> ok, Consumer<String> fail) {
		String token = Mythos.config().sessionToken;
		if (!token.isEmpty()) req.header("Authorization", "Bearer " + token);
		req.timeout(Duration.ofSeconds(30));
		CompletableFuture<HttpResponse<String>> f;
		try {
			f = http.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString());
		} catch (Exception e) {
			report(fail, e.toString());
			return;
		}
		f.whenComplete((resp, err) -> {
			if (err != null) {
				report(fail, "backend unreachable (" + err.getClass().getSimpleName() + ")");
				return;
			}
			if (resp.statusCode() == 404) {
				report(fail, "server has no " + resp.uri().getPath() + " - update skyblock-mythos and restart it");
				return;
			}
			if (resp.statusCode() == 401) {
				Minecraft.getInstance().execute(() -> {
					if (!Mythos.config().sessionToken.isEmpty()) {
						Mythos.config().sessionToken = "";
						Mythos.config().save();
						Mythos.chat("Backend login expired - run /myth login <user> <password>");
					}
				});
				report(fail, "not logged in");
				return;
			}
			JsonObject json;
			try {
				JsonElement el = JsonParser.parseString(resp.body());
				json = el.isJsonObject() ? el.getAsJsonObject() : new JsonObject();
			} catch (Exception e) {
				report(fail, "bad response (HTTP " + resp.statusCode() + ")");
				return;
			}
			if (json.has("ok") && !json.get("ok").getAsBoolean()) {
				report(fail, Json.str(json, "error", "HTTP " + resp.statusCode()));
				return;
			}
			lastError = null;
			Minecraft.getInstance().execute(() -> ok.accept(json));
		});
	}

	private void report(Consumer<String> fail, String msg) {
		lastError = msg;
		Mythos.LOG.debug("backend: {}", msg);
		if (fail != null) Minecraft.getInstance().execute(() -> fail.accept(msg));
	}
}
