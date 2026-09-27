package cn.zbx1425.sowcer.batch

import cn.zbx1425.sowcer.model.VertArrays
import cn.zbx1425.sowcer.`object`.VertArray
import cn.zbx1425.sowcer.shader.ShaderManager
import cn.zbx1425.sowcer.util.DrawContext
import mtr.mappings.RenderBufferSource
import net.minecraft.client.renderer.rendertype.RenderType
import java.util.ArrayList
import java.util.Comparator
import java.util.LinkedHashMap

/** Queues immutable retained meshes where possible, with captured triangles as the fallback. */
open class BatchManager {
    private val calls: MutableList<RenderCall> = ArrayList()

    open fun clear() { calls.clear() }

    open fun enqueue(model: VertArrays, enqueue: EnqueueProp?, shader: ShaderProp?) {
        for (array in model.meshList) this.enqueue(array!!, enqueue, shader)
    }

    open fun enqueue(array: VertArray, enqueue: EnqueueProp?, shader: ShaderProp?) {
        val retained = array.captureRetained(enqueue!!.attrState, shader)
        calls.add(RenderCall.create(array, array.materialProp!!.copy(), if (retained == null) array.capture(enqueue.attrState, shader) else null, retained))
    }

    open fun drawAll(shaders: ShaderManager?, context: DrawContext) {
        val batches: MutableMap<Key, MutableList<VertArray.Face>> = LinkedHashMap()
        var retainedBatches = 0
        for (call in calls) {
            context.recordDrawCall(call)
            val retained = call.retained
            if (retained != null) {
                RenderBufferSource.current().drawRetained(retained.geometry, retained.pose)
                retainedBatches++
                continue
            }
            val material = call.material!!
            val stage = if (material.translucent) 2 else if (material.cutoutHack) 1 else 0
            batches.computeIfAbsent(Key(shaders!!.material(material), stage)) { ArrayList() }.addAll(call.faces!!)
        }
        context.recordBatches(batches.size + retainedBatches)
        for (entry in batches.entries.stream().sorted(Comparator.comparingInt { entry: MutableMap.MutableEntry<Key, MutableList<VertArray.Face>> -> entry.key.stage }).toList()) {
            if (entry.key.stage == 2) entry.value.sortWith(Comparator.comparingDouble<VertArray.Face> { it.distanceSquared().toDouble() }.reversed())
            val consumer = RenderBufferSource.current().getBuffer(entry.key.type)
            for (face in entry.value) face.emit(consumer)
        }
        calls.clear()
    }

    private data class Key(val type: RenderType?, val stage: Int)

    class RenderCall private constructor(
        @JvmField val vertArray: VertArray,
        @get:JvmSynthetic internal val material: MaterialProp?,
        @get:JvmSynthetic internal val faces: List<VertArray.Face>?,
        @get:JvmSynthetic internal val retained: VertArray.RetainedDraw?,
    ) {
        @JvmField val faceCount: Int = if (retained == null) faces!!.size else retained.geometry!!.vertexCount() / 3
        @JvmField val instanced: Boolean = vertArray.instanceBuf != null

        companion object {
            // Kotlin's outer class cannot call a nested private constructor directly.
            // Keep that constructor private and hide the final factory from Java/script method discovery.
            @JvmSynthetic internal fun create(array: VertArray, material: MaterialProp?, faces: List<VertArray.Face>?, retained: VertArray.RetainedDraw?): RenderCall =
                RenderCall(array, material, faces, retained)
        }
    }
}
