package cn.zbx1425.mtrsteamloco.data

import mtr.data.Rail
import mtr.data.RailType
import net.minecraft.core.BlockPos
import net.minecraft.util.Mth

@JvmSuppressWildcards
interface RailExtraSupplier {
    // Empty selects the default model; the literal "null" hides it.
    fun getModelKey(): String?
    fun setModelKey(key: String?)
    fun getRenderReversed(): Boolean
    fun setRenderReversed(value: Boolean)
    fun getVerticalCurveRadius(): Float
    fun setVerticalCurveRadius(value: Float)
    fun getHeight(): Int
    fun getRollAngleMap(): MutableMap<Double?, Float?>?
    fun setRollAngleMap(rollAngleMap: MutableMap<Double?, Float?>?)
    fun getCustomConfigs(): MutableMap<String?, String?>?
    fun setCustomConfigs(customConfigs: MutableMap<String?, String?>?)
    fun getCustomResponders(): MutableMap<String?, ConfigResponder?>?
    fun setCustomResponders(customResponders: MutableMap<String?, ConfigResponder?>?)
    fun setOpeningDirection(direction: Int)
    fun setOpeningDirectionRaw(direction: Int)
    fun getOpeningDirection(): Int
    fun getOpeningDirectionRaw(): Int
    fun setRailType(railType: RailType?)
    fun partialCopyFrom(rail: Rail?)
    fun setRollingOffset(rollingOffset: Float)
    fun getRollingOffset(): Float
    fun isStraightOnly(): Boolean
    fun changePathMode(mode: Int)
    fun getPathMode(): Int
    fun isBetween(x: Double, y: Double, z: Double, radius: Double): Boolean
    fun getTransposition(railType: RailType?): Rail?
    fun setBezier(bezier: BezierCurve?)
    fun couldSwitchModeTo(mode: Int): Boolean
    fun sendUpdateC2S()
    fun getPosStart(): BlockPos?
    fun getPosEnd(): BlockPos?

    companion object {
        @JvmStatic fun getVTheta(rail: Rail?, verticalCurveRadius: Double): Float {
            val height = Math.abs((rail as RailExtraSupplier).getHeight()).toDouble()
            val length = rail.length
            return 2 * Mth.atan2(Math.sqrt(height * height - 4 * verticalCurveRadius * height + length * length) - length, height - 4 * verticalCurveRadius).toFloat()
        }

        @JvmStatic fun getRollAngle(rail: Rail?, input: Double): Float {
            val supplier = rail as RailExtraSupplier
            val angles = supplier.getRollAngleMap()
            val reversed = supplier.getRenderReversed()
            val multiplier = if (reversed) -1F else 1F
            val value = if (reversed) rail.length - input else input
            if (angles!!.isEmpty()) return 0F
            val keys = ArrayList(angles.keys)
            keys.sortWith { left, right -> java.lang.Double.compare(left!!, right!!) }
            if (value <= keys[0]!!) return multiplier * angles[keys[0]]!!
            if (value >= keys[keys.size - 1]!!) return multiplier * angles[keys[keys.size - 1]]!!
            var last = keys[0]!!
            for (i in 1 until keys.size) {
                val current = keys[i]!!
                if (last <= value && value < current) {
                    val t = (value - last) / (current - last)
                    val cosine = (1 - Math.cos(t * Math.PI)) / 2.0
                    val interpolated = (angles[last]!! * (1 - cosine) + angles[current]!! * cosine).toFloat()
                    return multiplier * interpolated
                }
                last = current
            }
            return 0F
        }
    }
}
