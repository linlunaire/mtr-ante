package cn.zbx1425.sowcerext.model

import cn.zbx1425.sowcer.math.Vector3f
import com.google.gson.JsonObject
import net.minecraft.resources.Identifier

/**
 * Synchronously prepares an independently mutable variant of a borrowed, read-only template.
 *
 * The definition is neither retained nor modified. A single [RawModel.copy] owns the resulting
 * mesh, material attributes (including position/normal vectors), vertices and faces. Its existing
 * copy contract shares `matrixModel` transform callbacks; callback closures are not cloned.
 * Parsing or transformation failure cannot modify the template. Resource loading, GPU upload
 * and cache ownership remain with the caller.
 */
object ModelVariantPreparation {
    /** Rail definitions historically ignore geometry fields, including malformed values. */
    enum class Geometry { APPLY, IGNORE }

    @JvmStatic
    fun prepare(template: RawModel?, key: String?, definition: JsonObject?, geometry: Geometry): RawModel {
        val model = template!!.copy()
        val json = definition!!
        if (json.has("textureId")) {
            model.replaceTexture("default.png", Identifier.parse(json.get("textureId").asString))
        }
        if (json.has("flipV") && json.get("flipV").asBoolean) {
            model.applyUVMirror(false, true)
        }

        if (geometry == Geometry.APPLY) {
            if (json.has("translation")) {
                val vector = json.get("translation").asJsonArray
                model.applyTranslation(vector.get(0).asFloat, vector.get(1).asFloat, vector.get(2).asFloat)
            }
            if (json.has("rotation")) {
                val vector = json.get("rotation").asJsonArray
                // Read each angle immediately before its rotation: failed later values must not
                // change the established sequence of successful earlier transformations.
                model.applyRotation(Vector3f(1f, 0f, 0f), vector.get(0).asFloat)
                model.applyRotation(Vector3f(0f, 1f, 0f), vector.get(1).asFloat)
                model.applyRotation(Vector3f(0f, 0f, 1f), vector.get(2).asFloat)
            }
            if (json.has("scale")) {
                val vector = json.get("scale").asJsonArray
                model.applyScale(vector.get(0).asFloat, vector.get(1).asFloat, vector.get(2).asFloat)
            }
            if (json.has("mirror")) {
                val vector = json.get("mirror").asJsonArray
                model.applyMirror(
                    vector.get(0).asBoolean, vector.get(1).asBoolean, vector.get(2).asBoolean,
                    vector.get(0).asBoolean, vector.get(1).asBoolean, vector.get(2).asBoolean
                )
            }
        }

        model.sourceLocation = Identifier.parse("${model.sourceLocation}/$key")
        return model
    }
}
