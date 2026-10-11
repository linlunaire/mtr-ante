package cn.zbx1425.sowcer.shader

import cn.zbx1425.mtrsteamloco.Main
import cn.zbx1425.sowcer.ContextCapability
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.Resource
import net.minecraft.server.packs.resources.ResourceProvider
import org.apache.commons.io.IOUtils
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.regex.Pattern

open class PatchingResourceProvider(private val source: ResourceProvider?) : ResourceProvider {
    override fun getResource(resourceLocation: Identifier): Optional<Resource> {
        var location = resourceLocation
        try {
            if (location.path.contains("_modelmat")) {
                location = Identifier.fromNamespaceAndPath(location.namespace, location.path.replace("_modelmat", ""))
            }
            val srcResource = source!!.getResource(location)
            if (srcResource.isEmpty) return Optional.empty()
            val srcInputStream = srcResource.get().open()
            val returningContent: String
            if (location.path.endsWith(".json")) {
                val srcContent = IOUtils.toString(srcInputStream, StandardCharsets.UTF_8)
                val data = Main.JSON_PARSER.parse(srcContent).asJsonObject
                data.addProperty("vertex", data.get("vertex").asString + "_modelmat")
                // Legacy formats declare attributes explicitly; newer shader JSON omits them.
                if (data.has("attributes")) {
                    val attribArray = data.getAsJsonArray("attributes")
                    val dummyAttribCount = 6 - attribArray.size()
                    for (i in 0 until dummyAttribCount) attribArray.add("Dummy" + i)
                    attribArray.add("ModelMat")
                }
                returningContent = data.toString()
                srcInputStream.close()
            } else if (location.path.endsWith(".vsh")) {
                val srcContent = IOUtils.toString(srcInputStream, StandardCharsets.UTF_8)
                returningContent = patchVertexShaderSource(srcContent)
                srcInputStream.close()
            } else {
                // Keep the existing provider's stream ownership and passthrough behavior.
                return srcResource
            }
            val newContentStream = ByteArrayInputStream(returningContent.toByteArray(StandardCharsets.UTF_8))
            return Optional.of(Resource(srcResource.get().source()) { newContentStream })
        } catch (ignored: IOException) {
            return Optional.empty()
        }
    }

    companion object {
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT")
        @JvmStatic open fun patchVertexShaderSource(srcContent: String?): String {
            // Java regex split drops trailing empty parts; Kotlin String.split does not.
            val contentParts = Pattern.compile("void main").split(srcContent!!)
            contentParts[0] = contentParts[0].replace("uniform mat4 ModelViewMat;", "uniform mat4 ModelViewMat;\nin mat4 ModelMat;")
            if (ContextCapability.isGL4ES) contentParts[0] = contentParts[0].replace("ivec2", "vec2")
            contentParts[1] = Pattern.compile("\\bPosition\\b").matcher(contentParts[1])
                .replaceAll("(MODELVIEWMAT * ModelMat * vec4(Position, 1.0)).xyz")
            contentParts[1] = Pattern.compile("\\bNormal\\b").matcher(contentParts[1])
                .replaceAll("normalize(mat3(MODELVIEWMAT * ModelMat) * Normal)")
                .replace("ModelViewMat", "mat4(1.0)")
                .replace("MODELVIEWMAT", "ModelViewMat")
            return contentParts[0] + "void main" + contentParts[1]
        }
    }
}
