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
    public void preTick() {
        budget.newTick();
        int maxPerTick = BombRunConfig.MAX_PER_TICK.value();
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

    public void clear() {
        tracked.clear();
        order.clear();
        budget.reset();
    }
}
