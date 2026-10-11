package cn.zbx1425.mtrsteamloco.mixin

import cn.zbx1425.mtrsteamloco.data.IRoute
import mtr.data.RailType
import mtr.data.Route
import mtr.path.PathData
import net.minecraft.network.FriendlyByteBuf
import org.msgpack.core.MessagePacker
import org.msgpack.value.Value
import org.spongepowered.asm.mixin.Mixin
import org.spongepowered.asm.mixin.Shadow
import org.spongepowered.asm.mixin.injection.At
import org.spongepowered.asm.mixin.injection.Inject
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
import java.io.IOException

@Mixin(Route::class)
@JvmSuppressWildcards
abstract class RouteMixin : IRoute {
    private var pathData: MutableList<PathData?> = ArrayList()

    @Shadow(remap = false)
    @JvmField
    protected var platformIds: MutableList<Route.RoutePlatform?>? = null

    override fun getPathData(): MutableList<PathData?> = pathData

    override fun setPathData(pathData: MutableList<PathData?>?) {
        var last: PathData? = null
        for (data in pathData!!) {
            if (data!!.rail!!.railType == RailType.PLATFORM && data.savedRailBaseId != 0L) {
                if (last == null || !last.isOppositeRail(data)) platformIds!!.add(Route.RoutePlatform(data.savedRailBaseId))
            }
            last = data
        }
        this.pathData = pathData
    }

    @Inject(method = ["<init>(Ljava/util/Map;)V"], at = [At("TAIL")], remap = false)
    private fun fromMessagePack(map: Map<String, Value>?, ci: CallbackInfo) {
        try {
            if (map!!.containsKey("ante_path")) {
                for (value in map["ante_path"]!!.asArrayValue()) {
                    val oldMap = value.asMapValue().map()
                    val result = HashMap<String?, Value?>(oldMap.size)
                    for ((key, entry) in oldMap) result[key.asStringValue().asString()] = entry
                    pathData.add(PathData(result))
                }
            }
        } catch (exception: Exception) {
            println("Error while loading path data: " + exception.message)
            exception.printStackTrace()
        }
    }

    @Inject(method = ["<init>(Lnet/minecraft/network/FriendlyByteBuf;)V"], at = [At("TAIL")])
    private fun fromPacket(packet: FriendlyByteBuf, ci: CallbackInfo) {
        val length = packet.readInt()
        for (i in 0 until length) pathData.add(PathData(packet))
    }

    @Throws(IOException::class)
    @Inject(method = ["toMessagePack"], at = [At("TAIL")], remap = false)
    private fun toMessagePack(messagePacker: MessagePacker, ci: CallbackInfo) {
        if (pathData.isEmpty()) return
        messagePacker.packString("ante_path")
        messagePacker.packArrayHeader(pathData.size)
        for (data in pathData) {
            messagePacker.packMapHeader(data!!.messagePackLength())
            data.toMessagePack(messagePacker)
        }
    }

    @Inject(method = ["writePacket"], at = [At("TAIL")])
    private fun toPacket(packet: FriendlyByteBuf, ci: CallbackInfo) {
        packet.writeInt(pathData.size)
        for (data in pathData) data!!.writePacket(packet)
    }

    @Inject(method = ["messagePackLength"], at = [At("TAIL")], cancellable = true, remap = false)
    private fun messagePackLength(ci: CallbackInfoReturnable<Int>) {
        ci.returnValue = ci.returnValue + if (pathData.isEmpty()) 0 else 1
    }
}
