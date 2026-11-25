package online.farmsteadminecraft.bombrun.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.client.remote.TargetMarker;
import org.joml.Matrix4f;

/**
 * The fire of a blast: a glowing ball that swells out of a white-hot core, turns orange, then dark red, and is gone
 * in under a second, leaving the smoke. Vanilla's explosion is a grey puff, which from a hundred blocks away reads
 * as dust, not as fire. Drawn full-bright, so it glows at night and lights nothing else.
 */
public final class Fireballs {
    /** How long a fireball lives, ticks. */
    private static final float LIFE = 16;
    private static final int MAX = 400;
    private static final int RINGS = 7, SEGMENTS = 12;

    private record Ball(double x, double y, double z, float size, long born, float spin) {
    }

    private static final List<Ball> BALLS = new ArrayList<>();

    private Fireballs() {
    }

    public static void add(double x, double y, double z, float power, long now, float spin) {
        if (BALLS.size() >= MAX) BALLS.remove(0);
        BALLS.add(new Ball(x, y, z, power, now, spin));
    }

    public static void clear() {
        BALLS.clear();
    }

    public static void render(Matrix4f view, Vec3 cam, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (BALLS.isEmpty() || mc.level == null) return;
        double now = mc.level.getGameTime() + partial;
        RenderType type = RenderType.entityTranslucent(TargetMarker.WHITE);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(type);
        for (int i = BALLS.size() - 1; i >= 0; i--) {
            Ball b = BALLS.get(i);
            float t = (float) ((now - b.born) / LIFE);
            if (t >= 1 || t < 0) {
                if (t >= 1) BALLS.remove(i);
                continue;
            }
            // swells fast, then slowly; the core is a smaller, hotter ball inside
            float grow = 1 - (1 - t) * (1 - t) * (1 - t);
            float r = b.size * (0.5f + 1.3f * grow);
            float fade = t < 0.5f ? 1 : 1 - (t - 0.5f) * 2;
            int[] outer = t < 0.15f ? new int[] {255, 240, 190} : t < 0.45f ? mix(255, 170, 40, 230, 70, 20, (t - 0.15f) / 0.3f)
                : mix(230, 70, 20, 70, 20, 10, (t - 0.45f) / 0.55f);
            sphere(vc, view, b.x - cam.x, b.y - cam.y, b.z - cam.z, r, b.spin, outer, (int) (200 * fade));
            if (t < 0.5f) {
                float core = 1 - t * 2;
                sphere(vc, view, b.x - cam.x, b.y - cam.y, b.z - cam.z, r * 0.55f, -b.spin, new int[] {255, 250, 220}, (int) (230 * core));
            }
        }
        buffers.endBatch(type);
    }

    private static int[] mix(int r0, int g0, int b0, int r1, int g1, int b1, float f) {
        f = Mth.clamp(f, 0, 1);
        return new int[] {(int) Mth.lerp(f, r0, r1), (int) Mth.lerp(f, g0, g1), (int) Mth.lerp(f, b0, b1)};
    }

    /** A lumpy ball of quads: each ring's radius wobbles a little so it reads as flame, not as a sphere. */
    private static void sphere(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, float r, float spin, int[] rgb, int a) {
        if (a <= 2) return;
        for (int i = 0; i < RINGS; i++) {
            float t0 = (float) Math.PI * i / RINGS, t1 = (float) Math.PI * (i + 1) / RINGS;
            for (int j = 0; j < SEGMENTS; j++) {
                float p0 = spin + (float) (2 * Math.PI) * j / SEGMENTS, p1 = spin + (float) (2 * Math.PI) * (j + 1) / SEGMENTS;
                vertex(vc, m, cx, cy, cz, r, t0, p0, rgb, a);
                vertex(vc, m, cx, cy, cz, r, t1, p0, rgb, a);
                vertex(vc, m, cx, cy, cz, r, t1, p1, rgb, a);
                vertex(vc, m, cx, cy, cz, r, t0, p1, rgb, a);
            }
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, double cx, double cy, double cz, float r, float theta, float phi, int[] rgb, int a) {
        float wobble = 1 + 0.12f * Mth.sin(phi * 3 + theta * 5) * Mth.sin(theta * 4);
        float sx = Mth.sin(theta) * Mth.cos(phi), sy = Mth.cos(theta), sz = Mth.sin(theta) * Mth.sin(phi);
        // flatter at the bottom: a fireball sits on the ground it came out of
        float y = sy < 0 ? sy * 0.6f : sy;
        vc.addVertex(m, (float) (cx + sx * r * wobble), (float) (cy + y * r * wobble), (float) (cz + sz * r * wobble))
            .setColor(rgb[0], rgb[1], rgb[2], a)
            .setUv(0.5f, 0.5f)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT)
            .setNormal(sx, sy, sz);
    }
}
