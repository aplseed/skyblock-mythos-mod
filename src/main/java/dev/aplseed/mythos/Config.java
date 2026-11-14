package dev.aplseed.mythos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;

/** Settings in .minecraft/config/mythos.json. Only the session token is stored, never the password. */
public final class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static transient Path path;

	public String backendUrl = "http://127.0.0.1:5658";
	public String username = "";
	public String sessionToken = "";
	public boolean onlyOnHypixel = true;

	public boolean overlays = true;
	public boolean fusionPlanner = true;
	public boolean absorbSync = true;
	public boolean bazaarPanel = true;
	public boolean forgeAdvisor = true;
	public boolean electionPanel = true;
	public boolean sellWarnings = true;
	public boolean shardTracker = true;

	public int forgeSlots = 7;
	public int quickForgeLevel = 0;
	public boolean coleMayor = false;
	public String fusionSellMode = "sell_order";
	public int pureReptileLevel = 0;

	public int hudX = 4;
	public int hudY = 60;

	// accessory calculator
	public boolean accessories = true;
	/** requirement -> your level: "slayer_wolf": 7, "hotm": 5. Missing = not told, treated as locked. */
	public Map<String, Integer> unlocks = new TreeMap<>();
	/** accessory bag upgrades bought from Jacobus (0-99); -1 = unknown, so no slots get planned */
	public int jacobusUpgrades = -1;
	public boolean riftPrism = false;
	/** free bag slots, when the scan counts them wrongly; -1 = count them */
	public int accFreeSlots = -1;
	public String accBudget = "";
	/** price accessories by their recipe when crafting is cheaper than buying */
	public boolean accCraft = true;

	public static Config load(Path file) {
		path = file;
		Config c = null;
		if (Files.exists(file)) {
			try {
				c = GSON.fromJson(Files.readString(file), Config.class);
			} catch (Exception e) {
				Mythos.LOG.warn("Unreadable {}, using defaults: {}", file, e.toString());
			}
		}
		if (c == null) c = new Config();
		c.sanitize();
		c.save();
		return c;
	}

	/** A hand-edited file can null a field or put a setting out of range. */
	private void sanitize() {
		Config d = new Config();
		if (backendUrl == null || backendUrl.isBlank()) backendUrl = d.backendUrl;
		if (username == null) username = "";
		if (sessionToken == null) sessionToken = "";
		if (!"instant_sell".equals(fusionSellMode)) fusionSellMode = "sell_order";
		forgeSlots = Math.max(2, Math.min(7, forgeSlots));
		quickForgeLevel = Math.max(0, Math.min(20, quickForgeLevel));
		pureReptileLevel = Math.max(0, Math.min(20, pureReptileLevel));
		hudX = Math.max(0, hudX);
		hudY = Math.max(0, hudY);
		if (unlocks == null) unlocks = new TreeMap<>();
		unlocks.values().removeIf(v -> v == null);
		jacobusUpgrades = Math.max(-1, Math.min(99, jacobusUpgrades));
		if (accBudget == null) accBudget = "";
	}

	public void save() {
		if (path == null) return;
		try {
			Files.createDirectories(path.getParent());
			// write beside the file, then swap: a crash mid-write can't leave half a config
			Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(this));
			Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Mythos.LOG.warn("Couldn't save {}: {}", path, e.toString());
		}
	}
}
