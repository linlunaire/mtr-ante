package cn.zbx1425.sowcer.shader;

import cn.zbx1425.mtrsteamloco.render.ShadersModHandler;
import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.util.AttrUtil;
import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.shaders.ProgramManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import cn.zbx1425.sowcer.math.Matrix4f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class ShaderManager {

    /**
     * Since 1.21 the individual default vertex elements are private.  The
     * vanilla entity layout is still the layout used by our patched shaders;
     * using the public complete format also keeps it in sync with Mojang's
     * future packing changes.
     */
    public static final VertexFormat MC_FORMAT_ENTITY_MAT = DefaultVertexFormat.NEW_ENTITY;

    public final Map<String, ShaderInstance> shaders = new HashMap<>();

    public boolean isReady() {
        return this.shaders.size() > 0;
    }

    public void reloadShaders(ResourceManager resourceManager) throws IOException {
        this.shaders.values().forEach(ShaderInstance::close);
        this.shaders.clear();
        PatchingResourceProvider provider = new PatchingResourceProvider(resourceManager);

        loadShader(provider, "rendertype_entity_cutout");
        loadShader(provider, "rendertype_entity_translucent_cull");
        loadShader(provider, "rendertype_beacon_beam");
    }

    private void loadShader(ResourceProvider resourceManager, String name) throws IOException {
        ShaderInstance shader = new ShaderInstance(resourceManager, name, MC_FORMAT_ENTITY_MAT);
        shaders.put(name, shader);
    }

    public void setupShaderBatchState(MaterialProp materialProp, ShaderProp shaderProp) {
        final boolean useCustomShader = ShadersModHandler.canUseCustomShader();
        ShaderInstance shaderInstance;

        if (useCustomShader) {
            shaderInstance = shaders.get(materialProp.shaderName);
            materialProp.setupCompositeState();
        } else {
            RenderType renderType = materialProp.getBlazeRenderType();
            renderType.setupRenderState();
            shaderInstance = RenderSystem.getShader();
        }

        if (shaderInstance == null) {
            throw new IllegalArgumentException("Cannot get shader: " + materialProp.shaderName
                    + (useCustomShader ? "_modelmat" : ""));
        }

        for (int l = 0; l < 8; ++l) {
            int o = RenderSystem.getShaderTexture(l);
            shaderInstance.setSampler("Sampler" + l, o);
        }
        if (shaderInstance.MODEL_VIEW_MATRIX != null) {
            Matrix4f mvMatrix = new Matrix4f(RenderSystem.getModelViewMatrix()).copy();
            if (shaderProp.viewMatrix != null) mvMatrix.multiply(shaderProp.viewMatrix);
            //if (materialProp.useMatixProcess()) AttrUtil.zeroRotation(mvMatrix);
            shaderInstance.MODEL_VIEW_MATRIX.set(mvMatrix.asMoj());
        }
        if (shaderInstance.PROJECTION_MATRIX != null) {
            shaderInstance.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        }
        if (shaderInstance.COLOR_MODULATOR != null) {
            shaderInstance.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        if (shaderInstance.FOG_START != null) {
            shaderInstance.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        if (shaderInstance.FOG_END != null) {
            shaderInstance.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        if (shaderInstance.FOG_COLOR != null) {
            shaderInstance.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
#if MC_VERSION >= "11800"
        if (shaderInstance.FOG_SHAPE != null) {
            shaderInstance.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
#endif
        if (shaderInstance.TEXTURE_MATRIX != null) {
            shaderInstance.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }
        if (shaderInstance.GAME_TIME != null) {
            shaderInstance.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }
        if (shaderInstance.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            shaderInstance.SCREEN_SIZE.set((float)window.getWidth(), (float)window.getHeight());
        }

        RenderSystem.setupShaderLights(shaderInstance);

        shaderInstance.apply();

        if (shaderInstance.programId != ShaderInstance.lastProgramId) {
            ProgramManager.glUseProgram(shaderInstance.programId);
            ShaderInstance.lastProgramId = shaderInstance.programId;
        }
    }

    public void cleanupShaderBatchState(MaterialProp materialProp, ShaderProp shaderProp) {
        final boolean useCustomShader = ShadersModHandler.canUseCustomShader();
        if (!useCustomShader) {
            ShaderInstance shaderInstance = RenderSystem.getShader();
            if (shaderInstance != null && shaderInstance.MODEL_VIEW_MATRIX != null) {
                // ModelViewMatrix might have got set in VertAttrState, reset it
                shaderInstance.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
                if (ShadersModHandler.canUseCustomShader()) {
                    shaderInstance.MODEL_VIEW_MATRIX.upload();
                } else {
                    shaderInstance.apply();
                }
            }
        }
    }

}
