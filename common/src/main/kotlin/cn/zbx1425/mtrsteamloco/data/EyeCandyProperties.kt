package cn.zbx1425.mtrsteamloco.data

import cn.zbx1425.mtrsteamloco.data.RelativePosition.Combination
import cn.zbx1425.mtrsteamloco.scripting.ScriptHolderBase
import cn.zbx1425.sowcer.math.Matrix4f
import cn.zbx1425.sowcerext.model.ModelCluster
import mtr.mappings.Text
import net.minecraft.network.chat.MutableComponent
import net.minecraft.resources.Identifier
import java.io.Closeable
import java.io.IOException

open class EyeCandyProperties(
    @JvmField var key: String?, @JvmField var name: MutableComponent?,
    @JvmField var model: ModelCluster?, @JvmField var itemModel: ModelCluster?,
    @JvmField var itemTransform: Matrix4f?, @JvmField var itemModelId: Identifier?,
    @JvmField var script: ScriptHolderBase?, @JvmField var shape: String?,
    @JvmField var collisionShape: String?, @JvmField var fixedMatrix: Boolean,
    @JvmField var lightLevel: Int, @JvmField var isTicketBarrier: Boolean,
    @JvmField var isEntrance: Boolean, @JvmField var asPlatform: Boolean,
    @JvmField var group: String?, @JvmField var positions: Combination?
) : Closeable {
    @JvmField var path: String? = "$group/$key"

    @Throws(IOException::class)
    override fun close() {
        model?.close()
    }

    companion object {
        @JvmField val DEFAULT = EyeCandyProperties(
            "default_key", Text.literal(""), null, null, null, null, null,
            "0, 0, 0, 16, 16, 16", "0, 0, 0, 0, 0, 0", true, 0,
            false, false, false, "ANTE", Combination.decode("")
        )
    }
}
