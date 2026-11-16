package dev.aplseed.mythos.acc;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Reads Hypixel's list of active auctions (api.hypixel.net/v2/skyblock/auctions, about 40
 * pages of 1000) for the one thing third-party price lists get wrong: whether an accessory
 * can be bought right now. Those lists keep the last price of items that are no longer
 * listed; this keeps only buy-it-now auctions that are still open.
 */
public final class Auctions {
	private Auctions() {}

	/**
	 * Adds the cheapest open accessory BIN per SkyBlock id on one page to `lowest`; returns the
	 * page count the response reports. Streamed: a page is a few megabytes of mostly item lore.
	 */
	public static int page(Reader json, long now, Map<String, Double> lowest) throws IOException {
		int pages = 0;
		JsonReader in = new JsonReader(json);
		in.beginObject();
		while (in.hasNext()) {
			String field = in.nextName();
			if (field.equals("totalPages")) {
				pages = in.nextInt();
			} else if (field.equals("success")) {
				if (!in.nextBoolean()) throw new IOException("the auction list reported a failure");
			} else if (field.equals("auctions")) {
				in.beginArray();
				while (in.hasNext()) auction(in, now, lowest);
				in.endArray();
			} else {
				in.skipValue();
			}
		}
		in.endObject();
		return pages;
	}

	private static void auction(JsonReader in, long now, Map<String, Double> lowest) throws IOException {
		boolean bin = false, claimed = false, accessory = false;
		double price = 0, bid = 0;
		long end = Long.MAX_VALUE;
		String bytes = null;
		in.beginObject();
		while (in.hasNext()) {
			String field = in.nextName();
			if (in.peek() == JsonToken.NULL) {
				in.skipValue();
				continue;
			}
			switch (field) {
				case "bin" -> bin = in.nextBoolean();
				case "claimed" -> claimed = in.nextBoolean();
				case "category" -> accessory = "accessories".equals(in.nextString());
				case "starting_bid" -> price = in.nextDouble();
				case "highest_bid_amount" -> bid = in.nextDouble();
				case "end" -> end = in.nextLong();
				case "item_bytes" -> {
					if (in.peek() == JsonToken.STRING) bytes = in.nextString();
					else in.skipValue();
				}
				default -> in.skipValue();
			}
		}
		in.endObject();
		// a BIN with a bid on it is sold and waiting to be claimed
		if (!bin || !accessory || claimed || bid > 0 || end <= now || price <= 0 || bytes == null) return;
		String id = id(bytes);
		if (id != null) lowest.merge(id, price, Math::min);
	}

	/**
	 * The SkyBlock id inside an auction's item_bytes (base64 of gzipped NBT): the first string
	 * tag named "id". The item's numeric id and enchantment ids are shorts, so they don't match.
	 */
	public static String id(String itemBytes) {
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(itemBytes)))) {
			byte[] nbt = in.readAllBytes();
			for (int i = 0; i + 7 < nbt.length; i++) {
				// TAG_String (8), name length 2, "id", then the value's length and bytes
				if (nbt[i] == 8 && nbt[i + 1] == 0 && nbt[i + 2] == 2 && nbt[i + 3] == 'i' && nbt[i + 4] == 'd') {
					int len = ((nbt[i + 5] & 0xFF) << 8) | (nbt[i + 6] & 0xFF);
					if (len == 0 || i + 7 + len > nbt.length) continue;
					return new String(nbt, i + 7, len, StandardCharsets.UTF_8);
				}
			}
		} catch (IOException | IllegalArgumentException e) {
			// not an item we can read: it just isn't counted
		}
		return null;
	}
}
