package cn.zbx1425.mtrsteamloco.data

import mtr.path.PathData

@JvmSuppressWildcards
interface IRoute {
    fun getPathData(): MutableList<PathData?>?
    fun setPathData(pathData: MutableList<PathData?>?)
}
