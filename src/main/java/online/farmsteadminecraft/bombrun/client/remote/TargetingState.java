package online.farmsteadminecraft.bombrun.client.remote;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.net.Net;
import online.farmsteadminecraft.bombrun.net.RequestStrikePayload;
import online.farmsteadminecraft.bombrun.registry.BombRunContent;
import online.farmsteadminecraft.bombrun.strike.Pattern;

/**
 * Aiming with the remote (PLAN.md §5.5): a ray from the camera finds the target, the wheel sets the radius,
 * Alt + wheel turns the run, Ctrl + wheel the number of planes, Shift + wheel the pattern, left click calls
 * the raid. The server checks everything again.
 */
public final class TargetingState {
    /** How far the ray reaches; the server's {@code remote.maxRange} decides in the end. */
    public static final double RAY = 256;
    public static final double RADIUS_MIN = 4, RADIUS_MAX = 96;
    public static final int PLANES_MAX = 12;

    private static double radius = 16;
    private static double headingOffset;
    private static int planes = 3;
    private static Pattern pattern = Pattern.CARPET;
    private static Vec3 target;
    private static boolean holding;
    private static int clickCooldown;

    private TargetingState() {
    }

    public static boolean holding() {
        return holding;
    }

    /** The aimed point, or null when the ray hits nothing or the remote is not in hand. */
    public static Vec3 target() {
        return holding ? target : null;
    }

    public static double radius() {
        return radius;
    }

    /** The run's direction: where the player looks, turned by the Alt + wheel offset. */
    public static double heading(Minecraft mc) {
        float yaw = mc.player == null ? 0 : mc.player.getYRot();
        return Mth.wrapDegrees(yaw + headingOffset);
    }

    private static boolean isRemote(ItemStack stack) {
        return BombRunContent.strikeRemote != null && stack.is(BombRunContent.strikeRemote.get());
    }

    public static void tick(Minecraft mc) {
        if (clickCooldown > 0) clickCooldown--;
        holding = mc.player != null && mc.level != null
            && (isRemote(mc.player.getItemInHand(InteractionHand.MAIN_HAND)) || isRemote(mc.player.getItemInHand(InteractionHand.OFF_HAND)));
        if (!holding) {
            target = null;
            return;
        }
        HitResult hit = mc.player.pick(RAY, 1f, false);
        target = hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getLocation() : null;
    }

    /** The wheel moved; true if the remote used it (the hotbar then stays put). */
    public static boolean scroll(Minecraft mc, double delta) {
        if (!holding || delta == 0) return false;
        int step = delta > 0 ? 1 : -1;
        if (Screen.hasAltDown()) {
            headingOffset = Mth.wrapDegrees(headingOffset + 15 * step);
        } else if (Screen.hasControlDown()) {
            planes = Mth.clamp(planes + step, 1, PLANES_MAX);
        } else if (Screen.hasShiftDown()) {
            Pattern[] all = Pattern.values();
            pattern = all[Math.floorMod(pattern.ordinal() + step, all.length)];
        } else {
            double inc = radius < 16 ? 1 : radius < 48 ? 2 : 4;
            radius = Mth.clamp(radius + inc * step, RADIUS_MIN, RADIUS_MAX);
        }
        showStatus(mc);
        return true;
    }

    private static void showStatus(Minecraft mc) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.translatable("farmstead_bombrun.remote.status", (int) radius, planes,
            Component.translatable("farmstead_bombrun.pattern." + pattern.name().toLowerCase()), (int) headingOffset), true);
    }

    /** Left click; true if the remote took it (no block is hit, no swing). */
    public static boolean attack(Minecraft mc) {
        if (!holding) return false;
        if (clickCooldown > 0) return true;
        clickCooldown = 10;
        if (target == null) {
            mc.player.displayClientMessage(Component.translatable("farmstead_bombrun.remote.no_target"), true);
            return true;
        }
        Net.get().toServer(new RequestStrikePayload(target.x, target.y, target.z, (float) radius, (float) heading(mc), planes,
            pattern.name()));
        if (BombRunContent.confirm != null) {
            mc.player.playNotifySound(BombRunContent.confirm.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        return true;
    }
}
