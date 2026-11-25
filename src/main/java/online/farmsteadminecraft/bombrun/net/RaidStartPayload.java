package online.farmsteadminecraft.bombrun.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import online.farmsteadminecraft.bombrun.BombRun;

/**
 * Server to the players of a dimension: a raid's whole flight plan in one packet. The client draws the planes
 * from it with no further traffic (PLAN.md §5.1). {@code startTick} is the level's game time of plan tick 0.
 */
public record RaidStartPayload(int raidId, long startTick, byte[] plan) implements CustomPacketPayload {
    public static final Type<RaidStartPayload> TYPE = new Type<>(BombRun.id("raid_start"));
    public static final StreamCodec<FriendlyByteBuf, RaidStartPayload> CODEC =
        StreamCodec.of(RaidStartPayload::write, RaidStartPayload::read);

    private static void write(FriendlyByteBuf buf, RaidStartPayload p) {
        buf.writeVarInt(p.raidId);
        buf.writeLong(p.startTick);
        buf.writeByteArray(p.plan);
    }

    private static RaidStartPayload read(FriendlyByteBuf buf) {
        return new RaidStartPayload(buf.readVarInt(), buf.readLong(), buf.readByteArray());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
