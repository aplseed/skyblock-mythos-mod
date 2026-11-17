package dev.aplseed.mythos.acc;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.aplseed.mythos.Mythos;
import dev.aplseed.mythos.net.Http;
import dev.aplseed.mythos.net.Json;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;

/**
 * Loads what the accessory calculator needs, straight from public sources (no
 * skyblock-mythos server or login involved):
 * the accessory list, the upgrade lines, bazaar and lowest-BIN prices, and
 * crafting recipes for accessories that upgrade from a lower tier. The item
 * list and recipes are cached in config/mythos/cache.
 */
public final class AccData {
	static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
	static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
	static final String NEU = "https://raw.githubusercontent.com/NotEnoughUpdates/NotEnoughUpdates-REPO/master/";
	/**
	 * Lowest-BIN lists, all {"ITEM_ID": price}; tried in order, the one that answers is used from
	 * then on. These price crafting ingredients only. They can't say whether something is for
	 * sale now (they keep the last price of items no longer listed), so accessories themselves
	 * are priced from Hypixel's active auctions - see {@link Auctions}.
	 */
	static final String[] LBIN_URLS = {
		"https://moulberry.codes/lowestbin.json",
		"https://hysky.de/api/auctions/lowestbins",
	};

	static final String AUCTIONS_URL = "https://api.hypixel.net/v2/skyblock/auctions?page=";
	private static final long CATALOG_TTL = 24 * 3_600_000L, PRICE_TTL = 5 * 60_000L, RECIPE_TTL = 7 * 24 * 3_600_000L;
	/** the auction list is ~40 requests of a megabyte each: refreshed less often, and only while the calculator is in use */
	private static final long BIN_TTL = 10 * 60_000L;
	private static final long RETRY = 30_000;
	private static final int PARALLEL = 6;

	private static volatile Catalog catalog;
	private static volatile Prices prices = Prices.empty();
	private static final Map<String, Prices.Recipe> recipes = new ConcurrentHashMap<>();
	private static long catalogAt, pricesAt, catalogTry, pricesTry, binsAt, binsTry;
	private static boolean binsBusy;
	private static int binPagesLeft, binScan;
	private static String binError;
	private static Map<String, Double> lbinNow = Map.of(), bazaarNow = Map.of(), binsNow = Map.of();
	private static boolean catalogBusy, pricesBusy, recipesBusy, recipesLoaded;
	private static int recipesLeft;
	private static int lbinSource;
	private static String catalogError, priceError;
	private static volatile long version;

	private AccData() {}

	public static Catalog catalog() {
		return catalog;
	}

	public static Prices prices() {
		return prices;
	}

	/** Changes whenever the catalog or prices do. */
	public static long version() {
		return version;
	}

	public static boolean ready() {
		return catalog != null;
	}

	public static boolean priced() {
		return pricesAt > 0 && binsAt > 0;
	}

	public static long pricesAge() {
		return pricesAt == 0 ? -1 : System.currentTimeMillis() - pricesAt;
	}

	/** One line on what's loading or what failed, or null when everything is in. */
	public static String status() {
		if (catalog == null) return catalogError != null ? "Accessory list failed: " + catalogError + " (retrying)" : "Loading accessory list...";
		if (pricesAt == 0) return priceError != null ? "Prices failed: " + priceError + " (retrying)" : "Loading prices...";
		if (binsAt == 0) {
			return binError != null ? "Auction list failed: " + binError + " (retrying)"
					: "Reading active auctions" + (binPagesLeft > 0 ? " (" + binPagesLeft + " pages left)..." : "...");
		}
		if (binError != null) return "Auctions are " + ((System.currentTimeMillis() - binsAt) / 60_000) + " min old: " + binError;
		if (recipesBusy) return "Loading recipes (" + recipesLeft + " left)...";
		return priceError != null ? "Prices are " + (pricesAge() / 60_000) + " min old: " + priceError : null;
	}

	/** Cache files; the accessory list's name changed with SkyBlock 0.27.3 so pre-update copies are refetched. */
	private static Path cache(String name) {
		return Mythos.configDir().resolve("cache").resolve(name);
	}

	private static void onClient(Runnable r) {
		Minecraft.getInstance().execute(r);
	}

	/** Starts whatever is stale. Cheap to call every frame. */
	public static void refresh(boolean force) {
		long now = System.currentTimeMillis();
		if (!catalogBusy && (catalog == null || now - catalogAt > CATALOG_TTL || force) && (force || now - catalogTry > RETRY)) {
			catalogTry = now;
			loadCatalog(force);
		}
		if (!pricesBusy && (now - pricesAt > PRICE_TTL || force) && (force || now - pricesTry > RETRY)) {
			pricesTry = now;
			loadPrices();
		}
		if (!binsBusy && (now - binsAt > BIN_TTL || force) && (force || now - binsTry > RETRY)) {
			binsTry = now;
			loadBins();
		}
		if (catalog != null && pricesAt > 0 && !recipesBusy && !recipesLoaded) loadRecipes();
	}

	// ---- open buy-it-now auctions ----

	private static void loadBins() {
		binsBusy = true;
		binPagesLeft = 0;
		int scan = ++binScan;
		long now = System.currentTimeMillis();
		Map<String, Double> lowest = new ConcurrentHashMap<>();
		Http.stream(AUCTIONS_URL + 0, body -> Auctions.page(body, now, lowest)).whenComplete((pages, err) -> {
			if (err != null || pages <= 1) {
				binsDone(scan, lowest, err);
				return;
			}
			onClient(() -> binPagesLeft = pages - 1);
			Deque<Integer> todo = new ArrayDeque<>();
			for (int p = 1; p < pages; p++) todo.add(p);
			int[] left = {pages - 1};
			Throwable[] failed = {null};
			Runnable[] next = new Runnable[1];
			next[0] = () -> {
				Integer page;
				synchronized (todo) {
					page = failed[0] != null ? null : todo.poll();
				}
				if (page == null) return;
				Http.stream(AUCTIONS_URL + page, body -> Auctions.page(body, now, lowest)).whenComplete((x, e) -> {
					boolean last;
					synchronized (todo) {
						// a page past the end means the list shrank while we read it; anything else fails the scan
						if (e != null && !"HTTP 404".equals(Http.reason(e)) && failed[0] == null) failed[0] = e;
						last = --left[0] == 0 || failed[0] != null;
						if (failed[0] != null) left[0] = -1;
					}
					onClient(() -> binPagesLeft = Math.max(0, binPagesLeft - 1));
					if (last) binsDone(scan, lowest, failed[0]);
					else next[0].run();
				});
			};
			for (int i = 0; i < 4; i++) next[0].run();
		});
	}

	private static void binsDone(int scan, Map<String, Double> lowest, Throwable err) {
		onClient(() -> {
			if (!binsBusy || scan != binScan) return;     // a failed scan reports once, and not into a newer one
			binsBusy = false;
			binPagesLeft = 0;
			if (err != null) {
				binError = Http.reason(err);
				Mythos.LOG.warn("Auction list: {}", err.toString());
				return;
			}
			binsNow = Map.copyOf(lowest);
			binsAt = System.currentTimeMillis();
			binError = null;
			prices = new Prices(lbinNow, bazaarNow, recipes, binsNow);
			version++;
		});
	}

	// ---- accessory list ----

	private static void loadCatalog(boolean force) {
		catalogBusy = true;
		Path file = cache("accessory-list.json");
		if (catalog == null) {
			try {
				if (Files.exists(file)) {
					JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
					catalog = Catalog.build(Json.obj(saved, "items"), Json.obj(saved, "misc"));
					catalogAt = (long) Json.num(saved, "at", 0);
					version++;
					if (!force && System.currentTimeMillis() - catalogAt < CATALOG_TTL) {
						catalogBusy = false;
						return;
					}
				}
			} catch (Exception e) {
				Mythos.LOG.warn("Unreadable {}: {}", file, e.toString());
			}
		}
		CompletableFuture<JsonElement> items = Http.json(ITEMS_URL), misc = Http.json(NEU + "constants/misc.json");
		items.thenCombine(misc, (i, m) -> {
			// keep only what the catalog reads, so the cache file stays small
			JsonObject slimItems = new JsonObject();
			JsonArray arr = new JsonArray();
			for (JsonObject it : Json.objs(i.getAsJsonObject(), "items")) {
				if (!"ACCESSORY".equals(Json.str(it, "category", ""))) continue;
				JsonObject s = new JsonObject();
				for (String k : new String[] {"id", "name", "category", "tier", "requirements", "soulbound", "origin",
						"rift_transferrable", "can_recombobulate"}) {
					if (it.has(k)) s.add(k, it.get(k));
				}
				arr.add(s);
			}
			slimItems.add("items", arr);
			JsonObject slimMisc = new JsonObject();
			slimMisc.add("talisman_upgrades", Json.obj(m.getAsJsonObject(), "talisman_upgrades"));
			slimMisc.add("ignored_talisman", Json.array(Json.strs(m.getAsJsonObject(), "ignored_talisman")));
			Catalog built = Catalog.build(slimItems, slimMisc);
			if (built.byId.isEmpty()) throw new IllegalStateException("no accessories in the item list");
			JsonObject out = new JsonObject();
			out.addProperty("at", System.currentTimeMillis());
			out.add("items", slimItems);
			out.add("misc", slimMisc);
			write(file, out);
			return built;
		}).whenComplete((built, err) -> onClient(() -> {
			catalogBusy = false;
			if (err != null) {
				catalogError = Http.reason(err);
				Mythos.LOG.warn("Accessory list: {}", err.toString());
				return;
			}
			catalog = built;
			catalogAt = System.currentTimeMillis();
			catalogError = null;
			recipesLoaded = false;
			version++;
		}));
	}

	private static void write(Path file, JsonObject json) {
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, json.toString());
		} catch (Exception e) {
			Mythos.LOG.warn("Couldn't write {}: {}", file, e.toString());
		}
	}

	// ---- prices ----

	private static void loadPrices() {
		pricesBusy = true;
		CompletableFuture<Map<String, Double>> bazaar = Http.json(BAZAAR_URL).thenApply(j -> {
			Map<String, Double> m = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : Json.obj(j.getAsJsonObject(), "products").entrySet()) {
				if (!e.getValue().isJsonObject()) continue;
				// quick_status.buyPrice: what an instant buy pays
				double buy = Json.num(Json.obj(e.getValue().getAsJsonObject(), "quick_status"), "buyPrice", 0);
				if (buy > 0) m.put(e.getKey(), buy);
			}
			return m;
		});
		CompletableFuture<Map<String, Double>> lbin = new CompletableFuture<>();
		lbin(0, lbin, null);
		bazaar.thenCombine(lbin, (b, l) -> new Map[] {b, l}).whenComplete((maps, err) -> onClient(() -> {
			pricesBusy = false;
			if (err != null) {
				priceError = Http.reason(err);
				Mythos.LOG.warn("Prices: {}", err.toString());
				return;
			}
			@SuppressWarnings("unchecked") Map<String, Double> b = maps[0], l = maps[1];
			lbinNow = Map.copyOf(l);
			bazaarNow = Map.copyOf(b);
			prices = new Prices(lbinNow, bazaarNow, recipes, binsNow);
			pricesAt = System.currentTimeMillis();
			priceError = null;
			version++;
		}));
	}

	private static void lbin(int attempt, CompletableFuture<Map<String, Double>> out, Throwable last) {
		if (attempt >= LBIN_URLS.length) {
			out.completeExceptionally(last);
			return;
		}
		int source = (lbinSource + attempt) % LBIN_URLS.length;
		Http.json(LBIN_URLS[source]).thenApply(j -> {
			Map<String, Double> m = new HashMap<>();
			for (Map.Entry<String, JsonElement> e : j.getAsJsonObject().entrySet()) {
				JsonElement v = e.getValue();
				if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber() && v.getAsDouble() > 0) m.put(e.getKey(), v.getAsDouble());
			}
			if (m.size() < 100) throw new IllegalStateException("empty price list");
			return m;
		}).whenComplete((m, err) -> {
			if (err == null) {
				lbinSource = source;
				out.complete(m);
			} else {
				Mythos.LOG.debug("lowest BIN from {}: {}", LBIN_URLS[source], err.toString());
				lbin(attempt + 1, out, err);
			}
		});
	}

	// ---- recipes ----

	/**
	 * The recipe in a NEU item file: "recipe": {"A1": "ENCHANTED_FEATHER:32", ...}, with
	 * "crafttext": "Requires: Feather IX" and "slayer_req": "WOLF_5" saying what unlocks it.
	 * No parts if the item isn't crafted.
	 */
	static Prices.Recipe recipe(JsonObject item) {
		String needs = Json.str(item, "crafttext", "").replaceFirst("^Requires:\\s*", "").trim();
		String slayer = Json.str(item, "slayer_req", "");
		int cut = slayer.lastIndexOf('_');
		String key = null;
		int level = 0;
		if (cut > 0) {
			try {
				level = Integer.parseInt(slayer.substring(cut + 1));
				key = "slayer_" + slayer.substring(0, cut).toLowerCase(java.util.Locale.ROOT);
			} catch (NumberFormatException ignored) {
				// not "BOSS_LEVEL": only the text is kept
			}
		}
		return new Prices.Recipe(Map.copyOf(parts(item)), needs.isEmpty() ? null : needs, key, level);
	}

	private static Map<String, Integer> parts(JsonObject item) {
		JsonObject grid = Json.obj(item, "recipe");
		if (grid.size() == 0) {
			for (JsonObject r : Json.objs(item, "recipes")) {
				if ("crafting".equals(Json.str(r, "type", "crafting"))) {
					grid = r;
					break;
				}
			}
		}
		Map<String, Integer> out = new HashMap<>();
		for (String row : new String[] {"A", "B", "C"}) {
			for (int col = 1; col <= 3; col++) {
				String cell = Json.str(grid, row + col, "");
				if (cell.isEmpty()) continue;
				int colon = cell.lastIndexOf(':');
				String id = colon < 0 ? cell : cell.substring(0, colon);
				int n = 1;
				try {
					if (colon >= 0) n = (int) Math.round(Double.parseDouble(cell.substring(colon + 1)));
				} catch (NumberFormatException ignored) {
					// a bare id
				}
				// NEU writes damage values as INK_SACK-4, the bazaar as INK_SACK:4
				out.merge(id.replace('-', ':'), Math.max(1, n), Integer::sum);
			}
		}
		return out;
	}

	/**
	 * Recipes matter where the player may hold an ingredient (higher tiers of a line) or
	 * where no auction exists, so only those files are fetched - a few hundred small ones,
	 * once a week.
	 */
	private static void loadRecipes() {
		recipesBusy = true;
		Path file = cache("recipes.json");
		long savedAt = 0;
		try {
			if (recipes.isEmpty() && Files.exists(file)) {
				JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
				savedAt = (long) Json.num(saved, "at", 0);
				for (Map.Entry<String, JsonElement> e : Json.obj(saved, "recipes").entrySet()) {
					JsonObject r = e.getValue().getAsJsonObject();
					Map<String, Integer> parts = new HashMap<>();
					for (Map.Entry<String, JsonElement> in : Json.obj(r, "parts").entrySet()) parts.put(in.getKey(), in.getValue().getAsInt());
					recipes.put(e.getKey(), new Prices.Recipe(Map.copyOf(parts), Json.str(r, "needs", null), Json.str(r, "slayer", null), (int) Json.num(r, "level", 0)));
				}
			}
		} catch (Exception e) {
			Mythos.LOG.warn("Unreadable {}: {}", file, e.toString());
		}
		boolean stale = System.currentTimeMillis() - savedAt > RECIPE_TTL && savedAt > 0;
		Catalog cat = catalog;
		Prices p = prices;
		Deque<String> todo = new ArrayDeque<>();
		for (Catalog.Acc a : cat.byId.values()) {
			if (!a.obtainable()) continue;
			boolean wanted = cat.families.get(a.family()).size() > 1 || Double.isNaN(p.ah(a.id()));
			if (wanted && (stale || !recipes.containsKey(a.id()))) todo.add(a.id());
		}
		if (todo.isEmpty()) {
			recipesBusy = false;
			recipesLoaded = true;
			version++;
			return;
		}
		recipesLeft = todo.size();
		int[] failed = {0};
		Consumer<Void>[] next = new Consumer[1];
		next[0] = v -> {
			String id;
			synchronized (todo) {
				id = todo.poll();
			}
			if (id == null) return;
			Http.json(NEU + "items/" + id + ".json").whenComplete((j, err) -> {
				// a 404 is an accessory NEU doesn't list: remember it as "no recipe"
				if (err == null && j.isJsonObject()) recipes.put(id, recipe(j.getAsJsonObject()));
				else if (err != null && "HTTP 404".equals(Http.reason(err))) recipes.put(id, Prices.Recipe.of(Map.of()));
				else failed[0]++;
				onClient(() -> {
					if (--recipesLeft <= 0) recipesDone(file, failed[0]);
				});
				next[0].accept(null);
			});
		};
		for (int i = 0; i < PARALLEL; i++) next[0].accept(null);
	}

	private static void recipesDone(Path file, int failed) {
		recipesBusy = false;
		recipesLoaded = true;     // failures are retried with the next catalog refresh, not in a loop
		if (failed > 0) Mythos.LOG.warn("{} accessory recipes didn't load", failed);
		JsonObject all = new JsonObject();
		for (Map.Entry<String, Prices.Recipe> e : recipes.entrySet()) {
			JsonObject parts = new JsonObject();
			e.getValue().parts().forEach(parts::addProperty);
			JsonObject r = new JsonObject();
			r.add("parts", parts);
			if (e.getValue().needs() != null) r.addProperty("needs", e.getValue().needs());
			if (e.getValue().slayerKey() != null) {
				r.addProperty("slayer", e.getValue().slayerKey());
				r.addProperty("level", e.getValue().slayerLevel());
			}
			all.add(e.getKey(), r);
		}
		JsonObject out = new JsonObject();
		out.addProperty("at", System.currentTimeMillis());
		out.add("recipes", all);
		CompletableFuture.runAsync(() -> write(file, out));
		version++;
	}

	/** For tests: use this data instead of the network. */
	public static void set(Catalog c, Prices p) {
		catalog = c;
		prices = p;
		catalogAt = pricesAt = binsAt = System.currentTimeMillis();
		recipesLoaded = true;
		version++;
	}
}
