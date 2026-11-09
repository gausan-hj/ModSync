package dev.modsync.plan;

import java.nio.file.Path;

/** A mod that is already installed in the local game instance. */
public record LocalMod(String id, String version, String name, String env, Path path) {
	public boolean hasJar() {
		return path != null;
	}
}
