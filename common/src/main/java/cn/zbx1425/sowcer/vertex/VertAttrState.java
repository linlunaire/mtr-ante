package cn.zbx1425.sowcer.vertex;

import cn.zbx1425.sowcer.util.AttrUtil;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Objects;
import java.util.Arrays;
import java.util.function.Function;

public class VertAttrState {

    public static final Function<Matrix4f, Matrix4f> BILLBOARD = (matrix) -> {
        Vector3f pos = matrix.getTranslationPart();
        Matrix4f result = new Matrix4f();
        result.translate(pos);
        return result;
    };
    public Vector3f position;
    public Integer color;
    public Float texU, texV;
    public Integer overlayUV;
    public Integer lightmapUV;
    public Vector3f normal;
    public Function<Matrix4f, Matrix4f> matrixModel;
    public boolean useMatixProcess = false;

    public VertAttrState setPosition(Vector3f position) {
        this.position = position;
        return this;
    }

    public VertAttrState setColor(int r, int g, int b, int a) {
        this.color = r << 24 | g << 16 | b << 8 | a;
        return this;
    }

    public VertAttrState setColor(int rgba) {
        this.color = rgba;
        return this;
    }

    public VertAttrState setTextureUV(float u, float v) {
        this.texU = u;
        this.texV = v;
        return this;
    }

    public VertAttrState setLightmapUV(short u, short v) {
        this.lightmapUV = u << 16 | v;
        return this;
    }

    public VertAttrState setOverlayUV(int uv) {
        this.overlayUV = AttrUtil.exchangeLightmapUVBits(uv);
        return this;
    }

    public VertAttrState setOverlayUVNoOverlay() {
        this.overlayUV = AttrUtil.exchangeLightmapUVBits(OverlayTexture.NO_OVERLAY);
        return this;
    }

    public VertAttrState setLightmapUV(int uv) {
        this.lightmapUV = uv;
        return this;
    }

    public VertAttrState setNormal(Vector3f position) {
        this.normal = position;
        return this;
    }

    public VertAttrState setModelMatrix(Matrix4f matrix) {
        this.matrixModel = (matrixIn) -> {
            // nowMatrix = matrix;
            return matrix;
        };
        return this;
    }

    public VertAttrState setMatixProcess(Function<Matrix4f, Matrix4f> matrixProcess) {
        this.matrixModel = matrixProcess;
        this.useMatixProcess = matrixProcess != null;
        return this;
    }

    public boolean useMatixProcess() {
        return useMatixProcess;
    }

    public boolean hasAttr(VertAttrType attrType) {
        switch (attrType) {
            case POSITION:
                return position != null;
            case COLOR:
                return color != null;
            case NORMAL:
                return normal != null;
            case UV_OVERLAY:
                return overlayUV != null;
            case UV_TEXTURE:
                return texU != null && texV != null;
            case UV_LIGHTMAP:
                return lightmapUV != null;
            case MATRIX_MODEL:
                return matrixModel != null;
        }
        return false;
    }

    public void clearAttr(VertAttrType attrType) {
        switch (attrType) {
            case POSITION:
                position = null;
                break;
            case COLOR:
                color = null;
                break;
            case NORMAL:
                normal = null;
                break;
            case UV_OVERLAY:
                overlayUV = null;
                break;
            case UV_TEXTURE:
                texU = null;
                texV = null;
                break;
            case UV_LIGHTMAP:
                lightmapUV = null;
                break;
            case MATRIX_MODEL:
                matrixModel = null;
                break;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VertAttrState that = (VertAttrState) o;
        return Objects.equals(overlayUV, that.overlayUV) && useMatixProcess == that.useMatixProcess && Objects.equals(position, that.position) && Objects.equals(color, that.color) && Objects.equals(texU, that.texU)
                && Objects.equals(texV, that.texV) && Objects.equals(lightmapUV, that.lightmapUV) && Objects.equals(normal, that.normal)
                && Objects.equals(matrixModel, that.matrixModel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position, color, texU, texV, overlayUV, lightmapUV, normal, matrixModel, useMatixProcess);
    }

    public VertAttrState copy() {
        VertAttrState clone = new VertAttrState();
        clone.position = this.position == null ? null : this.position.copy();
        clone.color = this.color;
        clone.overlayUV = this.overlayUV;
        clone.useMatixProcess = this.useMatixProcess;
        clone.texU = this.texU;
        clone.texV = this.texV;
        clone.lightmapUV = this.lightmapUV;
        clone.normal = this.normal == null ? null : this.normal.copy();
        clone.matrixModel = this.matrixModel == null ? null : this.matrixModel;
        return clone;
    }
}
