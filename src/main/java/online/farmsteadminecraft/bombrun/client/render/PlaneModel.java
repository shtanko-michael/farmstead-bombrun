package online.farmsteadminecraft.bombrun.client.render;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * The M1 placeholder plane: a chunky cartoon propeller bomber with no markings (PLAN.md §2, §5.7), built
 * from boxes in code. Model space: Y up, nose towards +Z, 16 units a block. The final Blockbench models replace
 * it in M2.
 */
public final class PlaneModel {
    public static final int TEX_W = 256, TEX_H = 128;

    private PlaneModel() {
    }

    public static ModelPart create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("fuselage", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4, -4, -24, 8, 8, 48)
                .texOffs(150, 88).addBox(-3, -3, 24, 6, 6, 4)
                .texOffs(112, 88).addBox(-3, 4, 4, 6, 4, 10),
            PartPose.ZERO);
        root.addOrReplaceChild("wings", CubeListBuilder.create()
                .texOffs(0, 64).addBox(-36, -1, -2, 72, 2, 14)
                .texOffs(176, 88).addBox(-20, -4, 6, 6, 6, 10)
                .texOffs(176, 88).addBox(14, -4, 6, 6, 6, 10),
            PartPose.ZERO);
        root.addOrReplaceChild("tail", CubeListBuilder.create()
                .texOffs(0, 88).addBox(-14, -1, -24, 28, 2, 8)
                .texOffs(80, 88).addBox(-1, 0, -24, 2, 12, 8),
            PartPose.ZERO);
        root.addOrReplaceChild("propeller", CubeListBuilder.create()
                .texOffs(0, 112).addBox(-12, -1, 0, 24, 2, 1)
                .texOffs(60, 100).addBox(-1, -12, 0, 2, 24, 1),
            PartPose.offset(0, 0, 28));
        root.addOrReplaceChild("door_left", CubeListBuilder.create()
                .texOffs(120, 0).addBox(0, -1, -8, 4, 1, 16),
            PartPose.offset(-4, -4, 0));
        root.addOrReplaceChild("door_right", CubeListBuilder.create()
                .texOffs(120, 20).addBox(-4, -1, -8, 4, 1, 16),
            PartPose.offset(4, -4, 0));
        return LayerDefinition.create(mesh, TEX_W, TEX_H).bakeRoot();
    }
}
