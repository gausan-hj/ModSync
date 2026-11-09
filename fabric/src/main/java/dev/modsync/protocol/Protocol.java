package dev.modsync.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Binary framing for {@link Message}. All multi-byte values are big-endian, matching
 * {@link DataOutputStream}.
 */
public final class Protocol {
	public static final int MAGIC = 0x4D53594E; // MSYN
	public static final short VERSION = 3;

	/** Defensive upper bound for a single custom payload. */
	public static final int MAX_FRAME = 1_000_000;
	public static final int MAX_CHUNK = 64 * 1024;
	public static final int MAX_FILES = 4096;
	public static final int MAX_MANIFEST_JSON = 900_000;

	private Protocol() {
	}

	public static byte[] encode(Message message) throws ProtocolException {
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream(256);
			DataOutputStream data = new DataOutputStream(out);
			data.writeInt(MAGIC);
			data.writeShort(VERSION);
			data.writeByte(message.type().code());

			switch (message) {
				case Message.Manifest m -> {
					writeString(data, m.nonce());
					writeBytes(data, m.json(), MAX_MANIFEST_JSON);
				}
				case Message.Ready m -> writeString(data, m.nonce());
				case Message.Needed m -> {
					writeString(data, m.nonce());
					writeFiles(data, m.files());
				}
				case Message.Request m -> {
					writeString(data, m.nonce());
					writeFiles(data, m.files());
				}
				case Message.Ping m -> writeString(data, m.nonce());
				case Message.Cancel m -> writeString(data, limit(m.reason(), 2048));
				case Message.FileStart m -> {
					data.writeInt(m.index());
					data.writeInt(m.total());
					writeString(data, limit(m.file(), 512));
					data.writeLong(m.size());
				}
				case Message.FileChunk m -> {
					data.writeInt(m.index());
					data.writeLong(m.offset());
					writeBytes(data, m.data(), MAX_CHUNK);
				}
				case Message.FileEnd m -> {
					data.writeInt(m.index());
					writeString(data, limit(m.sha256(), 128));
					data.writeBoolean(m.ok());
					writeString(data, limit(m.error(), 2048));
				}
				case Message.Complete m -> {
					data.writeInt(m.files());
					data.writeLong(m.bytes());
				}
				case Message.Installed m -> {
					writeString(data, m.nonce());
					data.writeInt(m.files());
					data.writeLong(m.bytes());
				}
				case Message.Error m -> {
					data.writeInt(m.code());
					writeString(data, limit(m.message(), 2048));
				}
			}

			data.flush();
			if (out.size() > MAX_FRAME) {
				throw new ProtocolException("frame too large: " + out.size());
			}
			return out.toByteArray();
		} catch (IOException e) {
			throw new ProtocolException("failed to encode " + message.type(), e);
		}
	}

	public static Message decode(byte[] frame) throws ProtocolException {
		if (frame == null || frame.length < 7 || frame.length > MAX_FRAME) {
			throw new ProtocolException("invalid frame length");
		}

		try {
			DataInputStream data = new DataInputStream(new ByteArrayInputStream(frame));
			if (data.readInt() != MAGIC) {
				throw new ProtocolException("bad magic");
			}
			short version = data.readShort();
			if (version != VERSION) {
				throw new ProtocolException("unsupported protocol version " + version);
			}
			int typeCode = data.readUnsignedByte();
			Message.Type type = Message.Type.byCode(typeCode);
			if (type == null) {
				throw new ProtocolException("unknown message type " + typeCode);
			}

			return switch (type) {
				case MANIFEST -> new Message.Manifest(readString(data, 512), readBytes(data, MAX_MANIFEST_JSON));
				case READY -> new Message.Ready(readString(data, 512));
				case NEEDED -> new Message.Needed(readString(data, 512), readFiles(data));
				case REQUEST -> new Message.Request(readString(data, 512), readFiles(data));
				case PING -> new Message.Ping(readString(data, 512));
				case CANCEL -> new Message.Cancel(readString(data, 2048));
				case FILE_START -> new Message.FileStart(data.readInt(), data.readInt(), readString(data, 512), data.readLong());
				case FILE_CHUNK -> new Message.FileChunk(data.readInt(), data.readLong(), readBytes(data, MAX_CHUNK));
				case FILE_END -> new Message.FileEnd(data.readInt(), readString(data, 128), data.readBoolean(), readString(data, 2048));
				case COMPLETE -> new Message.Complete(data.readInt(), data.readLong());
				case ERROR -> new Message.Error(data.readInt(), readString(data, 2048));
				case INSTALLED -> new Message.Installed(readString(data, 512), data.readInt(), data.readLong());
			};
		} catch (EOFException e) {
			throw new ProtocolException("truncated frame", e);
		} catch (IOException e) {
			throw new ProtocolException("malformed frame", e);
		}
	}

	private static void writeFiles(DataOutputStream data, List<String> files) throws IOException, ProtocolException {
		if (files.size() > MAX_FILES) {
			throw new ProtocolException("too many files: " + files.size());
		}
		data.writeInt(files.size());
		for (String file : files) {
			writeString(data, limit(file, 512));
		}
	}

	private static List<String> readFiles(DataInputStream data) throws IOException, ProtocolException {
		int count = data.readInt();
		if (count < 0 || count > MAX_FILES) {
			throw new ProtocolException("bad file count " + count);
		}
		List<String> files = new ArrayList<>(Math.min(count, 256));
		for (int i = 0; i < count; i++) {
			files.add(readString(data, 512));
		}
		return List.copyOf(files);
	}

	private static void writeBytes(DataOutputStream data, byte[] bytes, int max) throws IOException, ProtocolException {
		if (bytes == null || bytes.length > max) {
			throw new ProtocolException("byte array too large");
		}
		data.writeInt(bytes.length);
		data.write(bytes);
	}

	private static byte[] readBytes(DataInputStream data, int max) throws IOException, ProtocolException {
		int length = data.readInt();
		if (length < 0 || length > max) {
			throw new ProtocolException("bad byte array length " + length);
		}
		byte[] bytes = data.readNBytes(length);
		if (bytes.length != length) {
			throw new EOFException();
		}
		return bytes;
	}

	private static void writeString(DataOutputStream data, String value) throws IOException {
		data.writeUTF(value);
	}

	private static String readString(DataInputStream data, int max) throws IOException, ProtocolException {
		String value = data.readUTF();
		if (value.length() > max) {
			throw new ProtocolException("string too long");
		}
		return value;
	}

	private static String limit(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

	public static String describe(byte[] data) {
		if (data == null) {
			return "null";
		}
		return data.length + " bytes";
	}

	public static String utf8(byte[] data) {
		return new String(data, StandardCharsets.UTF_8);
	}

	public static final class ProtocolException extends Exception {
		public ProtocolException(String message) {
			super(message);
		}

		public ProtocolException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
