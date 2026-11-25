package online.farmsteadminecraft.bombrun.client.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.client.raid.ClientRaids;
import online.farmsteadminecraft.bombrun.config.BombRunClientConfig;
import online.farmsteadminecraft.bombrun.strike.FlightPlan;
import org.joml.Matrix4f;

/**
 * White trails behind every plane (PLAN.md §5.7): two ribbons from the engines that widen, sink a little, drift
 * with the wind and fade over {@code render.contrailSeconds}. Not particles, so they never run into the particle
 * limit or the "Particles: minimal" setting. Nothing is stored: the flight plan says where each plane was at every
 * moment, so the trail is drawn straight from it each frame.
 */
public final class Contrails {
    public static final ResourceLocation TEXTURE = BombRun.id("textures/fx/contrail.png");
    /** Ticks between two points of a trail. */
    private static final float STEP = 2;
    /** Engines sit this far either side of the fuselage, model units (16 a block) at plane scale 1. */
    private static final float ENGINE_X = 17 / 16f;
    /** The trail starts this far behind the plane's middle, at scale 1. */
    private static final float BEHIND = 0.6f;

    private Contrails() {
    }

    public static void render(Matrix4f view, Vec3 cam, float partial) {
        if (!BombRunClientConfig.CONTRAILS.on() || ClientRaids.all().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float scale = BombRunClientConfig.PLANE_SCALE.floatValue();
        float life = (float) (BombRunClientConfig.CONTRAIL_SECONDS.value() * 20);
        int points = Math.max(2, (int) (life / STEP));
        RenderType type = RenderType.entityTranslucent(TEXTURE);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(type);
        double[] p = new double[3];
        double[][] pts = new double[points][3];
        float[] ages = new float[points];
        for (ClientRaids.ClientRaid raid : ClientRaids.all()) {
            FlightPlan plan = raid.plan;
            double t = raid.time(mc, partial);
            double rightX = -plan.dirZ, rightZ = plan.dirX;
            for (int i = 0; i < plan.planes(); i++) {
                int light = -1;
                for (int side = -1; side <= 1; side += 2) {
                    int n = 0;
                    for (int k = 0; k < points; k++) {
                        float age = k * STEP;
                        double tk = t - age;
                        if (tk < 0) break; // before take-off: the trail starts here
                        if (!plan.flying(i, tk)) continue; // past the end of the run: the older part still hangs on
                        plan.position(i, tk, p);
                        if (light < 0) light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p[0], p[1], p[2]));
                        // the two trails spread apart and sink as they age; a light wind carries them sideways
                        double spread = ENGINE_X * scale * (1 + age / life * 0.8);
                        pts[n][0] = p[0] - plan.dirX * BEHIND * scale + rightX * spread * side + age * 0.015;
                        pts[n][1] = p[1] - 0.15 * scale - age * 0.006;
                        pts[n][2] = p[2] - plan.dirZ * BEHIND * scale + rightZ * spread * side + age * 0.01;
                        ages[n] = age;
                        n++;
                    }
                    if (n >= 2) ribbon(vc, view, cam, pts, ages, n, life, scale, light < 0 ? 0xF000F0 : light);
                }
            }
        }
        buffers.endBatch(type);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 cam, double[][] pts, float[] ages, int n, float life, float scale,
                               int light) {
        for (int k = 0; k + 1 < n; k++) {
            float[] a = side(cam, pts, k, n, ages[k], life, scale);
            float[] b = side(cam, pts, k + 1, n, ages[k + 1], life, scale);
            int alphaA = alpha(ages[k], life), alphaB = alpha(ages[k + 1], life);
            float ax = (float) (pts[k][0] - cam.x), ay = (float) (pts[k][1] - cam.y), az = (float) (pts[k][2] - cam.z);
            float bx = (float) (pts[k + 1][0] - cam.x), by = (float) (pts[k + 1][1] - cam.y), bz = (float) (pts[k + 1][2] - cam.z);
            float ua = ages[k] / life, ub = ages[k + 1] / life;
            v(vc, m, ax - a[0], ay - a[1], az - a[2], ua, 0, alphaA, light);
            v(vc, m, bx - b[0], by - b[1], bz - b[2], ub, 0, alphaB, light);
            v(vc, m, bx + b[0], by + b[1], bz + b[2], ub, 1, alphaB, light);
            v(vc, m, ax + a[0], ay + a[1], az + a[2], ua, 1, alphaA, light);
        }
    }

    /** Half the ribbon's width at point k, across the trail and facing the camera. */
    private static float[] side(Vec3 cam, double[][] pts, int k, int n, float age, float life, float scale) {
        int k0 = Math.max(0, k - 1), k1 = Math.min(n - 1, k + 1);
        double dx = pts[k1][0] - pts[k0][0], dy = pts[k1][1] - pts[k0][1], dz = pts[k1][2] - pts[k0][2];
        double vx = pts[k][0] - cam.x, vy = pts[k][1] - cam.y, vz = pts[k][2] - cam.z;
        double sx = dy * vz - dz * vy, sy = dz * vx - dx * vz, sz = dx * vy - dy * vx;
        double len = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (len < 1e-6) return new float[] {0, 0, 0};
        double half = scale * (0.18 + 0.9 * age / life);
        return new float[] {(float) (sx / len * half), (float) (sy / len * half), (float) (sz / len * half)};
    }

    /** Fades in over the first few ticks behind the engine and out towards the end of its life. */
    private static int alpha(float age, float life) {
        float in = Math.min(1, age / 6f);
        float f = 1 - age / life;
        return (int) (160 * in * f * f);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int a, int light) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light).setNormal(0, 1, 0);
    }
}
