package cn.zbx1425.mtrsteamloco.render;

import cn.zbx1425.mtrsteamloco.gui.SelectListScreen;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

public final class PreviewProjection {
    private PreviewProjection() {}

    public static boolean isActive() {
        return Minecraft.getInstance().gui.screen() instanceof SelectListScreen screen && screen.isSelecting();
    }

    public static Matrix4f apply(Matrix4f projection) {
        return new Matrix4f().translation(0.5F, 0, 0).scale(0.8F, 0.8F, 1).mul(projection);
    }
}
