package cn.zbx1425.mtrsteamloco.path

import mtr.path.PathData
import mtr.path.PathGenerationTask

import mtr.data.*
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import java.util.Comparator
import java.util.function.Function

/** ANTE's legacy angular search policy, kept distinct from MTR's exact-direction policy. */
open class BetterPathFinder {
    companion object {
        private const val MAX_AIRPLANE_TURN_ARC = 128

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        open fun findPath(
            path: MutableList<PathData?>?, rails: MutableMap<BlockPos, MutableMap<BlockPos, Rail>>?,
            savedRailBases: MutableList<SavedRailBase>?, stopIndexOffset: Int, cruisingAltitude: Int, useFastSpeed: Boolean
        ): Int {
            PathGenerationTask.checkInterrupted()
            path!!.clear()
            if (savedRailBases!!.size < 2) return 0
            for (i in 0 until savedRailBases.size - 1) {
                PathGenerationTask.checkInterrupted()
                val start = savedRailBases[i]
                val end = savedRailBases[i + 1]
                val runways = HashSet<BlockPos>()
                if (start.transportMode == TransportMode.AIRPLANE) {
                    rails!!.forEach { (startPos, railMap) ->
                        PathGenerationTask.checkInterrupted()
                        if (railMap.size == 1 && railMap.values.stream().allMatch { it.railType == RailType.RUNWAY }) {
                            runways.add(startPos)
                        }
                    }
                }
                val partial = findPath(rails!!, runways, start, end, i + stopIndexOffset, cruisingAltitude, useFastSpeed)
                if (partial.isEmpty()) {
                    path.clear()
                    return i + 1
                }
                appendPath(path, partial)
            }
            return savedRailBases.size
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic
        open fun appendPath(path: MutableList<PathData?>?, partialPath: MutableList<PathData?>?) {
            PathGenerationTask.checkInterrupted()
            if (partialPath!!.isEmpty()) {
                path!!.clear()
            } else {
                val sameFirstRail = !path!!.isEmpty() && path[path.size - 1]!!.isSameRail(partialPath[0])
                var j = 0
                while (j < partialPath.size) {
                    PathGenerationTask.checkInterrupted()
                    if (!(j == 0 && sameFirstRail)) path.add(partialPath[j])
                    j++
                }
            }
        }

        private fun findPath(
            rails: MutableMap<BlockPos, MutableMap<BlockPos, Rail>>, runways: MutableSet<BlockPos>,
            start: SavedRailBase, end: SavedRailBase, stopIndex: Int, cruisingAltitude: Int, useFastSpeed: Boolean
        ): MutableList<PathData?> {
            val endMidPos = end.getMidPos()
            val comparator = Function<MutableMap<BlockPos, Rail>, Comparator<BlockPos>> { connections ->
                Comparator { pos1, pos2 ->
                    if (pos1 === pos2) 0 else {
                        val connection1 = connections[pos1]
                        val connection2 = connections[pos2]
                        if (connection1 == null || connection2 == null || connection1.railType.speedLimit == connection2.railType.speedLimit) {
                            if (pos1.distSqr(endMidPos) > pos2.distSqr(endMidPos)) 1 else -1
                        } else connection2.railType.speedLimit - connection1.railType.speedLimit
                    }
                }
            }
            for (i in 0..1) {
                val path = ArrayList<PathPart>()
                val turnBacks = HashSet<BlockPos>()
                val positions = start.getOrderedPositions(endMidPos, i == 0)
                path.add(PathPart(null, positions[0], ArrayList()))
                addPathPart(rails, runways, positions[1], positions[0], path, turnBacks, comparator)
                while (path.size >= 2) {
                    PathGenerationTask.checkInterrupted()
                    val last = path[path.size - 1]
                    if (last.otherOptions.isEmpty()) {
                        path.remove(last)
                    } else {
                        val newPos = last.otherOptions.removeAt(0)
                        addPathPart(rails, runways, newPos, last.pos, path, turnBacks, comparator)
                        if (end.containsPos(newPos)) {
                            val railPath = ArrayList<PathData?>()
                            for (j in 0 until path.size - 1) {
                                PathGenerationTask.checkInterrupted()
                                val part1 = path[j]
                                val part2 = path[j + 1]
                                val pos1 = part1.pos
                                val pos2 = part2.pos
                                val rail = DataCache.tryGet(rails, pos1, pos2)
                                if (rail == null) {
                                    if (runways.isEmpty()) return ArrayList()
                                    val height1 = cruisingAltitude - pos1.y
                                    val height2 = cruisingAltitude - pos2.y
                                    val direction1 = part1.direction!!
                                    val direction2 = part2.direction!!
                                    val cruise1 = RailwayData.offsetBlockPos(pos1, direction1.cos * Math.abs(height1) * 4, height1.toDouble(), direction1.sin * Math.abs(height1) * 4)
                                    val cruise4 = RailwayData.offsetBlockPos(pos2, -direction2.cos * Math.abs(height2) * 4, height2.toDouble(), -direction2.sin * Math.abs(height2) * 4)
                                    val turnArc = Math.min(MAX_AIRPLANE_TURN_ARC, cruise1.distManhattan(cruise4) / 8)
                                    val dummyType = if (useFastSpeed) RailType.AIRPLANE_DUMMY else RailType.RUNWAY
                                    railPath.add(PathData(Rail(pos1, direction1, cruise1, direction1.getOpposite(), dummyType, TransportMode.AIRPLANE), 0, 0, pos1, cruise1, stopIndex))
                                    val expectedAngle = RailAngle.fromAngle(Math.toDegrees(Math.atan2((cruise4.z - cruise1.z).toDouble(), (cruise4.x - cruise1.x).toDouble())).toFloat())
                                    val cruise2 = addAirplanePath(direction1, cruise1, expectedAngle, turnArc, railPath, dummyType, stopIndex, false)
                                    val temp = ArrayList<PathData?>()
                                    val cruise3 = addAirplanePath(direction2.getOpposite(), cruise4, expectedAngle.getOpposite(), turnArc, temp, dummyType, stopIndex, true)
                                    railPath.add(PathData(Rail(cruise2, expectedAngle, cruise3, expectedAngle.getOpposite(), dummyType, TransportMode.AIRPLANE), 0, 0, cruise2, cruise3, stopIndex))
                                    railPath.addAll(temp)
                                    railPath.add(PathData(Rail(cruise4, direction2, pos2, direction2.getOpposite(), dummyType, TransportMode.AIRPLANE), 0, 0, cruise4, pos2, stopIndex))
                                } else {
                                    val turningBack = rail.railType == RailType.TURN_BACK && j < path.size - 2 && path[j + 2].pos == pos1
                                    railPath.add(PathData(rail, if (j == 0) start.id else 0, if (turningBack) 1 else 0, pos1, pos2, stopIndex))
                                }
                            }
                            val endPos = end.getOtherPosition(newPos)
                            val rail = DataCache.tryGet(rails, newPos, endPos) ?: return ArrayList()
                            railPath.add(PathData(rail, end.id, if (end is Platform) end.getDwellTime() else 0, newPos, endPos, stopIndex + 1))
                            return railPath
                        }
                    }
                }
            }
            return ArrayList()
        }

        private fun addPathPart(
            rails: MutableMap<BlockPos, MutableMap<BlockPos, Rail>>, runways: MutableSet<BlockPos>,
            newPos: BlockPos, lastPos: BlockPos, path: MutableList<PathPart>, turnBacks: MutableSet<BlockPos>,
            comparator: Function<MutableMap<BlockPos, Rail>, Comparator<BlockPos>>
        ) {
            val connections = rails[newPos]
            val oldRail = rails[lastPos]!![newPos]
            if (oldRail == null && runways.isEmpty()) return
            val direction = calculateNewDirection(oldRail, connections)
            val options = ArrayList<BlockPos>()
            if (connections != null) {
                val canTurnBack = oldRail != null && oldRail.railType == RailType.TURN_BACK && !turnBacks.contains(newPos)
                // The path cannot change during this candidate pass. Check membership only
                // on the first eligible edge, retaining short-circuiting for rejected edges.
                var pathChecked = false
                var pathContainsPositionAndDirection = false
                for ((connectedPos, rail) in connections) {
                    PathGenerationTask.checkInterrupted()
                    if (rail.railType != RailType.NONE &&
                        (canTurnBack || !anglesEqual(rail.facingStart, direction.getOpposite()))) {
                        if (!pathChecked) {
                            for (part in path) {
                                PathGenerationTask.checkInterrupted()
                                if (part.isSame(newPos, direction)) {
                                    pathContainsPositionAndDirection = true
                                    break
                                }
                            }
                            pathChecked = true
                        }
                        if (!pathContainsPositionAndDirection) {
                            options.add(connectedPos)
                            if (canTurnBack) turnBacks.add(newPos)
                        }
                    }
                }
            }
            if (options.isNotEmpty()) {
                options.sortWith(comparator.apply(connections!!))
                path.add(PathPart(direction, newPos, options))
            }
        }

        private fun calculateNewDirection(oldRail: Rail?, connections: MutableMap<BlockPos, Rail>?): RailAngle =
            if (oldRail == null) connections!!.values.stream().map { it.facingStart }.findFirst().orElse(RailAngle.E)
            else oldRail.facingEnd.getOpposite()

        // Preserve the actual legacy float threshold (approximately 45 degrees), not its old "1 degree" comment.
        private const val ANGLE_EQUALITY_THRESHOLD = 0.01745f * 45

        private fun anglesEqual(a: RailAngle?, b: RailAngle?): Boolean {
            if (a == null || b == null) return false
            val normalizedA = (a.angleRadians % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI)
            val normalizedB = (b.angleRadians % (2 * Math.PI) + 2 * Math.PI) % (2 * Math.PI)
            val diff = Math.abs(normalizedA - normalizedB)
            return Math.min(diff, 2 * Math.PI - diff) < ANGLE_EQUALITY_THRESHOLD
        }

        private fun addAirplanePath(
            startAngle: RailAngle, startPos: BlockPos, expectedAngle: RailAngle, turnArc: Int,
            path: MutableList<PathData?>, railType: RailType, stopIndex: Int, reverse: Boolean
        ): BlockPos {
            val turnRight = expectedAngle.sub(startAngle).angleRadians > 0
            var angle = startAngle
            var pos = startPos
            for (i in RailAngle.entries.indices) {
                if (angle == expectedAngle) break
                val oldAngle = angle
                val oldPos = pos
                val rotate = if (turnRight) RailAngle.SEE else RailAngle.NEE
                angle = angle.add(rotate)
                val offset = Vec3(turnArc.toDouble(), 0.0, 0.0).yRot(-oldAngle.angleRadians.toFloat() - rotate.angleRadians.toFloat() / 2)
                pos = RailwayData.offsetBlockPos(oldPos, offset.x, offset.y, offset.z)
                if (reverse) path.add(0, PathData(Rail(pos, angle.getOpposite(), oldPos, oldAngle, railType, TransportMode.AIRPLANE), 0, 0, pos, oldPos, stopIndex))
                else path.add(PathData(Rail(oldPos, oldAngle, pos, angle.getOpposite(), railType, TransportMode.AIRPLANE), 0, 0, oldPos, pos, stopIndex))
            }
            return pos
        }
    }

    private class PathPart(val direction: RailAngle?, val pos: BlockPos, otherOptions: MutableList<BlockPos>) {
        val otherOptions = ArrayList(otherOptions)
        fun isSame(newPos: BlockPos, newDirection: RailAngle): Boolean = newPos == pos && anglesEqual(direction, newDirection)
    }
}
