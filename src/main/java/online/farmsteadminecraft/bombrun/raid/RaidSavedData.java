package online.farmsteadminecraft.bombrun.raid;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import online.farmsteadminecraft.bombrun.BombRun;

/**
 * {@code world/data/farmstead_bombrun.dat}: the next raid id, raids in the air and the index of undo snapshots
 * (the blocks themselves are in {@code world/data/farmstead_bombrun/snapshots/}, PLAN.md §5.4).
 */
public final class RaidSavedData extends SavedData {
    public static final String NAME = BombRun.MOD_ID;

    int nextId = 1;
    final List<CompoundTag> raids = new ArrayList<>();
    final List<SnapshotEntry> snapshots = new ArrayList<>();

    /** One saved snapshot: which raid, where, which file. */
    public record SnapshotEntry(int raidId, String dimension, int x, int y, int z, int radius, String file, long gameTime,
                                int blocks) {
        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putInt("raid", raidId);
            t.putString("dimension", dimension);
            t.putInt("x", x);
            t.putInt("y", y);
            t.putInt("z", z);
            t.putInt("radius", radius);
            t.putString("file", file);
            t.putLong("time", gameTime);
            t.putInt("blocks", blocks);
            return t;
        }

        static SnapshotEntry load(CompoundTag t) {
            return new SnapshotEntry(t.getInt("raid"), t.getString("dimension"), t.getInt("x"), t.getInt("y"), t.getInt("z"),
                t.getInt("radius"), t.getString("file"), t.getLong("time"), t.getInt("blocks"));
        }
    }

    public RaidSavedData() {
    }

    public static Factory<RaidSavedData> factory() {
        return new Factory<>(RaidSavedData::new, RaidSavedData::load, null);
    }

    static RaidSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        RaidSavedData data = new RaidSavedData();
        data.nextId = Math.max(1, tag.getInt("nextId"));
        for (Tag t : tag.getList("raids", Tag.TAG_COMPOUND)) data.raids.add((CompoundTag) t);
        for (Tag t : tag.getList("snapshots", Tag.TAG_COMPOUND)) data.snapshots.add(SnapshotEntry.load((CompoundTag) t));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("nextId", nextId);
        ListTag list = new ListTag();
        list.addAll(raids);
        tag.put("raids", list);
        ListTag snaps = new ListTag();
        for (SnapshotEntry e : snapshots) snaps.add(e.save());
        tag.put("snapshots", snaps);
        return tag;
    }

    public List<SnapshotEntry> snapshots() {
        return snapshots;
    }
}
