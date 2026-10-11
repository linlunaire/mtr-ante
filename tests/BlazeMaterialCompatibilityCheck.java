package cn.zbx1425.sowcer.shader;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Inspects real Minecraft pipeline/setup metadata without creating a window or GPU device. */
public final class BlazeMaterialCompatibilityCheck {

    public static void main(String[] args) throws Exception {
        Identifier texture = Identifier.parse("mtrsteamloco:textures/test/material.png");
        RenderType cutout = BlazeRenderType.entityCutout(texture);
        RenderType translucent = BlazeRenderType.entityTranslucentCull(texture);
        RenderType opaqueBeam = BlazeRenderType.beaconBeam(texture, false);
        RenderType translucentBeam = BlazeRenderType.beaconBeam(texture, true);

        check(cutout, RenderTypes.entityCutoutCull(texture), RenderPipelines.ENTITY_CUTOUT_CULL,
                DefaultVertexFormat.ENTITY, "entity_cutout", true, false, true, true, false);
        check(translucent, RenderTypes.entityTranslucentCullItemTarget(texture), RenderPipelines.ENTITY_TRANSLUCENT_CULL,
                DefaultVertexFormat.ENTITY, "entity_translucent_cull", true, true, true, true, true);
        check(opaqueBeam, RenderTypes.beaconBeam(texture, false), RenderPipelines.BEACON_BEAM_OPAQUE,
                DefaultVertexFormat.BLOCK, "beacon_beam_opaque", false, false, true, false, true);
        check(translucentBeam, RenderTypes.beaconBeam(texture, true), RenderPipelines.BEACON_BEAM_TRANSLUCENT,
                DefaultVertexFormat.BLOCK, "beacon_beam_translucent", false, true, false, false, true);

        Identifier equalTexture = Identifier.parse(texture.toString());
        require(cutout == BlazeRenderType.entityCutout(equalTexture), "Cutout material cache lost identifier equality");
        require(translucent == BlazeRenderType.entityTranslucentCull(equalTexture), "Translucent material cache lost identifier equality");
        require(opaqueBeam == BlazeRenderType.beaconBeam(equalTexture, false), "Opaque beam is not memoized");
        require(translucentBeam == BlazeRenderType.beaconBeam(equalTexture, true), "Translucent beam is not memoized");
        Set<RenderType> materials = new HashSet<>(Arrays.asList(cutout, translucent, opaqueBeam, translucentBeam));
        require(materials.size() == 4, "Distinct material states share a cached render type");
        Identifier otherTexture = Identifier.parse("mtrsteamloco:textures/test/other.png");
        require(cutout != BlazeRenderType.entityCutout(otherTexture), "Cutout cache merges different textures");
        require(translucent != BlazeRenderType.entityTranslucentCull(otherTexture), "Translucent cache merges different textures");
        require(opaqueBeam != BlazeRenderType.beaconBeam(otherTexture, false), "Opaque beam cache merges different textures");
        require(translucentBeam != BlazeRenderType.beaconBeam(otherTexture, true), "Translucent beam cache merges different textures");

        System.out.println("PASS: four real 26.2 ANTE triangle materials, vertex formats, shaders/bindings, culling, blending, depth writes, main target, lighting/overlay, texture bindings, sorting and memoization (no GPU/gameplay test)");
    }

    private static void check(RenderType actual, RenderType vanilla, RenderPipeline template, VertexFormat format,
                              String name, boolean entity, boolean blending, boolean writeDepth,
                              boolean crumbling, boolean sorting) throws Exception {
        RenderPipeline pipeline = actual.pipeline();
        require(pipeline != template, name + ": mutated/reused the vanilla pipeline");
        require(template.getPrimitiveTopology() == PrimitiveTopology.QUADS, name + ": changed vanilla topology");
        require(actual.primitiveTopology() == PrimitiveTopology.TRIANGLES, name + ": triangle faces will be interpreted as quads");
        require(actual.format() == format, name + ": changed the legacy vertex format");
        require(pipeline.getLocation().equals(Identifier.fromNamespaceAndPath("mtrsteamloco", "pipeline/" + name)), name + ": pipeline identifier collision");
        require(actual.outputTarget() == OutputTarget.MAIN_TARGET, name + ": changed the legacy output target");
        require(pipeline.isCull() && pipeline.isCull() == template.isCull(), name + ": lost back-face culling");
        require(pipeline.getColorTargetState().blendFunction().isPresent() == blending, name + ": wrong transparency mode");
        require(Arrays.equals(pipeline.getColorTargetStates(), template.getColorTargetStates()), name + ": changed color masks/blend factors");
        require(pipeline.getDepthStencilState().writeDepth() == writeDepth, name + ": wrong depth-write mode");
        // 26.2 reverses the depth range; GEQUAL retains the old nearest-surface LEQUAL behavior.
        require(pipeline.getDepthStencilState().depthTest() == CompareOp.GREATER_THAN_OR_EQUAL, name + ": changed depth comparison");
        require(pipeline.getDepthStencilState().equals(template.getDepthStencilState()), name + ": changed depth/bias state");
        require(pipeline.getPolygonMode() == template.getPolygonMode(), name + ": changed polygon mode");
        require(pipeline.getVertexShader().equals(template.getVertexShader()), name + ": changed vertex shader");
        require(pipeline.getFragmentShader().equals(template.getFragmentShader()), name + ": changed fragment shader");
        require(pipeline.getShaderDefines().equals(template.getShaderDefines()), name + ": changed alpha cutoff/lighting defines");
        require(pipeline.getBindGroupLayouts().equals(template.getBindGroupLayouts()), name + ": changed shader resource bindings");
        for (int i = 1; i < pipeline.getVertexFormatBindings().length; i++) {
            require(pipeline.getVertexFormatBinding(i) == template.getVertexFormatBinding(i), name + ": changed an additional vertex binding");
        }
        require(actual.affectsCrumbling() == crumbling, name + ": changed crumbling behavior");
        require(actual.sortOnUpload() == sorting, name + ": changed upload sorting");
        require(actual.outline().isPresent() == entity, name + ": changed outline participation");

        RenderSetup setup = (RenderSetup) field(RenderType.class, actual, "state");
        RenderSetup vanillaSetup = (RenderSetup) field(RenderType.class, vanilla, "state");
        require(field(RenderSetup.class, setup, "useLightmap").equals(entity), name + ": changed lightmap use");
        require(field(RenderSetup.class, setup, "useOverlay").equals(entity), name + ": changed overlay use");
        for (String field : new String[]{"textures", "outlineProperty", "textureTransform", "layeringTransform"}) {
            require(field(RenderSetup.class, setup, field).equals(field(RenderSetup.class, vanillaSetup, field)), name + ": changed " + field);
        }
        Map<?, ?> textures = (Map<?, ?>) field(RenderSetup.class, setup, "textures");
        require(textures.size() == 1 && textures.containsKey("Sampler0"), name + ": missing model texture sampler");
    }

    private static Object field(Class<?> owner, Object target, String name) throws Exception {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
