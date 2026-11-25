package online.farmsteadminecraft.bombrun.payload;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import online.farmsteadminecraft.bombrun.config.BombRunConfig;
import online.farmsteadminecraft.bombrun.strike.PayloadKind;

/**
 * The bombs of one level: vanilla {@link PrimedTnt} marked with scoreboard tags, so mods that change TNT and
 * explosions, claim mods and vanilla clients all see ordinary TNT (PLAN.md §5.2). This class is the
 * {@code BombDropper} (release with the plane's speed), the {@code ImpactWatcher} (impact payload: fuse to zero
 * on touching a block or water) and the per-level {@link ExplosionBudget} (§5.3) in one place.
 */
public final class Bombs {
    /** Every bomb of the mod carries this tag. Scoreboard tags are vanilla and saved with the entity. */
    public static final String TAG = "farmstead_bombrun.bomb";
    /** Impact payload: explodes when it hits something. */
    public static final String TAG_IMPACT = "farmstead_bombrun.impact";
    /** {@code farmstead_bombrun.raid.<id>}: which raid dropped it (undo clears a raid's leftover bombs). */
    public static final String TAG_RAID = "farmstead_bombrun.raid.";
    /** A fallback fuse for impact bombs that never touch anything (fell into the void, stuck on a ledge). */
    public static final int IMPACT_FALLBACK_FUSE = 600;

    private final ExplosionBudget budget = new ExplosionBudget();
    private final Set<PrimedTnt> tracked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<PrimedTnt> order = new ArrayList<>();
    /** Where bombs blew up this tick, {x, y, z, reach²}: items popping out near them are dropped (explosions.dropItems). */
    private final List<double[]> blasts = new ArrayList<>();

    public ExplosionBudget budget() {
        return budget;
    }

    public static boolean isBomb(Entity e) {
        return e instanceof PrimedTnt && e.getTags().contains(TAG);
    }

    /** A bomb entered the level (dropped now, or loaded with its chunk): count and watch it. */
    public void track(PrimedTnt tnt) {
        if (tracked.add(tnt)) {
            order.add(tnt);
            budget.bombAdded();
        }
    }

    public boolean canDrop() {
        return budget.canDrop(BombRunConfig.MAX_LIVE.value());
    }

    /** Releases one bomb: a primed TNT with the plane's velocity. The level's join event then tracks it. */
    public static PrimedTnt drop(ServerLevel level, double[] d, PayloadKind payload, int fuseTicks, int raidId, LivingEntity owner) {
        PrimedTnt tnt = new PrimedTnt(level, d[0], d[1], d[2], owner);
        tnt.setDeltaMovement(d[3], d[4], d[5]);
        tnt.setFuse(payload == PayloadKind.FUSE ? fuseTicks : IMPACT_FALLBACK_FUSE);
        tnt.addTag(TAG);
        tnt.addTag(TAG_RAID + raidId);
        if (payload == PayloadKind.IMPACT) tnt.addTag(TAG_IMPACT);
        level.addFreshEntity(tnt);
        return tnt;
    }

    /**
     * Runs before the level ticks its entities: impact bombs that touched something get fuse 1 (they explode in
     * this tick's entity pass), and bombs about to explode over the per-tick limit wait a tick.
     */
    public void preTick(double lastTickMs) {
        budget.newTick();
        blasts.clear();
        int max = BombRunConfig.MAX_PER_TICK.value();
        if (!order.isEmpty()) budget.adapt(lastTickMs, BombRunConfig.TARGET_TICK_MS.value(), BombRunConfig.MIN_PER_TICK.value(), max);
        int maxPerTick = budget.allowance(max);
        for (int i = 0; i < order.size(); i++) {
            PrimedTnt tnt = order.get(i);
            if (tnt.isRemoved()) {
                tracked.remove(tnt);
                order.set(i, order.get(order.size() - 1));
                order.remove(order.size() - 1);
                budget.bombGone();
                i--;
                continue;
            }
            if (tnt.getTags().contains(TAG_IMPACT) && tnt.getFuse() > 1
                && (tnt.onGround() || tnt.horizontalCollision || tnt.verticalCollision || tnt.isInWater() || tnt.isInLava())) {
                tnt.setFuse(1);
            }
            // fuse 1 now means 0 after this tick's decrement: it explodes this tick unless the budget is spent
            if (tnt.getFuse() <= 1 && !budget.tryExplode(maxPerTick)) {
                tnt.setFuse(2);
            }
        }
    }

    /** A bomb exploded at {@code (x, y, z)} with this power: remember it until the end of the tick. */
    public void blast(double x, double y, double z, float power) {
        double reach = power * 2 + 2;
        blasts.add(new double[] {x, y, z, reach * reach});
    }

    /** This tick's blasts as {@code x, y, z, power} floats for {@code BlastsPayload}, or null if none. */
    public float[] blastsPacket() {
        if (blasts.isEmpty()) return null;
        int n = Math.min(blasts.size(), 2048);
        float[] out = new float[n * 4];
        for (int i = 0; i < n; i++) {
            double[] b = blasts.get(i);
            out[i * 4] = (float) b[0];
            out[i * 4 + 1] = (float) b[1];
            out[i * 4 + 2] = (float) b[2];
            out[i * 4 + 3] = (float) ((Math.sqrt(b[3]) - 2) / 2);
        }
        return out;
    }

    /** True if {@code (x, y, z)} is within reach of a bomb that blew up this tick. */
    public boolean nearBlast(double x, double y, double z) {
        for (double[] b : blasts) {
            double dx = x - b[0], dy = y - b[1], dz = z - b[2];
            if (dx * dx + dy * dy + dz * dz <= b[3]) return true;
        }
        return false;
    }

    public void clear() {
        tracked.clear();
        order.clear();
        blasts.clear();
        budget.reset();
    }
}
