package online.farmsteadminecraft.bombrun.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import online.farmsteadminecraft.bombrun.BombRun;

/**
 * Client to server: the player confirmed a target with the remote. Everything here is a wish; the server
 * checks it all again (PLAN.md §5.5). {@code pattern} is a {@link online.farmsteadminecraft.bombrun.strike.Pattern} name.
 */
public record RequestStrikePayload(double x, double y, double z, float radius, float heading, int planes, String pattern)
    implements CustomPacketPayload {

    public static final Type<RequestStrikePayload> TYPE = new Type<>(BombRun.id("request_strike"));
    public static final StreamCodec<FriendlyByteBuf, RequestStrikePayload> CODEC =
        StreamCodec.of(RequestStrikePayload::write, RequestStrikePayload::read);

    private static void write(FriendlyByteBuf buf, RequestStrikePayload p) {
        buf.writeDouble(p.x);
        buf.writeDouble(p.y);
        buf.writeDouble(p.z);
        buf.writeFloat(p.radius);
        buf.writeFloat(p.heading);
        buf.writeVarInt(p.planes);
        buf.writeUtf(p.pattern, 32);
    }

    private static RequestStrikePayload read(FriendlyByteBuf buf) {
        return new RequestStrikePayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(),
            buf.readFloat(), buf.readVarInt(), buf.readUtf(32));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
