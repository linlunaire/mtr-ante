package cn.zbx1425.mtrsteamloco.data

import cn.zbx1425.mtrsteamloco.Main
import net.minecraft.world.level.block.Block
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.regex.Pattern

open class ShapeSerializer {
    companion object {
        private val shapeMap = HashMap<String, VoxelShape>()
        private val shapeSeparator = Pattern.compile("/")
        private val coordinateSeparator = Pattern.compile(",")

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun isValid(shape: String?, yRot: Int): Boolean {
            if (shape.isNullOrEmpty()) return false
            return try {
                getShape(shape, yRot)
                true
            } catch (error: Exception) {
                Main.LOGGER.error("Error parsing shape: $shape", error)
                false
            }
        }

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic @Throws(Exception::class)
        open fun getShape(shape: String?, yRot: Int): VoxelShape {
            if (shape.isNullOrEmpty()) return Shapes.empty()
            val key = shape + "_" + yRot
            return shapeMap[key] ?: parseShape(shape, yRot).also { shapeMap[key] = it }
        }

        @Throws(Exception::class)
        private fun parseShape(shape: String, yRot: Int): VoxelShape {
            val boxes = ArrayList<VoxelShape>()
            for (part in shapeSeparator.split(shape)) {
                val coordinates = coordinateSeparator.split(part)
                if (coordinates.size != 6) throw Exception("Invalid shape: $shape")
                val pos = DoubleArray(6) { java.lang.Double.parseDouble(coordinates[it].trim { char -> char <= ' ' }) }
                val x1 = pos[0]; val y1 = pos[1]; val z1 = pos[2]
                val x2 = pos[3]; val y2 = pos[4]; val z2 = pos[5]
                val box = when (yRot) {
                    90 -> Block.box(16 - z2, y1, x1, 16 - z1, y2, x2)
                    180 -> Block.box(16 - x2, y1, 16 - z2, 16 - x1, y2, 16 - z1)
                    270 -> Block.box(z1, y1, 16 - x2, z2, y2, 16 - x1)
                    else -> Block.box(x1, y1, z1, x2, y2, z2)
                }
                boxes.add(box)
            }
            // Parse every box before union, preserving the legacy failure order.
            var combined: VoxelShape? = null
            for (box in boxes) combined = if (combined == null) box else Shapes.or(combined, box)
            return combined ?: Shapes.empty()
        }
    }
}
