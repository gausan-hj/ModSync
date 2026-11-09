package dev.modsync.net;

import dev.modsync.ModSync;
import dev.modsync.protocol.Protocol;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One opaque protocol frame carried in a configuration-phase custom payload. */
public record SyncPayload(byte[] data) implements CustomPacketPayload {
	public static final Type<SyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ModSync.MOD_ID, "sync"));
	public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = CustomPacketPayload.codec(SyncPayload::write, SyncPayload::new);

	public SyncPayload {
		data = data.clone();
	}

	public SyncPayload(FriendlyByteBuf buf) {
		this(buf.readByteArray(Protocol.MAX_FRAME));
	}

	public void write(FriendlyByteBuf buf) {
		buf.writeByteArray(data);
	}

	@Override
	public byte[] data() {
		return data.clone();
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
