package cn.zbx1425.mtrsteamloco.data

import mtr.data.RailAngle

interface RailAngleExtra {
    fun _fromDegrees(degrees: Double): RailAngle?
    fun _fromRadians(radians: Double): RailAngle?
    fun setRadians(radians: Double)

    companion object {
        @JvmStatic fun fromDegrees(degrees: Double): RailAngle? = (RailAngle.S as Any as RailAngleExtra)._fromDegrees(degrees)
        @JvmStatic fun fromRadians(radians: Double): RailAngle? = (RailAngle.S as Any as RailAngleExtra)._fromRadians(radians)
    }
}
