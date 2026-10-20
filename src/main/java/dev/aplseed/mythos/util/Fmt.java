package dev.aplseed.mythos.util;

import java.util.Locale;

public final class Fmt {
	private Fmt() {}

	/** 950, 12.3k, 4.56M, 1.2B */
	public static String coins(double v) {
		double a = Math.abs(v);
		String s;
		if (a >= 1e9) s = trim(v / 1e9) + "B";
		else if (a >= 1e6) s = trim(v / 1e6) + "M";
		else if (a >= 1e3) s = trim(v / 1e3) + "k";
		else s = String.valueOf(Math.round(v));
		return s;
	}

	public static String signed(double v) {
		return (v >= 0 ? "+" : "") + coins(v);
	}

	/** 0.081 -> "+8.1%" */
	public static String pct(double frac) {
		return String.format(Locale.ROOT, "%+.1f%%", frac * 100);
	}

	public static String hours(double h) {
		if (h < 1) return Math.round(h * 60) + "m";
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
