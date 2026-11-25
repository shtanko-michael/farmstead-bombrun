package online.farmsteadminecraft.bombrun.strike;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class FlightPlanTest {
    private static StrikeOrder order(Pattern pattern, Formation formation, int planes, int bombs, double radius, double heading) {
        return new StrikeOrder(100.5, 64, -200.5, radius, heading, planes, bombs, pattern, formation, PayloadKind.IMPACT,
            48, 1.5, 100, 96, 12345L);
    }

    /** Lands a bomb with the vanilla TNT physics and returns {x, z} where it crosses the target height. */
    private static double[] land(FlightPlan plan, int k) {
        double[] d = plan.drop(k);
        double x = d[0], y = d[1], z = d[2], vx = d[3], vy = d[4], vz = d[5];
        for (int t = 0; t < 2000; t++) {
            vy -= Ballistics.GRAVITY;
            double nx = x + vx, ny = y + vy, nz = z + vz;
            if (ny <= plan.targetY) {
                double f = (y - plan.targetY) / (y - ny);
                return new double[] {x + (nx - x) * f, z + (nz - z) * f};
            }
            x = nx;
            y = ny;
            z = nz;
            vx *= Ballistics.DRAG;
            vy *= Ballistics.DRAG;
            vz *= Ballistics.DRAG;
        }
        throw new AssertionError("bomb never landed");
    }

    @ParameterizedTest
    @EnumSource(Pattern.class)
    void everyBombLandsInsideTheCircle(Pattern pattern) {
        for (Formation f : Formation.values()) {
            for (int planes : new int[] {1, 3, 12}) {
                FlightPlan plan = RaidPlanner.plan(order(pattern, f, planes, 300, 32, 37));
                assertEquals(300, plan.drops());
                for (int k = 0; k < plan.drops(); k++) {
                    double[] p = land(plan, k);
                    double dist = Math.hypot(p[0] - plan.targetX, p[1] - plan.targetZ);
                    assertTrue(dist <= 32 + 1e-6, pattern + " " + f + " " + planes + ": bomb " + k + " at " + dist);
                }
            }
        }
    }

    @Test
    void bombsLeaveFromTheirPlaneMovingForward() {
        FlightPlan plan = RaidPlanner.plan(order(Pattern.CARPET, Formation.WEDGE, 5, 200, 40, 90));
        double[] pos = new double[3];
        for (int k = 0; k < plan.drops(); k++) {
            double[] d = plan.drop(k);
            plan.position(plan.dropPlane(k), plan.dropTick(k), pos);
            assertEquals(pos[0], d[0], 1e-9);
            assertEquals(pos[2], d[2], 1e-9);
            assertEquals(pos[1] - RaidPlanner.BAY_DROP, d[1], 1e-9);
            double forward = d[3] * plan.dirX + d[5] * plan.dirZ;
            assertTrue(forward > 0.5 * plan.speed && forward < 1.5 * plan.speed, "forward speed " + forward);
            assertTrue(plan.flying(plan.dropPlane(k), plan.dropTick(k)));
        }
    }

    @Test
    void dropsAreSortedAndAfterTheLeadTime() {
        FlightPlan plan = RaidPlanner.plan(order(Pattern.RANDOM, Formation.LINE, 4, 500, 64, 200));
        for (int k = 1; k < plan.drops(); k++) assertTrue(plan.dropTick(k) >= plan.dropTick(k - 1));
        assertTrue(plan.firstDropTick() >= 100, "first drop " + plan.firstDropTick());
        assertTrue(plan.duration() > plan.lastDropTick());
    }

    @Test
    void headingIsTheFlightDirection() {
        // yaw 0 flies south (+Z), yaw 90 west (-X)
        FlightPlan south = RaidPlanner.plan(order(Pattern.CIRCLE, Formation.LINE, 1, 10, 8, 0));
        assertEquals(0, south.dirX, 1e-12);
        assertEquals(1, south.dirZ, 1e-12);
        FlightPlan west = RaidPlanner.plan(order(Pattern.CIRCLE, Formation.LINE, 1, 10, 8, 90));
        assertEquals(-1, west.dirX, 1e-12);
        assertEquals(0, west.dirZ, 1e-12);
        assertEquals(90, west.heading(), 1e-9);
        // the plane starts before the target and passes over it
        double[] p = west.position(0, 0, new double[3]);
        assertTrue(p[0] > west.targetX + 50);
    }

    @Test
    void wedgeLeaderFliesInFront() {
        FlightPlan plan = RaidPlanner.plan(order(Pattern.CARPET, Formation.WEDGE, 3, 30, 30, 0));
        double[] lead = plan.position(1, 50, new double[3]);
        double[] wing = plan.position(0, 50, new double[3]);
        assertTrue(lead[2] > wing[2], "leader ahead along +Z");
        assertTrue(wing[1] > lead[1], "wingmen climb");
    }

    @Test
    void samePlanFromSameOrderAndRoundTrip() throws Exception {
        StrikeOrder o = order(Pattern.RANDOM, Formation.WEDGE, 7, 777, 50, 123);
        byte[] a = RaidPlanner.plan(o).toBytes();
        byte[] b = RaidPlanner.plan(o).toBytes();
        assertArrayEquals(a, b);
        FlightPlan back = FlightPlan.fromBytes(a);
        assertArrayEquals(a, back.toBytes());
        assertEquals(7, back.planes());
        assertEquals(777, back.drops());
    }

    @Test
    void zeroBombsStillFly() {
        FlightPlan plan = RaidPlanner.plan(order(Pattern.CARPET, Formation.LINE, 2, 0, 10, 0));
        assertEquals(0, plan.drops());
        assertTrue(plan.duration() > 0);
    }

    @Test
    void carpetCoversTheCircleEvenly() {
        double[][] pts = Patterns.points(Pattern.CARPET, 40, 400, 0);
        assertEquals(400, pts.length);
        // every quarter of the circle gets a fair share
        int[] quad = new int[4];
        for (double[] p : pts) quad[(p[0] >= 0 ? 1 : 0) + (p[1] >= 0 ? 2 : 0)]++;
        for (int q : quad) assertTrue(q > 70 && q < 130, "quadrant count " + q);
    }

    @Test
    void ballisticsMatchesStep() {
        double drop = 47.5;
        double unit = Ballistics.unitTravel(drop);
        double ticks = Ballistics.fallTicks(drop);
        double[] s = Ballistics.step(0, 0, 0, 1, 0, 0, (int) Math.floor(ticks));
        assertTrue(s[1] > -drop && s[0] <= unit);
        double[] s2 = Ballistics.step(0, 0, 0, 1, 0, 0, (int) Math.ceil(ticks));
        assertTrue(s2[1] <= -drop && s2[0] >= unit);
    }
}
