package online.farmsteadminecraft.bombrun.strike;

import java.util.Arrays;

/**
 * Turns a {@link StrikeOrder} into a {@link FlightPlan}, once, on the server (PLAN.md §5.1). Deterministic:
 * the same order always gives the same plan.
 *
 * <p>The circle is split into lanes, one per plane, across the run. Each bomb's point goes to the plane whose
 * lane is nearest. The plane releases it on the tick it is closest to {@code point - speed * unitTravel}, with
 * the velocity that carries the bomb exactly onto the point: forward at about the plane's speed, plus a small
 * sideways part for points off the lane, so the bomb flies on an arc ahead of the plane like a real one.</p>
 */
public final class RaidPlanner {
    /** Planes never fly closer than this side by side, blocks. */
    public static final double MIN_LANE_SPACING = 8;
    /** How far back each rank of a wedge flies behind the one in front, blocks. */
    public static final double WEDGE_STEP = 9;
    /** How much higher each rank of a wedge flies, blocks. */
    public static final double WEDGE_CLIMB = 1.5;
    /** Bombs leave the plane this far below its middle. */
    public static final double BAY_DROP = 1.5;

    private RaidPlanner() {
    }

    public static FlightPlan plan(StrikeOrder o) {
        double yaw = Math.toRadians(o.heading());
        double dirX = -Math.sin(yaw), dirZ = Math.cos(yaw);
        // to the right of the run
        double rightX = -dirZ, rightZ = dirX;
        int n = o.planes();
        double r = o.radius(), v = o.speed();

        double spacing = Math.max(2 * r / n, MIN_LANE_SPACING);
        double[] lane = new double[n], back = new double[n], alt = new double[n];
        for (int i = 0; i < n; i++) {
            double rank = i - (n - 1) / 2.0;
            lane[i] = rank * spacing;
            boolean wedge = o.formation() == Formation.WEDGE;
            back[i] = wedge ? Math.abs(rank) * WEDGE_STEP : 0;
            alt[i] = o.y() + o.altitude() + (wedge ? Math.abs(rank) * WEDGE_CLIMB : 0);
        }

        // the planes appear far enough out that the first bomb falls after the lead time
        double maxFall = Ballistics.unitTravel(o.altitude() + n * WEDGE_CLIMB);
        double approach = r + v * maxFall + Math.max(v * o.leadTicks(), o.approachMin());

        double[][] points = Patterns.points(o.pattern(), r, o.bombs(), o.seed());
        int m = points.length;
        int[] tick = new int[m];
        short[] plane = new short[m];
        double[] x = new double[m], y = new double[m], z = new double[m], vx = new double[m], vy = new double[m], vz = new double[m];
        for (int k = 0; k < m; k++) {
            double along = points[k][0], across = points[k][1];
            int p = nearestLane(lane, across);
            double releaseY = alt[p] - BAY_DROP;
            double drop = releaseY - o.y();
            double unit = Ballistics.unitTravel(drop);
            // along-track position of plane p at tick t: -approach - back + v * t
            double want = along - v * unit;
            int t = (int) Math.max(0, Math.round((want + approach + back[p]) / v));
            double s = -approach - back[p] + v * t;
            double va = unit > 0 ? (along - s) / unit : 0;
            double vl = unit > 0 ? (across - lane[p]) / unit : 0;
            tick[k] = t;
            plane[k] = (short) p;
            x[k] = o.x() + dirX * s + rightX * lane[p];
            y[k] = releaseY;
            z[k] = o.z() + dirZ * s + rightZ * lane[p];
            vx[k] = dirX * va + rightX * vl;
            vy[k] = 0;
            vz[k] = dirZ * va + rightZ * vl;
        }

        // sort the bombs by tick (stable, so equal ticks keep pattern order)
        Integer[] order = new Integer[m];
        for (int k = 0; k < m; k++) order[k] = k;
        Arrays.sort(order, (a, b) -> Integer.compare(tick[a], tick[b]));

        double[] sx = new double[n], sy = new double[n], sz = new double[n];
        int[] st = new int[n], et = new int[n];
        for (int i = 0; i < n; i++) {
            double s0 = -approach - back[i];
            sx[i] = o.x() + dirX * s0 + rightX * lane[i];
            sy[i] = alt[i];
            sz[i] = o.z() + dirZ * s0 + rightZ * lane[i];
            st[i] = 0;
            // flies on past the circle as far as it came from
            et[i] = (int) Math.ceil((2 * approach + back[i]) / v);
        }
        return new FlightPlan(o.x(), o.y(), o.z(), r, dirX, dirZ, v, o.payload(), sx, sy, sz, st, et,
            pick(tick, order), pickShort(plane, order), pick(x, order), pick(y, order), pick(z, order),
            pick(vx, order), pick(vy, order), pick(vz, order));
    }

    private static int nearestLane(double[] lane, double across) {
        int best = 0;
        for (int i = 1; i < lane.length; i++) {
            if (Math.abs(lane[i] - across) < Math.abs(lane[best] - across)) best = i;
        }
        return best;
    }

    private static int[] pick(int[] a, Integer[] order) {
        int[] out = new int[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[order[i]];
        return out;
    }

    private static short[] pickShort(short[] a, Integer[] order) {
        short[] out = new short[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[order[i]];
        return out;
    }

    private static double[] pick(double[] a, Integer[] order) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; i++) out[i] = a[order[i]];
        return out;
    }
}
