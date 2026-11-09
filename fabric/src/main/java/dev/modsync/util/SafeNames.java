package dev.modsync.util;

import java.util.Locale;
import java.util.Set;

/**
 * Turns an untrusted server-provided file name into a safe file name inside the mods folder.
 * The server never gets to choose a path, only a single jar name.
 */
public final class SafeNames {
	private static final Set<String> WINDOWS_RESERVED = Set.of(
			"CON", "PRN", "AUX", "NUL",
			"COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
			"LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
	);

	private SafeNames() {
	}

	public static String jarName(String proposed, int index) {
		String value = proposed == null ? "" : proposed;
		value = value.replace('\\', '/');
		int slash = value.lastIndexOf('/');
		if (slash >= 0) {
			value = value.substring(slash + 1);
		}

		value = value.replaceAll("[^A-Za-z0-9._+\\-]", "_");
		value = value.replaceAll("^\\.+", "");
		if (value.isBlank()) {
			value = "mod-" + index;
		}
		if (value.length() > 180) {
			value = value.substring(0, 180);
		}
		if (!value.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			value = value + ".jar";
		}

		String stem = value.substring(0, value.length() - 4);
		String upper = stem.toUpperCase(Locale.ROOT);
		if (WINDOWS_RESERVED.contains(upper)) {
			value = "mod-" + value;
		}
		if (value.equals(".jar") || value.isBlank()) {
			value = "mod-" + index + ".jar";
		}
		return value;
	}

	/** Safe name for a resource pack or shader pack; keeps its original extension. */
	public static String assetName(String proposed, int index) {
		String value = proposed == null ? "" : proposed;
		value = value.replace('\\', '/');
		int slash = value.lastIndexOf('/');
		if (slash >= 0) {
			value = value.substring(slash + 1);
		}
		value = value.replaceAll("[^A-Za-z0-9._+\\-]", "_");
		value = value.replaceAll("^\\.+", "");
		if (value.isBlank()) {
			value = "pack-" + index + ".zip";
		}
		if (value.length() > 180) {
			value = value.substring(0, 180);
		}
		if (!value.toLowerCase(Locale.ROOT).endsWith(".zip") && !value.toLowerCase(Locale.ROOT).endsWith(".jar")) {
			value = value + ".zip";
		}
		return value;
	}

	public static String displayName(String fileName) {
		if (fileName == null) {
			return "";
		}
		String cleaned = fileName.replace('\\', '/');
		int slash = cleaned.lastIndexOf('/');
		return slash >= 0 ? cleaned.substring(slash + 1) : cleaned;
	}
}
