package cn.zbx1425.mtrsteamloco.data

import mtr.data.Rail
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level

@JvmSuppressWildcards
interface RailActionsModuleExtraSupplier {
    fun getRailActions(): MutableList<Rail.RailActions?>?
    fun getRails(): MutableMap<BlockPos?, MutableMap<BlockPos?, Rail?>?>?
    fun getWorld(): Level?
    fun sendUpdateS2C()
}
