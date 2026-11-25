package online.farmsteadminecraft.bombrun.strike;

/** What a bomb does once it is in the air. Both are vanilla primed TNT (PLAN.md §5.2). */
public enum PayloadKind {
    /** Explodes the tick it touches a block or water. */
    IMPACT,
    /** A normal TNT fuse: falls, lands, hisses, then explodes (the Reel34 look). */
    FUSE;

    public static PayloadKind byName(String name) {
        for (PayloadKind p : values()) if (p.name().equalsIgnoreCase(name)) return p;
        return null;
    }
}
