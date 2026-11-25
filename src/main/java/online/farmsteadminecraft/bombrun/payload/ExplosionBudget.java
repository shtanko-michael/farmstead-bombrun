package online.farmsteadminecraft.bombrun.payload;

/**
 * Limits on what the mod lets happen at once (PLAN.md §5.3): live bombs in a level and explosions per tick.
 * The bottleneck of a big raid is thousands of explosions, not the planes. Plain Java; one per level.
 *
 * <p>Drops over the live-bomb limit wait in the plane (the client does not see it); bombs whose fuse runs out
 * over the per-tick limit wait for the next tick.</p>
 */
public final class ExplosionBudget {
    private int live;
    private int explodedThisTick;
    private long deferredDrops;
    private long delayedExplosions;
    private long explosions;
    private int peakPerTick;

    /** Starts a new tick: the per-tick explosion count goes back to zero. */
    public void newTick() {
        peakPerTick = Math.max(peakPerTick, explodedThisTick);
        explodedThisTick = 0;
    }

    public boolean canDrop(int maxLive) {
        return live < maxLive;
    }

    public void deferredDrop() {
        deferredDrops++;
    }

    public void bombAdded() {
        live++;
    }

    public void bombGone() {
        if (live > 0) live--;
    }

    /** One more bomb may explode this tick; true and counted if it fits, false (and counted as delayed) if not. */
    public boolean tryExplode(int maxPerTick) {
        if (explodedThisTick < maxPerTick) {
            explodedThisTick++;
            explosions++;
            return true;
        }
        delayedExplosions++;
        return false;
    }

    public int live() {
        return live;
    }

    public long deferredDrops() {
        return deferredDrops;
    }

    public long delayedExplosions() {
        return delayedExplosions;
    }

    public long explosions() {
        return explosions;
    }

    public int peakPerTick() {
        return Math.max(peakPerTick, explodedThisTick);
    }

    public void reset() {
        live = 0;
        explodedThisTick = 0;
    }
}
