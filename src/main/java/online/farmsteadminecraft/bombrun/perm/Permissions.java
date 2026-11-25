package online.farmsteadminecraft.bombrun.perm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

/**
 * Who may do what. M1 keeps to vanilla permission levels (operator = level 2, everyone in single player);
 * the LuckPerms nodes and meta of PLAN.md §7 replace these bodies in M3 without changing the callers.
 */
public final class Permissions {
    public static final int OP = 2;

    private Permissions() {
    }

    /** {@code farmstead_bombrun.use}: call a raid with the remote. */
    public static boolean mayUse(ServerPlayer player) {
        return player.hasPermissions(OP) || player.server.isSingleplayerOwner(player.getGameProfile());
    }

    /** {@code farmstead_bombrun.bypass.cooldown}. */
    public static boolean bypassCooldown(ServerPlayer player) {
        return false;
    }

    /** {@code farmstead_bombrun.command.*}: every /bombrun subcommand. */
    public static boolean mayCommand(CommandSourceStack source) {
        return source.hasPermission(OP);
    }
}
