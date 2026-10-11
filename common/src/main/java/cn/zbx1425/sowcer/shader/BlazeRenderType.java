package cn.zbx1425.sowcer.shader;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

public class BlazeRenderType {

    private record PipelineKey(String shader, boolean blending, boolean depth) { }
    private record MaterialKey(Identifier texture, PipelineKey pipeline) { }
    private static final java.util.Map<PipelineKey, RenderPipeline> MATERIAL_PIPELINES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Map<MaterialKey, RenderType> MATERIALS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Preserve SOWCER's independent blend/depth flags, including cutout beam blending. */
    public static RenderType material(Identifier texture, String shader, boolean blending, boolean depth) {
        final String family = switch (shader) {
            case "rendertype_beacon_beam" -> "beacon_beam";
            case "rendertype_entity_translucent_cull" -> "entity_translucent_cull";
            default -> "entity_cutout";
        };
        return MATERIALS.computeIfAbsent(new MaterialKey(texture, new PipelineKey(family, blending, depth)), key -> {
            final boolean beam = family.equals("beacon_beam");
            final RenderPipeline pipeline = MATERIAL_PIPELINES.computeIfAbsent(key.pipeline, state -> {
                final RenderPipeline template = beam ? RenderPipelines.BEACON_BEAM_OPAQUE
                        : family.equals("entity_translucent_cull") ? RenderPipelines.ENTITY_TRANSLUCENT_CULL : RenderPipelines.ENTITY_CUTOUT_CULL;
                final var colors = template.getColorTargetState();
                final var depths = template.getDepthStencilState();
                return RenderPipeline.builder(snippet(template))
                        .withLocation(Identifier.fromNamespaceAndPath("mtrsteamloco", "pipeline/sowcer_" + family + "_" + state.blending + "_" + state.depth))
                        .withVertexBinding(0, beam ? DefaultVertexFormat.BLOCK : DefaultVertexFormat.ENTITY)
                        .withColorTargetState(new ColorTargetState(state.blending ? Optional.of(BlendFunction.TRANSLUCENT) : Optional.empty(), colors.format(), colors.writeMask()))
                        .withDepthStencilState(new DepthStencilState(depths.depthTest(), state.depth, depths.depthBiasScaleFactor(), depths.depthBiasConstant()))
                        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
            });
            final var setup = RenderSetup.builder(pipeline).withTexture("Sampler0", texture).setOutputTarget(OutputTarget.MAIN_TARGET);
            if (!beam) setup.useLightmap().useOverlay().affectsCrumbling().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE);
            if (blending) setup.sortOnUpload();
            return RenderType.create("ante_sowcer_" + family, setup.createRenderSetup());
        });
    }

    private static final RenderPipeline CUTOUT_PIPELINE = triangles("entity_cutout", RenderPipelines.ENTITY_CUTOUT_CULL, DefaultVertexFormat.ENTITY);
    private static final RenderPipeline TRANSLUCENT_PIPELINE = triangles("entity_translucent_cull", RenderPipelines.ENTITY_TRANSLUCENT_CULL, DefaultVertexFormat.ENTITY);
    private static final RenderPipeline BEAM_OPAQUE_PIPELINE = triangles("beacon_beam_opaque", RenderPipelines.BEACON_BEAM_OPAQUE, DefaultVertexFormat.BLOCK);
    private static final RenderPipeline BEAM_TRANSLUCENT_PIPELINE = triangles("beacon_beam_translucent", RenderPipelines.BEACON_BEAM_TRANSLUCENT, DefaultVertexFormat.BLOCK);

    private static final Function<Identifier, RenderType> ENTITY_CUTOUT = Util.memoize(texture ->
            RenderType.create("ante_entity_cutout", RenderSetup.builder(CUTOUT_PIPELINE)
                    .withTexture("Sampler0", texture).setOutputTarget(OutputTarget.MAIN_TARGET)
                    .useLightmap().useOverlay().affectsCrumbling()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
    private static final Function<Identifier, RenderType> ENTITY_TRANSLUCENT_CULL = Util.memoize(texture ->
            RenderType.create("ante_entity_translucent_cull", RenderSetup.builder(TRANSLUCENT_PIPELINE)
                    // Vanilla's renamed factory uses ITEM_ENTITY_TARGET; legacy ANTE used MAIN_TARGET.
                    .withTexture("Sampler0", texture).setOutputTarget(OutputTarget.MAIN_TARGET)
                    .useLightmap().useOverlay().affectsCrumbling().sortOnUpload()
                    .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup()));
    private static final BiFunction<Identifier, Boolean, RenderType> BEACON_BEAM = Util.memoize((texture, translucent) ->
            RenderType.create("ante_beacon_beam", RenderSetup.builder(translucent ? BEAM_TRANSLUCENT_PIPELINE : BEAM_OPAQUE_PIPELINE)
                    // Beam shaders are full-bright: do not bind entity lightmap or overlay state.
                    .withTexture("Sampler0", texture).setOutputTarget(OutputTarget.MAIN_TARGET)
                    .sortOnUpload().createRenderSetup()));

    public static RenderType entityCutout(Identifier texture) {
        return ENTITY_CUTOUT.apply(texture);
    }

    public static RenderType entityTranslucentCull(Identifier texture) {
        return ENTITY_TRANSLUCENT_CULL.apply(texture);
    }

    public static RenderType beaconBeam(Identifier texture, boolean translucent) {
        return BEACON_BEAM.apply(texture, translucent);
    }

    private static RenderPipeline triangles(String name, RenderPipeline template, VertexFormat format) {
        // RawMesh emits three vertices per face. Keep vanilla shader/binding/depth/blend
        // state intact, but never interpret these vertices with vanilla QUADS topology.
        return RenderPipeline.builder(snippet(template))
                .withLocation(Identifier.fromNamespaceAndPath("mtrsteamloco", "pipeline/" + name))
                .withVertexBinding(0, format).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build();
    }

    private static RenderPipeline.Snippet snippet(RenderPipeline template) {
        return new RenderPipeline.Snippet(
                Optional.of(template.getVertexShader()), Optional.of(template.getFragmentShader()),
                Optional.of(template.getShaderDefines()), Optional.of(template.getBindGroupLayouts()),
                template.getColorTargetStates().clone(), template.getColorTargetStates().length,
                Optional.ofNullable(template.getDepthStencilState()), Optional.of(template.getPolygonMode()),
                Optional.of(template.isCull()), template.getVertexFormatBindings().clone(),
                Optional.of(PrimitiveTopology.TRIANGLES));
    }
}
