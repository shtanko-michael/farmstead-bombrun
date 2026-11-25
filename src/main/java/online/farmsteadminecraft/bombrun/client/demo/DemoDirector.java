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
 * fresh world, calls one raid in front of the camera, saves screenshots at fixed moments, undoes the raid and quits.
 * It is how CI takes the pictures for pull requests. Without the property it does nothing.
 */
public final class DemoDirector {
    public static final boolean ENABLED = Boolean.getBoolean("farmstead_bombrun.demo");
    private static final String WORLD = "bombrun-demo";
    private static final int TIMEOUT = 20 * 60 * 6;

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
                    server(mc, (server, level, player) -> run(server, "bombrun strike " + tx + " " + ty + " " + tz
                        + " 20 3 160 carpet impact wedge 90 40"));
                    stage = 3;
                    wait = 0;
                }
            }
            case 3 -> {
                if (!ClientRaids.all().isEmpty()) {
                    raid = ClientRaids.all().iterator().next();
                    FlightPlan plan = raid.plan;
                    int first = plan.firstDropTick(), last = plan.lastDropTick();
                    shots = new double[] {first - 50, first + 12, first + 40, last + 100};
                    names = new String[] {"2-approach", "3-bombs-away", "4-impact", "5-aftermath"};
                    nextShot = 0;
                    BombRun.LOGGER.info("[demo] raid {} first drop {} last drop {} duration {}", raid.id, first, last, plan.duration());
                    stage = 4;
                }
            }
            case 4 -> {
                double t = raid.time(mc, 0);
                if (nextShot < shots.length && t >= shots[nextShot]) {
                    shot(mc, names[nextShot]);
                    nextShot++;
                }
                if (nextShot >= shots.length && t > raid.plan.duration() + 60) {
                    server(mc, (server, level, player) -> run(server, "bombrun undo"));
                    stage = 5;
                    wait = 0;
                }
            }
            case 5 -> {
                if (++wait == 400) {
                    shot(mc, "6-undone");
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
            tz = spawn.getZ() + 40;
            level.getChunk(tx >> 4, tz >> 4);
            ty = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx, tz);
            int vx = tx, vz = tz - 75, vy = ty + 35;
            level.setBlockAndUpdate(new BlockPos(vx, vy - 1, vz), Blocks.GLASS.defaultBlockState());
            float pitch = (float) Math.toDegrees(Math.atan2(vy + 1.62 - ty, 75));
            player.teleportTo(level, vx + 0.5, vy, vz + 0.5, 0f, pitch);
            player.getInventory().selected = 0;
            player.getInventory().setItem(0, new ItemStack(BombRunContent.strikeRemote.get()));
            BombRun.LOGGER.info("[demo] target {} {} {}, camera {} {} {} pitch {}", tx, ty, tz, vx, vy, vz, pitch);
        });
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
