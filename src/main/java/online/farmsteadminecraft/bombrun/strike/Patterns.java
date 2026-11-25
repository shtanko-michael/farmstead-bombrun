package online.farmsteadminecraft.bombrun.strike;

import java.util.SplittableRandom;

/**
 * Impact points of a pattern in the circle's own frame: {@code along} the flight direction and {@code across}
 * it (to the right of the run), both relative to the middle. Every point lies inside the circle.
 */
public final class Patterns {
    /** Golden angle in radians: consecutive sunflower points never line up. */
    private static final double GOLDEN = Math.PI * (3 - Math.sqrt(5));

    private Patterns() {
    }

    /** {@code [count][2]}: {@code {along, across}} per bomb. */
    public static double[][] points(Pattern pattern, double radius, int count, long seed) {
        if (count <= 0) return new double[0][];
        return switch (pattern) {
            case CIRCLE -> sunflower(radius, count, seed);
            case RANDOM -> random(radius, count, seed);
            case CARPET -> carpet(radius, count);
        };
    }

    private static double[][] sunflower(double radius, int count, long seed) {
        double turn = new SplittableRandom(seed).nextDouble() * 2 * Math.PI;
        double[][] out = new double[count][];
        for (int k = 0; k < count; k++) {
            double r = radius * Math.sqrt((k + 0.5) / count);
            double a = k * GOLDEN + turn;
            out[k] = new double[] {r * Math.cos(a), r * Math.sin(a)};
        }
        return out;
    }

    private static double[][] random(double radius, int count, long seed) {
        SplittableRandom rnd = new SplittableRandom(seed);
        double[][] out = new double[count][];
        for (int k = 0; k < count; k++) {
            double r = radius * Math.sqrt(rnd.nextDouble());
            double a = rnd.nextDouble() * 2 * Math.PI;
            out[k] = new double[] {r * Math.cos(a), r * Math.sin(a)};
        }
        return out;
    }

    /**
     * Rows parallel to the run, as far apart as the bombs inside a row, filling the circle. The spacing is tuned
     * until the grid holds at least {@code count} points, then points are taken evenly so exactly {@code count}
     * remain and the cover stays even.
     */
    private static double[][] carpet(double radius, int count) {
        double spacing = Math.sqrt(Math.PI * radius * radius / count);
        double[][] grid = grid(radius, spacing);
        for (int i = 0; i < 40 && grid.length < count; i++) {
            spacing *= 0.97;
            grid = grid(radius, spacing);
        }
        if (grid.length == 0) return new double[][] {{0, 0}};
        double[][] out = new double[count][];
        for (int k = 0; k < count; k++) {
            int idx = (int) Math.min(grid.length - 1L, (long) k * grid.length / count);
            out[k] = grid[idx];
        }
        return out;
    }

    private static double[][] grid(double radius, double s) {
        java.util.List<double[]> pts = new java.util.ArrayList<>();
        for (double across = -radius + s / 2; across < radius; across += s) {
            double half = Math.sqrt(Math.max(0, radius * radius - across * across));
            int n = (int) Math.floor(2 * half / s);
            if (n <= 0) {
                if (half > 0) pts.add(new double[] {0, across});
                continue;
            }
            double start = -(n - 1) * s / 2;
            for (int i = 0; i < n; i++) pts.add(new double[] {start + i * s, across});
        }
        return pts.toArray(new double[0][]);
    }
}
