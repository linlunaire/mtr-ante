package cn.zbx1425.mtrsteamloco.path

import mtr.data.*
import mtr.packet.PacketTrainDataGuiServer
import mtr.path.PathGenerationTask
import java.util.concurrent.CancellationException
import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.Level
import java.util.function.Consumer

open class DepotPathGen {
    companion object {
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        open fun generateMainRoute(
            minecraftServer: MinecraftServer?, world: Level?, dataCache: DataCache?,
            rails: MutableMap<BlockPos, MutableMap<BlockPos, Rail>>?, sidings: MutableSet<Siding>?,
            callback: Consumer<Thread>?, depot: Depot?
        ) {
            val plan = DepotRoutePlan.capture(depot!!.routeIds, dataCache)
            val altitude = depot.cruisingAltitude
            val fast = altitude >= (world!!.maxY + 1) + 64
            val id = depot.id
            val name = depot.name
            val thread = Thread {
                val request = PathGenerationTask.current()
                try {
                    request?.check()
                    PathGenerationTask.checkInterrupted()
                    val mainRoute = plan.assemble(rails, altitude, fast)
                    var successfulSegments = Int.MAX_VALUE
                    sidings!!.forEach { siding ->
                        PathGenerationTask.checkInterrupted()
                        val midPos = siding.getMidPos()
                        if (siding.isTransportMode(depot.transportMode) && depot.inArea(midPos.x, midPos.z)) {
                            val result = siding.generateRoute(minecraftServer, mainRoute.path, mainRoute.mainSegments, rails, mainRoute.firstPlatform, mainRoute.lastPlatform, depot.repeatInfinitely, altitude, fast)
                            if (result < successfulSegments) successfulSegments = result
                        }
                    }
                    PathGenerationTask.publish(request, minecraftServer) {
                        PacketTrainDataGuiServer.generatePathS2C(world, id, successfulSegments)
                        println("Finished path generation" + if (name!!.isEmpty()) "" else " for $name")
                    }
                } catch (_: CancellationException) {
                    // The replacement request will publish its own status.
                } catch (exception: Exception) {
                    if (!Thread.currentThread().isInterrupted && (request == null || request.isCurrent)) {
                        exception.printStackTrace()
                        PathGenerationTask.publish(request, minecraftServer) {
                            PacketTrainDataGuiServer.generatePathS2C(world, id, 0)
                            println("Failed to generate path" + if (name!!.isEmpty()) "" else " for $name")
                        }
                    }
                }
            }
            callback!!.accept(thread)
            thread.start()
        }

    }
}
