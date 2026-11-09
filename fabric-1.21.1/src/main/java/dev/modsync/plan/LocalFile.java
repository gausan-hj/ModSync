package dev.modsync.plan;

import java.nio.file.Path;

/** A resource-pack or shader-pack zip already present on the client. */
public record LocalFile(String kind, String name, Path path, long size, String sha256) {
	public String key() {
		return kind + "/" + name;
	}
}
