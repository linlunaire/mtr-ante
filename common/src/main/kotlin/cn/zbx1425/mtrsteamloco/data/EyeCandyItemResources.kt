package cn.zbx1425.mtrsteamloco.data

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.logging.LogUtils
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.MultiPackResourceManager
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

/** Prepares legacy item references before vanilla model and atlas loading. */
class EyeCandyItemResources private constructor() {
    companion object {
        private val LOGGER = LogUtils.getLogger()

        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun prepare(type: PackType?, manager: MultiPackResourceManager?) {
            if (type != PackType.CLIENT_RESOURCES) return
            manager!!.listResourceStacks("eyecandies") { id -> id.namespace == "mtrsteamloco" && id.path.endsWith(".json") }
                .forEach { (id, stack) ->
                    for (resource in stack) {
                        try {
                            resource.openAsReader().use { reader ->
                                val root = JsonParser.parseReader(reader).asJsonObject
                                if (root.has("model") || root.has("scriptFiles")) prepareDefinition(manager, root, id.toString())
                                else for ((key, value) in root.entrySet()) {
                                    if (value.isJsonObject) prepareDefinition(manager, value.asJsonObject, "$id#$key")
                                }
                            }
                        } catch (error: Exception) {
                            LOGGER.error("Failed preparing eye-candy item resources {} from {}", id, resource.sourcePackId(), error)
                        }
                    }
                }
        }

        private fun prepareDefinition(manager: MultiPackResourceManager, definition: JsonObject, source: String) {
            if (!definition.has("itemModel")) return
            try {
                val reference = Identifier.parse(definition.get("itemModel").asString)
                val item = clientItemId(reference) ?: return
                val model: Identifier
                if (reference.path.endsWith(".png")) {
                    model = item.withPrefix("item/")
                    val textures = JsonObject()
                    textures.addProperty("layer0", model.toString())
                    val geometry = JsonObject()
                    geometry.addProperty("parent", "minecraft:item/generated")
                    geometry.add("textures", textures)
                    addJson(manager, resource(model, "models/", ".json"), geometry)
                    // Retain this reload's manager in the lazy texture supplier.
                    DynamicResource.addResources(manager, resource(model, "textures/", ".png")) { manager.getResourceOrThrow(reference).open() }
                } else model = reference.withPrefix("item/")
                val modelNode = JsonObject()
                modelNode.addProperty("type", "minecraft:model")
                modelNode.addProperty("model", model.toString())
                val clientItem = JsonObject()
                clientItem.add("model", modelNode)
                addJson(manager, resource(item, "items/", ".json"), clientItem)
            } catch (error: Exception) {
                LOGGER.error("Failed preparing eye-candy item model {}", source, error)
            }
        }

        /** Returns null for ANTE raw-model formats instead of routing them through vanilla. */
        @Suppress("NON_FINAL_MEMBER_IN_OBJECT") @JvmStatic
        open fun clientItemId(reference: Identifier?): Identifier? {
            val path = reference!!.path
            if (path.endsWith(".png")) return Identifier.fromNamespaceAndPath("dynamic___item__" + reference.namespace, path.substring(0, path.length - 4) + "_png")
            if (path.substring(path.lastIndexOf('/') + 1).contains('.')) return null
            return Identifier.fromNamespaceAndPath("dynamic___model__" + reference.namespace, path)
        }

        private fun resource(id: Identifier, prefix: String, suffix: String): Identifier =
            Identifier.fromNamespaceAndPath(id.namespace, prefix + id.path + suffix)

        private fun addJson(manager: MultiPackResourceManager, id: Identifier, json: JsonObject) {
            val bytes = json.toString().toByteArray(StandardCharsets.UTF_8)
            DynamicResource.addResources(manager, id) { ByteArrayInputStream(bytes) }
        }
    }
}
