package online.farmsteadminecraft.bombrun.command;

import java.util.Arrays;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.config.BombRunConfig;
import online.farmsteadminecraft.bombrun.perm.Permissions;
import online.farmsteadminecraft.bombrun.raid.Raid;
import online.farmsteadminecraft.bombrun.raid.RaidManager;
import online.farmsteadminecraft.bombrun.raid.RaidSavedData;
import online.farmsteadminecraft.bombrun.strike.Denial;
import online.farmsteadminecraft.bombrun.strike.Formation;
import online.farmsteadminecraft.bombrun.strike.PayloadKind;
import online.farmsteadminecraft.bombrun.strike.Pattern;
import online.farmsteadminecraft.bombrun.strike.StrikeOrder;
import online.farmsteadminecraft.bombrun.strike.StrikeRequest;
import online.farmsteadminecraft.bombrun.strike.StrikeValidator;

/**
 * {@code /bombrun} (PLAN.md §5.9): strike, cancel, undo, snapshots, stats, reload. Presets come with the
 * console in M2.
 *
 * <pre>
 * /bombrun strike &lt;pos&gt; [radius] [planes] [bombs] [pattern] [payload] [formation] [heading] [altitude]
 * </pre>
 * Arguments left out take the server's defaults; {@code bombs} defaults to what the remote would drop on that
 * circle, {@code heading} to the direction the caller faces.
 */
public final class BombRunCommand {
    private static final List<String> PATTERNS = Arrays.stream(Pattern.values()).map(p -> p.name().toLowerCase()).toList();
    private static final List<String> PAYLOADS = Arrays.stream(PayloadKind.values()).map(p -> p.name().toLowerCase()).toList();
    private static final List<String> FORMATIONS = Arrays.stream(Formation.values()).map(p -> p.name().toLowerCase()).toList();

    private BombRunCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bombrun")
            .requires(Permissions::mayCommand)
            .then(Commands.literal("strike").then(strikeArgs()))
            .then(Commands.literal("cancel")
                .executes(c -> cancelAll(c.getSource()))
                .then(Commands.literal("all").executes(c -> cancelAll(c.getSource())))
                .then(Commands.argument("raid", IntegerArgumentType.integer(1))
                    .executes(c -> cancel(c.getSource(), IntegerArgumentType.getInteger(c, "raid")))))
            .then(Commands.literal("undo")
                .executes(c -> undo(c.getSource(), null))
                .then(Commands.argument("raid", IntegerArgumentType.integer(1))
                    .executes(c -> undo(c.getSource(), IntegerArgumentType.getInteger(c, "raid")))))
            .then(Commands.literal("snapshots").executes(c -> snapshots(c.getSource())))
            .then(Commands.literal("raids").executes(c -> raids(c.getSource())))
            .then(Commands.literal("stats").executes(c -> stats(c.getSource())))
            .then(Commands.literal("reload").executes(c -> reload(c.getSource()))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> strikeArgs() {
        String[] names = {"radius", "planes", "bombs", "pattern", "payload", "formation", "heading", "altitude"};
        // built from the innermost argument outwards, so each one is optional after the ones before it
        ArgumentBuilder<CommandSourceStack, ?> next = null;
        for (int i = names.length - 1; i >= 0; i--) {
            ArgumentBuilder<CommandSourceStack, ?> arg = argument(names[i]).executes(BombRunCommand::strike);
            if (next != null) arg.then(next);
            next = arg;
        }
        return Commands.argument("pos", Vec3Argument.vec3()).executes(BombRunCommand::strike).then(next);
    }

    private static ArgumentBuilder<CommandSourceStack, ?> argument(String name) {
        return switch (name) {
            case "radius" -> Commands.argument(name, DoubleArgumentType.doubleArg(1, 512));
            case "planes" -> Commands.argument(name, IntegerArgumentType.integer(1, 64));
            case "bombs" -> Commands.argument(name, IntegerArgumentType.integer(0, 100000));
            case "heading" -> Commands.argument(name, DoubleArgumentType.doubleArg(-360, 360));
            case "altitude" -> Commands.argument(name, IntegerArgumentType.integer(8, 512));
            case "pattern" -> Commands.argument(name, StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(PATTERNS, b));
            case "payload" -> Commands.argument(name, StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(PAYLOADS, b));
            case "formation" -> Commands.argument(name, StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(FORMATIONS, b));
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static <T> T opt(CommandContext<CommandSourceStack> c, String name, Class<T> type, T def) {
        try {
            return c.getArgument(name, type);
        } catch (IllegalArgumentException e) {
            return def;
        }
    }

    private static int strike(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        CommandSourceStack src = c.getSource();
        RaidManager manager = RaidManager.get();
        if (manager == null) return 0;
        ServerLevel level = src.getLevel();
        Vec3 pos = Vec3Argument.getVec3(c, "pos");
        double radius = opt(c, "radius", Double.class, 16.0);
        int planes = opt(c, "planes", Integer.class, BombRunConfig.PLANES.value());
        int bombs = opt(c, "bombs", Integer.class, BombRunConfig.bombsFor(radius));
        Pattern pattern = parse(src, opt(c, "pattern", String.class, null), Pattern::byName, BombRunConfig.PATTERN.get(), PATTERNS);
        PayloadKind payload = parse(src, opt(c, "payload", String.class, null), PayloadKind::byName, BombRunConfig.PAYLOAD.get(), PAYLOADS);
        Formation formation = parse(src, opt(c, "formation", String.class, null), Formation::byName, BombRunConfig.FORMATION.get(), FORMATIONS);
        if (pattern == null || payload == null || formation == null) return 0;
        double heading = opt(c, "heading", Double.class, (double) src.getRotation().y);
        Integer altitude = opt(c, "altitude", Integer.class, null);

        StrikeRequest q = new StrikeRequest(false, true, RaidManager.dimensionAllowed(level), 0, radius, planes, bombs, 0, true,
            manager.raids().size());
        Denial d = StrikeValidator.check(q, BombRunConfig.limits());
        if (d != null) {
            src.sendFailure(Component.translatable(d.key(), d.args()));
            return 0;
        }
        StrikeOrder base = RaidManager.order(level, pos.x, pos.y, pos.z, radius, heading, planes, bombs, pattern);
        StrikeOrder order = new StrikeOrder(base.x(), base.y(), base.z(), radius, heading, planes, bombs, pattern, formation, payload,
            altitude != null ? altitude : base.altitude(), base.speed(), base.leadTicks(), base.approachMin(), base.seed());
        Raid raid = manager.start(level, order, src.getPlayer(), BombRunConfig.FUSE.value());
        src.sendSuccess(() -> Component.translatable("farmstead_bombrun.raid.called", raid.id, planes, bombs), true);
        return raid.id;
    }

    private static <E> E parse(CommandSourceStack src, String text, java.util.function.Function<String, E> by, E def, List<String> known) {
        if (text == null) return def;
        E e = by.apply(text);
        if (e == null) src.sendFailure(Component.translatable("farmstead_bombrun.command.unknown", text, String.join(", ", known)));
        return e;
    }

    private static int cancel(CommandSourceStack src, int id) {
        RaidManager m = RaidManager.get();
        if (m == null || !m.cancel(id)) {
            src.sendFailure(Component.translatable("farmstead_bombrun.cancel.unknown", id));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("farmstead_bombrun.cancel.done", id), true);
        return 1;
    }

    private static int cancelAll(CommandSourceStack src) {
        RaidManager m = RaidManager.get();
        int n = m == null ? 0 : m.cancelAll();
        src.sendSuccess(() -> Component.translatable("farmstead_bombrun.cancel.all", n), true);
        return n;
    }

    private static int undo(CommandSourceStack src, Integer raid) {
        RaidManager m = RaidManager.get();
        if (m == null) return 0;
        Component error = m.undo(raid, msg -> src.sendSuccess(() -> msg, true));
        if (error != null) {
            src.sendFailure(error);
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("farmstead_bombrun.undo.started"), true);
        return 1;
    }

    private static int snapshots(CommandSourceStack src) {
        RaidManager m = RaidManager.get();
        if (m == null) return 0;
        List<RaidSavedData.SnapshotEntry> list = m.snapshots();
        if (list.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("farmstead_bombrun.undo.none"), false);
            return 0;
        }
        for (RaidSavedData.SnapshotEntry e : list) {
            src.sendSuccess(() -> Component.translatable("farmstead_bombrun.snapshots.line", e.raidId(), e.dimension(), e.x(), e.y(),
                e.z(), e.radius(), e.blocks()), false);
        }
        return list.size();
    }

    private static int raids(CommandSourceStack src) {
        RaidManager m = RaidManager.get();
        if (m == null || m.raids().isEmpty()) {
            src.sendSuccess(() -> Component.translatable("farmstead_bombrun.raids.none"), false);
            return 0;
        }
        for (Raid r : m.raids().values()) {
            src.sendSuccess(() -> Component.translatable("farmstead_bombrun.raids.line", r.id, r.phase().name().toLowerCase(),
                r.target().getX(), r.target().getY(), r.target().getZ(), r.dropped(), r.plan.drops()), false);
        }
        return m.raids().size();
    }

    private static int stats(CommandSourceStack src) {
        RaidManager m = RaidManager.get();
        if (m == null) return 0;
        for (Component line : m.stats()) src.sendSuccess(() -> line, false);
        return 1;
    }

    private static int reload(CommandSourceStack src) {
        List<String> problems = BombRunConfig.load();
        if (problems.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("farmstead_bombrun.reload.done"), true);
        } else {
            for (String p : problems) src.sendFailure(Component.literal(p).withStyle(ChatFormatting.YELLOW));
        }
        return 1;
    }
}
