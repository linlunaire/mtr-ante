package cn.zbx1425.sowcer.batch

import cn.zbx1425.sowcer.vertex.VertAttrState

/** Additional rendering state supplied at enqueue time; it does not affect batching. */
open class EnqueueProp(@JvmField var attrState: VertAttrState?) {
    companion object {
        @JvmField var DEFAULT: EnqueueProp? = EnqueueProp(null)
    }
}
