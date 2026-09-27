package cn.zbx1425.mtrsteamloco.mixin

import cn.zbx1425.mtrsteamloco.path.DepotPathGen
import mtr.data.DataCache
import mtr.data.Depot
import mtr.data.Rail
import mtr.data.Siding
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import java.util.function.Consumer

@Mixin(Depot::class)
open class DepotMixin {
    @Inject(method = ["generateMainRoute"], remap = false, at = [At("HEAD")], cancellable = true)
    private fun generateMainRoute(
        minecraftServer: MinecraftServer?, world: Level?, dataCache: DataCache?,
        rails: MutableMap<BlockPos, MutableMap<BlockPos, Rail>>?, sidings: MutableSet<Siding>?,
        callback: Consumer<Thread>?, ci: CallbackInfo
    ) {
        System.out.println("DepotMixin generateMainRoute")
        ci.cancel()
        DepotPathGen.generateMainRoute(minecraftServer, world, dataCache, rails, sidings, callback, this as Any as Depot)
    }
}
