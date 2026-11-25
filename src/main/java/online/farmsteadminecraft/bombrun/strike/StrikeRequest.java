package online.farmsteadminecraft.bombrun.strike;

/**
 * One call for a raid as the validator sees it.
 *
 * @param fromPlayer        false for the command, which skips range, cooldown and the use permission
 * @param distance          blocks from the player's eyes to the target
 * @param ticksSinceLast    ticks since this player's last raid ({@code Long.MAX_VALUE} if none)
 */
public record StrikeRequest(boolean fromPlayer, boolean mayUse, boolean dimensionAllowed, double distance, double radius,
                            int planes, int bombs, long ticksSinceLast, boolean bypassCooldown, int activeRaids) {
}
