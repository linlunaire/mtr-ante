package cn.zbx1425.mtrsteamloco.data

import net.minecraft.world.phys.Vec3

interface VehicleRidingClientExtraSupplier {
    fun getRoll(index: Int): Float
    fun setRoll(rolls: FloatArray?)
    fun setPositions(positions: Array<Vec3?>?)
    fun setReversed(reversed: Boolean)
}
