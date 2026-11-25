package online.farmsteadminecraft.bombrun.raid;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.config.BombRunConfig;
import online.farmsteadminecraft.bombrun.net.Net;
import online.farmsteadminecraft.bombrun.net.RaidEndPayload;
import online.farmsteadminecraft.bombrun.net.RaidStartPayload;
import online.farmsteadminecraft.bombrun.net.RequestStrikePayload;
import online.farmsteadminecraft.bombrun.payload.Bombs;
import online.farmsteadminecraft.bombrun.perm.Permissions;
import online.farmsteadminecraft.bombrun.registry.BombRunContent;
import online.farmsteadminecraft.bombrun.strike.Denial;
import online.farmsteadminecraft.bombrun.strike.FlightPlan;
import online.farmsteadminecraft.bombrun.strike.Pattern;
import online.farmsteadminecraft.bombrun.strike.RaidPlanner;
import online.farmsteadminecraft.bombrun.strike.StrikeOrder;
import online.farmsteadminecraft.bombrun.strike.StrikeRequest;
import online.farmsteadminecraft.bombrun.strike.StrikeValidator;
import online.farmsteadminecraft.bombrun.undo.RaidSnapshot;
import online.farmsteadminecraft.bombrun.undo.Restorer;

/**
 * Every raid of the server (PLAN.md §5, {@code raid/}): starts them, takes their undo snapshot, drops their
 * bombs on the plan's ticks, ends them, and runs {@code /bombrun undo}. One per running server; the loader
 * forwards its server, level and player events here.
 */
public final class RaidManager {
    private static RaidManager instance;

    private final MinecraftServer server;
    private final RaidSavedData data;
    private final Map<Integer, Raid> raids = new LinkedHashMap<>();
    private final Map<ResourceKey<Level>, Bombs> bombs = new HashMap<>();
    private final Map<UUID, Long> lastStrike = new HashMap<>();
    private final List<UndoJob> undoJobs = new ArrayList<>();
    private final Map<Integer, CompletableFuture<Void>> pendingWrites = new HashMap<>();

    /** A running {@code /bombrun undo}. */
    private record UndoJob(ResourceKey<Level> dimension, Restorer restorer, Consumer<Component> feedback) {
    }

    private RaidManager(MinecraftServer server) {
        this.server = server;
        this.data = server.overworld().getDataStorage().computeIfAbsent(RaidSavedData.factory(), RaidSavedData.NAME);
    }

    public static RaidManager get() {
        return instance;
    }

    // ---- lifecycle -------------------------------------------------------------------------------------------

    public static void serverStarted(MinecraftServer server) {
        instance = new RaidManager(server);
        instance.restore();
    }

    public static void serverStopping() {
        if (instance != null) instance.saveRaids();
        instance = null;
    }

    private void restore() {
        boolean resume = BombRunConfig.RESUME.on();
        for (CompoundTag tag : data.raids) {
            try {
                Raid raid = Raid.load(tag);
                if (!resume) {
                    BombRun.LOGGER.info("[Bomb Run] raid #{} was in the air at shutdown; cancelled (raid.resumeOnRestart = false)", raid.id);
                    continue;
                }
                if (server.getLevel(raid.dimension) == null) continue;
                if (raid.phase == Raid.Phase.PREPARING) raid.capture = null; // the snapshot starts over
                raids.put(raid.id, raid);
                BombRun.LOGGER.info("[Bomb Run] raid #{} resumes after restart", raid.id);
            } catch (IOException | RuntimeException e) {
                BombRun.LOGGER.warn("[Bomb Run] a saved raid could not be read, dropped: {}", e.toString());
            }
        }
        saveRaids();
    }

    private void saveRaids() {
        data.raids.clear();
        for (Raid raid : raids.values()) data.raids.add(raid.save());
        data.setDirty();
    }

    // ---- calling a raid --------------------------------------------------------------------------------------

    /** The remote asked for a raid: check everything again, then start it or tell the player why not. */
    public void request(ServerPlayer player, RequestStrikePayload p) {
        ServerLevel level = player.serverLevel();
        double radius = p.radius();
        int planes = Math.max(1, p.planes());
        int bombsCount = BombRunConfig.bombsFor(Math.max(1, Math.min(radius, BombRunConfig.RADIUS_MAX.value())));
        Vec3 target = new Vec3(p.x(), p.y(), p.z());
        long now = level.getGameTime();
        Long last = lastStrike.get(player.getUUID());
        StrikeRequest q = new StrikeRequest(true, Permissions.mayUse(player), dimensionAllowed(level),
            player.getEyePosition().distanceTo(target), radius, planes, bombsCount,
            last == null ? Long.MAX_VALUE : now - last, Permissions.bypassCooldown(player), raids.size());
        Denial denial = StrikeValidator.check(q, BombRunConfig.limits());
        if (denial == null && !Double.isFinite(p.x() + p.y() + p.z() + p.heading())) denial = new Denial(StrikeValidator.PREFIX + "range", 0);
        if (denial != null) {
            deny(player, denial);
            return;
        }
        Pattern pattern = Pattern.byName(p.pattern());
        if (pattern == null) pattern = BombRunConfig.PATTERN.get();
        double ground = groundAt(level, p.x(), p.y(), p.z());
        StrikeOrder order = order(level, p.x(), ground, p.z(), radius, p.heading(), planes, bombsCount, pattern);
        lastStrike.put(player.getUUID(), now);
        Raid raid = start(level, order, player, BombRunConfig.FUSE.value());
        player.displayClientMessage(Component.translatable("farmstead_bombrun.raid.called", raid.id, planes, bombsCount)
            .withStyle(ChatFormatting.GOLD), true);
    }

    private static void deny(ServerPlayer player, Denial d) {
        player.displayClientMessage(Component.translatable(d.key(), d.args()).withStyle(ChatFormatting.RED), true);
        if (BombRunContent.deny != null) {
            player.playNotifySound(BombRunContent.deny.get(), SoundSource.PLAYERS, 0.6f, 1.0f);
        }
    }

    public static boolean dimensionAllowed(ServerLevel level) {
        return BombRunConfig.DIMENSIONS.contains(level.dimension().location().toString());
    }

    /** The ground at the target as the server sees it (the client's ray may have hit a wall). */
    private static double groundAt(ServerLevel level, double x, double y, double z) {
        BlockPos at = BlockPos.containing(x, y, z);
        if (!level.hasChunkAt(at)) return y;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at.getX(), at.getZ());
        // aiming under an overhang or at a wall: keep the hit height, it is where the player looked
        return Math.abs(top - y) <= 3 ? top : y;
    }

    /** An order with the server's defaults. The squadron flies high enough to clear the tallest roof in the circle. */
    public static StrikeOrder order(ServerLevel level, double x, double y, double z, double radius, double heading, int planes,
                                    int bombsCount, Pattern pattern) {
        double clear = BombRunConfig.ALTITUDE.value();
        for (int i = 0; i < 64; i++) {
            double a = i * 2.399963, r = radius * Math.sqrt((i + 0.5) / 64);
            int sx = (int) Math.floor(x + r * Math.cos(a)), sz = (int) Math.floor(z + r * Math.sin(a));
            if (!level.hasChunkAt(new BlockPos(sx, 0, sz))) continue;
            int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, sx, sz);
            clear = Math.max(clear, top - y + 16);
        }
        double altitude = Math.min(clear, level.getMaxBuildHeight() + 64 - y);
        return new StrikeOrder(x, y, z, radius, heading, planes, bombsCount, pattern, BombRunConfig.FORMATION.get(),
            BombRunConfig.PAYLOAD.get(), Math.max(8, altitude), BombRunConfig.SPEED.value(),
            BombRunConfig.COUNTDOWN.value() * 20, 96, level.getRandom().nextLong());
    }

    /** Plans and starts a raid; it takes off once its undo snapshot is taken. */
    public Raid start(ServerLevel level, StrikeOrder order, ServerPlayer owner, int fuseTicks) {
        FlightPlan plan = RaidPlanner.plan(order);
        int id = data.nextId++;
        Raid raid = new Raid(id, level.dimension(), owner == null ? null : owner.getUUID(),
            owner == null ? "" : owner.getGameProfile().getName(), plan, plan.toBytes(), fuseTicks, level.getGameTime());
        if (BombRunConfig.UNDO.on()) {
            raid.capture = new RaidSnapshot.Capture(plan.targetX, plan.targetY, plan.targetZ, plan.radius,
                BombRunConfig.UNDO_DEPTH.value(), BombRunConfig.UNDO_MAX_BLOCKS.value());
        }
        raids.put(id, raid);
        holdTickets(level, raid);
        saveRaids();
        BombRun.LOGGER.info("[Bomb Run] raid #{} called by {} at {} {} {}: radius {}, {} planes, {} bombs", id,
            owner == null ? "command" : owner.getGameProfile().getName(), (int) plan.targetX, (int) plan.targetY,
            (int) plan.targetZ, (int) plan.radius, plan.planes(), plan.drops());
        return raid;
    }

    // ---- ticking ---------------------------------------------------------------------------------------------

    public Bombs bombs(ServerLevel level) {
        return bombs.computeIfAbsent(level.dimension(), k -> new Bombs());
    }

    public void entityJoined(Entity entity, ServerLevel level) {
        if (entity instanceof PrimedTnt tnt && Bombs.isBomb(tnt)) bombs(level).track(tnt);
    }

    /** Before the level ticks its entities. */
    public void levelTick(ServerLevel level) {
        Bombs levelBombs = bombs(level);
        levelBombs.preTick();
        boolean changed = false;
        for (Iterator<Raid> it = raids.values().iterator(); it.hasNext(); ) {
            Raid raid = it.next();
            if (raid.dimension != level.dimension()) continue;
            if (raid.phase == Raid.Phase.PREPARING) {
                prepare(level, raid);
                changed = true;
                continue;
            }
            long t = level.getGameTime() - raid.startTick;
            FlightPlan plan = raid.plan;
            while (raid.nextDrop < plan.drops() && plan.dropTick(raid.nextDrop) <= t) {
                raid.held.add(raid.nextDrop++);
                changed = true;
            }
            LivingEntity owner = raid.owner == null ? null : server.getPlayerList().getPlayer(raid.owner);
            while (!raid.held.isEmpty() && levelBombs.canDrop()) {
                Bombs.drop(level, plan.drop(raid.held.poll()), plan.payload, raid.fuseTicks, raid.id, owner);
            }
            if (!raid.held.isEmpty()) levelBombs.budget().deferredDrop();
            if (raid.nextDrop >= plan.drops() && raid.held.isEmpty() && t > plan.duration()) {
                finish(level, raid, false);
                it.remove();
                changed = true;
            }
        }
        if (changed) saveRaids();
        for (Iterator<UndoJob> it = undoJobs.iterator(); it.hasNext(); ) {
            UndoJob job = it.next();
            if (job.dimension != level.dimension()) continue;
            if (job.restorer.step(BombRunConfig.UNDO_PER_TICK.value())) {
                job.feedback.accept(Component.translatable("farmstead_bombrun.undo.done", job.restorer.raidId, job.restorer.changed()));
                it.remove();
            }
        }
    }

    private void prepare(ServerLevel level, Raid raid) {
        if (raid.capture == null && BombRunConfig.UNDO.on() && !raid.undoable) {
            FlightPlan p = raid.plan;
            raid.capture = new RaidSnapshot.Capture(p.targetX, p.targetY, p.targetZ, p.radius,
                BombRunConfig.UNDO_DEPTH.value(), BombRunConfig.UNDO_MAX_BLOCKS.value());
        }
        if (raid.capture != null) {
            if (!raid.capture.step(level, BombRunConfig.CAPTURE_PER_TICK.value())) return;
            if (raid.capture.tooBig()) {
                BombRun.LOGGER.warn("[Bomb Run] raid #{}: target over undo.maxBlocks ({}), flying without undo", raid.id,
                    BombRunConfig.UNDO_MAX_BLOCKS.value());
                notifyOwner(raid, Component.translatable("farmstead_bombrun.undo.too_big", BombRunConfig.UNDO_MAX_BLOCKS.value())
                    .withStyle(ChatFormatting.YELLOW));
            } else {
                saveSnapshot(level, raid, raid.capture);
                raid.undoable = true;
            }
            raid.capture = null;
        }
        raid.phase = Raid.Phase.FLYING;
        raid.startTick = level.getGameTime();
        Net.get().toDimension(level, new RaidStartPayload(raid.id, raid.startTick, raid.planBytes));
    }

    private void finish(ServerLevel level, Raid raid, boolean cancelled) {
        releaseTickets(level, raid);
        Net.get().toDimension(level, new RaidEndPayload(raid.id, cancelled));
        BombRun.LOGGER.info("[Bomb Run] raid #{} {}", raid.id, cancelled ? "cancelled" : "finished");
    }

    private void notifyOwner(Raid raid, Component message) {
        if (raid.owner == null) return;
        ServerPlayer p = server.getPlayerList().getPlayer(raid.owner);
        if (p != null) p.sendSystemMessage(message);
    }

    /** A player joined or changed dimension: send them the raids flying there. */
    public void syncTo(ServerPlayer player) {
        for (Raid raid : raids.values()) {
            if (raid.phase == Raid.Phase.FLYING && raid.dimension == player.level().dimension()) {
                Net.get().toPlayer(player, new RaidStartPayload(raid.id, raid.startTick, raid.planBytes));
            }
        }
    }

    // ---- chunk tickets ---------------------------------------------------------------------------------------

    private static void holdTickets(ServerLevel level, Raid raid) {
        if (!BombRunConfig.FORCE_LOAD.on() || raid.ticketsHeld) return;
        // everything from the first release point to the far side of the circle
        double reach = raid.plan.radius + RaidSnapshot.MARGIN;
        for (int k = 0; k < raid.plan.drops(); k++) {
            double[] d = raid.plan.drop(k);
            reach = Math.max(reach, Math.hypot(d[0] - raid.plan.targetX, d[2] - raid.plan.targetZ) + 4);
        }
        raid.ticketChunks = (int) Math.ceil(reach / 16.0);
        forEachChunk(raid, (cx, cz) -> ChunkTickets.get().force(level, raid.target(), cx, cz, true));
        raid.ticketsHeld = true;
    }

    private static void releaseTickets(ServerLevel level, Raid raid) {
        if (!raid.ticketsHeld) return;
        forEachChunk(raid, (cx, cz) -> ChunkTickets.get().force(level, raid.target(), cx, cz, false));
        raid.ticketsHeld = false;
    }

    private interface ChunkVisitor {
        void visit(int cx, int cz);
    }

    private static void forEachChunk(Raid raid, ChunkVisitor v) {
        int ccx = raid.target().getX() >> 4, ccz = raid.target().getZ() >> 4, r = raid.ticketChunks;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz <= (r + 1) * (r + 1)) v.visit(ccx + dx, ccz + dz);
            }
        }
    }

    // ---- commands --------------------------------------------------------------------------------------------

    public Map<Integer, Raid> raids() {
        return raids;
    }

    /** Ends a raid now: the planes vanish, bombs not yet dropped are never dropped. */
    public boolean cancel(int id) {
        Raid raid = raids.remove(id);
        if (raid == null) return false;
        ServerLevel level = server.getLevel(raid.dimension);
        if (level != null) finish(level, raid, true);
        saveRaids();
        return true;
    }

    public int cancelAll() {
        int n = 0;
        for (Integer id : new ArrayList<>(raids.keySet())) if (cancel(id)) n++;
        return n;
    }

    public List<RaidSavedData.SnapshotEntry> snapshots() {
        return data.snapshots;
    }

    /**
     * Starts putting a raid's target back; {@code raidId} null means the latest snapshot. Returns an error
     * message, or null when the undo started.
     */
    public Component undo(Integer raidId, Consumer<Component> feedback) {
        if (data.snapshots.isEmpty()) return Component.translatable("farmstead_bombrun.undo.none");
        RaidSavedData.SnapshotEntry entry = null;
        if (raidId == null) {
            entry = data.snapshots.get(data.snapshots.size() - 1);
        } else {
            for (RaidSavedData.SnapshotEntry e : data.snapshots) if (e.raidId() == raidId) entry = e;
        }
        if (entry == null) return Component.translatable("farmstead_bombrun.undo.unknown", raidId);
        if (raids.containsKey(entry.raidId())) return Component.translatable("farmstead_bombrun.undo.flying", entry.raidId());
        for (UndoJob job : undoJobs) {
            if (job.restorer.raidId == entry.raidId()) return Component.translatable("farmstead_bombrun.undo.running", entry.raidId());
        }
        ServerLevel level = null;
        for (ServerLevel l : server.getAllLevels()) if (l.dimension().location().toString().equals(entry.dimension())) level = l;
        if (level == null) return Component.translatable("farmstead_bombrun.undo.unknown", entry.raidId());
        CompletableFuture<Void> write = pendingWrites.remove(entry.raidId());
        if (write != null) write.join();
        Path file = snapshotDir().resolve(entry.file());
        try {
            CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            undoJobs.add(new UndoJob(level.dimension(), new Restorer(level, entry.raidId(), tag), feedback));
        } catch (IOException | RuntimeException e) {
            BombRun.LOGGER.warn("[Bomb Run] snapshot of raid #{} could not be read: {}", entry.raidId(), e.toString());
            return Component.translatable("farmstead_bombrun.undo.broken", entry.raidId());
        }
        data.snapshots.remove(entry);
        data.setDirty();
        deleteLater(file);
        return null;
    }

    private Path snapshotDir() {
        return server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(BombRun.MOD_ID).resolve("snapshots");
    }

    private void saveSnapshot(ServerLevel level, Raid raid, RaidSnapshot.Capture capture) {
        String dim = level.dimension().location().toString();
        CompoundTag tag = capture.toTag(dim);
        String name = raid.id + ".dat";
        Path dir = snapshotDir();
        Path file = dir.resolve(name);
        // written off the main thread; an undo that comes before it is done waits for it
        pendingWrites.put(raid.id, CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(dir);
                NbtIo.writeCompressed(tag, file);
            } catch (IOException e) {
                BombRun.LOGGER.warn("[Bomb Run] snapshot of raid #{} could not be written: {}", raid.id, e.toString());
            }
        }, Util.ioPool()));
        data.snapshots.add(new RaidSavedData.SnapshotEntry(raid.id, dim, (int) Math.floor(raid.plan.targetX),
            (int) Math.floor(raid.plan.targetY), (int) Math.floor(raid.plan.targetZ), (int) Math.round(raid.plan.radius), name,
            level.getGameTime(), capture.blocks()));
        while (data.snapshots.size() > BombRunConfig.UNDO_KEEP.value()) {
            RaidSavedData.SnapshotEntry old = data.snapshots.remove(0);
            CompletableFuture<Void> w = pendingWrites.remove(old.raidId());
            if (w != null) w.join();
            deleteLater(dir.resolve(old.file()));
        }
        data.setDirty();
    }

    private static void deleteLater(Path file) {
        CompletableFuture.runAsync(() -> {
            try {
                Files.deleteIfExists(file);
            } catch (IOException ignored) {
                // a leftover file is harmless; the index no longer names it
            }
        }, Util.ioPool());
    }

    // ---- stats -----------------------------------------------------------------------------------------------

    public List<Component> stats() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("farmstead_bombrun.stats.raids", raids.size(), undoJobs.size(), data.snapshots.size()));
        for (Map.Entry<ResourceKey<Level>, Bombs> e : bombs.entrySet()) {
            Bombs b = e.getValue();
            lines.add(Component.translatable("farmstead_bombrun.stats.level", e.getKey().location().toString(), b.budget().live(),
                b.budget().explosions(), b.budget().peakPerTick(), b.budget().delayedExplosions(), b.budget().deferredDrops()));
        }
        return lines;
    }
}
