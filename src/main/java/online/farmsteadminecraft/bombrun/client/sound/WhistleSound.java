package online.farmsteadminecraft.bombrun.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import online.farmsteadminecraft.bombrun.registry.BombRunContent;
import online.farmsteadminecraft.bombrun.strike.Ballistics;
import online.farmsteadminecraft.bombrun.strike.FlightPlan;

/**
 * A falling bomb's whistle. The client does not know which TNT entity is which bomb, so the sound flies the
 * bomb's own path from the plan, with the same physics the server's TNT has, and falls in pitch as it drops.
 */
public final class WhistleSound extends AbstractTickableSoundInstance {
    private final double groundY;
    private double vx, vy, vz;
    private int age;

    public WhistleSound(FlightPlan plan, int drop) {
        super(BombRunContent.whistle.get(), SoundSource.HOSTILE, SoundInstance.createUnseededRandom());
        double[] d = plan.drop(drop);
        x = d[0];
        y = d[1];
        z = d[2];
        vx = d[3];
        vy = d[4];
        vz = d[5];
        groundY = plan.targetY;
        looping = false;
        delay = 0;
        volume = 3f;
        pitch = 1.6f;
    }

    @Override
    public void tick() {
        double[] s = Ballistics.step(x, y, z, vx, vy, vz, 1);
        x = s[0];
        y = s[1];
        z = s[2];
        vx = s[3];
        vy = s[4];
        vz = s[5];
        age++;
        pitch = Math.max(0.6f, 1.6f - age * 0.02f);
        if (y <= groundY || age > 400) stop();
    }
}
