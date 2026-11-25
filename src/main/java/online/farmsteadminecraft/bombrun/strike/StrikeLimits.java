package online.farmsteadminecraft.bombrun.strike;

/** The server's limits on one raid, from the config (and later from LuckPerms meta, PLAN.md §7). */
public record StrikeLimits(boolean enabled, double maxRange, double radiusMin, double radiusMax, int maxPlanes,
                           int maxBombs, int cooldownTicks, int maxActiveRaids) {
}
