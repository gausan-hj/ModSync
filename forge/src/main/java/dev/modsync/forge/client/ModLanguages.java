package dev.modsync.forge.client;

/** Supported Minecraft locales for the in-game language button. */
public final class ModLanguages {
	public static final String[] CODES = {
			"en_us", "zh_cn", "ja_jp", "ko_kr", "de_de", "fr_fr", "es_es", "pt_br",
			"it_it", "ru_ru", "tr_tr", "pl_pl", "nl_nl", "th_th", "id_id", "vi_vn"
	};
	public static final String[] NAMES = {
			"English", "中文", "日本語", "한국어", "Deutsch", "Français", "Español", "Português",
			"Italiano", "Русский", "Türkçe", "Polski", "Nederlands", "ไทย", "Bahasa Indonesia", "Tiếng Việt"
	};

	private ModLanguages() {
	}

	public static String next(String current) {
		for (int i = 0; i < CODES.length; i++) {
			if (CODES[i].equals(current)) {
				return CODES[(i + 1) % CODES.length];
			}
		}
		return CODES[0];
	}

	public static String label(String current) {
		for (int i = 0; i < CODES.length; i++) {
			if (CODES[i].equals(current)) {
				return NAMES[(i + 1) % NAMES.length];
			}
		}
		return NAMES[0];
	}
	public static String currentName(String current) {
		for (int i = 0; i < CODES.length; i++) {
			if (CODES[i].equals(current)) {
				return NAMES[i];
			}
		}
		return NAMES[0];
	}

}
