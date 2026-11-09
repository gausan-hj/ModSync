package dev.modsync.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class Hashes {
	private static final char[] HEX = "0123456789abcdef".toCharArray();

	private Hashes() {
	}

	public static String sha256(Path file) throws IOException {
		MessageDigest digest = newDigest();
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = in.read(buffer)) >= 0) {
				if (read > 0) {
					digest.update(buffer, 0, read);
				}
			}
		}
		return hex(digest.digest());
	}

	public static String sha256(byte[] data) {
		MessageDigest digest = newDigest();
		digest.update(data);
		return hex(digest.digest());
	}

	public static MessageDigest newDigest() {
		try {
			return MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 is required by the JVM", e);
		}
	}

	public static String hex(byte[] bytes) {
		char[] chars = new char[bytes.length * 2];
		for (int i = 0; i < bytes.length; i++) {
			int value = bytes[i] & 0xFF;
			chars[i * 2] = HEX[value >>> 4];
			chars[i * 2 + 1] = HEX[value & 0x0F];
		}
		return new String(chars);
	}
}
