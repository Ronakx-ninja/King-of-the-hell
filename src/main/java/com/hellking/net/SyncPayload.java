package com.hellking.net;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> Client. state: 0 normal, 1 transforming, 2 hell king. flying: 1/0 */
public record SyncPayload(int entityId, int state, int flying) implements CustomPayload {
    public static final CustomPayload.Id<SyncPayload> ID = new CustomPayload.Id<>(Identifier.of("hellking", "sync"));
    public static final PacketCodec<RegistryByteBuf, SyncPayload> CODEC = PacketCodec.tuple(
            PacketCodecs.VAR_INT, SyncPayload::entityId,
            PacketCodecs.VAR_INT, SyncPayload::state,
            PacketCodecs.VAR_INT, SyncPayload::flying,
            SyncPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
