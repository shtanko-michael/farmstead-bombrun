package online.farmsteadminecraft.bombrun.strike;

import java.util.ArrayList;
import java.util.List;

/**
 * A strike power level, picked on the remote: how many planes fly and how densely they drop. The number of
 * bombs follows from the circle — one per {@code spacing}² of its area — so a bigger ring at the same power gets
 * more bombs at the same density. Plain Java.
 *
 * @param planes  planes of the squadron at this level
 * @param spacing blocks between neighbouring bombs on the ground
 */
public record StrikePower(int planes, double spacing) {
    /** Light, medium, heavy, devastating, apocalypse. Written {@code planes:spacing} in the config. */
    public static final List<String> DEFAULTS = List.of("1:8", "3:5", "6:3.5", "9:2.5", "12:1.6");

    public StrikePower {
        if (planes < 1) throw new IllegalArgumentException("planes < 1");
        if (!(spacing >= 0.5)) throw new IllegalArgumentException("spacing < 0.5");
    }

    /** Bombs on a circle of {@code radius} at this density, at least one and at most {@code maxBombs}. */
    public int bombs(double radius, int maxBombs) {
        long n = Math.round(Math.PI * radius * radius / (spacing * spacing));
        return (int) Math.max(1, Math.min(maxBombs, n));
    }

    /** Planes at this level within the server's limit. */
    public int planes(int maxPlanes) {
        return Math.max(1, Math.min(planes, maxPlanes));
    }

    /** {@code "6:3.5"} → planes 6, spacing 3.5; null if the text is not that. */
    public static StrikePower parse(String text) {
        if (text == null) return null;
        String[] parts = text.trim().split(":");
        if (parts.length != 2) return null;
        try {
            return new StrikePower(Integer.parseInt(parts[0].trim()), Double.parseDouble(parts[1].trim()));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Every level of a config list; a broken entry falls back to the default at its place. */
    public static List<StrikePower> parseAll(List<String> texts, List<String> problems) {
        List<StrikePower> out = new ArrayList<>();
        int n = texts == null || texts.isEmpty() ? DEFAULTS.size() : texts.size();
        for (int i = 0; i < n; i++) {
            String t = texts == null || i >= texts.size() ? null : texts.get(i);
            StrikePower p = parse(t);
            if (p == null) {
                if (t != null && problems != null) problems.add("strike.powerLevels[" + i + "] = \"" + t + "\" is not planes:spacing");
                p = parse(DEFAULTS.get(Math.min(i, DEFAULTS.size() - 1)));
            }
            out.add(p);
        }
        return out;
    }
}
