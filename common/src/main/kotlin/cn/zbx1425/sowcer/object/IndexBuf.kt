package cn.zbx1425.sowcer.`object`

open class IndexBuf(@JvmField var faceCount: Int, @JvmField val indexType: Int) : VertBuf() {
    @JvmField var vertexCount: Int = faceCount * 3

    open fun setFaceCount(faceCount: Int) {
        this.faceCount = faceCount
        vertexCount = faceCount * 3
    }
}
