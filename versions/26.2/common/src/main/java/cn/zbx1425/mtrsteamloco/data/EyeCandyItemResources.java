package cn.zbx1425.mtrsteamloco.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Makes legacy eye-candy item references visible to both model and atlas preparation. */
public final class EyeCandyItemResources {
    private static final Logger LOGGER = LogUtils.getLogger();

    private EyeCandyItemResources() { }

    public static void prepare(PackType type, MultiPackResourceManager manager) {
        if (type != PackType.CLIENT_RESOURCES) return;
        // Match EyeCandyRegistry: every pack's definition can contribute distinct prefabs.
        manager.listResourceStacks("eyecandies", id -> id.getNamespace().equals("mtrsteamloco") && id.getPath().endsWith(".json"))
                .forEach((id, stack) -> {
                    for (Resource resource : stack) {
                        try (Reader reader = resource.openAsReader()) {
                            final JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                            if (root.has("model") || root.has("scriptFiles")) {
                                prepareDefinition(manager, root, id.toString());
                            } else {
                                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                                    if (entry.getValue().isJsonObject()) {
                                        prepareDefinition(manager, entry.getValue().getAsJsonObject(), id + "#" + entry.getKey());
                                    }
                                }
                            }
                        } catch (Exception ex) {
                            LOGGER.error("Failed preparing eye-candy item resources {} from {}", id, resource.sourcePackId(), ex);
                        }
                    }
                });
    }

    private static void prepareDefinition(MultiPackResourceManager manager, JsonObject definition, String source) {
        if (!definition.has("itemModel")) return;
        try {
            final Identifier reference = Identifier.parse(definition.get("itemModel").getAsString());
            final Identifier item = clientItemId(reference);
            // OBJ/BBModel/etc. remain on ANTE's raw-model path, not the vanilla model loader.
            if (item == null) return;

            final Identifier model;
            if (reference.getPath().endsWith(".png")) {
                model = item.withPrefix("item/");
                final JsonObject textures = new JsonObject();
                textures.addProperty("layer0", model.toString());
                final JsonObject geometry = new JsonObject();
                geometry.addProperty("parent", "minecraft:item/generated");
                geometry.add("textures", textures);
                addJson(manager, resource(model, "models/", ".json"), geometry);
                // Capture this reload's manager; do not read a global Minecraft/old script manager.
                DynamicResource.addResources(manager, resource(model, "textures/", ".png"),
                        () -> manager.getResourceOrThrow(reference).open());
            } else {
                // Legacy ModelResourceLocation(reference, "inventory") resolves models/item/<path>.
                model = reference.withPrefix("item/");
            }
            final JsonObject modelNode = new JsonObject();
            modelNode.addProperty("type", "minecraft:model");
            modelNode.addProperty("model", model.toString());
            final JsonObject clientItem = new JsonObject();
            clientItem.add("model", modelNode);
            addJson(manager, resource(item, "items/", ".json"), clientItem);
        } catch (Exception ex) {
            LOGGER.error("Failed preparing eye-candy item model {}", source, ex);
        }
    }

    /** Identifier for ModelManager.getItemModel; null means an ANTE raw model. */
    public static Identifier clientItemId(Identifier reference) {
        final String path = reference.getPath();
        if (path.endsWith(".png")) {
            return Identifier.fromNamespaceAndPath("dynamic___item__" + reference.getNamespace(),
                    path.substring(0, path.length() - 4) + "_png");
        }
        if (path.substring(path.lastIndexOf('/') + 1).contains(".")) return null;
        return Identifier.fromNamespaceAndPath("dynamic___model__" + reference.getNamespace(), path);
    }

    private static Identifier resource(Identifier id, String prefix, String suffix) {
        return Identifier.fromNamespaceAndPath(id.getNamespace(), prefix + id.getPath() + suffix);
    }

    private static void addJson(MultiPackResourceManager manager, Identifier id, JsonObject json) {
        final byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        DynamicResource.addResources(manager, id, () -> new ByteArrayInputStream(bytes));
    }
}
