package online.farmsteadminecraft.bombrun.client.sound;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.client.raid.ClientRaids;
import online.farmsteadminecraft.bombrun.registry.BombRunContent;
import online.farmsteadminecraft.bombrun.strike.FlightPlan;

/**
 * One plane's engine: a looping sound that follows the plane along its plan, its pitch shifted by how fast it
 * comes closer or flies away (a Doppler shift, PLAN.md §5.7).
 */
public final class EngineSound extends AbstractTickableSoundInstance {
    /** "Speed of sound" for the Doppler shift, blocks per tick: low on purpose, so the shift is heard. */
    private static final double SOUND_SPEED = 7.5;
    private static final float BASE_PITCH = 0.7f;

    private final ClientRaids.ClientRaid raid;
    private final int plane;
    private final double[] pos = new double[3];

    public EngineSound(ClientRaids.ClientRaid raid, int plane) {
        super(BombRunContent.engine.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        this.raid = raid;
        this.plane = plane;
        this.looping = true;
        this.delay = 0;
        this.volume = 5f;
        this.pitch = BASE_PITCH;
        update();
    }

    @Override
    public void tick() {
        if (!raid.alive()) {
            stop();
            return;
        }
        update();
    }

    private void update() {
        Minecraft mc = Minecraft.getInstance();
        double t = raid.time(mc, 0);
        FlightPlan plan = raid.plan;
        if (!plan.flying(plane, t)) {
            stop();
            return;
        }
        plan.position(plane, t, pos);
        x = pos[0];
        y = pos[1];
        z = pos[2];
        if (mc.player != null) {
            Vec3 ear = mc.player.getEyePosition();
            double dx = ear.x - pos[0], dy = ear.y - pos[1], dz = ear.z - pos[2];
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double closing = len < 1e-6 ? 0 : (plan.dirX * dx + plan.dirZ * dz) * plan.speed / len;
            pitch = (float) Math.max(0.3, Math.min(2.0, BASE_PITCH * SOUND_SPEED / (SOUND_SPEED - closing)));
        }
    }
}
