package online.farmsteadminecraft.bombrun.strike;

/**
 * Checks a call for a raid against the limits before anything flies (PLAN.md §5, {@code strike/}). The server
 * repeats every check the client's targeting already showed: the client may be out of date or modified.
 */
public final class StrikeValidator {
    public static final String PREFIX = "farmstead_bombrun.deny.";

    private StrikeValidator() {
    }

    /** Null when the raid may go, otherwise the first reason it may not. */
    public static Denial check(StrikeRequest q, StrikeLimits l) {
        if (!l.enabled()) return new Denial(PREFIX + "disabled");
        if (q.fromPlayer() && !q.mayUse()) return new Denial(PREFIX + "permission");
        if (!q.dimensionAllowed()) return new Denial(PREFIX + "dimension");
        if (q.fromPlayer() && q.distance() > l.maxRange()) return new Denial(PREFIX + "range", (int) Math.round(l.maxRange()));
        if (q.radius() < l.radiusMin() || q.radius() > l.radiusMax()) {
            return new Denial(PREFIX + "radius", (int) l.radiusMin(), (int) l.radiusMax());
        }
        if (q.planes() < 1 || q.planes() > l.maxPlanes()) return new Denial(PREFIX + "planes", l.maxPlanes());
        if (q.bombs() < 0 || q.bombs() > l.maxBombs()) return new Denial(PREFIX + "bombs", l.maxBombs());
        if (q.activeRaids() >= l.maxActiveRaids()) return new Denial(PREFIX + "busy", l.maxActiveRaids());
        if (q.fromPlayer() && !q.bypassCooldown() && q.ticksSinceLast() < l.cooldownTicks()) {
            long left = (l.cooldownTicks() - q.ticksSinceLast() + 19) / 20;
            return new Denial(PREFIX + "cooldown", (int) left);
        }
        return null;
    }
}
