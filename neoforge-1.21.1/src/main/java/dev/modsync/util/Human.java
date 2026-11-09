package dev.modsync.util;

import java.util.Locale;

public final class Human {
	private Human() {
	}

	public static String bytes(long bytes) {
		if (bytes < 0) {
			return "?";
		}
		if (bytes < 1024) {
			return bytes + " B";
		}
		double value = bytes;
		String[] units = {"KB", "MB", "GB", "TB"};
		for (String unit : units) {
			value /= 1024.0;
			if (value < 1024.0 || unit.equals("TB")) {
				return String.format(Locale.ROOT, "%.1f %s", value, unit);
			}
		}
		return bytes + " B";
	}

	public static String percent(long current, long total) {
		if (total <= 0) {
			return "0%";
		}
		return Math.min(100, (int) (current * 100 / total)) + "%";
	}
}
