package online.farmsteadminecraft.bombrun.undo;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import online.farmsteadminecraft.bombrun.payload.Bombs;

/**
 * Puts a snapshot back (PLAN.md §5.4): a slice of blocks per tick, only where they differ, without neighbour
 * updates and without drops; block entities get their saved NBT. At the end items, falling blocks, orbs and
 * leftover bombs in the zone are removed.
 */
public final class Restorer {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    public final int raidId;
    private final ServerLevel level;
    private final int minX, minZ, size;
    private final int[] yMin, yMax;
    private final BlockState[] palette;
    private final int[] states;
    private final Map<Integer, CompoundTag> blockEntities = new HashMap<>();
    private int column;
    private int index;
    private int changed;
    private boolean finished;

    public Restorer(ServerLevel level, int raidId, CompoundTag tag) throws IOException {
        if (tag.getInt("format") != RaidSnapshot.FORMAT) throw new IOException("unknown snapshot format " + tag.getInt("format"));
        this.level = level;
        this.raidId = raidId;
        this.minX = tag.getInt("minX");
        this.minZ = tag.getInt("minZ");
        this.size = tag.getInt("size");
        this.yMin = tag.getIntArray("yMin");
        this.yMax = tag.getIntArray("yMax");
        if (yMin.length != size * size || yMax.length != size * size) throw new IOException("snapshot columns do not match its size");
        ListTag pal = tag.getList("palette", Tag.TAG_COMPOUND);
        this.palette = new BlockState[pal.size()];
        for (int i = 0; i < pal.size(); i++) palette[i] = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), pal.getCompound(i));
        int count = tag.getInt("count");
        this.states = BitPacker.unpack(tag.getLongArray("data"), count, tag.getInt("bits"));
        for (Tag t : tag.getList("blockEntities", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            blockEntities.put(e.getInt("i"), e.getCompound("nbt"));
        }
    }

    public boolean finished() {
        return finished;
    }

    public int changed() {
        return changed;
    }

    /** Restores about {@code budget} blocks; true when all are back and the zone is cleaned up. */
    public boolean step(int budget) {
        if (finished) return true;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int done = 0;
        while (column < size * size && done < budget) {
            int c = column++;
            if (yMax[c] < yMin[c]) continue;
            int x = minX + c / size, z = minZ + c % size;
            LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
            for (int y = yMin[c]; y <= yMax[c]; y++, index++) {
                pos.set(x, y, z);
                BlockState want = palette[states[index]];
                if (chunk.getBlockState(pos) != want) {
                    level.setBlock(pos, want, FLAGS);
                    changed++;
                }
                CompoundTag nbt = blockEntities.get(index);
                if (nbt != null) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be != null) {
                        be.loadWithComponents(nbt, level.registryAccess());
                        be.setChanged();
                        level.sendBlockUpdated(pos, want, want, Block.UPDATE_ALL);
                    }
                }
            }
            done += yMax[c] - yMin[c] + 1;
        }
        if (column >= size * size) {
            cleanUp();
            finished = true;
        }
        return finished;
    }

    private void cleanUp() {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int c = 0; c < yMin.length; c++) {
            if (yMax[c] < yMin[c]) continue;
            lo = Math.min(lo, yMin[c]);
            hi = Math.max(hi, yMax[c]);
        }
        if (lo > hi) return;
        AABB box = new AABB(minX, lo, minZ, minX + size, hi + 32, minZ + size);
        List<Entity> junk = level.getEntitiesOfClass(Entity.class, box, e -> e instanceof ItemEntity
            || e instanceof ExperienceOrb || e instanceof FallingBlockEntity || Bombs.isBomb(e));
        for (Entity e : junk) e.discard();
    }
}
