package online.farmsteadminecraft.bombrun.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import online.farmsteadminecraft.bombrun.BombRun;

/**
 * Server to client, once per tick with bombs going off: where they blew up and how hard. Vanilla only tells players
 * within 64 blocks about an explosion, so from the remote's usual distance the town just vanished without a flash
 * or a sound; this carries the blasts out to {@code explosions.effectDistance} for the client's effects.
 *
 * @param xyzp {@code x, y, z, power} per blast
 */
public record BlastsPayload(float[] xyzp) implements CustomPacketPayload {
    public static final Type<BlastsPayload> TYPE = new Type<>(BombRun.id("blasts"));
    public static final StreamCodec<FriendlyByteBuf, BlastsPayload> CODEC = StreamCodec.of(BlastsPayload::write, BlastsPayload::read);
    /** Blasts in one packet at most; a tick never has more under the explosion budget. */
    public static final int MAX = 2048;

    public int count() {
        return xyzp.length / 4;
    }

    private static void write(FriendlyByteBuf buf, BlastsPayload p) {
        int n = Math.min(MAX, p.count());
        buf.writeVarInt(n);
        for (int i = 0; i < n * 4; i++) buf.writeFloat(p.xyzp[i]);
    }

    private static BlastsPayload read(FriendlyByteBuf buf) {
        int n = Math.min(MAX, buf.readVarInt());
        float[] a = new float[n * 4];
        for (int i = 0; i < a.length; i++) a[i] = buf.readFloat();
        return new BlastsPayload(a);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
