package dev.modsync.forge.net;

import dev.modsync.manifest.SyncManifest;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Plain HTTP client used before Minecraft joins the server. */
public final class SyncHttpClient {
	private static final HttpClient CLIENT = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(4))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	private SyncHttpClient() {
	}

	public static Remote fetch(ServerData server) throws IOException {
		Address address = Address.parse(server.ip);
		Set<Integer> ports = new LinkedHashSet<>();
		ports.add(address.port() + 1);
		ports.add(8710);
		ports.add(address.port() + 2);

		IOException last = null;
		for (int port : ports) {
			if (port <= 0 || port > 65535) {
				continue;
			}
			String base = "http://" + address.hostForUrl() + ":" + port + "/modsync/";
			try {
				SyncManifest manifest = fetchManifest(base);
				if (!manifest.compatible()) {
					throw new IOException("unsupported manifest protocol " + manifest.protocol());
				}
				return new Remote(base, manifest);
			} catch (IOException e) {
				last = e;
			}
		}
		throw new IOException("无法连接房主的 ModSync 同步服务（尝试了游戏端口+1 / 8710）。"
				+ "请确认房主已装 Forge 版 ModSync，并开放了额外的 HTTP 端口。", last);
	}

	private static SyncManifest fetchManifest(String base) throws IOException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(base + "manifest.json"))
				.timeout(Duration.ofSeconds(8))
				.header("Accept", "application/json")
				.GET()
				.build();
		try {
			HttpResponse<byte[]> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofByteArray());
			if (response.statusCode() != 200) {
				throw new IOException("manifest HTTP " + response.statusCode());
			}
			return SyncManifest.fromJsonBytes(response.body());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}

	public static String download(Remote remote, String fileName, Path target, Progress progress) throws IOException {
		Files.createDirectories(target.getParent());
		String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
		HttpRequest request = HttpRequest.newBuilder(URI.create(remote.base() + "files/" + encoded))
				.timeout(Duration.ofMinutes(30))
				.header("Accept", "application/java-archive")
				.GET()
				.build();
		try {
			HttpResponse<InputStream> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
			if (response.statusCode() != 200) {
				throw new IOException("file HTTP " + response.statusCode());
			}
			MessageDigest digest = dev.modsync.util.Hashes.newDigest();
			long received = 0;
			try (InputStream in = response.body(); var out = Files.newOutputStream(target)) {
				byte[] buffer = new byte[64 * 1024];
				int read;
				while ((read = in.read(buffer)) >= 0) {
					if (read == 0) {
						continue;
					}
					out.write(buffer, 0, read);
					digest.update(buffer, 0, read);
					received += read;
					if (progress != null) {
						progress.update(received);
					}
				}
			}
			return dev.modsync.util.Hashes.hex(digest.digest());
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("interrupted", e);
		}
	}

	public interface Progress {
		void update(long bytes);
	}

	public record Remote(String base, SyncManifest manifest) {
	}

	private record Address(String host, int port) {
		private static Address parse(String value) {
			String input = value == null ? "" : value.trim();
			if (input.startsWith("[")) {
				int close = input.indexOf(']');
				if (close > 0) {
					String host = input.substring(1, close);
					int port = 25565;
					if (input.length() > close + 2 && input.charAt(close + 1) == ':') {
						try {
							port = Integer.parseInt(input.substring(close + 2));
						} catch (NumberFormatException ignored) {
						}
					}
					return new Address(host, port);
				}
			}
			int colon = input.lastIndexOf(':');
			if (colon > 0 && input.indexOf(':') == colon) {
				try {
					return new Address(input.substring(0, colon), Integer.parseInt(input.substring(colon + 1)));
				} catch (NumberFormatException ignored) {
				}
			}
			return new Address(input, 25565);
		}

		private String hostForUrl() {
			return host.contains(":") ? "[" + host + "]" : host;
		}
	}
}
