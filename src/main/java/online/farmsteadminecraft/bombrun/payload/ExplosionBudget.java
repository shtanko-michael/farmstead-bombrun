package online.farmsteadminecraft.bombrun.payload;

/**
 * Limits on what the mod lets happen at once (PLAN.md §5.3): live bombs in a level and explosions per tick.
 * The bottleneck of a big raid is thousands of explosions, not the planes. Plain Java; one per level.
 *
 * <p>Drops over the live-bomb limit wait in the plane (the client does not see it); bombs whose fuse runs out
 * over the per-tick limit wait for the next tick.</p>
 *
 * <p>The per-tick limit adapts to the server ({@link #adapt}): when a tick ran over the target time the allowance
 * drops by a fifth, when it ran well under it grows by a fifth plus one. A 5000-bomb raid then explodes as fast as
 * the machine can take while keeping it near the target tick time, on a fast server and a slow one alike.</p>
 */
public final class ExplosionBudget {
    private int live;
    private int explodedThisTick;
    private long deferredDrops;
    private long delayedExplosions;
    private long explosions;
    private int peakPerTick;
    /** Explosions allowed per tick now; below zero until the first {@link #adapt}. */
    private double allowance = -1;
    private double lastTickMs;

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

    /**
     * Adapts the per-tick allowance to how long the last tick took: over {@code targetMs} it shrinks by a fifth,
     * under 80 % of it it grows by a fifth plus one; always within {@code [min, max]}.
     */
    public void adapt(double lastTickMs, double targetMs, int min, int max) {
        this.lastTickMs = lastTickMs;
        int lo = Math.max(1, Math.min(min, max));
        if (allowance < 0) allowance = Math.min(max, Math.max(lo, 16));
        if (lastTickMs > targetMs) {
            allowance = allowance * 0.8;
        } else if (lastTickMs < targetMs * 0.8) {
            allowance = allowance * 1.2 + 1;
        }
        allowance = Math.max(lo, Math.min(max, allowance));
    }

    /** Explosions allowed this tick: the adapted allowance, or {@code max} before the first {@link #adapt}. */
    public int allowance(int max) {
        return allowance < 0 ? max : (int) Math.floor(allowance);
    }

    public double lastTickMs() {
        return lastTickMs;
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
