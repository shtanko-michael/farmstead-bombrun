package online.farmsteadminecraft.bombrun.client.remote;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.config.BombRunClientConfig;
import org.joml.Matrix4f;

/**
 * The target ring (PLAN.md §5.5): a band of quads laid on the top of the blocks around the circle, so it climbs
 * over roofs and follows the ground; a fainter half-radius ring; an arrow for the direction of the run. It pulses.
 */
public final class TargetMarker {
    public static final ResourceLocation WHITE = BombRun.id("textures/misc/white.png");
    private static final float LIFT = 0.04f;

    private TargetMarker() {
    }

    public static void render(Matrix4f view, Vec3 camera, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 target = TargetingState.target();
        ClientLevel level = mc.level;
        if (target == null || level == null) return;
        double radius = TargetingState.radius();
        int rgb = BombRunClientConfig.markerRgb();
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float pulse = 0.8f + 0.2f * Mth.sin((level.getGameTime() + partial) * 0.3f);
        int alpha = (int) (255 * BombRunClientConfig.MARKER_OPACITY.value() * pulse);

        RenderType type = RenderType.entityTranslucent(WHITE);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(type);
        Matrix4f m = new Matrix4f(view);
        double width = Math.max(0.3, radius * 0.025);
        ring(vc, m, level, camera, target, radius, width, r, g, b, alpha);
        ring(vc, m, level, camera, target, radius * 0.5, width * 0.5, r, g, b, alpha / 3);
        arrow(vc, m, level, camera, target, radius, Math.toRadians(TargetingState.heading(mc)), r, g, b, alpha);
        buffers.endBatch(type);
    }

    private static float surface(ClientLevel level, double x, double z) {
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x), Mth.floor(z)) + LIFT;
    }

    private static void ring(VertexConsumer vc, Matrix4f m, ClientLevel level, Vec3 cam, Vec3 c, double radius, double width,
                             int r, int g, int b, int a) {
        int segments = Mth.clamp((int) Math.ceil(2 * Math.PI * radius / 0.6), 24, 1024);
        double in = radius - width / 2, out = radius + width / 2;
        for (int i = 0; i < segments; i++) {
            double a0 = 2 * Math.PI * i / segments, a1 = 2 * Math.PI * (i + 1) / segments;
            double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
            // one height per segment (its middle) keeps the band flat on each block top
            double mx = c.x + radius * Math.cos((a0 + a1) / 2), mz = c.z + radius * Math.sin((a0 + a1) / 2);
            float y = (float) (surface(level, mx, mz) - cam.y);
            quad(vc, m,
                (float) (c.x + in * c0 - cam.x), y, (float) (c.z + in * s0 - cam.z),
                (float) (c.x + out * c0 - cam.x), y, (float) (c.z + out * s0 - cam.z),
                (float) (c.x + out * c1 - cam.x), y, (float) (c.z + out * s1 - cam.z),
                (float) (c.x + in * c1 - cam.x), y, (float) (c.z + in * s1 - cam.z), r, g, b, a);
        }
    }

    /** An arrow from the middle towards the direction of the run, lying on the ground at the target. */
    private static void arrow(VertexConsumer vc, Matrix4f m, ClientLevel level, Vec3 cam, Vec3 c, double radius, double yaw,
                              int r, int g, int b, int a) {
        double dx = -Math.sin(yaw), dz = Math.cos(yaw);
        double px = -dz, pz = dx;
        double len = Math.min(radius * 0.7, 8), half = Math.max(0.4, len * 0.12), head = len * 0.35;
        float y = (float) (surface(level, c.x, c.z) - cam.y) + 0.01f;
        double ox = c.x - cam.x, oz = c.z - cam.z;
        double sx = ox - dx * len * 0.5, sz = oz - dz * len * 0.5;
        double ex = ox + dx * (len * 0.5 - head), ez = oz + dz * (len * 0.5 - head);
        // shaft
        quad(vc, m, (float) (sx + px * half), y, (float) (sz + pz * half), (float) (ex + px * half), y, (float) (ez + pz * half),
            (float) (ex - px * half), y, (float) (ez - pz * half), (float) (sx - px * half), y, (float) (sz - pz * half), r, g, b, a);
        // head (a quad with two corners at the tip)
        double tx = ox + dx * len * 0.5, tz = oz + dz * len * 0.5, w = half * 3;
        quad(vc, m, (float) (ex + px * w), y, (float) (ez + pz * w), (float) tx, y, (float) tz, (float) tx, y, (float) tz,
            (float) (ex - px * w), y, (float) (ez - pz * w), r, g, b, a);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1,
                             float x2, float y2, float z2, float x3, float y3, float z3, int r, int g, int b, int a) {
        vertex(vc, m, x0, y0, z0, 0, 0, r, g, b, a);
        vertex(vc, m, x1, y1, z1, 0, 1, r, g, b, a);
        vertex(vc, m, x2, y2, z2, 1, 1, r, g, b, a);
        vertex(vc, m, x3, y3, z3, 1, 0, r, g, b, a);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int r, int g, int b, int a) {
        vc.addVertex(m, x, y, z).setColor(r, g, b, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(0f, 1f, 0f);
    }
}
