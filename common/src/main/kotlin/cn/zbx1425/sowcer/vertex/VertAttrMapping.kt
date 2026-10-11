package cn.zbx1425.sowcer.vertex

import java.util.HashMap

open class VertAttrMapping private constructor(sourceValues: HashMap<VertAttrType?, VertAttrSrc?>) {
    @JvmField val sources = HashMap(sourceValues)
    @JvmField val pointers = HashMap<VertAttrType?, Int?>()
    @JvmField val strideVertex: Int
    @JvmField val strideInstance: Int
    @JvmField val paddingVertex: Int
    @JvmField val paddingInstance: Int

    init {
        var vertexStride = 0
        var instanceStride = 0
        for (attrType in VertAttrType.values()) {
            // The Java switch required an explicit source for every attribute, including GLOBAL.
            when (sourceValues[attrType]!!) {
                VertAttrSrc.VERTEX_BUF, VertAttrSrc.VERTEX_BUF_OR_GLOBAL -> {
                    pointers[attrType] = vertexStride
                    vertexStride += attrType.byteSize
                }
                VertAttrSrc.INSTANCE_BUF, VertAttrSrc.INSTANCE_BUF_OR_GLOBAL -> {
                    pointers[attrType] = instanceStride
                    instanceStride += attrType.byteSize
                }
                VertAttrSrc.GLOBAL -> Unit
            }
        }
        paddingVertex = if (vertexStride % 4 != 0) 4 - vertexStride % 4 else 0
        paddingInstance = if (instanceStride % 4 != 0) 4 - instanceStride % 4 else 0
        strideVertex = vertexStride + paddingVertex
        strideInstance = instanceStride + paddingInstance
    }

    open class Builder {
        private val sources = HashMap<VertAttrType?, VertAttrSrc?>()

        open fun set(type: VertAttrType?, src: VertAttrSrc?): Builder {
            sources[type] = src
            return this
        }

        open fun build(): VertAttrMapping = VertAttrMapping(sources)
    }
}
