package online.farmsteadminecraft.bombrun.client.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.config.BombRunClientConfig;
import online.farmsteadminecraft.bombrun.net.BlastsPayload;

/**
 * What a bomb going off looks and sounds like from anywhere in sight (PLAN.md §5.7): a glowing fireball
 * ({@link Fireballs}) with flames thrown out of it, the vanilla explosion puff for
 * players past vanilla's 64-block range, a white flash, embers, a column of smoke that hangs over the ruins for half
 * a minute, a boom that arrives late the further away you are (sound travels about 17 blocks a tick), and the camera
 * shaking with every blast close enough to feel. A tick with more blasts than {@code fx.maxBlastsPerTick} gets
 * effects on an even sample of them, so a 5000-bomb carpet stays smooth.
 */
public final class BlastFx {
    /** Vanilla sends explosions to players within this distance; the fireball is already theirs. */
    private static final double VANILLA_RANGE = 64;
    /** Blocks a sound travels per tick (343 m/s). */
    private static final double SOUND_SPEED = 17;
    private static final RandomSource RANDOM = RandomSource.create();

    /** Camera shake, degrees; decays every tick. */
    private static float shake;
    private static float shakeTime;

    private BlastFx() {
    }

    public static void onBlasts(BlastsPayload p) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        int n = p.count();
        int budget = Math.max(1, BombRunClientConfig.FX_PER_TICK.value());
        int stride = Math.max(1, Mth.ceil(n / (double) budget));
        int sounds = 0, maxSounds = BombRunClientConfig.FX_SOUNDS.value();
        boolean smoke = BombRunClientConfig.FX_SMOKE.on();
        float[] a = p.xyzp();
        for (int i = 0; i < n; i++) {
            double x = a[i * 4], y = a[i * 4 + 1], z = a[i * 4 + 2];
            float power = a[i * 4 + 3];
            double d = Math.sqrt(cam.distanceToSqr(x, y, z));
            boolean vanillaShown = mc.player.distanceToSqr(x, y, z) < VANILLA_RANGE * VANILLA_RANGE;
            shake += (float) (power * 0.35 / (1 + d / 20));
            if (i % stride == 0) {
                Fireballs.add(x, y + power * 0.3, z, power * 0.9f, level.getGameTime(), RANDOM.nextFloat() * 6.28f);
                for (int k = 0; k < 10; k++) {
                    double vx = RANDOM.nextGaussian() * 0.25, vy = 0.15 + RANDOM.nextDouble() * 0.35, vz = RANDOM.nextGaussian() * 0.25;
                    level.addAlwaysVisibleParticle(k % 3 == 0 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME, true, x, y + 0.5, z, vx, vy, vz);
                }
                if (!vanillaShown) level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, x, y, z, 1, 0, 0);
                level.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, x, y + 1, z, 0, 0, 0);
                for (int k = 0; k < 4; k++) {
                    level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, x, y + 0.5, z, 0, 0, 0);
                }
                if (smoke) {
                    for (int k = 0; k < 2; k++) {
                        level.addAlwaysVisibleParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, true,
                            x + RANDOM.nextGaussian() * power * 0.5, y + 0.5, z + RANDOM.nextGaussian() * power * 0.5,
                            0, 0.05 + RANDOM.nextDouble() * 0.04, 0);
                    }
                }
            }
            if (!vanillaShown && sounds < maxSounds) {
                sounds++;
                float volume = (float) Math.max(4, d / 10);
                float pitch = (float) Mth.clamp(0.95 - d / 600, 0.45, 0.95) * (0.9f + RANDOM.nextFloat() * 0.2f);
                SimpleSoundInstance boom = new SimpleSoundInstance(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, volume, pitch,
                    RANDOM, x, y, z);
                mc.getSoundManager().playDelayed(boom, (int) (d / SOUND_SPEED));
            }
        }
        shake = Math.min(shake, 4f);
    }

    public static void tick() {
        shake *= 0.82f;
        if (shake < 0.01f) shake = 0;
        shakeTime += 1;
    }

    /** Offsets for the camera this frame: {yaw, pitch, roll}, degrees. */
    public static float[] cameraOffsets(float partial) {
        float s = shake * (float) BombRunClientConfig.FX_SHAKE.value();
        if (s <= 0) return null;
        float t = (shakeTime + partial) * 1.7f;
        return new float[] {
            s * (Mth.sin(t * 2.1f) + 0.5f * Mth.sin(t * 5.3f)) * 0.5f,
            s * (Mth.sin(t * 2.7f + 1.3f) + 0.5f * Mth.sin(t * 6.1f)) * 0.5f,
            s * Mth.sin(t * 1.9f + 0.7f) * 0.4f};
    }

    public static void clear() {
        shake = 0;
    }
}
