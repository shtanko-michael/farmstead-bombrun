package online.farmsteadminecraft.bombrun.strike;

/** Where the bombs of a raid land inside the target circle (PLAN.md §3 M1). Plain Java: no game types. */
public enum Pattern {
    /** Rows of bombs along the run, evenly spaced, filling the circle. */
    CARPET,
    /** The circle filled evenly from the middle out (a sunflower spiral). */
    CIRCLE,
    /** Uniformly at random inside the circle, from the order's seed. */
    RANDOM;

    public static Pattern byName(String name) {
        for (Pattern p : values()) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }
}
