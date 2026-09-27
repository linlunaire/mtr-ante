package cn.zbx1425.mtrsteamloco.mixin

import cn.zbx1425.mtrsteamloco.data.RailActionsModuleExtraSupplier
import mtr.data.Rail
import mtr.data.RailwayData
import mtr.data.RailwayDataModuleBase
import mtr.data.RailwayDataRailActionsModule
import mtr.packet.PacketTrainDataGuiServer
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow

@JvmSuppressWildcards
@Mixin(RailwayDataRailActionsModule::class)
open class RailwayDataRailActionsModuleMixin(
    railwayData: RailwayData?, world: Level?, rails: MutableMap<BlockPos, MutableMap<BlockPos, Rail>>?
) : RailwayDataModuleBase(railwayData, world, rails), RailActionsModuleExtraSupplier {
    @field:Shadow(remap = false)
    private var railActions: MutableList<Rail.RailActions?>? = null

    override fun getRailActions(): MutableList<Rail.RailActions?>? = railActions

    // The inherited map is live; the extension interface also permits nullable Java entries.
    @Suppress("UNCHECKED_CAST")
    override fun getRails(): MutableMap<BlockPos?, MutableMap<BlockPos?, Rail?>?>? =
        rails as MutableMap<BlockPos?, MutableMap<BlockPos?, Rail?>?>?

    override fun getWorld(): Level? = world

    override fun sendUpdateS2C() {
        PacketTrainDataGuiServer.updateRailActionsS2C(world, railActions)
    }
}
