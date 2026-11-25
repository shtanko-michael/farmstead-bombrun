package online.farmsteadminecraft.bombrun.strike;

/** How the planes of a squadron fly relative to each other. */
public enum Formation {
    /** A V: the leader in front in the middle, wingmen further back the further out they fly. */
    WEDGE,
    /** Abreast: every plane level with the others. */
    LINE;

    public static Formation byName(String name) {
        for (Formation f : values()) if (f.name().equalsIgnoreCase(name)) return f;
        return null;
    }
}
