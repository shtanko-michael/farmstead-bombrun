package online.farmsteadminecraft.bombrun.config;

import java.util.List;

import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.ModLoaderAccess;

/** {@code config/farmstead_bombrun-client.toml}: what this player sees and hears (PLAN.md §6). */
public final class BombRunClientConfig {
    public static final ConfigFile FILE = new ConfigFile(BombRun.MOD_ID, "client", 1).command("bombrun");

    public static final IntValue MAX_ENGINES = FILE.integer("sound", "maxEngines", 6, 0, 32)
        .comment("Engine sounds playing at once; further planes fly silently.");
    public static final IntValue WHISTLES = FILE.integer("sound", "whistles", 12, 0, 200)
        .comment("The first this many bombs of a raid whistle as they fall.");
    public static final BoolValue SIREN = FILE.bool("sound", "siren", true)
        .comment("Siren at the target during the countdown.");

    public static final StringValue MARKER_COLOR = FILE.string("marker", "color", "#FF3B30")
        .comment("Colour of the target ring, #RRGGBB.");
    public static final DoubleValue MARKER_OPACITY = FILE.number("marker", "opacity", 0.7, 0.05, 1.0)
        .comment("How strongly the target ring shows.");

    public static final DoubleValue PLANE_SCALE = FILE.number("render", "planeScale", 2.0, 0.5, 8.0)
        .comment("Size of the planes; 1 is a 3-block fuselage.");
    public static final IntValue RENDER_DISTANCE = FILE.integer("render", "planeDistance", 512, 64, 4096)
        .comment("Planes further than this from the camera are not drawn, blocks.");

    private BombRunClientConfig() {
    }

    public static List<String> load() {
        List<String> problems = FILE.load(ModLoaderAccess.get().configDir());
        for (String p : problems) BombRun.LOGGER.warn("[Bomb Run] client config: {}", p);
        return problems;
    }

    /** The ring colour as 0xRRGGBB; red if the setting does not parse. */
    public static int markerRgb() {
        String s = MARKER_COLOR.get().trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            return Integer.parseInt(s, 16) & 0xFFFFFF;
        } catch (NumberFormatException e) {
            return 0xFF3B30;
        }
    }
}
