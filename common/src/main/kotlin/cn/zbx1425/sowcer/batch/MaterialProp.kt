package cn.zbx1425.sowcer.batch

import cn.zbx1425.sowcer.math.Matrix4f
import cn.zbx1425.sowcer.shader.BlazeRenderType
import cn.zbx1425.sowcer.vertex.VertAttrState
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Objects
import java.util.function.Function

/** Material properties assigned during model loading. Affect batching. */
open class MaterialProp {
    @JvmField var shaderName: String? = ""
    /** Null disables the texture. */
    @JvmField var texture: Identifier? = null
    @JvmField var attrState: VertAttrState? = VertAttrState()
    @JvmField var translucent: Boolean = false
    @JvmField var writeDepthBuf: Boolean = true
    @JvmField var cutoutHack: Boolean = false
    @JvmField var sheetElementsU: Int = 0
    @JvmField var sheetElementsV: Int = 0

    constructor()
    constructor(shaderName: String?) { this.shaderName = shaderName; checkShaderName() }

    @Throws(IOException::class)
    constructor(dis: DataInputStream) {
        val len = dis.readInt()
        val content = String(dis.readNBytes(len), StandardCharsets.UTF_8)
        val material = JsonParser().parse(content) as JsonObject
        shaderName = material.get("shaderName").asString
        texture = if (material.get("texture").isJsonNull) null else Identifier.parse(material.get("texture").asString)
        attrState!!.color = if (material.get("color").isJsonNull) null else material.get("color").asInt
        attrState!!.lightmapUV = if (material.get("lightmapUV").isJsonNull) null else material.get("lightmapUV").asInt
        translucent = material.has("translucent") && material.get("translucent").asBoolean
        writeDepthBuf = material.has("writeDepthBuf") && material.get("writeDepthBuf").asBoolean
        val isBillboard = material.has("billboard") && material.get("billboard").asBoolean
        if (isBillboard) attrState!!.setMatixProcess(VertAttrState.BILLBOARD)
        cutoutHack = material.has("cutoutHack") && material.get("cutoutHack").asBoolean
        checkShaderName()
    }

    private fun checkShaderName() { if (shaderName == null) shaderName = "" }

    open fun getBlazeRenderType(): RenderType {
        val textureToUse = texture ?: WHITE_TEXTURE_LOCATION
        checkShaderName()
        return BlazeRenderType.material(textureToUse, shaderName, translucent || cutoutHack, writeDepthBuf)
    }

    open fun copy(): MaterialProp {
        val result = MaterialProp()
        result.copyFrom(this)
        return result
    }

    open fun copyFrom(other: MaterialProp) {
        shaderName = other.shaderName
        texture = other.texture
        attrState = other.attrState!!.copy()
        translucent = other.translucent
        writeDepthBuf = other.writeDepthBuf
        cutoutHack = other.cutoutHack
        sheetElementsU = other.sheetElementsU
        sheetElementsV = other.sheetElementsV
    }

    @Throws(IOException::class)
    open fun serializeTo(dos: DataOutputStream) {
        val material = JsonObject()
        material.addProperty("version", 2)
        material.addProperty("shaderName", shaderName)
        if (texture == null) material.add("texture", JsonNull())
        else material.addProperty("texture", texture!!.toString())
        if (attrState!!.color == null) material.add("color", JsonNull())
        else material.addProperty("color", attrState!!.color)
        if (attrState!!.lightmapUV == null) material.add("lightmapUV", JsonNull())
        else material.addProperty("lightmapUV", attrState!!.lightmapUV)
        material.addProperty("translucent", translucent)
        material.addProperty("writeDepthBuf", writeDepthBuf)
        material.addProperty("billboard", attrState!!.useMatixProcess())
        material.addProperty("cutoutHack", cutoutHack)
        val contentBytes = material.toString().toByteArray(StandardCharsets.UTF_8)
        dos.writeInt(contentBytes.size)
        dos.write(contentBytes)
    }

    open fun useMatixProcess(): Boolean = attrState!!.useMatixProcess()
    open fun setMatixProcess(matrixProces: Function<Matrix4f?, Matrix4f?>?) { attrState!!.setMatixProcess(matrixProces) }

    override fun toString(): String = String.format("{%s: %s%s}", if (texture == null) "null" else texture!!.toString(), if (translucent) " T-" else "", shaderName)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass != other.javaClass) return false
        val that = other as MaterialProp
        return translucent == that.translucent && writeDepthBuf == that.writeDepthBuf && cutoutHack == that.cutoutHack &&
            sheetElementsU == that.sheetElementsU && sheetElementsV == that.sheetElementsV &&
            Objects.equals(shaderName, that.shaderName) && Objects.equals(texture, that.texture) && Objects.equals(attrState, that.attrState)
    }

    override fun hashCode(): Int = Objects.hash(shaderName, texture, attrState, translucent, writeDepthBuf, cutoutHack, sheetElementsU, sheetElementsV)

    companion object {
        @JvmField val WHITE_TEXTURE_LOCATION: Identifier = Identifier.parse("minecraft:textures/misc/white.png")
    }
}
