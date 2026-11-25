package online.farmsteadminecraft.bombrun.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import online.farmsteadminecraft.bombrun.BombRun;

/** Server to clients: the raid is over or cancelled; the planes vanish if still in the sky. */
public record RaidEndPayload(int raidId, boolean cancelled) implements CustomPacketPayload {
    public static final Type<RaidEndPayload> TYPE = new Type<>(BombRun.id("raid_end"));
    public static final StreamCodec<FriendlyByteBuf, RaidEndPayload> CODEC =
        StreamCodec.of(RaidEndPayload::write, RaidEndPayload::read);

    private static void write(FriendlyByteBuf buf, RaidEndPayload p) {
        buf.writeVarInt(p.raidId);
        buf.writeBoolean(p.cancelled);
    }

    private static RaidEndPayload read(FriendlyByteBuf buf) {
        return new RaidEndPayload(buf.readVarInt(), buf.readBoolean());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
