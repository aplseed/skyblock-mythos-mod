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

	/** Why a panel has no data: the login prompt, the last failure, or `loading`. */
	public String waiting(String loading) {
		if (!loggedIn()) return "Not logged in - run /myth to log in";
		return lastError != null ? lastError : loading;
	}

	public static String q(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}

	public void get(String path, Consumer<JsonObject> ok) {
		get(path, ok, null);
	}

	public void get(String path, Consumer<JsonObject> ok, Consumer<String> fail) {
		send(path, b -> b.GET(), ok, fail);
	}

	/** Logs in and stores the session token; `ok` gets the account name. */
	public void login(String user, String password, Consumer<String> ok, Consumer<String> fail) {
		JsonObject body = new JsonObject();
		body.addProperty("username", user);
		body.addProperty("password", password);
		body.addProperty("client", "mod");
		Mythos.config().sessionToken = "";
		post("/api/auth/login", body, j -> {
			String token = Json.str(j, "token", "");
			if (token.isEmpty()) {
				fail.accept("the server sent no session token");
				return;
			}
			Mythos.config().sessionToken = token;
			Mythos.config().username = Json.str(Json.obj(j, "user"), "name", user);
			Mythos.config().save();
			ok.accept(Mythos.config().username);
		}, fail);
	}

	/** Ends the session here whether or not the server hears about it. */
	public void logout() {
		if (loggedIn()) post("/api/auth/logout", new JsonObject(), j -> {}, err -> {});
		Mythos.config().sessionToken = "";
		Mythos.config().save();
		lastError = null;
	}

	public void post(String path, JsonObject body, Consumer<JsonObject> ok) {
		post(path, body, ok, null);
	}

	public void post(String path, JsonObject body, Consumer<JsonObject> ok, Consumer<String> fail) {
		send(path, b -> b.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body.toString())), ok, fail);
	}

	private void send(String path, java.util.function.UnaryOperator<HttpRequest.Builder> method, Consumer<JsonObject> ok, Consumer<String> fail) {
		String token = Mythos.config().sessionToken;
		boolean login = path.equals("/api/auth/login");
		CompletableFuture<HttpResponse<String>> f;
		try {
			// a hand-edited address can be anything: a bad one is an error line, not a crash
			String base = Mythos.config().backendUrl.replaceAll("/+$", "");
			HttpRequest.Builder req = method.apply(HttpRequest.newBuilder(URI.create(base + path)));
			if (!token.isEmpty()) req.header("Authorization", "Bearer " + token);
			req.timeout(Duration.ofSeconds(30));
			f = http.sendAsync(req.build(), HttpResponse.BodyHandlers.ofString());
		} catch (IllegalArgumentException e) {
			report(fail, "bad server address \"" + Mythos.config().backendUrl + "\" - set it with /myth url");
			return;
		} catch (Exception e) {
			report(fail, e.toString());
			return;
		}
		f.whenComplete((resp, err) -> {
			if (err != null) {
				Throwable cause = err.getCause() != null ? err.getCause() : err;
				report(fail, "server unreachable at " + Mythos.config().backendUrl + " (" + cause.getClass().getSimpleName() + ")");
				return;
			}
			if (resp.statusCode() == 404) {
				report(fail, "server has no " + resp.uri().getPath() + " - update skyblock-mythos and restart it");
				return;
			}
			if (resp.statusCode() == 401 && login) {
				report(fail, errorIn(resp.body(), "wrong user or password"));
				return;
			}
			if (resp.statusCode() == 401) {
				Minecraft.getInstance().execute(() -> {
					// only the session this request used: a newer login must survive a late 401
					if (!token.isEmpty() && token.equals(Mythos.config().sessionToken)) {
						Mythos.config().sessionToken = "";
						Mythos.config().save();
						Mythos.chat("Server login expired - log in again in /myth");
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
			if ((json.has("ok") && !Json.bool(json, "ok")) || resp.statusCode() >= 400) {
				report(fail, Json.str(json, "error", "HTTP " + resp.statusCode()));
				return;
			}
			lastError = null;
			Minecraft.getInstance().execute(() -> ok.accept(json));
		});
	}

	/** The "error" of a JSON body, or `fallback`. */
	private static String errorIn(String body, String fallback) {
		try {
			JsonElement el = JsonParser.parseString(body);
			return el.isJsonObject() ? Json.str(el.getAsJsonObject(), "error", fallback) : fallback;
		} catch (Exception e) {
			return fallback;
		}
	}

	private void report(Consumer<String> fail, String msg) {
		lastError = msg;
		Mythos.LOG.debug("backend: {}", msg);
		if (fail != null) Minecraft.getInstance().execute(() -> fail.accept(msg));
	}
}
