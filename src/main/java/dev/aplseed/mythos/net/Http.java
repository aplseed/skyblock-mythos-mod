package dev.aplseed.mythos.net;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

/** Public JSON APIs (Hypixel's item list and bazaar, lowest-BIN lists, the NEU repo). */
public final class Http {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(8))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private Http() {}

	/** Something that reads a response body as it arrives. */
	public interface Parser<T> {
		T parse(java.io.Reader body) throws java.io.IOException;
	}

	/** Like {@link #json}, but hands the body to `parser` as a stream instead of building a tree. */
	public static <T> CompletableFuture<T> stream(String url, Parser<T> parser) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(60))
				.header("Accept", "application/json")
				.header("Accept-Encoding", "gzip")
				.header("User-Agent", "mythos-mod")
				.GET().build();
		return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream()).thenApplyAsync(resp -> {
			try (java.io.InputStream raw = resp.body()) {
				if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
				boolean gzip = resp.headers().firstValue("Content-Encoding").map(v -> v.contains("gzip")).orElse(false);
				return parser.parse(new InputStreamReader(gzip ? new GZIPInputStream(raw) : raw, StandardCharsets.UTF_8));
			} catch (java.io.IOException e) {
				throw new IllegalStateException("bad response", e);
			}
		});
	}

	/** Fetches and parses off the client thread; fails on anything but a 200 with JSON in it. */
	public static CompletableFuture<JsonElement> json(String url) {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("Accept", "application/json")
				.header("Accept-Encoding", "gzip")
				.header("User-Agent", "mythos-mod")
				.GET().build();
		return CLIENT.sendAsync(req, HttpResponse.BodyHandlers.ofByteArray()).thenApplyAsync(resp -> {
			if (resp.statusCode() != 200) throw new IllegalStateException("HTTP " + resp.statusCode());
			try {
				byte[] body = resp.body();
				boolean gzip = resp.headers().firstValue("Content-Encoding").map(v -> v.contains("gzip")).orElse(false)
						|| (body.length > 2 && (body[0] & 0xFF) == 0x1F && (body[1] & 0xFF) == 0x8B);
				try (var in = gzip ? new GZIPInputStream(new ByteArrayInputStream(body)) : new ByteArrayInputStream(body)) {
					return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
				}
			} catch (Exception e) {
				throw new IllegalStateException("bad response", e);
			}
		});
	}

	/** "HTTP 525" / "TimeoutException" - short enough for a status line. */
	public static String reason(Throwable t) {
		while (t.getCause() != null && (t instanceof java.util.concurrent.CompletionException || t.getMessage() == null)) t = t.getCause();
		String m = t.getMessage();
		return m != null && m.startsWith("HTTP ") ? m : t.getClass().getSimpleName();
	}
}
