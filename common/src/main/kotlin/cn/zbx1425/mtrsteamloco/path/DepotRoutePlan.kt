package cn.zbx1425.mtrsteamloco.path

import cn.zbx1425.mtrsteamloco.data.IRoute
import cn.zbx1425.mtrsteamloco.mixin.PathDataAccessor
import mtr.data.DataCache
import mtr.data.Rail
import mtr.data.RailType
import mtr.data.SavedRailBase
import mtr.path.PathData
import mtr.path.PathFinder
import mtr.path.PathGenerationTask
import net.minecraft.core.BlockPos
import java.util.Arrays

/**
 * Request-owned main-route computation, with no worker, world, siding or packet lifecycle.
 *
 * [capture] runs on the caller before its callback. It owns copied route lists and PathData
 * metadata, but borrows Rail/Platform objects; this is not an immutable world snapshot.
 * [assemble] consumes and modifies captured state, reading the graph at computation time. This
 * is a single-owner, non-concurrent computation, not an idempotent or retry-safe operation.
 * A fresh request or retry must capture a new plan, including after failed assembly. Repeated
 * calls are not rejected: the legacy generator callback can invoke its worker before starting it.
 * The plan/results own their mutable path metadata and retain borrowed rail/platform identity;
 * multiple results from the same consumed plan can share that metadata.
 */
class DepotRoutePlan private constructor(
    private val routes: List<RouteInput>,
    private val platformsMerged: List<SavedRailBase>
) {
    class Result internal constructor(
        @JvmField val path: MutableList<PathData?>,
        @JvmField val mainSegments: Int,
        @JvmField val firstPlatform: SavedRailBase?,
        @JvmField val lastPlatform: SavedRailBase?
    )

    fun assemble(rails: MutableMap<BlockPos, MutableMap<BlockPos, Rail>>?, altitude: Int, fast: Boolean): Result {
        PathGenerationTask.checkInterrupted()
        val path = ArrayList<PathData?>()
        var lastPlatform: SavedRailBase? = null
        for (route in routes) {
            PathGenerationTask.checkInterrupted()
            val platforms = route.platforms
            if (lastPlatform != null && platforms.isNotEmpty() && platforms[0] !== lastPlatform) {
                val connection = ArrayList<PathData?>()
                findPath(connection, rails, Arrays.asList(lastPlatform, platforms[0]), altitude, fast)
                path.addAll(connection)
            }
            if (platforms.isNotEmpty()) lastPlatform = platforms[platforms.size - 1]
            val routePath = route.path
            if (routePath.isEmpty()) findPath(routePath, rails, platforms, altitude, fast)
            if (routePath.size > 2) {
                val first = routePath[0]!!
                val last = if (path.isEmpty()) null else path[path.size - 1]
                if (last != null) {
                    if (first.isOppositeRail(last)) {
                        (first as PathDataAccessor).setDwellTime(0)
                        (first as PathDataAccessor).setSavedRailBaseId(0)
                    } else if (first.isSameRail(last)) {
                        routePath.removeAt(0)
                    }
                }
                path.addAll(routePath)
            }
        }
        var stopIndex = 1
        for (i in 1 until path.size) {
            PathGenerationTask.checkInterrupted()
            val curr = path[i]!!
            if (curr.dwellTime != 0 && curr.rail!!.railType == RailType.PLATFORM && curr.savedRailBaseId != 0L && path[i - 1]!!.rail!!.railType != RailType.PLATFORM) stopIndex++
            (curr as PathDataAccessor).setStopIndex(stopIndex)
        }
        return Result(path, platformsMerged.size, platformsMerged.firstOrNull(), platformsMerged.lastOrNull())
    }

    private class RouteInput(val platforms: MutableList<SavedRailBase?>, val path: MutableList<PathData?>)

    companion object {
        @JvmStatic
        fun capture(routeIds: MutableList<Long?>?, dataCache: DataCache?): DepotRoutePlan {
            val platformsMerged = ArrayList<SavedRailBase>()
            val routes = ArrayList<RouteInput>()
            routeIds!!.forEach { routeId ->
                val route = dataCache!!.routeIdMap[routeId]
                if (route != null) {
                    // RouteMixin adds this interface to the otherwise final Route class.
                    @Suppress("CAST_NEVER_SUCCEEDS")
                    val routePath = ArrayList<PathData?>((route as IRoute).getPathData())
                    val routePlatforms = ArrayList<SavedRailBase?>()
                    if (routePath.isEmpty()) {
                        route.platformIds.forEach { platformId ->
                            val platform = dataCache.platformIdMap[platformId!!.platformId]
                            routePlatforms.add(platform)
                            if (platform != null && (platformsMerged.isEmpty() || platform.id != platformsMerged[platformsMerged.size - 1].id)) {
                                platformsMerged.add(platform)
                            }
                        }
                    } else {
                        for (i in route.platformIds.indices) {
                            val platform = dataCache.platformIdMap[route.platformIds[i]!!.platformId]
                            if (platform != null) {
                                routePlatforms.add(platform)
                                if (i != 0 || platformsMerged.isEmpty() || platform.id != platformsMerged[platformsMerged.size - 1].id) {
                                    platformsMerged.add(platform)
                                }
                            }
                        }
                    }
                    routes.add(RouteInput(routePlatforms, routePath))
                }
            }
            // Preserve the caller-side ordering: resolve all routes before copying their metadata.
            for (route in routes) {
                for (i in route.path.indices) {
                    val data = route.path[i]!!
                    route.path[i] = PathData(data.rail, data.savedRailBaseId, data.dwellTime, data.startingPos,
                        (data as PathDataAccessor).endingPos, data.stopIndex)
                }
            }
            return DepotRoutePlan(routes, platformsMerged)
        }

        // An uncached route keeps missing platforms, failing during assembly rather than capture.
        @Suppress("UNCHECKED_CAST")
        private fun findPath(path: MutableList<PathData?>, rails: MutableMap<BlockPos, MutableMap<BlockPos, Rail>>?, platforms: MutableList<SavedRailBase?>, altitude: Int, fast: Boolean) {
            PathFinder.findPath(path, rails, platforms as MutableList<SavedRailBase>, 1, altitude, fast)
        }
    }
}
