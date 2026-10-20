package dev.aplseed.mythos;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
		c.save();
		return c;
	}

	public void save() {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this));
		} catch (IOException e) {
			Mythos.LOG.warn("Couldn't save {}: {}", path, e.toString());
		}
	}
}
