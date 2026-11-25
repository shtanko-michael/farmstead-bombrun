package online.farmsteadminecraft.bombrun.config;

import java.util.List;

import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.ModLoaderAccess;
import online.farmsteadminecraft.bombrun.strike.Formation;
import online.farmsteadminecraft.bombrun.strike.PayloadKind;
import online.farmsteadminecraft.bombrun.strike.Pattern;
import online.farmsteadminecraft.bombrun.strike.StrikeLimits;

/**
 * {@code config/farmstead_bombrun-server.toml}: the server's settings (PLAN.md §6). Read at start-up and by
 * {@code /bombrun reload}. In single player the integrated server reads the same file.
 */
public final class BombRunConfig {
    public static final ConfigFile FILE = new ConfigFile(BombRun.MOD_ID, "server", 1).command("bombrun");

    public static final BoolValue ENABLED = FILE.bool("general", "enabled", true)
        .comment("Master switch. Off: no raids, the remote and /bombrun strike are refused.");
    public static final BoolValue CHECK_UPDATES = FILE.bool("general", "checkUpdates", true)
        .comment("Once at start-up, ask Modrinth whether a newer Bomb Run is out and say so in the log. Sends nothing about you.");
    public static final StringListValue DIMENSIONS = FILE.list("general", "dimensions", List.of("minecraft:overworld"))
        .comment("Dimensions where raids are allowed.");

    public static final IntValue MAX_RANGE = FILE.integer("remote", "maxRange", 256, 16, 1024)
        .comment("How far the remote reaches, blocks from the player's eyes. The server checks it again.");
    public static final IntValue RADIUS_MIN = FILE.integer("remote", "radiusMin", 4, 1, 64)
        .comment("Smallest target radius, blocks.");
    public static final IntValue RADIUS_MAX = FILE.integer("remote", "radiusMax", 96, 4, 512)
        .comment("Largest target radius, blocks.");

    public static final IntValue MAX_PLANES = FILE.integer("raid", "maxPlanes", 12, 1, 64)
        .comment("Most planes in one raid.");
    public static final IntValue MAX_BOMBS = FILE.integer("raid", "maxBombs", 2000, 1, 100000)
        .comment("Most bombs in one raid.");
    public static final IntValue MAX_ACTIVE = FILE.integer("raid", "maxActiveRaids", 8, 1, 256)
        .comment("Most raids in the air at once on the whole server.");
    public static final IntValue COOLDOWN = FILE.integer("raid", "cooldownSeconds", 30, 0, 86400)
        .comment("Seconds a player waits between raids called with the remote. Operators wait too.");
    public static final IntValue COUNTDOWN = FILE.integer("raid", "countdownSeconds", 5, 0, 120)
        .comment("Siren countdown: seconds from the call to the first bomb, at least.");
    public static final BoolValue FORCE_LOAD = FILE.bool("raid", "forceLoadTarget", true)
        .comment("Keep the chunks of the target loaded while the raid flies, so bombs fall even with nobody near.");
    public static final BoolValue RESUME = FILE.bool("raid", "resumeOnRestart", false)
        .comment("After a server restart, raids that were in the air carry on. Off: they are cancelled.");

    public static final IntValue PLANES = FILE.integer("strike", "planes", 3, 1, 64)
        .comment("Planes in a raid called with the remote (the remote can change it with Ctrl + scroll).");
    public static final DoubleValue BOMB_SPACING = FILE.number("strike", "bombSpacing", 5.0, 1.0, 32.0)
        .comment("Blocks between bombs when the remote works out how many to drop for the circle.");
    public static final EnumValue<Pattern> PATTERN = FILE.choice("strike", "pattern", Pattern.CARPET)
        .comment("Default pattern: carpet (rows along the run), circle (evenly filled) or random.");
    public static final EnumValue<Formation> FORMATION = FILE.choice("strike", "formation", Formation.WEDGE)
        .comment("How the squadron flies: wedge or line.");
    public static final EnumValue<PayloadKind> PAYLOAD = FILE.choice("strike", "payload", PayloadKind.IMPACT)
        .comment("impact: bombs explode when they hit. fuse: vanilla TNT fuse, they land, hiss and explode.");
    public static final IntValue ALTITUDE = FILE.integer("strike", "altitude", 48, 8, 320)
        .comment("Flight height above the target, blocks.");
    public static final DoubleValue SPEED = FILE.number("strike", "planeSpeed", 1.5, 0.2, 8.0)
        .comment("Plane speed, blocks per tick (1.5 = 30 blocks a second).");
    public static final IntValue FUSE = FILE.integer("strike", "fuseTicks", 80, 1, 400)
        .comment("Fuse of fuse-payload bombs, ticks (vanilla TNT: 80).");

    public static final IntValue MAX_LIVE = FILE.integer("limits", "maxLiveBombs", 600, 1, 100000)
        .comment("Most bombs alive in a dimension. Drops over it wait in the plane until bombs explode.");
    public static final IntValue MAX_PER_TICK = FILE.integer("limits", "maxExplosionsPerTick", 40, 1, 10000)
        .comment("Most bomb explosions in one tick per dimension; the rest wait for the next tick.");

    public static final BoolValue BREAK_BLOCKS = FILE.bool("explosions", "breakBlocks", true)
        .comment("Off: a safe mode for events, bombs hurt but leave blocks whole.");

    public static final BoolValue UNDO = FILE.bool("undo", "enabled", true)
        .comment("Save the target before each raid so /bombrun undo can put it back.");
    public static final IntValue UNDO_KEEP = FILE.integer("undo", "keep", 5, 1, 100)
        .comment("How many raid snapshots to keep per server; older ones are deleted.");
    public static final IntValue UNDO_MAX_BLOCKS = FILE.integer("undo", "maxBlocks", 8_000_000, 1000, 200_000_000)
        .comment("Largest snapshot, blocks. A bigger target flies without undo, with a warning.");
    public static final IntValue UNDO_PER_TICK = FILE.integer("undo", "blocksPerTick", 20_000, 100, 10_000_000)
        .comment("Blocks restored per tick by /bombrun undo.");
    public static final IntValue CAPTURE_PER_TICK = FILE.integer("undo", "captureBlocksPerTick", 200_000, 1000, 10_000_000)
        .comment("Blocks read per tick while the snapshot is taken (before the raid starts).");
    public static final IntValue UNDO_DEPTH = FILE.integer("undo", "depth", 24, 4, 128)
        .comment("How far below the ground the snapshot reaches, blocks.");

    private BombRunConfig() {
    }

    public static List<String> load() {
        List<String> problems = FILE.load(ModLoaderAccess.get().configDir());
        for (String p : problems) BombRun.LOGGER.warn("[Bomb Run] config: {}", p);
        return problems;
    }

    public static StrikeLimits limits() {
        return new StrikeLimits(ENABLED.on(), MAX_RANGE.value(), RADIUS_MIN.value(),
            Math.max(RADIUS_MIN.value(), RADIUS_MAX.value()), MAX_PLANES.value(), MAX_BOMBS.value(),
            COOLDOWN.value() * 20, MAX_ACTIVE.value());
    }

    /** Bombs the remote drops on a circle of {@code radius}: one per {@code bombSpacing}² of area, within the limit. */
    public static int bombsFor(double radius) {
        double s = BOMB_SPACING.value();
        return (int) Math.max(1, Math.min(MAX_BOMBS.value(), Math.round(Math.PI * radius * radius / (s * s))));
    }
}
