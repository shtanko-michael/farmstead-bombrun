package online.farmsteadminecraft.bombrun.strike;

/**
 * The flight of a vanilla primed TNT, tick by tick, the way {@code PrimedTnt.tick()} moves it in 1.21.1:
 * gravity first ({@code vy -= 0.04}), then the move, then drag ({@code v *= 0.98}) on every axis.
 *
 * <p>Horizontal travel is linear in the starting horizontal speed: a bomb released at speed {@code v}
 * travels {@code v * unitTravel(h)} before it has fallen {@code h} blocks. That lets the planner pick a
 * release velocity that lands every bomb exactly on its point.</p>
 */
public final class Ballistics {
    public static final double GRAVITY = 0.04;
    public static final double DRAG = 0.98;
    private static final int MAX_TICKS = 2400;

    private Ballistics() {
    }

    /**
     * Horizontal distance covered per block/tick of starting horizontal speed while the bomb falls {@code drop}
     * blocks from rest (vertically). Fractional: the last tick is interpolated to the exact crossing.
     */
    public static double unitTravel(double drop) {
        if (drop <= 0) return 0;
        double y = 0, vy = 0, s = 0, vh = 1;
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            vy -= GRAVITY;
            double ny = y + vy;
            double ns = s + vh;
            if (ny <= -drop) {
                double f = (y + drop) / (y - ny);
                return s + (ns - s) * f;
            }
            y = ny;
            s = ns;
            vy *= DRAG;
            vh *= DRAG;
        }
        return s;
    }

    /** Ticks (fractional) a bomb takes to fall {@code drop} blocks from rest. */
    public static double fallTicks(double drop) {
        if (drop <= 0) return 0;
        double y = 0, vy = 0;
        for (int tick = 0; tick < MAX_TICKS; tick++) {
            vy -= GRAVITY;
            double ny = y + vy;
            if (ny <= -drop) return tick + (y + drop) / (y - ny);
            y = ny;
            vy *= DRAG;
        }
        return MAX_TICKS;
    }

    /**
     * Where a bomb released at {@code (x, y, z)} with velocity {@code (vx, vy, vz)} is after {@code ticks}
     * whole ticks: {@code {x, y, z, vx, vy, vz}}. Used by the client for the falling whistle and by tests.
     */
    public static double[] step(double x, double y, double z, double vx, double vy, double vz, int ticks) {
        for (int i = 0; i < ticks; i++) {
            vy -= GRAVITY;
            x += vx;
            y += vy;
            z += vz;
            vx *= DRAG;
            vy *= DRAG;
            vz *= DRAG;
        }
        return new double[] {x, y, z, vx, vy, vz};
    }
}
