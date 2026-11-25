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
import online.farmsteadminecraft.bombrun.net.ServerCapsPayload;
import online.farmsteadminecraft.bombrun.registry.BombRunContent;
import online.farmsteadminecraft.bombrun.strike.Pattern;

/**
 * Aiming with the remote (PLAN.md §5.5): a ray from the camera finds the target, the wheel sets the radius,
 * Alt + wheel turns the run, Ctrl + wheel the strike power (planes and bomb density), Shift + wheel the
 * pattern, left click calls the raid. The limits and power levels come from the server ({@link ServerCapsPayload});
 * the server checks everything again.
 */
public final class TargetingState {
    /** Until the server says otherwise (single player before the first packet, a server without the mod). */
    private static final ServerCapsPayload DEFAULT_CAPS = new ServerCapsPayload(256, 4, 96, 12, 5000, 2,
        new int[] {1, 3, 6, 9, 12}, new float[] {8f, 5f, 3.5f, 2.5f, 1.6f});

    private static ServerCapsPayload caps = DEFAULT_CAPS;
    private static double radius = 16;
    private static double headingOffset;
    private static int power = DEFAULT_CAPS.defaultPower();
    private static boolean powerChosen;
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

    /** The server's limits arrived (joining, or {@code /bombrun reload}). */
    public static void caps(ServerCapsPayload p) {
        caps = p.planes().length == 0 ? DEFAULT_CAPS : p;
        radius = Mth.clamp(radius, caps.radiusMin(), caps.radiusMax());
        if (!powerChosen) power = caps.defaultPower();
        power = Mth.clamp(power, 1, levels());
    }

    /** Left the world: the next server sends its own limits. */
    public static void reset() {
        caps = DEFAULT_CAPS;
        powerChosen = false;
        power = caps.defaultPower();
    }

    private static int levels() {
        return Math.min(caps.planes().length, caps.spacing().length);
    }

    public static int power() {
        return power;
    }

    /** Planes the server will send at the current power. */
    public static int planes() {
        return Math.min(caps.planes()[power - 1], caps.maxPlanes());
    }

    /** Bombs the server will drop on the current circle at the current power. */
    public static int bombs() {
        double s = caps.spacing()[power - 1];
        long n = Math.round(Math.PI * radius * radius / (s * s));
        return (int) Math.max(1, Math.min(caps.maxBombs(), n));
    }

    /** True when the aimed point is within the server's range: the ring is drawn in the marker colour, else grey-red. */
    public static boolean inRange(Minecraft mc) {
        return target == null || mc.player == null || mc.player.getEyePosition().distanceTo(target) <= caps.maxRange();
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
        HitResult hit = mc.player.pick(Math.max(caps.maxRange() + 64, 128), 1f, false);
        target = hit.getType() == HitResult.Type.BLOCK ? ((BlockHitResult) hit).getLocation() : null;
    }

    /** The wheel moved; true if the remote used it (the hotbar then stays put). */
    public static boolean scroll(Minecraft mc, double delta) {
        if (!holding || delta == 0) return false;
        int step = delta > 0 ? 1 : -1;
        if (Screen.hasAltDown()) {
            headingOffset = Mth.wrapDegrees(headingOffset + 15 * step);
        } else if (Screen.hasControlDown()) {
            power = Mth.clamp(power + step, 1, levels());
            powerChosen = true;
        } else if (Screen.hasShiftDown()) {
            Pattern[] all = Pattern.values();
            pattern = all[Math.floorMod(pattern.ordinal() + step, all.length)];
        } else {
            double inc = radius < 16 ? 1 : radius < 48 ? 2 : 4;
            radius = Mth.clamp(radius + inc * step, caps.radiusMin(), caps.radiusMax());
        }
        showStatus(mc);
        return true;
    }

    private static void showStatus(Minecraft mc) {
        if (mc.player == null) return;
        mc.player.displayClientMessage(Component.translatable("farmstead_bombrun.remote.status", powerName(power), planes(), bombs(),
            (int) radius, Component.translatable("farmstead_bombrun.pattern." + pattern.name().toLowerCase()), (int) headingOffset), true);
    }

    /** "Heavy" for the default five levels, "Power 7" past them. */
    public static Component powerName(int level) {
        return level <= 5 ? Component.translatable("farmstead_bombrun.power." + level)
            : Component.translatable("farmstead_bombrun.power.n", level);
    }

    /** Left click; true if the remote took it (no block is hit, no swing). */
    public static boolean attack(Minecraft mc) {
        if (!holding) return false;
        // the demo run calls its own raid; a stray click on its window must not add one
        if (clickCooldown > 0 || online.farmsteadminecraft.bombrun.client.demo.DemoDirector.ENABLED) return true;
        clickCooldown = 10;
        if (target == null) {
            mc.player.displayClientMessage(Component.translatable("farmstead_bombrun.remote.no_target"), true);
            return true;
        }
        if (!inRange(mc)) {
            mc.player.displayClientMessage(Component.translatable("farmstead_bombrun.deny.range", caps.maxRange()), true);
            return true;
        }
        Net.get().toServer(new RequestStrikePayload(target.x, target.y, target.z, (float) radius, (float) heading(mc), power,
            pattern.name()));
        if (BombRunContent.confirm != null) {
            mc.player.playNotifySound(BombRunContent.confirm.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        }
        return true;
    }
}
