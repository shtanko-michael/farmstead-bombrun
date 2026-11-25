package online.farmsteadminecraft.bombrun.client.demo;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.client.raid.ClientRaids;
import online.farmsteadminecraft.bombrun.registry.BombRunContent;
import online.farmsteadminecraft.bombrun.strike.FlightPlan;


/**
 * Development only: with {@code -Dfarmstead_bombrun.demo=true} (the {@code runDemo} Gradle run) the client makes a
 * fresh world, builds a small town on a stone platform, calls one raid on it in front of the camera, saves screenshots
 * at fixed moments, undoes the raid, checks block by block that the town is back, and quits. It logs the server's
 * slowest tick during the raid, so a heavy strike shows whether the explosion budget holds. It is how CI takes the
 * pictures for pull requests. Without the property it does nothing.
 *
 * <p>{@code -Dfarmstead_bombrun.demo.radius=64 -Dfarmstead_bombrun.demo.power=5} turn it into a stress run
 * (the default is radius 40 at power 5, about 2000 bombs).</p>
 */
public final class DemoDirector {
    public static final boolean ENABLED = Boolean.getBoolean("farmstead_bombrun.demo");
    private static final String WORLD = "bombrun-demo";
    private static final int TIMEOUT = 20 * 60 * 6;
    private static final int RADIUS = Integer.getInteger("farmstead_bombrun.demo.radius", 40);
    private static final int POWER = Integer.getInteger("farmstead_bombrun.demo.power", 5);
    /** The town check: a box around the target, from this far below the platform to this far above. */
    private static final int CHECK_BELOW = 20, CHECK_ABOVE = 32;
    private static int[] before;
    private static long worstTickNanos, tickNanosSum;
    private static int ticksSeen;

    private static int stage;
    private static int wait;
    private static int total;
    private static int tx, ty, tz;
    private static double[] shots;
    private static String[] names;
    private static int nextShot;
    private static ClientRaids.ClientRaid raid;

    private DemoDirector() {
    }

    public static void tick(Minecraft mc) {
        if (!ENABLED) return;
        if (stage > 0 && ++total > TIMEOUT && stage < 9) {
            BombRun.LOGGER.error("[demo] timed out at stage {}", stage);
            shot(mc, "9-timeout");
            stage = 9;
            wait = 0;
        }
        switch (stage) {
            case 0 -> {
                if (mc.getOverlay() == null && mc.screen != null && mc.level == null) {
                    BombRun.LOGGER.info("[demo] creating world");
                    createWorld(mc);
                    stage = 1;
                }
            }
            case 1 -> {
                if (mc.level != null && mc.player != null && mc.screen == null && ++wait > 40) {
                    setUp(mc);
                    stage = 2;
                    wait = 0;
                }
            }
            case 2 -> {
                // give the chunks around the view time to load and render
                if (++wait == 240) {
                    shot(mc, "1-target");
                } else if (wait == 250) {
                    server(mc, (server, level, player) -> {
                        before = sample(level);
                        run(server, "bombrun strike " + tx + " " + ty + " " + tz + " " + RADIUS + " power " + POWER + " carpet impact");
                    });
                    stage = 3;
                    wait = 0;
                }
            }
            case 3 -> {
                if (!ClientRaids.all().isEmpty()) {
                    raid = ClientRaids.all().iterator().next();
                    FlightPlan plan = raid.plan;
                    int first = plan.firstDropTick(), last = plan.lastDropTick();
                    shots = new double[] {first - 50, first + 12, first + 40, last + 30, last + 100};
                    names = new String[] {"2-approach", "3-bombs-away", "4-impact", "4b-fire", "5-aftermath"};
                    nextShot = 0;
                    BombRun.LOGGER.info("[demo] raid {} first drop {} last drop {} duration {}", raid.id, first, last, plan.duration());
                    stage = 4;
                }
            }
            case 4 -> {
                MinecraftServer srv = mc.getSingleplayerServer();
                if (srv != null) {
                    long[] times = srv.getTickTimesNanos();
                    long last = times[Math.floorMod(srv.getTickCount() - 1, times.length)];
                    worstTickNanos = Math.max(worstTickNanos, last);
                    tickNanosSum += last;
                    ticksSeen++;
                    if (ticksSeen % 20 == 0) {
                        srv.execute(() -> {
                            var b = online.farmsteadminecraft.bombrun.raid.RaidManager.get().bombs(srv.overworld()).budget();
                            BombRun.LOGGER.info("[demo] t={} live {} allowance {} last tick {} ms exploded {}", ticksSeen, b.live(),
                                b.allowance(online.farmsteadminecraft.bombrun.config.BombRunConfig.MAX_PER_TICK.value()),
                                String.format(java.util.Locale.ROOT, "%.1f", b.lastTickMs()), b.explosions());
                        });
                    }
                }
                double t = raid.time(mc, 0);
                if (nextShot < shots.length && t >= shots[nextShot]) {
                    shot(mc, names[nextShot]);
                    nextShot++;
                }
                if (nextShot >= shots.length && t > raid.plan.duration() + 60) {
                    BombRun.LOGGER.info("[demo] raid of {} bombs: server tick worst {} ms, mean {} ms over {} client ticks",
                        raid.plan.drops(), worstTickNanos / 1_000_000, ticksSeen == 0 ? 0 : tickNanosSum / ticksSeen / 1_000_000, ticksSeen);
                    server(mc, (server, level, player) -> {
                        for (var line : online.farmsteadminecraft.bombrun.raid.RaidManager.get().stats()) {
                            BombRun.LOGGER.info("[demo] {}", line.getString());
                        }
                        BombRun.LOGGER.info("[demo] blocks changed by the raid: {}", differ(level));
                        run(server, "bombrun undo " + raid.id);
                    });
                    stage = 5;
                    wait = 0;
                }
            }
            case 5 -> {
                if (++wait == 400) {
                    shot(mc, "6-undone");
                    server(mc, (server, level, player) -> {
                        int diff = differ(level);
                        BombRun.LOGGER.info("[demo] undo check: {} blocks differ from before the raid{}", diff, diff == 0 ? " (OK)" : " (FAILED)");
                    });
                    stage = 9;
                    wait = 0;
                }
            }
            case 9 -> {
                if (++wait == 40) {
                    BombRun.LOGGER.info("[demo] done");
                    mc.stop();
                }
            }
            default -> {
            }
        }
    }

    private static void createWorld(Minecraft mc) {
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(),
            WorldDataConfiguration.DEFAULT);
        WorldOptions options = new WorldOptions(20261125L, true, false);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, options,
            registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.NORMAL).value()
                .createWorldDimensions(),
            mc.screen);
    }

    private static void setUp(Minecraft mc) {
        mc.options.hideGui = false;
        mc.player.getInventory().selected = 0;
        server(mc, (server, level, player) -> {
            run(server, "gamerule doDaylightCycle false");
            run(server, "gamerule doWeatherCycle false");
            run(server, "gamerule doMobSpawning false");
            run(server, "time set 6000");
            run(server, "weather clear");
            BlockPos spawn = level.getSharedSpawnPos();
            tx = spawn.getX();
            tz = spawn.getZ() + 40 + RADIUS;
            level.getChunk(tx >> 4, tz >> 4);
            ty = Math.max(level.getSeaLevel() + 2, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz));
            buildTown(level);
            int back = 40 + RADIUS * 2, vx = tx, vz = tz - back, vy = ty + 20 + RADIUS / 2;
            level.setBlockAndUpdate(new BlockPos(vx, vy - 1, vz), Blocks.GLASS.defaultBlockState());
            float pitch = (float) Math.toDegrees(Math.atan2(vy + 1.62 - ty, back));
            player.teleportTo(level, vx + 0.5, vy, vz + 0.5, 0f, pitch);
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, new ItemStack(BombRunContent.strikeRemote.get()));
            BombRun.LOGGER.info("[demo] target {} {} {}, camera {} {} {} pitch {}", tx, ty, tz, vx, vy, vz, pitch);
        });
    }

    /**
     * A stone platform over the whole circle with the air above it cleared, and a grid of small houses and two
     * towers on it: something for the bombs to knock down and for the undo to put back.
     */
    private static void buildTown(ServerLevel level) {
        int reach = RADIUS + 12;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = tx - reach; x <= tx + reach; x++) {
            for (int z = tz - reach; z <= tz + reach; z++) {
                for (int y = ty - 3; y <= ty + CHECK_ABOVE + 8; y++) {
                    p.set(x, y, z);
                    level.setBlock(p, y <= ty ? (y == ty ? Blocks.SMOOTH_STONE : Blocks.STONE).defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        for (int gx = -RADIUS + 4; gx <= RADIUS - 10; gx += 12) {
            for (int gz = -RADIUS + 4; gz <= RADIUS - 10; gz += 12) {
                if ((gx + 3) * (gx + 3) + (gz + 3) * (gz + 3) > (RADIUS - 6) * (RADIUS - 6)) continue;
                house(level, tx + gx, ty + 1, tz + gz);
            }
        }
        tower(level, tx - RADIUS / 3, ty + 1, tz + RADIUS / 4);
        tower(level, tx + RADIUS / 4, ty + 1, tz - RADIUS / 3);
    }

    private static void house(ServerLevel level, int x0, int y0, int z0) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 7; x++) {
            for (int z = 0; z < 7; z++) {
                for (int y = 0; y < 6; y++) {
                    boolean wall = x == 0 || z == 0 || x == 6 || z == 6;
                    p.set(x0 + x, y0 + y, z0 + z);
                    if (y == 5) level.setBlock(p, Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
                    else if (wall && y == 2 && (x == 3 || z == 3)) level.setBlock(p, Blocks.GLASS.defaultBlockState(), 2);
                    else if (wall) level.setBlock(p, (y == 0 ? Blocks.COBBLESTONE : Blocks.OAK_PLANKS).defaultBlockState(), 2);
                }
            }
        }
    }

    private static void tower(ServerLevel level, int x0, int y0, int z0) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                for (int y = 0; y < 22; y++) {
                    if (x == 0 || z == 0 || x == 4 || z == 4 || y == 21) {
                        level.setBlock(p.set(x0 + x, y0 + y, z0 + z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                    }
                }
            }
        }
    }

    /** Block ids in the check box, column by column. */
    private static int[] sample(ServerLevel level) {
        int reach = RADIUS + 8, side = 2 * reach + 1, height = CHECK_BELOW + CHECK_ABOVE + 1;
        int[] out = new int[side * side * height];
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int x = tx - reach; x <= tx + reach; x++) {
            for (int z = tz - reach; z <= tz + reach; z++) {
                for (int y = ty - CHECK_BELOW; y <= ty + CHECK_ABOVE; y++) {
                    out[i++] = net.minecraft.world.level.block.Block.getId(level.getBlockState(p.set(x, y, z)));
                }
            }
        }
        return out;
    }

    private static int differ(ServerLevel level) {
        if (before == null) return -1;
        int[] now = sample(level);
        int n = 0;
        java.util.Map<String, Integer> kinds = new java.util.TreeMap<>();
        int reach = RADIUS + 8, height = CHECK_BELOW + CHECK_ABOVE + 1, side = 2 * reach + 1;
        for (int i = 0; i < now.length; i++) {
            if (now[i] == before[i]) continue;
            n++;
            int dx = i / (side * height) - reach, dz = (i / height) % side - reach, dy = i % height - CHECK_BELOW;
            String k = net.minecraft.world.level.block.Block.stateById(before[i]).getBlock().getDescriptionId() + " -> "
                + net.minecraft.world.level.block.Block.stateById(now[i]).getBlock().getDescriptionId()
                + " r=" + (int) Math.sqrt(dx * dx + dz * dz) / 4 * 4 + " dy=" + dy / 4 * 4;
            kinds.merge(k, 1, Integer::sum);
        }
        if (n > 0 && n < 5000) kinds.forEach((k, v) -> BombRun.LOGGER.info("[demo]   {} x{}", k, v));
        return n;
    }

    private interface ServerJob {
        void run(MinecraftServer server, ServerLevel level, ServerPlayer player);
    }

    private static void server(Minecraft mc, ServerJob job) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        server.execute(() -> {
            ServerLevel level = server.overworld();
            ServerPlayer player = server.getPlayerList().getPlayers().isEmpty() ? null : server.getPlayerList().getPlayers().get(0);
            if (player != null) job.run(server, level, player);
        });
    }

    private static void run(MinecraftServer server, String command) {
        CommandSourceStack src = server.createCommandSourceStack();
        server.getCommands().performPrefixedCommand(src, command);
    }

    private static void shot(Minecraft mc, String name) {
        BombRun.LOGGER.info("[demo] screenshot {}", name);
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
            msg -> BombRun.LOGGER.info("[demo] {}", msg.getString()));
    }
}
