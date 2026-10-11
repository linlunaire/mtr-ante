package cn.zbx1425.sowcer.shader

import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.vertex.VertexFormat
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiFunction
import java.util.function.Function

open class BlazeRenderType {
    private data class PipelineKey(val shader: String, val blending: Boolean, val depth: Boolean)
    private data class MaterialKey(val texture: Identifier?, val pipeline: PipelineKey)

    companion object {
        private val MATERIAL_PIPELINES: MutableMap<PipelineKey, RenderPipeline> = ConcurrentHashMap()
        private val MATERIALS: MutableMap<MaterialKey, RenderType> = ConcurrentHashMap()

        /** Preserve independent blend/depth flags, including cutout beam blending. */
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun material(texture: Identifier?, shader: String?, blending: Boolean, depth: Boolean): RenderType {
            val family = when (shader!!) {
                "rendertype_beacon_beam" -> "beacon_beam"
                "rendertype_entity_translucent_cull" -> "entity_translucent_cull"
                else -> "entity_cutout"
            }
            return MATERIALS.computeIfAbsent(MaterialKey(texture, PipelineKey(family, blending, depth))) { key ->
                val beam = family == "beacon_beam"
                val pipeline = MATERIAL_PIPELINES.computeIfAbsent(key.pipeline) { state ->
                    val template = if (beam) RenderPipelines.BEACON_BEAM_OPAQUE
                        else if (family == "entity_translucent_cull") RenderPipelines.ENTITY_TRANSLUCENT_CULL
                        else RenderPipelines.ENTITY_CUTOUT_CULL
                    val colors = template.colorTargetState!!
                    val depths = template.depthStencilState!!
                    RenderPipeline.builder(snippet(template))
                        .withLocation(Identifier.fromNamespaceAndPath("mtrsteamloco", "pipeline/sowcer_" + family + "_" + state.blending + "_" + state.depth))
                        .withVertexBinding(0, if (beam) DefaultVertexFormat.BLOCK else DefaultVertexFormat.ENTITY)
                        .withColorTargetState(ColorTargetState(if (state.blending) Optional.of(BlendFunction.TRANSLUCENT) else Optional.empty(), colors.format(), colors.writeMask()))
                        .withDepthStencilState(DepthStencilState(depths.depthTest(), state.depth, depths.depthBiasScaleFactor(), depths.depthBiasConstant()))
                        .withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build()
                }
                val setup = RenderSetup.builder(pipeline).withTexture("Sampler0", javaNullable(texture)).setOutputTarget(OutputTarget.MAIN_TARGET)
                if (!beam) setup.useLightmap().useOverlay().affectsCrumbling().setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                if (blending) setup.sortOnUpload()
                RenderType.create("ante_sowcer_" + family, setup.createRenderSetup())
            }
        }

        private val CUTOUT_PIPELINE = triangles("entity_cutout", RenderPipelines.ENTITY_CUTOUT_CULL, DefaultVertexFormat.ENTITY)
        private val TRANSLUCENT_PIPELINE = triangles("entity_translucent_cull", RenderPipelines.ENTITY_TRANSLUCENT_CULL, DefaultVertexFormat.ENTITY)
        private val BEAM_OPAQUE_PIPELINE = triangles("beacon_beam_opaque", RenderPipelines.BEACON_BEAM_OPAQUE, DefaultVertexFormat.BLOCK)
        private val BEAM_TRANSLUCENT_PIPELINE = triangles("beacon_beam_translucent", RenderPipelines.BEACON_BEAM_TRANSLUCENT, DefaultVertexFormat.BLOCK)

        private val ENTITY_CUTOUT: Function<Identifier, RenderType> = Util.memoize<Identifier, RenderType>(Function<Identifier, RenderType> { texture ->
            RenderType.create("ante_entity_cutout", RenderSetup.builder(CUTOUT_PIPELINE)
                .withTexture("Sampler0", texture).setOutputTarget(OutputTarget.MAIN_TARGET)
                .useLightmap().useOverlay().affectsCrumbling()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup())
        })
        private val ENTITY_TRANSLUCENT_CULL: Function<Identifier, RenderType> = Util.memoize<Identifier, RenderType>(Function<Identifier, RenderType> { texture ->
            RenderType.create("ante_entity_translucent_cull", RenderSetup.builder(TRANSLUCENT_PIPELINE)
                // Legacy ANTE uses MAIN_TARGET, not vanilla's renamed ITEM_ENTITY_TARGET factory.
                .withTexture("Sampler0", texture).setOutputTarget(OutputTarget.MAIN_TARGET)
                .useLightmap().useOverlay().affectsCrumbling().sortOnUpload()
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE).createRenderSetup())
        })
        // The Java BiFunction memoizer keys by Pair, so a nullable first component is valid
        // despite its annotated non-null type bound. Keep the callback itself nullable.
        @Suppress("UNCHECKED_CAST")
        private val BEACON_BEAM: BiFunction<Identifier, Boolean, RenderType> = Util.memoize<Identifier, Boolean, RenderType>(BiFunction<Identifier?, Boolean, RenderType> { texture, translucent ->
            RenderType.create("ante_beacon_beam", RenderSetup.builder(if (translucent) BEAM_TRANSLUCENT_PIPELINE else BEAM_OPAQUE_PIPELINE)
                // Beam shaders are full-bright and do not bind entity lightmap/overlay state.
                .withTexture("Sampler0", javaNullable(texture)).setOutputTarget(OutputTarget.MAIN_TARGET)
                .sortOnUpload().createRenderSetup())
        } as BiFunction<Identifier, Boolean, RenderType>)

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun entityCutout(texture: Identifier?): RenderType = ENTITY_CUTOUT.apply(texture!!)

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun entityTranslucentCull(texture: Identifier?): RenderType = ENTITY_TRANSLUCENT_CULL.apply(texture!!)

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun beaconBeam(texture: Identifier?, translucent: Boolean): RenderType = BEACON_BEAM.apply(javaNullable(texture), translucent)

        // 26.2 annotates withTexture as non-null, but its Java implementation accepts null.
        // Preserve the old beam factories' deferred binding and nullable memoization contract.
        // Erasure forwards the reference unchanged; no wrapper, reflection or allocation is used.
        @Suppress("UNCHECKED_CAST")
        private fun <T> javaNullable(value: T?): T = value as T

        private fun triangles(name: String, template: RenderPipeline, format: VertexFormat): RenderPipeline =
            // Preserve vanilla state while interpreting each RawMesh face as three vertices.
            RenderPipeline.builder(snippet(template))
                .withLocation(Identifier.fromNamespaceAndPath("mtrsteamloco", "pipeline/" + name))
                .withVertexBinding(0, format).withPrimitiveTopology(PrimitiveTopology.TRIANGLES).build()

        private fun snippet(template: RenderPipeline): RenderPipeline.Snippet = RenderPipeline.Snippet(
            Optional.of(template.vertexShader), Optional.of(template.fragmentShader),
            Optional.of(template.shaderDefines), Optional.of(template.bindGroupLayouts),
            template.colorTargetStates.clone(), template.colorTargetStates.size,
            Optional.ofNullable(template.depthStencilState), Optional.of(template.polygonMode),
            Optional.of(template.isCull), template.vertexFormatBindings.clone(),
            Optional.of(PrimitiveTopology.TRIANGLES))
    }
}
