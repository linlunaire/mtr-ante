package cn.zbx1425.sowcer.object;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.model.Mesh;
import cn.zbx1425.sowcer.util.AttrUtil;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import cn.zbx1425.sowcer.vertex.VertAttrSrc;
import cn.zbx1425.sowcer.vertex.VertAttrState;
import cn.zbx1425.sowcer.vertex.VertAttrType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import mtr.mappings.RetainedGeometry;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** A retained buffer layout, decoded on extraction into immutable Minecraft geometry. */
public class VertArray implements Closeable {
    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    public int id = NEXT_ID.getAndIncrement();
    public MaterialProp materialProp;
    public IndexBuf indexBuf;
    public InstanceBuf instanceBuf;
    public VertAttrMapping mapping;
    private VertBuf vertexBuf;
    private GeometryKey geometryKey;
    private RetainedGeometry geometry;

    public void create(Mesh mesh, VertAttrMapping mapping, InstanceBuf instances) {
        if (vertexBuf != null || id == 0) throw new IllegalStateException("Vertex array already initialized or closed");
        vertexBuf = mesh.vertBuf;
        indexBuf = mesh.indexBuf;
        instanceBuf = instances;
        materialProp = mesh.materialProp;
        this.mapping = mapping;
        vertexBuf.retain();
        indexBuf.retain();
        if (instances != null) instances.retain();
    }

    public int getFaceCount() { return indexBuf.faceCount * (instanceBuf == null ? 1 : instanceBuf.size); }

    public VertArray copyForMaterialChanges() {
        if (id == 0) throw new IllegalStateException("Copy of closed vertex array");
        final var copy = new VertArray();
        copy.create(new Mesh(vertexBuf, indexBuf, materialProp.copy()), mapping, instanceBuf);
        return copy;
    }

    @Override public void close() {
        if (id == 0) return;
        id = 0;
        geometry = null;
        geometryKey = null;
        if (vertexBuf != null) { vertexBuf.release(); indexBuf.release(); if (instanceBuf != null) instanceBuf.release(); }
    }

    /**
     * Static, translated opaque meshes keep their object-space vertices across frames.
     * All other layouts/transforms still use capture(), including translucent sorting.
     */
    public RetainedDraw captureRetained(VertAttrState enqueue, ShaderProp shader) {
        if (id == 0) throw new IllegalStateException("Capture of closed vertex array");
        if (instanceBuf != null || mapping.sources.get(VertAttrType.MATRIX_MODEL) != VertAttrSrc.GLOBAL
                || materialProp.translucent || materialProp.cutoutHack || !materialProp.writeDepthBuf
                || indexBuf.vertexCount == 0) return null;

        final var type = materialProp.getBlazeRenderType();
        if (!RetainedGeometry.supports(type)) return null;
        final var capturedEnqueue = enqueue == null ? null : enqueue.copy();
        final VertBuf.Upload vertexUpload = vertexBuf.snapshotUpload(), indexUpload = indexBuf.snapshotUpload();
        final ByteBuffer vertices = vertexUpload.data(), indices = indexUpload.data();
        final Matrix4f model = new Reader(vertices, null, 0, 0, capturedEnqueue).matrix();
        final org.joml.Matrix4f pose = shader.viewMatrix == null ? new org.joml.Matrix4f() : new org.joml.Matrix4f(shader.viewMatrix.asMoj());
        pose.mul(model.asMoj());
        // The retained shader applies camera-relative translation to fog as well as
        // projection. Rotation/nonuniform scale need a separate normal transform.
        if ((pose.properties() & org.joml.Matrix4fc.PROPERTY_TRANSLATION) == 0) return null;

        final var key = new GeometryKey(vertexUpload.revision(), indexUpload.revision(), indexBuf.vertexCount,
                withoutMatrix(capturedEnqueue), withoutMatrix(materialProp.attrState), type);
        if (!key.equals(geometryKey)) {
            final var faces = new ArrayList<Face>(indexBuf.vertexCount / 3);
            final Matrix4f identity = new Matrix4f();
            for (int element = 0; element < indexBuf.vertexCount; element += 3) {
                final Value a = decode(vertices, null, index(indices, element), 0, capturedEnqueue, ShaderProp.DEFAULT, identity);
                final Value b = decode(vertices, null, index(indices, element + 1), 0, capturedEnqueue, ShaderProp.DEFAULT, identity);
                final Value c = decode(vertices, null, index(indices, element + 2), 0, capturedEnqueue, ShaderProp.DEFAULT, identity);
                faces.add(new Face(a, b, c));
            }
            final List<Face> immutableFaces = List.copyOf(faces);
            geometry = new RetainedGeometry(type, indexBuf.vertexCount, consumer -> {
                for (Face face : immutableFaces) face.emit(consumer);
            });
            geometryKey = key;
        }
        return new RetainedDraw(geometry, pose);
    }

    private static VertAttrState withoutMatrix(VertAttrState state) {
        if (state == null) return null;
        final var result = state.copy();
        result.matrixModel = null;
        result.useMatixProcess = false;
        return result;
    }

    private record GeometryKey(long vertices, long indices, int count, VertAttrState enqueue,
                               VertAttrState material, net.minecraft.client.renderer.rendertype.RenderType type) { }

    public record RetainedDraw(RetainedGeometry geometry, org.joml.Matrix4f pose) { }

    public List<Face> capture(VertAttrState enqueue, ShaderProp shader) {
        if (id == 0) throw new IllegalStateException("Capture of closed vertex array");
        final int count = instanceBuf == null ? 1 : instanceBuf.size;
        if (count == 0 || indexBuf.vertexCount == 0) return List.of();
        final ByteBuffer vertices = vertexBuf.snapshot(), indices = indexBuf.snapshot();
        final ByteBuffer instances = instanceBuf == null ? null : instanceBuf.snapshot();
        final var capturedShader = new ShaderProp().setViewMatrix(shader.viewMatrix == null ? null : shader.viewMatrix.copy());
        final var capturedEnqueue = enqueue == null ? null : enqueue.copy();
        final var faces = new ArrayList<Face>();
        for (int instance = 0; instance < count; instance++) {
            final Matrix4f instanceMatrix = mapping.sources.get(VertAttrType.MATRIX_MODEL).inVertBuf() ? null
                    : new Reader(vertices, instances, 0, instance, capturedEnqueue).matrix();
            for (int element = 0; element < indexBuf.vertexCount; element += 3) {
                final Value[] triangle = new Value[3];
                for (int corner = 0; corner < 3; corner++) {
                    final int vertex = index(indices, element + corner);
                    triangle[corner] = decode(vertices, instances, vertex, instance, capturedEnqueue, capturedShader, instanceMatrix);
                }
                faces.add(new Face(triangle[0], triangle[1], triangle[2]));
            }
        }
        return List.copyOf(faces);
    }

    private int index(ByteBuffer indices, int element) {
        return switch (indexBuf.indexType) {
            case 0x1405 -> indices.getInt(element * 4); // unsigned int
            case 0x1403 -> Short.toUnsignedInt(indices.getShort(element * 2));
            case 0x1401 -> Byte.toUnsignedInt(indices.get(element));
            default -> throw new IllegalArgumentException("Unsupported index type: " + indexBuf.indexType);
        };
    }

    private Value decode(ByteBuffer vertices, ByteBuffer instances, int vertex, int instance, VertAttrState enqueue, ShaderProp shader, Matrix4f instanceMatrix) {
        final Reader reader = new Reader(vertices, instances, vertex, instance, enqueue);
        final Vector3f position = reader.vector(VertAttrType.POSITION);
        final Vector3f normal = reader.vector(VertAttrType.NORMAL);
        final Matrix4f model = instanceMatrix == null ? reader.matrix() : instanceMatrix;
        final org.joml.Matrix4f matrix = shader.viewMatrix == null ? new org.joml.Matrix4f() : new org.joml.Matrix4f(shader.viewMatrix.asMoj());
        matrix.mul(model.asMoj()).transformPosition(position);
        matrix.normal(new Matrix3f()).transform(normal);
        if (normal.lengthSquared() > 0) normal.normalize();
        final VertAttrState colorState = reader.global(VertAttrType.COLOR);
        final int color = colorState == null ? reader.bufferColor() : AttrUtil.rgbaToArgb(colorState.color == null ? -1 : colorState.color);
        final VertAttrState uvState = reader.global(VertAttrType.UV_TEXTURE);
        final float u = uvState == null ? reader.data(VertAttrType.UV_TEXTURE).getFloat(reader.offset(VertAttrType.UV_TEXTURE)) : uvState.texU == null ? 0 : uvState.texU;
        final float v = uvState == null ? reader.data(VertAttrType.UV_TEXTURE).getFloat(reader.offset(VertAttrType.UV_TEXTURE) + 4) : uvState.texV == null ? 0 : uvState.texV;
        return new Value(position.x, position.y, position.z, normal.x, normal.y, normal.z, u, v, color,
                reader.packed(VertAttrType.UV_LIGHTMAP), reader.packed(VertAttrType.UV_OVERLAY));
    }

    private final class Reader {
        private final ByteBuffer vertices, instances;
        private final int vertex, instance;
        private final VertAttrState enqueue;
        private final VertAttrState defaults = new VertAttrState();
        Reader(ByteBuffer vertices, ByteBuffer instances, int vertex, int instance, VertAttrState enqueue) {
            this.vertices = vertices; this.instances = instances; this.vertex = vertex; this.instance = instance; this.enqueue = enqueue;
        }
        VertAttrState global(VertAttrType type) {
            final VertAttrSrc source = mapping.sources.get(type);
            if (source != VertAttrSrc.GLOBAL && !source.isToggleable()) return null;
            if (materialProp.attrState != null && materialProp.attrState.hasAttr(type)) return materialProp.attrState;
            if (enqueue != null && enqueue.hasAttr(type)) return enqueue;
            return source == VertAttrSrc.GLOBAL ? defaults : null;
        }
        ByteBuffer data(VertAttrType type) {
            final ByteBuffer result = mapping.sources.get(type).inVertBuf() ? vertices : instances;
            if (result == null) throw new IllegalStateException("Missing instance buffer for " + type);
            return result;
        }
        int offset(VertAttrType type) { return mapping.pointers.get(type) + (mapping.sources.get(type).inVertBuf() ? vertex * mapping.strideVertex : instance * mapping.strideInstance); }
        Vector3f vector(VertAttrType type) {
            final VertAttrState state = global(type);
            if (state != null) {
                final var value = type == VertAttrType.POSITION ? state.position : state.normal;
                return value == null ? new Vector3f() : new Vector3f(value.x(), value.y(), value.z());
            }
            final ByteBuffer data = data(type); final int offset = offset(type);
            if (type == VertAttrType.NORMAL) return new Vector3f(Math.max(-1, data.get(offset) / 127F), Math.max(-1, data.get(offset + 1) / 127F), Math.max(-1, data.get(offset + 2) / 127F));
            return new Vector3f(data.getFloat(offset), data.getFloat(offset + 4), data.getFloat(offset + 8));
        }
        int bufferColor() {
            final ByteBuffer data = data(VertAttrType.COLOR); final int offset = offset(VertAttrType.COLOR);
            return (data.get(offset + 3) & 255) << 24 | (data.get(offset) & 255) << 16 | (data.get(offset + 1) & 255) << 8 | (data.get(offset + 2) & 255);
        }
        int packed(VertAttrType type) {
            final VertAttrState state = global(type);
            if (state != null) {
                final Integer value = type == VertAttrType.UV_OVERLAY ? state.overlayUV : state.lightmapUV;
                return value == null ? 0 : AttrUtil.exchangeLightmapUVBits(value);
            }
            final ByteBuffer data = data(type); final int offset = offset(type);
            return Short.toUnsignedInt(data.getShort(offset)) | Short.toUnsignedInt(data.getShort(offset + 2)) << 16;
        }
        Matrix4f matrix() {
            final VertAttrState state = global(VertAttrType.MATRIX_MODEL);
            if (state != null) {
                Matrix4f result = new Matrix4f();
                if (enqueue != null && enqueue.matrixModel != null) result = enqueue.matrixModel.apply(result);
                if (materialProp.attrState != null && materialProp.attrState.matrixModel != null) result = materialProp.attrState.matrixModel.apply(result);
                return result.copy();
            }
            // JOML's native-memory fast path requires direct buffers; uploads here are heap snapshots.
            final ByteBuffer buffer = data(VertAttrType.MATRIX_MODEL);
            final int offset = offset(VertAttrType.MATRIX_MODEL);
            final float[] values = new float[16];
            for (int i = 0; i < values.length; i++) values[i] = buffer.getFloat(offset + i * 4);
            return new Matrix4f(new org.joml.Matrix4f().set(values));
        }
    }

    public record Face(Value a, Value b, Value c) {
        public float distanceSquared() { final float x = a.x + b.x + c.x, y = a.y + b.y + c.y, z = a.z + b.z + c.z; return x * x + y * y + z * z; }
        public void emit(VertexConsumer consumer) { a.emit(consumer); b.emit(consumer); c.emit(consumer); }
    }
    public record Value(float x, float y, float z, float nx, float ny, float nz, float u, float v, int color, int light, int overlay) {
        private void emit(VertexConsumer consumer) { consumer.addVertex(x, y, z).setColor(color).setUv(u, v).setLight(light).setOverlay(overlay).setNormal(nx, ny, nz); }
    }
}
