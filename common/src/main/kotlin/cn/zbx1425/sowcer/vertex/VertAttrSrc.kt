package cn.zbx1425.sowcer.vertex

enum class VertAttrSrc {
    /** In MaterialProp or EnqueueProp, MaterialProp has priority. */
    GLOBAL,
    /** In the vertex buffer. */
    VERTEX_BUF,
    /** Use enqueue/material state when specified, otherwise the vertex buffer. */
    VERTEX_BUF_OR_GLOBAL,
    /** In the instance buffer. */
    INSTANCE_BUF,
    /** Use enqueue/material state when specified, otherwise the instance buffer. */
    INSTANCE_BUF_OR_GLOBAL;

    open fun isToggleable(): Boolean = this == VERTEX_BUF_OR_GLOBAL || this == INSTANCE_BUF_OR_GLOBAL
    open fun inVertBuf(): Boolean = this == VERTEX_BUF || this == VERTEX_BUF_OR_GLOBAL
}
