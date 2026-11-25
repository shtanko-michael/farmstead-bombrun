package online.farmsteadminecraft.bombrun.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Sending the mod's packets; each loader installs its implementation. The payloads themselves are vanilla
 * {@link CustomPacketPayload} records, the same on every loader (PLAN.md §5.10).
 */
public interface Net {
    Holder HOLDER = new Holder();

    static Net get() {
        return HOLDER.net;
    }

    void toServer(CustomPacketPayload payload);

    void toPlayer(ServerPlayer player, CustomPacketPayload payload);

    void toDimension(ServerLevel level, CustomPacketPayload payload);

    final class Holder {
        private Net net;

        public void install(Net net) {
            if (this.net != null) throw new IllegalStateException("Net already installed");
            this.net = net;
        }
    }
}
