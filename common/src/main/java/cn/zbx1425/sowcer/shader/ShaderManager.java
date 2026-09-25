package cn.zbx1425.sowcer.shader;

import cn.zbx1425.sowcer.batch.MaterialProp;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Pipeline selection only; shader compilation, bindings and reload ownership belong to Minecraft. */
public class ShaderManager {
    private final Set<Identifier> requiredSources = new LinkedHashSet<>();

    public ShaderManager() {
        final Identifier texture = MaterialProp.WHITE_TEXTURE_LOCATION;
        for (RenderType type : new RenderType[]{BlazeRenderType.entityCutout(texture), BlazeRenderType.entityTranslucentCull(texture),
                BlazeRenderType.beaconBeam(texture, false), BlazeRenderType.beaconBeam(texture, true)}) {
            final RenderPipeline pipeline = type.pipeline();
            requiredSources.add(pipeline.getVertexShader().withPrefix("shaders/").withSuffix(".vsh"));
            requiredSources.add(pipeline.getFragmentShader().withPrefix("shaders/").withSuffix(".fsh"));
        }
    }

    public boolean isReady() { return !requiredSources.isEmpty(); }

    public void reloadShaders(ResourceManager resources) throws IOException {
        // Detect missing resources early; the engine recompiles the referenced pipelines on reload.
        for (Identifier source : requiredSources) resources.getResourceOrThrow(source);
    }

    public RenderType material(MaterialProp material) { return material.getBlazeRenderType(); }
}
