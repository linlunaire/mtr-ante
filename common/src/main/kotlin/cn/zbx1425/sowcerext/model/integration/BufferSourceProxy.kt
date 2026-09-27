package cn.zbx1425.sowcerext.model.integration

import mtr.mappings.RenderBufferSource
import net.minecraft.client.renderer.rendertype.RenderType

open class BufferSourceProxy(private val bufferSource: RenderBufferSource?) {
    private val builders = HashMap<RenderType?, FaceList>()

    open fun getBuffer(renderType: RenderType?, needSorting: Boolean): FaceList =
        builders.computeIfAbsent(renderType) { FaceList(renderType, needSorting) }

    open fun commit() {
        for (builder in builders.values) builder.commit(bufferSource)
        // Failed commits retain the queue, matching the caller's existing retry semantics.
        builders.clear()
    }
}
