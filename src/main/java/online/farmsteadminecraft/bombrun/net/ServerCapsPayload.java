package online.farmsteadminecraft.bombrun.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import online.farmsteadminecraft.bombrun.BombRun;

/**
 * Server to client, on joining and after {@code /bombrun reload} (PLAN.md §5.10): the server's limits and power
 * levels, so the remote shows the radius, planes and bombs the server will really send, and paints the ring red
 * when the target is out of reach.
 *
 * @param planes  planes per power level, weakest first
 * @param spacing bomb spacing per power level, blocks
 */
public record ServerCapsPayload(int maxRange, int radiusMin, int radiusMax, int maxPlanes, int maxBombs, int defaultPower,
                                int[] planes, float[] spacing) implements CustomPacketPayload {

    public static final Type<ServerCapsPayload> TYPE = new Type<>(BombRun.id("server_caps"));
    public static final StreamCodec<FriendlyByteBuf, ServerCapsPayload> CODEC = StreamCodec.of(ServerCapsPayload::write, ServerCapsPayload::read);
    /** More power levels than this are not sent (a broken config must not make a huge packet). */
    public static final int MAX_LEVELS = 16;

    private static void write(FriendlyByteBuf buf, ServerCapsPayload p) {
        buf.writeVarInt(p.maxRange);
        buf.writeVarInt(p.radiusMin);
        buf.writeVarInt(p.radiusMax);
        buf.writeVarInt(p.maxPlanes);
        buf.writeVarInt(p.maxBombs);
        buf.writeVarInt(p.defaultPower);
        int n = Math.min(MAX_LEVELS, Math.min(p.planes.length, p.spacing.length));
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeVarInt(p.planes[i]);
            buf.writeFloat(p.spacing[i]);
        }
    }

    private static ServerCapsPayload read(FriendlyByteBuf buf) {
        int maxRange = buf.readVarInt(), radiusMin = buf.readVarInt(), radiusMax = buf.readVarInt(), maxPlanes = buf.readVarInt(),
            maxBombs = buf.readVarInt(), defaultPower = buf.readVarInt();
        int n = Math.min(MAX_LEVELS, buf.readVarInt());
        int[] planes = new int[n];
        float[] spacing = new float[n];
        for (int i = 0; i < n; i++) {
            planes[i] = buf.readVarInt();
            spacing[i] = buf.readFloat();
        }
        return new ServerCapsPayload(maxRange, radiusMin, radiusMax, maxPlanes, maxBombs, defaultPower, planes, spacing);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
