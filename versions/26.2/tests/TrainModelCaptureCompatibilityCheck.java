package cn.zbx1425.mtrsteamloco.render.integration;

import com.mojang.blaze3d.vertex.PoseStack;
import mtr.model.ModelTrainBase;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

import java.util.EnumSet;

/** Real vanilla cube emission into ANTE's consumer; recursive accessor weaving is separate. */
public final class TrainModelCaptureCompatibilityCheck {
    private static int assertions;

    public static void run() {
        var cube = new ModelPart.Cube(0, 0, 0, 0, 0, 16, 16, 16, 0, 0, 0, false, 64, 64, EnumSet.allOf(Direction.class));
        var texture = Identifier.parse("mtrsteamloco:textures/test.png");
        var consumer = new CapturingVertexConsumer();
        for (var stage : ModelTrainBase.RenderStage.values()) {
            for (int part = 0; part < 5; part++) {
                consumer.reset();
                consumer.beginStage(texture, stage);
                PoseStack pose = new PoseStack();
                float x = part == 0 ? 0 : part <= 2 ? 2 : -2;
                float z = part == 0 ? 0 : part % 2 == 1 ? TrainModelCapture.DOOR_OFFSET : -TrainModelCapture.DOOR_OFFSET;
                pose.translate(x, 0, z);
                cube.compile(pose.last(), consumer, 0x00F000F0, 0, -1);
                for (int index = 0; index < consumer.models.length; index++) {
                    var mesh = consumer.models[index].meshList.values().iterator().next();
                    require(mesh.vertices.size() == (index == part ? 24 : 0), "Vanilla cube vertices lost or assigned to the wrong door");
                    require(mesh.faces.size() == (index == part ? 6 : 0), "Quad boundaries changed in the new VertexConsumer API");
                    mesh.validateVertIndex();
                    if (index != part) continue;
                    require(mesh.materialProp.texture.equals(texture), "Capture lost texture");
                    require(mesh.materialProp.translucent == (stage == ModelTrainBase.RenderStage.ALWAYS_ON_LIGHTS || stage == ModelTrainBase.RenderStage.INTERIOR_TRANSLUCENT), "Capture lost stage blending");
                    require(mesh.materialProp.writeDepthBuf == (stage != ModelTrainBase.RenderStage.ALWAYS_ON_LIGHTS), "Capture lost stage depth setting");
                    for (var vertex : mesh.vertices) {
                        require(vertex.position.x() >= x && vertex.position.x() <= x + 1
                                && vertex.position.y() >= 0 && vertex.position.y() <= 1
                                && vertex.position.z() >= 0 && vertex.position.z() <= 1,
                                "Model units or door offset compensation changed");
                        float normalLength = vertex.normal.x() * vertex.normal.x() + vertex.normal.y() * vertex.normal.y() + vertex.normal.z() * vertex.normal.z();
                        require(Math.abs(normalLength - 1) < 0.0001F && Float.isFinite(vertex.u) && Float.isFinite(vertex.v), "Capture lost normal/UV attributes");
                    }
                }
            }
        }
        consumer.reset();
        for (var model : consumer.models) require(model.meshList.isEmpty(), "Reset retained the previous captured model");
        System.out.println("PASS: real vanilla cube emission, five render stages, body/four door partitions and reset; " + assertions + " assertions (not accessor weaving/GPU)");
    }

    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
