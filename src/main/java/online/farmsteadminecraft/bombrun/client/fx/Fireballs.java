package online.farmsteadminecraft.bombrun.client.fx;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.BombRun;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The fire of a blast: a cluster of textured flame puffs ({@code textures/fx/fire.png}, eight frames from a
 * white-hot core to orange flame, red embers and dark smoke) that bursts out of the impact point, swells, rises and
 * burns out in about a second. Vanilla's explosion is a grey puff, which from a hundred blocks away reads as dust,
 * not as fire. Drawn full-bright, so it glows at night.
 */
public final class Fireballs {
    public static final ResourceLocation TEXTURE = BombRun.id("textures/fx/fire.png");
    private static final int FRAMES = 8;
    /** How long a fireball lives, ticks. */
    private static final float LIFE = 22;
    private static final int MAX = 400;
    /** Puffs per fireball: one in the middle, the rest thrown out around it. */
    private static final int PUFFS = 9;

    private record Ball(double x, double y, double z, float size, long born, long seed) {
    }

    private static final List<Ball> BALLS = new ArrayList<>();

    private Fireballs() {
    }

    public static void add(double x, double y, double z, float power, long now, long seed) {
        if (BALLS.size() >= MAX) BALLS.remove(0);
        BALLS.add(new Ball(x, y, z, power, now, seed));
    }

    public static void clear() {
        BALLS.clear();
    }

    public static void render(Matrix4f view, Vec3 cam, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (BALLS.isEmpty() || mc.level == null) return;
        Camera camera = mc.gameRenderer.getMainCamera();
        Vector3f left = camera.getLeftVector(), up = camera.getUpVector();
        double now = mc.level.getGameTime() + partial;
        RenderType type = RenderType.entityTranslucent(TEXTURE);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(type);
        for (int i = BALLS.size() - 1; i >= 0; i--) {
            Ball b = BALLS.get(i);
            float age = (float) (now - b.born);
            if (age >= LIFE + PUFFS) {
                BALLS.remove(i);
                continue;
            }
            for (int k = 0; k < PUFFS; k++) puff(vc, view, cam, left, up, b, k, age);
        }
        buffers.endBatch(type);
    }

    private static void puff(VertexConsumer vc, Matrix4f m, Vec3 cam, Vector3f left, Vector3f up, Ball b, int k, float age) {
        // each puff starts a little later than the one before, so the ball keeps boiling instead of popping once
        float t = (age - k * 0.8f) / LIFE;
        if (t < 0 || t >= 1) return;
        long h = b.seed * 31 + k * 0x9E3779B97F4A7C15L;
        float a1 = (h >>> 8 & 0xFFFF) / 65535f * Mth.TWO_PI, a2 = (h >>> 24 & 0xFFFF) / 65535f;
        float out = k == 0 ? 0 : b.size * (0.35f + 0.55f * a2);
        float grow = 1 - (1 - t) * (1 - t);
        float ox = Mth.cos(a1) * out * grow, oz = Mth.sin(a1) * out * grow;
        float oy = out * 0.35f * grow + b.size * 0.9f * t * t; // thrown up a little, then the hot air rises
        float size = b.size * (k == 0 ? 1.1f : 0.75f) * (0.55f + 0.9f * grow);
        int frame = Math.min(FRAMES - 1, (int) (t * FRAMES));
        float u0 = frame / (float) FRAMES, u1 = (frame + 1) / (float) FRAMES;
        int alpha = (int) (255 * (t < 0.7f ? 1 : 1 - (t - 0.7f) / 0.3f));
        // a random turn of the sprite in the view plane, so neighbouring puffs never look alike
        float rot = (h >>> 40 & 0xFFFF) / 65535f * Mth.TWO_PI + t * (a2 - 0.5f);
        float c = Mth.cos(rot) * size, s = Mth.sin(rot) * size;
        float rx = left.x() * c + up.x() * s, ry = left.y() * c + up.y() * s, rz = left.z() * c + up.z() * s;
        float ux = -left.x() * s + up.x() * c, uy = -left.y() * s + up.y() * c, uz = -left.z() * s + up.z() * c;
        float px = (float) (b.x - cam.x) + ox, py = (float) (b.y - cam.y) + oy, pz = (float) (b.z - cam.z) + oz;
        v(vc, m, px - rx - ux, py - ry - uy, pz - rz - uz, u0, 1, alpha);
        v(vc, m, px + rx - ux, py + ry - uy, pz + rz - uz, u1, 1, alpha);
        v(vc, m, px + rx + ux, py + ry + uy, pz + rz + uz, u1, 0, alpha);
        v(vc, m, px - rx + ux, py - ry + uy, pz - rz + uz, u0, 0, alpha);
    }

    private static void v(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v, int a) {
        vc.addVertex(m, x, y, z).setColor(255, 255, 255, a).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(LightTexture.FULL_BRIGHT).setNormal(0, 1, 0);
    }
}
