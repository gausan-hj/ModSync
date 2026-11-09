package dev.modsync.protocol;

import java.util.List;

/**
 * Wire messages for the ModSync configuration-phase protocol.
 *
 * <p>The protocol is deliberately tiny and independent of Minecraft classes so it can be
 * unit tested without launching the game. Every frame starts with a magic number and a
 * protocol version; unknown versions are rejected before any file is touched.</p>
 */
public sealed interface Message {
	Type type();

	/** Server -> client: the list of jars the host is willing to share. */
	record Manifest(String nonce, byte[] json) implements Message {
		public Manifest {
			json = json.clone();
		}

		@Override
		public Type type() {
			return Type.MANIFEST;
		}
	}

	/** Client -> server: the local mod set already matches, continue joining. */
	record Ready(String nonce) implements Message {
		@Override
		public Type type() {
			return Type.READY;
		}
	}

	/** Client -> server: the client needs the listed files and is showing the review screen. */
	record Needed(String nonce, List<String> files) implements Message {
		public Needed {
			files = List.copyOf(files);
		}

		@Override
		public Type type() {
			return Type.NEEDED;
		}
	}

	/** Client -> server: explicit consent to download these files. */
	record Request(String nonce, List<String> files) implements Message {
		public Request {
			files = List.copyOf(files);
		}

		@Override
		public Type type() {
			return Type.REQUEST;
		}
	}

	/** Client -> server: keeps the configuration task alive while the user reads the screen. */
	record Ping(String nonce) implements Message {
		@Override
		public Type type() {
			return Type.PING;
		}
	}

	/** Either direction: abort the current sync. */
	record Cancel(String reason) implements Message {
		@Override
		public Type type() {
			return Type.CANCEL;
		}
	}

	record FileStart(int index, int total, String file, long size) implements Message {
		@Override
		public Type type() {
			return Type.FILE_START;
		}
	}

	record FileChunk(int index, long offset, byte[] data) implements Message {
		public FileChunk {
			data = data.clone();
		}

		@Override
		public Type type() {
			return Type.FILE_CHUNK;
		}
	}

	record FileEnd(int index, String sha256, boolean ok, String error) implements Message {
		@Override
		public Type type() {
			return Type.FILE_END;
		}
	}

	record Complete(int files, long bytes) implements Message {
		@Override
		public Type type() {
			return Type.COMPLETE;
		}
	}

	/** Client -> server: all files were verified and installed; it is safe to close the connection. */
	record Installed(String nonce, int files, long bytes) implements Message {
		@Override
		public Type type() {
			return Type.INSTALLED;
		}
	}

	record Error(int code, String message) implements Message {
		@Override
		public Type type() {
			return Type.ERROR;
		}
	}

	enum Type {
		MANIFEST(1),
		READY(2),
		NEEDED(3),
		REQUEST(4),
		PING(5),
		CANCEL(6),
		FILE_START(10),
		FILE_CHUNK(11),
		FILE_END(12),
		COMPLETE(13),
		ERROR(14),
		INSTALLED(15);

		private final int code;

		Type(int code) {
			this.code = code;
		}

		public int code() {
			return code;
		}

		public static Type byCode(int code) {
			for (Type type : values()) {
				if (type.code == code) {
					return type;
				}
			}
			return null;
		}
	}
}
