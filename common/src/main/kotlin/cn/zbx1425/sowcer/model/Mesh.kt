package cn.zbx1425.sowcer.model

import cn.zbx1425.sowcer.batch.MaterialProp
import cn.zbx1425.sowcer.`object`.IndexBuf
import cn.zbx1425.sowcer.`object`.VertBuf
import java.io.Closeable

open class Mesh(
    @JvmField var vertBuf: VertBuf?,
    @JvmField var indexBuf: IndexBuf?,
    @JvmField var materialProp: MaterialProp?,
) : Closeable {
    override fun close() {
        vertBuf!!.close()
        indexBuf!!.close()
    }
}
