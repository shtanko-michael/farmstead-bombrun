package online.farmsteadminecraft.bombrun.strike;

/**
 * Everything a raid is planned from, already checked and clamped by the server. Plain Java, so the planner
 * and its tests run without the game.
 *
 * @param x            target point (the middle of the ring), world coordinates
 * @param y            height of the ground at the target; bombs are aimed to land at this height
 * @param z            target point
 * @param radius       radius of the target circle, blocks
 * @param heading      direction the squadron flies, Minecraft yaw degrees (0 = south/+Z, 90 = west/-X)
 * @param planes       squadron size
 * @param bombs        bombs in the whole raid
 * @param pattern      where the bombs land
 * @param formation    how the planes fly
 * @param payload      impact fuse or vanilla fuse
 * @param altitude     flight height above {@code y}, blocks
 * @param speed        plane speed, blocks per tick
 * @param leadTicks    at least this many ticks between the plan start and the first bomb (the siren countdown)
 * @param approachMin  planes appear at least this far before the circle, blocks
 * @param seed         seed of the {@link Pattern#RANDOM} pattern
 */
public record StrikeOrder(double x, double y, double z, double radius, double heading, int planes, int bombs,
                          Pattern pattern, Formation formation, PayloadKind payload, double altitude, double speed,
                          int leadTicks, double approachMin, long seed) {

    public StrikeOrder {
        if (!(radius > 0)) throw new IllegalArgumentException("radius must be > 0");
        if (planes < 1) throw new IllegalArgumentException("planes must be >= 1");
        if (bombs < 0) throw new IllegalArgumentException("bombs must be >= 0");
        if (!(speed > 0)) throw new IllegalArgumentException("speed must be > 0");
        if (!(altitude > 2)) throw new IllegalArgumentException("altitude must be > 2");
        if (pattern == null || formation == null || payload == null) throw new IllegalArgumentException("null enum");
    }
}
