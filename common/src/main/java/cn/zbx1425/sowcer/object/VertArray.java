package cn.zbx1425.sowcer.object;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.model.Mesh;
import cn.zbx1425.sowcer.util.GlStateTracker;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import com.mojang.blaze3d.systems.RenderSystem;
import org.lwjgl.opengl.GL33;

import java.io.Closeable;

public class VertArray implements Closeable {

    public int id;
    public MaterialProp materialProp;
    public IndexBuf indexBuf;
    public InstanceBuf instanceBuf;
    public VertAttrMapping mapping;
    private SharedVertexArray ownedArray;

    public VertArray() {
        ownedArray = new SharedVertexArray();
        id = ownedArray.id;
    }

    private VertArray(VertArray other) {
        synchronized (other) {
            if (other.ownedArray == null) throw new IllegalStateException("Cannot copy a closed vertex array");
            ownedArray = other.ownedArray;
            ownedArray.retain();
            this.id = other.id;
            this.materialProp = other.materialProp;
            this.indexBuf = other.indexBuf;
            this.instanceBuf = other.instanceBuf;
            this.mapping = other.mapping;
        }
    }

    public void create(Mesh mesh, VertAttrMapping mapping, InstanceBuf instanceBuf) {
        this.materialProp = mesh.materialProp;
        this.indexBuf = mesh.indexBuf;
        this.instanceBuf = instanceBuf;
        this.mapping = mapping;
        GL33.glBindVertexArray(id);
        try {
            mapping.setupAttrsToVao(mesh.vertBuf, instanceBuf);
            mesh.indexBuf.bind(GL33.GL_ELEMENT_ARRAY_BUFFER);
        } finally {
            unbind();
        }
    }

    public void bind() {
        GlStateTracker.assertProtected();
        GL33.glBindVertexArray(id);
    }

    public static void unbind() {
        GlStateTracker.assertProtected();
        GL33.glBindVertexArray(0);
    }

    public void draw() {
        if (indexBuf == null || id == 0 || indexBuf.vertexCount == 0) return;
        if (instanceBuf == null) {
            GL33.glDrawElements(GL33.GL_TRIANGLES, indexBuf.vertexCount, indexBuf.indexType, 0L);
        } else {
            if (instanceBuf.size < 1) return;
            GL33.glDrawElementsInstanced(GL33.GL_TRIANGLES, indexBuf.vertexCount, indexBuf.indexType, 0L, instanceBuf.size);
        }
    }

    public int getFaceCount() {
        return indexBuf.faceCount * (instanceBuf == null ? 1 : instanceBuf.size);
    }

    public VertArray copyForMaterialChanges() {
        VertArray result = new VertArray(this);
        try {
            result.materialProp = result.materialProp.copy();
            return result;
        } catch (RuntimeException | Error ex) {
            try { result.close(); } catch (RuntimeException | Error cleanup) { ex.addSuppressed(cleanup); }
            throw ex;
        }
    }

    @Override
    public void close() {
        final SharedVertexArray released;
        synchronized (this) {
            released = ownedArray;
            ownedArray = null;
            id = 0;
        }
        if (released != null) released.release();
    }

    private static void _close(int id) {
        if (RenderSystem.isOnRenderThread()) {
            GL33.glDeleteVertexArrays(id);
        } else {
            RenderSystem.recordRenderCall(() -> _close(id));
        }
    }

    /** Material views share a VAO; deletion belongs to the last live wrapper. */
    private static final class SharedVertexArray {
        private final int id = GL33.glGenVertexArrays();
        private int references = 1;

        synchronized void retain() {
            if (references == 0) throw new IllegalStateException("Vertex array already released");
            references = Math.addExact(references, 1);
        }

        void release() {
            final boolean last;
            synchronized (this) { last = --references == 0; }
            if (last && id != 0) {
                final int releasedId = id;
                // Queue after prior rendering, and never capture a wrapper's mutable id field.
                RenderSystem.recordRenderCall(() -> _close(releasedId));
            }
        }
    }
}
