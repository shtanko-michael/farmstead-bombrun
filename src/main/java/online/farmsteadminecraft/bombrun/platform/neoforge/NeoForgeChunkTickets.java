package online.farmsteadminecraft.bombrun.platform.neoforge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.raid.ChunkTickets;

/**
 * Raid target chunks kept loaded with a NeoForge ticket controller. Tickets saved with the world are all
 * dropped on load: a raid that resumes after a restart takes its tickets again.
 */
final class NeoForgeChunkTickets implements ChunkTickets {
    static final TicketController CONTROLLER = new TicketController(BombRun.id("raid_target"),
        (level, helper) -> new java.util.ArrayList<>(helper.getBlockTickets().keySet()).forEach(helper::removeAllTickets));

    static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    @Override
    public void force(ServerLevel level, BlockPos owner, int chunkX, int chunkZ, boolean add) {
        CONTROLLER.forceChunk(level, owner, chunkX, chunkZ, add, true);
    }
}
