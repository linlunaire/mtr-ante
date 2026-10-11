package cn.zbx1425.mtrsteamloco.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.culling.Frustum;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// Retain the existing mixin name; culling now belongs to Camera, not LevelRenderer.
@Mixin(Camera.class)
public interface LevelRendererAccessor {
    @Accessor("cullFrustum")
    Frustum getCullingFrustum();
}
