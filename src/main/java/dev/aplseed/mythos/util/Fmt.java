package dev.aplseed.mythos.util;

import java.util.Locale;

public final class Fmt {
	private Fmt() {}

	/** 950, 12.3k, 4.56M, 1.2B */
	public static String coins(double v) {
		if (Double.isNaN(v) || Double.isInfinite(v)) return "?";
		double a = Math.abs(v);
		// thresholds sit where the rounded figure rolls over, so 999,960 reads "1M", not "1000k"
		if (a >= 999.5e6) return trim(v / 1e9) + "B";
		if (a >= 999.5e3) return trim(v / 1e6) + "M";
		if (a >= 999.5) return trim(v / 1e3) + "k";
		return String.valueOf(Math.round(v));
	}

	/** "1,234,567" */
	public static String full(double v) {
		return String.format(Locale.ROOT, "%,d", Math.round(v));
	}

	/** "50m", "1.5b", "750k", "2,000,000" -> coins; -1 if it isn't an amount. */
	public static double parseCoins(String s) {
		String t = s.trim().toLowerCase(Locale.ROOT).replace(",", "").replace("_", "").replace(" ", "");
		if (t.isEmpty()) return -1;
		double mult = 1;
		char last = t.charAt(t.length() - 1);
		if (last == 'k') mult = 1e3;
		else if (last == 'm') mult = 1e6;
		else if (last == 'b') mult = 1e9;
		if (mult != 1) t = t.substring(0, t.length() - 1);
		try {
			double v = Double.parseDouble(t) * mult;
			return v >= 0 && !Double.isInfinite(v) && !Double.isNaN(v) ? Math.floor(v) : -1;
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static String signed(double v) {
		return (v >= 0 ? "+" : "") + coins(v);
	}

	/** 0.081 -> "+8.1%" */
	public static String pct(double frac) {
		return String.format(Locale.ROOT, "%+.1f%%", frac * 100);
	}

	public static String hours(double h) {
		if (Math.round(h * 60) < 60) return Math.round(h * 60) + "m";
		if (h < 48) return trim(h) + "h";
		return trim(h / 24) + "d";
	}

	private static String trim(double v) {
		double a = Math.abs(v);
		String s = String.format(Locale.ROOT, a >= 100 ? "%.0f" : a >= 10 ? "%.1f" : "%.2f", v);
		if (s.contains(".")) s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
		return s;
	}
}
