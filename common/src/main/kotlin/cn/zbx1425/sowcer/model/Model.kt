package cn.zbx1425.sowcer.model

import java.io.Closeable
import java.util.ArrayList

open class Model : Closeable {
    @JvmField var meshList: ArrayList<Mesh?>? = ArrayList()

    override fun close() {
        for (mesh in meshList!!) mesh!!.close()
    }
}
