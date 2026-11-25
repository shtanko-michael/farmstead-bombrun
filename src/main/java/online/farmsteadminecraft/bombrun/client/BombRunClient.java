package online.farmsteadminecraft.bombrun.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.client.demo.DemoDirector;
import online.farmsteadminecraft.bombrun.client.fx.BlastFx;
import online.farmsteadminecraft.bombrun.client.raid.ClientRaids;
import online.farmsteadminecraft.bombrun.client.remote.TargetMarker;
import online.farmsteadminecraft.bombrun.client.remote.TargetingState;
import online.farmsteadminecraft.bombrun.client.render.PlaneRenderer;
import online.farmsteadminecraft.bombrun.config.BombRunClientConfig;
import org.joml.Matrix4f;

/** Loader-independent client side: each loader forwards its client events here. */
public final class BombRunClient {
    private BombRunClient() {
    }

    public static void init() {
        BombRunClientConfig.load();
    }

    public static void tick(Minecraft mc) {
        TargetingState.tick(mc);
        ClientRaids.tick(mc);
        BlastFx.tick();
        DemoDirector.tick(mc);
    }

    /** Called after entities are drawn, with an identity pose and the camera-rotated model-view on the GPU. */
    public static void renderWorld(Matrix4f view, Vec3 camera, float partial) {
        online.farmsteadminecraft.bombrun.client.fx.Contrails.render(view, camera, partial);
        PlaneRenderer.render(view, camera, partial);
        online.farmsteadminecraft.bombrun.client.fx.Fireballs.render(view, camera, partial);
        TargetMarker.render(view, camera, partial);
    }

    public static void leftWorld() {
        ClientRaids.clear();
        TargetingState.reset();
        BlastFx.clear();
        online.farmsteadminecraft.bombrun.client.fx.Fireballs.clear();
    }
}
