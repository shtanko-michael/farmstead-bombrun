package online.farmsteadminecraft.bombrun.raid;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import online.farmsteadminecraft.bombrun.strike.FlightPlan;
import online.farmsteadminecraft.bombrun.undo.RaidSnapshot;

/** One raid on the server: its plan, where it is in the plan, its snapshot. */
public final class Raid {
    public enum Phase {
        /** The snapshot for undo is being taken; nothing flies yet. */
        PREPARING,
        /** The plan runs: bombs drop on their ticks. */
        FLYING
    }

    public final int id;
    public final ResourceKey<Level> dimension;
    public final UUID owner;
    public final String ownerName;
    public final FlightPlan plan;
    public final byte[] planBytes;
    public final int fuseTicks;
    public final long createdTick;

    Phase phase = Phase.PREPARING;
    /** Game time of plan tick 0; set when the raid takes off. */
    long startTick = -1;
    /** Next bomb of the plan not yet due. */
    int nextDrop;
    /** Bombs that are due but wait for the live-bomb limit. */
    final ArrayDeque<Integer> held = new ArrayDeque<>();
    RaidSnapshot.Capture capture;
    boolean undoable;
    boolean ticketsHeld;
    /** Ticket radius in chunks around the target chunk. */
    int ticketChunks;

    Raid(int id, ResourceKey<Level> dimension, UUID owner, String ownerName, FlightPlan plan, byte[] planBytes, int fuseTicks,
         long createdTick) {
        this.id = id;
        this.dimension = dimension;
        this.owner = owner;
        this.ownerName = ownerName;
        this.plan = plan;
        this.planBytes = planBytes;
        this.fuseTicks = fuseTicks;
        this.createdTick = createdTick;
    }

    public Phase phase() {
        return phase;
    }

    public long startTick() {
        return startTick;
    }

    public int dropped() {
        return nextDrop - held.size();
    }

    public BlockPos target() {
        return BlockPos.containing(plan.targetX, plan.targetY, plan.targetZ);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", id);
        tag.putString("dimension", dimension.location().toString());
        if (owner != null) tag.putUUID("owner", owner);
        tag.putString("ownerName", ownerName);
        tag.putByteArray("plan", planBytes);
        tag.putInt("fuse", fuseTicks);
        tag.putLong("created", createdTick);
        tag.putString("phase", phase.name());
        tag.putLong("start", startTick);
        // bombs held back are dropped again after a restart
        tag.putInt("next", nextDrop - held.size());
        tag.putBoolean("undoable", undoable);
        return tag;
    }

    static Raid load(CompoundTag tag) throws IOException {
        byte[] bytes = tag.getByteArray("plan");
        FlightPlan plan = FlightPlan.fromBytes(bytes);
        ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dimension")));
        Raid raid = new Raid(tag.getInt("id"), dim, tag.hasUUID("owner") ? tag.getUUID("owner") : null,
            tag.getString("ownerName"), plan, bytes, tag.getInt("fuse"), tag.getLong("created"));
        raid.phase = "FLYING".equals(tag.getString("phase")) ? Phase.FLYING : Phase.PREPARING;
        raid.startTick = tag.getLong("start");
        raid.nextDrop = tag.getInt("next");
        raid.undoable = tag.getBoolean("undoable");
        return raid;
    }
}
