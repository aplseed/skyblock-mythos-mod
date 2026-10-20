package dev.aplseed.mythos.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

/** Null-tolerant accessors: backend fields are often null or missing. */
public final class Json {
	private Json() {}

	private static JsonElement el(JsonObject o, String k) {
		if (o == null) return null;
		JsonElement e = o.get(k);
		return e == null || e.isJsonNull() ? null : e;
	}

	public static String str(JsonObject o, String k, String def) {
		JsonElement e = el(o, k);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : def;
	}

	public static double num(JsonObject o, String k, double def) {
		JsonElement e = el(o, k);
		try {
			return e != null && e.isJsonPrimitive() ? e.getAsDouble() : def;
		} catch (NumberFormatException ex) {
			return def;
		}
	}

	public static boolean bool(JsonObject o, String k) {
		JsonElement e = el(o, k);
		return e != null && e.isJsonPrimitive() && e.getAsBoolean();
	}

	public static JsonObject obj(JsonObject o, String k) {
		JsonElement e = el(o, k);
		return e != null && e.isJsonObject() ? e.getAsJsonObject() : new JsonObject();
	}

	public static List<JsonObject> objs(JsonObject o, String k) {
		JsonElement e = el(o, k);
		List<JsonObject> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) if (x.isJsonObject()) out.add(x.getAsJsonObject());
		}
		return out;
	}

	public static List<String> strs(JsonObject o, String k) {
		JsonElement e = el(o, k);
		List<String> out = new ArrayList<>();
		if (e != null && e.isJsonArray()) {
			for (JsonElement x : e.getAsJsonArray()) if (x.isJsonPrimitive()) out.add(x.getAsString());
		}
		return out;
	}

	public static JsonArray array(Iterable<String> items) {
		JsonArray a = new JsonArray();
		for (String s : items) a.add(s);
		return a;
	}
}
