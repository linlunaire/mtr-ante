package cn.zbx1425.sowcer.`object`

import cn.zbx1425.sowcer.batch.MaterialProp
import cn.zbx1425.sowcer.batch.ShaderProp
import cn.zbx1425.sowcer.math.Matrix4f
import cn.zbx1425.sowcer.model.Mesh
import cn.zbx1425.sowcer.util.AttrUtil
import cn.zbx1425.sowcer.vertex.VertAttrMapping
import cn.zbx1425.sowcer.vertex.VertAttrSrc
import cn.zbx1425.sowcer.vertex.VertAttrState
import cn.zbx1425.sowcer.vertex.VertAttrType
import com.mojang.blaze3d.vertex.VertexConsumer
import mtr.mappings.RetainedGeometry
import net.minecraft.client.renderer.rendertype.RenderType
import org.joml.Matrix3f
import org.joml.Vector3f
import java.io.Closeable
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger

/** A retained buffer layout, decoded on extraction into immutable Minecraft geometry. */
open class VertArray : Closeable {
    @JvmField var id: Int = NEXT_ID.getAndIncrement()
    @JvmField var materialProp: MaterialProp? = null
    @JvmField var indexBuf: IndexBuf? = null
    @JvmField var instanceBuf: InstanceBuf? = null
    @JvmField var mapping: VertAttrMapping? = null
    private var vertexBuf: VertBuf? = null
    private var geometryKey: GeometryKey? = null
    private var geometry: RetainedGeometry? = null

    open fun create(mesh: Mesh?, mapping: VertAttrMapping?, instances: InstanceBuf?) {
        if (vertexBuf != null || id == 0) throw IllegalStateException("Vertex array already initialized or closed")
        vertexBuf = mesh!!.vertBuf
        indexBuf = mesh.indexBuf
        instanceBuf = instances
        materialProp = mesh.materialProp
        this.mapping = mapping
        vertexBuf!!.retainShared()
        indexBuf!!.retainShared()
        if (instances != null) instances.retainShared()
    }

    open fun getFaceCount(): Int = indexBuf!!.faceCount * (if (instanceBuf == null) 1 else instanceBuf!!.size)

    open fun copyForMaterialChanges(): VertArray {
        if (id == 0) throw IllegalStateException("Copy of closed vertex array")
        val copy = VertArray()
        copy.create(Mesh(vertexBuf, indexBuf, materialProp!!.copy()), mapping, instanceBuf)
        return copy
    }

    override fun close() {
        if (id == 0) return
        id = 0
        geometry = null
        geometryKey = null
        if (vertexBuf != null) {
            vertexBuf!!.releaseShared()
            indexBuf!!.releaseShared()
            if (instanceBuf != null) instanceBuf!!.releaseShared()
        }
    }

    /**
     * Static translated opaque meshes retain object-space vertices across frames.
     * Other layouts/transforms still use capture(), including translucent sorting.
     */
    open fun captureRetained(enqueue: VertAttrState?, shader: ShaderProp?): RetainedDraw? {
        if (id == 0) throw IllegalStateException("Capture of closed vertex array")
        if (instanceBuf != null || mapping!!.sources[VertAttrType.MATRIX_MODEL] != VertAttrSrc.GLOBAL ||
            materialProp!!.translucent || materialProp!!.cutoutHack || !materialProp!!.writeDepthBuf ||
            indexBuf!!.vertexCount == 0) return null

        val type = materialProp!!.getBlazeRenderType()
        if (!RetainedGeometry.supports(type)) return null
        val capturedEnqueue = enqueue?.copy()
        val vertexUpload = vertexBuf!!.snapshotShared()
        val indexUpload = indexBuf!!.snapshotShared()
        val vertices = vertexUpload.data!!
        val indices = indexUpload.data!!
        val model = Reader(vertices, null, 0, 0, capturedEnqueue).matrix()
        val pose = if (shader!!.viewMatrix == null) org.joml.Matrix4f() else org.joml.Matrix4f(shader.viewMatrix!!.asMoj())
        pose.mul(model.asMoj())
        // Retained shaders translate camera-relative fog too; other transforms need normal transforms.
        if ((pose.properties() and org.joml.Matrix4fc.PROPERTY_TRANSLATION.toInt()) == 0) return null

        val key = GeometryKey(vertexUpload.revision, indexUpload.revision, indexBuf!!.vertexCount,
            withoutMatrix(capturedEnqueue), withoutMatrix(materialProp!!.attrState), type)
        if (key != geometryKey) {
            val faces = ArrayList<Face>(indexBuf!!.vertexCount / 3)
            val identity = Matrix4f()
            var element = 0
            while (element < indexBuf!!.vertexCount) {
                val a = decode(vertices, null, index(indices, element), 0, capturedEnqueue, ShaderProp.DEFAULT, identity)
                val b = decode(vertices, null, index(indices, element + 1), 0, capturedEnqueue, ShaderProp.DEFAULT, identity)
                val c = decode(vertices, null, index(indices, element + 2), 0, capturedEnqueue, ShaderProp.DEFAULT, identity)
                faces.add(Face(a, b, c))
                element += 3
            }
            val immutableFaces = java.util.List.copyOf(faces)
            geometry = RetainedGeometry(type, indexBuf!!.vertexCount) { consumer ->
                for (face in immutableFaces) face.emit(consumer)
            }
            geometryKey = key
        }
        return RetainedDraw(geometry, pose)
    }

    private data class GeometryKey(val vertices: Long, val indices: Long, val count: Int,
        val enqueue: VertAttrState?, val material: VertAttrState?, val type: RenderType?)

    @JvmRecord
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    data class RetainedDraw(open val geometry: RetainedGeometry?, open val pose: org.joml.Matrix4f?) {
        // Preserve Java record accessor/method modifiers, nulls, equality and square-bracket text.
        final override fun equals(other: Any?): Boolean = this === other || other is RetainedDraw &&
            java.util.Objects.equals(geometry, other.geometry) && java.util.Objects.equals(pose, other.pose)
        final override fun hashCode(): Int = 31 * java.util.Objects.hashCode(geometry) + java.util.Objects.hashCode(pose)
        final override fun toString(): String = "RetainedDraw[geometry=" + geometry + ", pose=" + pose + "]"
    }

    open fun capture(enqueue: VertAttrState?, shader: ShaderProp?): List<Face> {
        if (id == 0) throw IllegalStateException("Capture of closed vertex array")
        val count = if (instanceBuf == null) 1 else instanceBuf!!.size
        if (count == 0 || indexBuf!!.vertexCount == 0) return java.util.List.of()
        val vertices = vertexBuf!!.snapshot()
        val indices = indexBuf!!.snapshot()
        val instances = if (instanceBuf == null) null else instanceBuf!!.snapshot()
        val capturedShader = ShaderProp().setViewMatrix(if (shader!!.viewMatrix == null) null else shader.viewMatrix!!.copy())
        val capturedEnqueue = enqueue?.copy()
        val faces = ArrayList<Face>()
        for (instance in 0 until count) {
            val instanceMatrix = if (mapping!!.sources[VertAttrType.MATRIX_MODEL]!!.inVertBuf()) null
                else Reader(vertices, instances, 0, instance, capturedEnqueue).matrix()
            var element = 0
            while (element < indexBuf!!.vertexCount) {
                val triangle = arrayOfNulls<Value>(3)
                for (corner in 0 until 3) {
                    val vertex = index(indices, element + corner)
                    triangle[corner] = decode(vertices, instances, vertex, instance, capturedEnqueue, capturedShader, instanceMatrix)
                }
                faces.add(Face(triangle[0], triangle[1], triangle[2]))
                element += 3
            }
        }
        return java.util.List.copyOf(faces)
    }

    private fun index(indices: ByteBuffer, element: Int): Int = when (indexBuf!!.indexType) {
        0x1405 -> indices.getInt(element * 4)
        0x1403 -> java.lang.Short.toUnsignedInt(indices.getShort(element * 2))
        0x1401 -> java.lang.Byte.toUnsignedInt(indices.get(element))
        else -> throw IllegalArgumentException("Unsupported index type: " + indexBuf!!.indexType)
    }

    private fun decode(vertices: ByteBuffer, instances: ByteBuffer?, vertex: Int, instance: Int,
        enqueue: VertAttrState?, shader: ShaderProp?, instanceMatrix: Matrix4f?): Value {
        val reader = Reader(vertices, instances, vertex, instance, enqueue)
        val position = reader.vector(VertAttrType.POSITION)
        val normal = reader.vector(VertAttrType.NORMAL)
        val model = instanceMatrix ?: reader.matrix()
        val matrix = if (shader!!.viewMatrix == null) org.joml.Matrix4f() else org.joml.Matrix4f(shader.viewMatrix!!.asMoj())
        matrix.mul(model.asMoj()).transformPosition(position)
        matrix.normal(Matrix3f()).transform(normal)
        if (normal.lengthSquared() > 0) normal.normalize()
        val colorState = reader.global(VertAttrType.COLOR)
        val color = if (colorState == null) reader.bufferColor() else AttrUtil.rgbaToArgb(colorState.color ?: -1)
        val uvState = reader.global(VertAttrType.UV_TEXTURE)
        val u = if (uvState == null) reader.data(VertAttrType.UV_TEXTURE).getFloat(reader.offset(VertAttrType.UV_TEXTURE)) else uvState.texU ?: 0f
        val v = if (uvState == null) reader.data(VertAttrType.UV_TEXTURE).getFloat(reader.offset(VertAttrType.UV_TEXTURE) + 4) else uvState.texV ?: 0f
        return Value(position.x, position.y, position.z, normal.x, normal.y, normal.z, u, v, color,
            reader.packed(VertAttrType.UV_LIGHTMAP), reader.packed(VertAttrType.UV_OVERLAY))
    }

    private inner class Reader(private val vertices: ByteBuffer, private val instances: ByteBuffer?,
        private val vertex: Int, private val instance: Int, private val enqueue: VertAttrState?) {
        private val defaults = VertAttrState()

        fun global(type: VertAttrType): VertAttrState? {
            val source = mapping!!.sources[type]
            if (source != VertAttrSrc.GLOBAL && !source!!.isToggleable()) return null
            if (materialProp!!.attrState != null && materialProp!!.attrState!!.hasAttr(type)) return materialProp!!.attrState
            if (enqueue != null && enqueue.hasAttr(type)) return enqueue
            return if (source == VertAttrSrc.GLOBAL) defaults else null
        }

        fun data(type: VertAttrType): ByteBuffer {
            val result = if (mapping!!.sources[type]!!.inVertBuf()) vertices else instances
            if (result == null) throw IllegalStateException("Missing instance buffer for " + type)
            return result
        }

        fun offset(type: VertAttrType): Int = mapping!!.pointers[type]!! +
            if (mapping!!.sources[type]!!.inVertBuf()) vertex * mapping!!.strideVertex else instance * mapping!!.strideInstance

        fun vector(type: VertAttrType): Vector3f {
            val state = global(type)
            if (state != null) {
                val value = if (type == VertAttrType.POSITION) state.position else state.normal
                return if (value == null) Vector3f() else Vector3f(value.x(), value.y(), value.z())
            }
            val data = data(type)
            val offset = offset(type)
            if (type == VertAttrType.NORMAL) return Vector3f(Math.max(-1f, data.get(offset) / 127f),
                Math.max(-1f, data.get(offset + 1) / 127f), Math.max(-1f, data.get(offset + 2) / 127f))
            return Vector3f(data.getFloat(offset), data.getFloat(offset + 4), data.getFloat(offset + 8))
        }

        fun bufferColor(): Int {
            val data = data(VertAttrType.COLOR)
            val offset = offset(VertAttrType.COLOR)
            return ((data.get(offset + 3).toInt() and 255) shl 24) or ((data.get(offset).toInt() and 255) shl 16) or
                ((data.get(offset + 1).toInt() and 255) shl 8) or (data.get(offset + 2).toInt() and 255)
        }

        fun packed(type: VertAttrType): Int {
            val state = global(type)
            if (state != null) {
                val value = if (type == VertAttrType.UV_OVERLAY) state.overlayUV else state.lightmapUV
                return if (value == null) 0 else AttrUtil.exchangeLightmapUVBits(value)
            }
            val data = data(type)
            val offset = offset(type)
            return java.lang.Short.toUnsignedInt(data.getShort(offset)) or (java.lang.Short.toUnsignedInt(data.getShort(offset + 2)) shl 16)
        }

        fun matrix(): Matrix4f {
            val state = global(VertAttrType.MATRIX_MODEL)
            if (state != null) {
                var result: Matrix4f? = Matrix4f()
                if (enqueue != null && enqueue.matrixModel != null) result = enqueue.matrixModel!!.apply(result)
                if (materialProp!!.attrState != null && materialProp!!.attrState!!.matrixModel != null) result = materialProp!!.attrState!!.matrixModel!!.apply(result)
                return result!!.copy()
            }
            // Uploaded snapshots are heap buffers, so avoid JOML's direct-memory fast path.
            val buffer = data(VertAttrType.MATRIX_MODEL)
            val offset = offset(VertAttrType.MATRIX_MODEL)
            val values = FloatArray(16)
            for (i in values.indices) values[i] = buffer.getFloat(offset + i * 4)
            return Matrix4f(org.joml.Matrix4f().set(values))
        }
    }

    @JvmRecord
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    data class Face(open val a: Value?, open val b: Value?, open val c: Value?) {
        open fun distanceSquared(): Float {
            val x = a!!.x + b!!.x + c!!.x
            val y = a!!.y + b!!.y + c!!.y
            val z = a!!.z + b!!.z + c!!.z
            return x * x + y * y + z * z
        }
        open fun emit(consumer: VertexConsumer?) { a!!.emit(consumer); b!!.emit(consumer); c!!.emit(consumer) }
        final override fun equals(other: Any?): Boolean = this === other || other is Face &&
            java.util.Objects.equals(a, other.a) && java.util.Objects.equals(b, other.b) && java.util.Objects.equals(c, other.c)
        final override fun hashCode(): Int = 31 * (31 * java.util.Objects.hashCode(a) + java.util.Objects.hashCode(b)) + java.util.Objects.hashCode(c)
        final override fun toString(): String = "Face[a=" + a + ", b=" + b + ", c=" + c + "]"
    }

    @JvmRecord
    @Suppress("NON_FINAL_MEMBER_IN_FINAL_CLASS")
    data class Value(open val x: Float, open val y: Float, open val z: Float, open val nx: Float, open val ny: Float, open val nz: Float,
        open val u: Float, open val v: Float, open val color: Int, open val light: Int, open val overlay: Int) {
        @JvmSynthetic internal fun emit(consumer: VertexConsumer?) {
            consumer!!.addVertex(x, y, z).setColor(color).setUv(u, v).setLight(light).setOverlay(overlay).setNormal(nx, ny, nz)
        }
        final override fun equals(other: Any?): Boolean = this === other || other is Value &&
            java.lang.Float.compare(x, other.x) == 0 && java.lang.Float.compare(y, other.y) == 0 &&
            java.lang.Float.compare(z, other.z) == 0 && java.lang.Float.compare(nx, other.nx) == 0 &&
            java.lang.Float.compare(ny, other.ny) == 0 && java.lang.Float.compare(nz, other.nz) == 0 &&
            java.lang.Float.compare(u, other.u) == 0 && java.lang.Float.compare(v, other.v) == 0 &&
            color == other.color && light == other.light && overlay == other.overlay

        final override fun hashCode(): Int {
            var hash = java.lang.Float.hashCode(x)
            hash = 31 * hash + java.lang.Float.hashCode(y)
            hash = 31 * hash + java.lang.Float.hashCode(z)
            hash = 31 * hash + java.lang.Float.hashCode(nx)
            hash = 31 * hash + java.lang.Float.hashCode(ny)
            hash = 31 * hash + java.lang.Float.hashCode(nz)
            hash = 31 * hash + java.lang.Float.hashCode(u)
            hash = 31 * hash + java.lang.Float.hashCode(v)
            hash = 31 * hash + color
            hash = 31 * hash + light
            return 31 * hash + overlay
        }

        final override fun toString(): String = "Value[x=" + x + ", y=" + y + ", z=" + z + ", nx=" + nx +
            ", ny=" + ny + ", nz=" + nz + ", u=" + u + ", v=" + v + ", color=" + color + ", light=" + light + ", overlay=" + overlay + "]"
    }

    companion object {
        private val NEXT_ID = AtomicInteger(1)

        private fun withoutMatrix(state: VertAttrState?): VertAttrState? {
            if (state == null) return null
            val result = state.copy()
            result.matrixModel = null
            result.useMatixProcess = false
            return result
        }
    }
}
