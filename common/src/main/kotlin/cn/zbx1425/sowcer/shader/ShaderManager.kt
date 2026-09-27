package cn.zbx1425.sowcer.shader

import cn.zbx1425.sowcer.batch.MaterialProp
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import java.io.IOException
import java.util.LinkedHashSet

/** Pipeline selection; Minecraft owns shader compilation, bindings and reloads. */
open class ShaderManager {
    private val requiredSources: MutableSet<Identifier> = LinkedHashSet()

    init {
        val texture = MaterialProp.WHITE_TEXTURE_LOCATION
        for (type in arrayOf(BlazeRenderType.entityCutout(texture), BlazeRenderType.entityTranslucentCull(texture),
            BlazeRenderType.beaconBeam(texture, false), BlazeRenderType.beaconBeam(texture, true))) {
            val pipeline = type.pipeline()
            requiredSources.add(pipeline.vertexShader.withPrefix("shaders/").withSuffix(".vsh"))
            requiredSources.add(pipeline.fragmentShader.withPrefix("shaders/").withSuffix(".fsh"))
        }
    }

    open fun isReady(): Boolean = requiredSources.isNotEmpty()

    @Throws(IOException::class)
    open fun reloadShaders(resources: ResourceManager?) {
        // Detect missing resources early; the engine recompiles these pipelines on reload.
        for (source in requiredSources) resources!!.getResourceOrThrow(source)
    }

    open fun material(material: MaterialProp?): RenderType = material!!.getBlazeRenderType()
}
