package cn.zbx1425.sowcer.shader;

import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.sowcer.ContextCapability;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;

public class PatchingResourceProvider implements ResourceProvider {

    private final ResourceProvider source;

    public PatchingResourceProvider(ResourceProvider source) {
        this.source = source;
    }

    @Override
    public Optional<Resource> getResource(Identifier resourceLocation) {
        try {
            if (resourceLocation.getPath().contains("_modelmat"))
                resourceLocation = Identifier.fromNamespaceAndPath(resourceLocation.getNamespace(),
                        resourceLocation.getPath().replace("_modelmat", ""));

            InputStream srcInputStream;
            Optional<Resource> srcResource = source.getResource(resourceLocation);
            if (srcResource.isEmpty()) {
                return Optional.empty();
            } else {
                srcInputStream = srcResource.get().open();
            }
            String returningContent = "";

            if (resourceLocation.getPath().endsWith(".json")) {
                String srcContent = IOUtils.toString(srcInputStream, StandardCharsets.UTF_8);
                JsonObject data = Main.JSON_PARSER.parse(srcContent).getAsJsonObject();
                data.addProperty("vertex", data.get("vertex").getAsString() + "_modelmat");

                // Since 1.21 vanilla shader JSON no longer declares vertex
                // attributes; they are supplied by ShaderInstance's
                // VertexFormat instead.  Older versions still need this
                // padding to align the custom ModelMat attribute.
                if (data.has("attributes")) {
                    JsonArray attribArray = data.getAsJsonArray("attributes");
                    int dummyAttribCount = 6 - attribArray.size();
                    for (int i = 0; i < dummyAttribCount; i++) {
                        attribArray.add("Dummy" + i);
                    }
                    attribArray.add("ModelMat");
                }
                returningContent = data.toString();
                srcInputStream.close();
            } else if (resourceLocation.getPath().endsWith(".vsh")) {
                String srcContent = IOUtils.toString(srcInputStream, StandardCharsets.UTF_8);
                returningContent = patchVertexShaderSource(srcContent);
                srcInputStream.close();
            } else {
                return srcResource;
            }

            final InputStream newContentStream = new ByteArrayInputStream(returningContent.getBytes(StandardCharsets.UTF_8));
            return Optional.of(new Resource(srcResource.get().source(), () -> newContentStream));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    public static String patchVertexShaderSource(String srcContent) {
        String[] contentParts = srcContent.split("void main");
        contentParts[0] = contentParts[0]
                .replace("uniform mat4 ModelViewMat;", "uniform mat4 ModelViewMat;\nin mat4 ModelMat;")
        ;
        if (ContextCapability.isGL4ES) {
            contentParts[0] = contentParts[0].replace("ivec2", "vec2");
        }
        contentParts[1] = contentParts[1]
                .replaceAll("\\bPosition\\b", "(MODELVIEWMAT * ModelMat * vec4(Position, 1.0)).xyz")
                .replaceAll("\\bNormal\\b", "normalize(mat3(MODELVIEWMAT * ModelMat) * Normal)")
                .replace("ModelViewMat", "mat4(1.0)")
                .replace("MODELVIEWMAT", "ModelViewMat")
        ;
        return contentParts[0] + "void main" + contentParts[1];
    }
}
