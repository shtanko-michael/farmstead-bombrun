package online.farmsteadminecraft.bombrun.raid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Keeps a raid's target chunks loaded while it flies (PLAN.md §5.1); each loader installs its own. */
public interface ChunkTickets {
    Holder HOLDER = new Holder();

    static ChunkTickets get() {
        return HOLDER.tickets;
    }

    /** {@code owner} identifies the raid's tickets (its target block). */
    void force(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add);

    final class Holder {
        private ChunkTickets tickets;

        public void install(ChunkTickets tickets) {
            if (this.tickets != null) throw new IllegalStateException("ChunkTickets already installed");
            this.tickets = tickets;
        }
    }
}
