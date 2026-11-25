package online.farmsteadminecraft.bombrun.config;

import java.util.List;

import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.ModLoaderAccess;
import online.farmsteadminecraft.bombrun.strike.Formation;
import online.farmsteadminecraft.bombrun.strike.PayloadKind;
import online.farmsteadminecraft.bombrun.strike.Pattern;
import online.farmsteadminecraft.bombrun.strike.StrikeLimits;
import online.farmsteadminecraft.bombrun.strike.StrikePower;

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
    public static final IntValue MAX_BOMBS = FILE.integer("raid", "maxBombs", 5000, 1, 100000)
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

    public static final IntValue POWER = FILE.integer("strike", "power", 2, 1, 16)
        .comment("Strike power the remote starts at and /bombrun strike uses without planes and bombs (1 = the first of powerLevels).");
    public static final StringListValue POWER_LEVELS = FILE.list("strike", "powerLevels", StrikePower.DEFAULTS)
        .comment("Strike power levels, weakest first, each \"planes:spacing\": how many planes fly and how many blocks apart"
            + " their bombs land. Bombs = circle area / spacing², at most raid.maxBombs. The remote picks one with Ctrl + scroll.");
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
    public static final IntValue APPROACH = FILE.integer("strike", "approachDistance", 520, 64, 4096)
        .comment("How far from the target circle the planes appear and disappear, blocks. Further than the planes' render"
            + " distance (client render.planeDistance, 512) they fly in from beyond sight instead of popping up in the sky.");
    public static final IntValue FUSE = FILE.integer("strike", "fuseTicks", 80, 1, 400)
        .comment("Fuse of fuse-payload bombs, ticks (vanilla TNT: 80).");

    public static final IntValue MAX_LIVE = FILE.integer("limits", "maxLiveBombs", 1500, 1, 100000)
        .comment("Most bombs alive in a dimension. Drops over it wait in the plane until bombs explode.");
    public static final IntValue MAX_PER_TICK = FILE.integer("limits", "maxExplosionsPerTick", 48, 1, 10000)
        .comment("Most bomb explosions in one tick per dimension, however fast the server is.");
    public static final IntValue MIN_PER_TICK = FILE.integer("limits", "minExplosionsPerTick", 6, 1, 10000)
        .comment("Bomb explosions per tick the server always gets through, however slow it is.");
    public static final IntValue TARGET_TICK_MS = FILE.integer("limits", "targetTickMs", 35, 5, 1000)
        .comment("The explosion budget adapts to the server: while a tick takes longer than this (ms), fewer bombs explode"
            + " per tick, while it is well under, more. 50 ms is 20 TPS; 35 leaves room for everything else.");

    public static final BoolValue BREAK_BLOCKS = FILE.bool("explosions", "breakBlocks", true)
        .comment("Off: a safe mode for events, bombs hurt but leave blocks whole.");
    public static final IntValue EFFECT_DISTANCE = FILE.integer("explosions", "effectDistance", 512, 64, 4096)
        .comment("Players this close to a bomb see and hear it go off (vanilla stops at 64 blocks), blocks.");
    public static final BoolValue DROP_ITEMS = FILE.bool("explosions", "dropItems", false)
        .comment("Blocks broken by bombs drop as items. Off by default: a 5000-bomb raid would leave tens of thousands of items.");
    public static final BoolValue BOMBS_IGNORE_BLASTS = FILE.bool("explosions", "bombsIgnoreBlasts", true)
        .comment("Falling bombs are not pushed by other bombs' blasts: they land where they were aimed, and each blast"
            + " skips the costly line-of-sight check for every bomb near it.");

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
        StrikePower.parseAll(POWER_LEVELS.get(), problems);
        for (String p : problems) BombRun.LOGGER.warn("[Bomb Run] config: {}", p);
        return problems;
    }

    public static StrikeLimits limits() {
        return new StrikeLimits(ENABLED.on(), MAX_RANGE.value(), RADIUS_MIN.value(),
            Math.max(RADIUS_MIN.value(), RADIUS_MAX.value()), MAX_PLANES.value(), MAX_BOMBS.value(),
            COOLDOWN.value() * 20, MAX_ACTIVE.value());
    }

    /** The power levels of the config, weakest first (never empty). */
    public static List<StrikePower> powers() {
        return StrikePower.parseAll(POWER_LEVELS.get(), null);
    }

    /** Power level {@code level} (1-based), clamped to the levels there are. */
    public static StrikePower power(int level) {
        List<StrikePower> all = powers();
        return all.get(Math.max(1, Math.min(level, all.size())) - 1);
    }

    /** The default power level, clamped to the levels there are. */
    public static int defaultPower() {
        return Math.max(1, Math.min(POWER.value(), powers().size()));
    }
}
