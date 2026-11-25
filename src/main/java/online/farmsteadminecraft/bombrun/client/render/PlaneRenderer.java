package online.farmsteadminecraft.bombrun.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import online.farmsteadminecraft.bombrun.BombRun;
import online.farmsteadminecraft.bombrun.client.raid.ClientRaids;
import online.farmsteadminecraft.bombrun.config.BombRunClientConfig;
import online.farmsteadminecraft.bombrun.strike.FlightPlan;
import org.joml.Matrix4f;

/**
 * Draws every plane of every known raid at {@code plan.position(t)} (PLAN.md §5.1, §5.7): propellers spin,
 * bomb bays open two seconds before the first bomb, a slight roll keeps the squadron alive.
 */
public final class PlaneRenderer {
    public static final ResourceLocation TEXTURE = BombRun.id("textures/entity/plane.png");
    private static ModelPart model;

    private PlaneRenderer() {
    }

    public static void render(Matrix4f view, Vec3 camera, float partial) {
        if (ClientRaids.all().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        if (model == null) model = PlaneModel.create();
        ModelPart propeller = model.getChild("propeller");
        ModelPart doorLeft = model.getChild("door_left");
        ModelPart doorRight = model.getChild("door_right");

        PoseStack pose = new PoseStack();
        pose.mulPose(view);
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityCutoutNoCull(TEXTURE);
        VertexConsumer vc = buffers.getBuffer(type);
        float scale = BombRunClientConfig.PLANE_SCALE.floatValue();
        double maxDist = BombRunClientConfig.RENDER_DISTANCE.value();
        double[] p = new double[3];
        for (ClientRaids.ClientRaid raid : ClientRaids.all()) {
            FlightPlan plan = raid.plan;
            double t = raid.time(mc, partial);
            float heading = (float) plan.heading();
            for (int i = 0; i < plan.planes(); i++) {
                if (!plan.flying(i, t)) continue;
                plan.position(i, t, p);
                double dx = p[0] - camera.x, dy = p[1] - camera.y, dz = p[2] - camera.z;
                if (dx * dx + dy * dy + dz * dz > maxDist * maxDist) continue;
                propeller.zRot = (float) (t * 1.3 + i);
                float open = plan.bayOpen(i, t) ? 1f : 0f;
                doorLeft.zRot = -1.3f * open;
                doorRight.zRot = 1.3f * open;
                pose.pushPose();
                pose.translate(dx, dy, dz);
                pose.mulPose(Axis.YP.rotationDegrees(-heading));
                pose.mulPose(Axis.ZP.rotationDegrees(3f * Mth.sin((float) (t * 0.05 + i * 1.7))));
                pose.scale(scale, scale, scale);
                int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(p[0], p[1], p[2]));
                model.render(pose, vc, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
        }
        buffers.endBatch(type);
    }
}
