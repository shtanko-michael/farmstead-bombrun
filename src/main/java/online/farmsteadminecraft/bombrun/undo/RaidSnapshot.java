package online.farmsteadminecraft.bombrun.undo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The target as it was before a raid (PLAN.md §5.4): a cylinder of "radius + margin" around the target, each
 * column from {@code depth} below the ground to one block above its top. Blocks are a palette plus bit-packed
 * indices, block entities are their full NBT. Taken a slice per tick ({@link Capture#step}) so a big town does
 * not freeze the server.
 */
public final class RaidSnapshot {
    public static final int FORMAT = 1;
    /** How far past the target circle explosions reach, blocks. */
    public static final int MARGIN = 10;

    private RaidSnapshot() {
    }

    /** Taking a snapshot, column by column. */
    public static final class Capture {
        final int centerX, centerZ, minX, minZ, size;
        final long r2;
        final int groundY, depth, maxBlocks;
        final int[] yMin, yMax;
        int[] states = new int[1 << 16];
        int count;
        final Map<BlockState, Integer> palette = new HashMap<>();
        final List<BlockState> paletteList = new ArrayList<>();
        final ListTag blockEntities = new ListTag();
        int column;
        boolean tooBig;

        public Capture(double x, double y, double z, double radius, int depth, int maxBlocks) {
            this.centerX = (int) Math.floor(x);
            this.centerZ = (int) Math.floor(z);
            int reach = (int) Math.ceil(radius) + MARGIN;
            this.r2 = (long) reach * reach;
            this.minX = centerX - reach;
            this.minZ = centerZ - reach;
            this.size = 2 * reach + 1;
            this.groundY = (int) Math.floor(y);
            this.depth = depth;
            this.maxBlocks = maxBlocks;
            this.yMin = new int[size * size];
            this.yMax = new int[size * size];
        }

        public boolean done() {
            return tooBig || column >= size * size;
        }

        /** True when the target was larger than {@code undo.maxBlocks}: the raid flies without undo. */
        public boolean tooBig() {
            return tooBig;
        }

        public int blocks() {
            return count;
        }

        boolean inside(int dx, int dz) {
            long ddx = dx - (centerX - minX), ddz = dz - (centerZ - minZ);
            return ddx * ddx + ddz * ddz <= r2;
        }

        /** Reads columns until about {@code budget} blocks are read; true when the snapshot is complete. */
        public boolean step(ServerLevel level, int budget) {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int read = 0;
            int bottom = level.getMinBuildHeight();
            while (!done() && read < budget) {
                int c = column++;
                int dx = c / size, dz = c % size;
                yMin[c] = 0;
                yMax[c] = -1;
                if (!inside(dx, dz)) continue;
                int x = minX + dx, z = minZ + dz;
                LevelChunk chunk = level.getChunk(x >> 4, z >> 4);
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1;
                int lo = Math.max(bottom, Math.min(top - 1, groundY) - depth);
                int hi = Math.min(level.getMaxBuildHeight() - 1, top + 1);
                yMin[c] = lo;
                yMax[c] = hi;
                for (int y = lo; y <= hi; y++) {
                    pos.set(x, y, z);
                    BlockState state = chunk.getBlockState(pos);
                    add(state);
                    if (state.hasBlockEntity()) {
                        BlockEntity be = chunk.getBlockEntity(pos);
                        if (be != null) {
                            CompoundTag entry = new CompoundTag();
                            entry.putInt("i", count - 1);
                            entry.put("nbt", be.saveWithFullMetadata(level.registryAccess()));
                            blockEntities.add(entry);
                        }
                    }
                }
                read += hi - lo + 1;
                if (count > maxBlocks) tooBig = true;
            }
            return done();
        }

        private void add(BlockState state) {
            Integer idx = palette.get(state);
            if (idx == null) {
                idx = paletteList.size();
                palette.put(state, idx);
                paletteList.add(state);
            }
            if (count == states.length) states = java.util.Arrays.copyOf(states, states.length * 2);
            states[count++] = idx;
        }

        public CompoundTag toTag(String dimension) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("format", FORMAT);
            tag.putString("dimension", dimension);
            tag.putInt("centerX", centerX);
            tag.putInt("centerZ", centerZ);
            tag.putInt("minX", minX);
            tag.putInt("minZ", minZ);
            tag.putInt("size", size);
            tag.putLong("r2", r2);
            tag.put("yMin", new IntArrayTag(yMin));
            tag.put("yMax", new IntArrayTag(yMax));
            ListTag pal = new ListTag();
            for (BlockState s : paletteList) pal.add(NbtUtils.writeBlockState(s));
            tag.put("palette", pal);
            int bits = BitPacker.bitsFor(paletteList.size());
            tag.putInt("bits", bits);
            tag.putInt("count", count);
            tag.put("data", new LongArrayTag(BitPacker.pack(states, count, bits)));
            tag.put("blockEntities", blockEntities);
            return tag;
        }
    }
}
