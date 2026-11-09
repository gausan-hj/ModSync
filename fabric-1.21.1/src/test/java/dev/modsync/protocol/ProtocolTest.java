package dev.modsync.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolTest {
	@Test
	void roundTripsManifest() throws Exception {
		byte[] json = "{\"files\":[]}".getBytes();
		Message.Manifest original = new Message.Manifest("nonce", json);
		Message decoded = Protocol.decode(Protocol.encode(original));

		Message.Manifest manifest = (Message.Manifest) decoded;
		assertEquals("nonce", manifest.nonce());
		assertArrayEquals(json, manifest.json());
	}

	@Test
	void roundTripsRequestAndChunk() throws Exception {
		Message.Request request = new Message.Request("n", List.of("a.jar", "b.jar"));
		Message.Request decoded = (Message.Request) Protocol.decode(Protocol.encode(request));
		assertEquals(List.of("a.jar", "b.jar"), decoded.files());

		byte[] data = new byte[Protocol.MAX_CHUNK];
		Message.FileChunk chunk = new Message.FileChunk(3, 1234L, data);
		Message.FileChunk chunk2 = (Message.FileChunk) Protocol.decode(Protocol.encode(chunk));
		assertEquals(3, chunk2.index());
		assertEquals(1234L, chunk2.offset());
		assertArrayEquals(data, chunk2.data());
	}

	@Test
	void roundTripsInstalledAcknowledgement() throws Exception {
		Message.Installed installed = new Message.Installed("n", 20, 22719565L);
		Message.Installed decoded = (Message.Installed) Protocol.decode(Protocol.encode(installed));
		assertEquals("n", decoded.nonce());
		assertEquals(20, decoded.files());
		assertEquals(22719565L, decoded.bytes());
	}

	@Test
	void rejectsBadMagicAndVersion() throws Exception {
		byte[] good = Protocol.encode(new Message.Ready("n"));
		byte[] badMagic = good.clone();
		badMagic[0] = 0;
		assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode(badMagic));

		byte[] badVersion = good.clone();
		badVersion[5] = 99;
		assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode(badVersion));
	}

	@Test
	void rejectsOversizedChunk() throws Exception {
		Protocol.ProtocolException error = assertThrows(Protocol.ProtocolException.class,
				() -> Protocol.encode(new Message.FileChunk(0, 0, new byte[Protocol.MAX_CHUNK + 1])));
		assertEquals("byte array too large", error.getMessage());
	}

	@Test
	void rejectsTruncatedFrame() {
		assertThrows(Protocol.ProtocolException.class, () -> Protocol.decode(new byte[] {1, 2, 3}));
	}
}
