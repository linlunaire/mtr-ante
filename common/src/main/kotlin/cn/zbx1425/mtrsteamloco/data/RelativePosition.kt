package cn.zbx1425.mtrsteamloco.data

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import java.util.Collections
import java.util.regex.Pattern

@JvmSuppressWildcards
open class RelativePosition(x: Int, y: Int, z: Int) {
    @JvmField val x: Byte
    @JvmField val y: Byte
    @JvmField val z: Byte

    init {
        require(x in Byte.MIN_VALUE..Byte.MAX_VALUE && y in Byte.MIN_VALUE..Byte.MAX_VALUE && z in Byte.MIN_VALUE..Byte.MAX_VALUE) {
            "Coordinates out of bounds: ($x, $y, $z)"
        }
        this.x = x.toByte()
        this.y = y.toByte()
        this.z = z.toByte()
    }

    open fun transform(pos: BlockPos?, direction: Direction?): BlockPos {
        var nx = x.toInt()
        var nz = z.toInt()
        var rotations = direction!!.get2DDataValue()
        while (rotations > 0) {
            val previousX = nx
            nx = -nz
            nz = previousX
            rotations--
        }
        return BlockPos(pos!!.x + nx, pos.y + y, pos.z + nz)
    }

    override fun toString(): String = "($x, $y, $z)"
    override fun equals(other: Any?): Boolean = other === this || other is RelativePosition && other.x == x && other.y == y && other.z == z

    // Objects.hash(byte, byte, byte), without the temporary Object[] and boxing.
    override fun hashCode(): Int = ((31 + x) * 31 + y) * 31 + z

    open class Suit(@JvmField val rp: RelativePosition?, @JvmField val bp: BlockPos?)

    open class Combination(@JvmField val positions: MutableSet<RelativePosition?>?) {
        open fun transform(direction: Direction?, pos: BlockPos?): MutableSet<Suit> {
            val suits = HashSet<Suit>()
            for (rp in positions!!) suits.add(Suit(rp, rp!!.transform(pos, direction)))
            return suits
        }

        companion object {
            private val partSeparator = Pattern.compile(";")
            private val coordinateSeparator = Pattern.compile(",")

            @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
            @JvmStatic
            open fun decode(src: String?): Combination {
                val positions = HashSet<RelativePosition?>()
                for (part in partSeparator.split(src!!)) {
                    val coordinates = coordinateSeparator.split(part)
                    if (coordinates.size == 3) positions.add(RelativePosition(
                        java.lang.Byte.parseByte(coordinates[0]).toInt(),
                        java.lang.Byte.parseByte(coordinates[1]).toInt(),
                        java.lang.Byte.parseByte(coordinates[2]).toInt()))
                }
                positions.add(ZERO)
                return Combination(Collections.unmodifiableSet(positions))
            }

            @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
            @JvmStatic
            open fun encode(combination: Combination?): String {
                val result = StringBuilder()
                for (pos in combination!!.positions!!) {
                    if (result.isNotEmpty()) result.append(';')
                    result.append(pos!!.x).append(',').append(pos.y).append(',').append(pos.z)
                }
                return result.toString()
            }
        }
    }

    companion object {
        @JvmField val ZERO = RelativePosition(0, 0, 0)
    }
}
