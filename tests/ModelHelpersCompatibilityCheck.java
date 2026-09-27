package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcerext.model.Face;
import cn.zbx1425.sowcerext.model.RawMesh;
import cn.zbx1425.sowcerext.model.Vertex;
import cn.zbx1425.sowcerext.model.integration.RawMeshBuilder;
import cn.zbx1425.sowcerext.reuse.AtlasSprite;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** CPU-only original-Java oracle: mutable builder state, exact UV bits and failure ordering. */
public final class ModelHelpersCompatibilityCheck {
    private static int assertions;
    private static final Identifier OLD = Identifier.parse("test:old");
    private static final Identifier SHEET = Identifier.parse("test:sheet");
    private static final List<String> records = new ArrayList<>();
    private static final List<String> warnings = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        boolean kotlin = Files.isDirectory(source);
        for (Class<?> type : List.of(RawMeshBuilder.class, AtlasSprite.class)) {
            require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong source for " + type);
            require(Arrays.stream(type.getDeclaredAnnotations()).anyMatch(a -> a.annotationType().getName().equals("kotlin.Metadata")) == kotlin, "Wrong language for " + type);
        }
        var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger("SowCerExt");
        var oldLevel = logger.getLevel(); boolean oldAdditive = logger.isAdditive();
        var appender = new AbstractAppender("model-helper-oracle", null, null, false, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) { warnings.add(event.getLevel() + ":" + event.getMessage().getFormattedMessage()); }
        };
        appender.start(); logger.addAppender(appender); logger.setAdditive(false); logger.setLevel(org.apache.logging.log4j.Level.WARN);
        try { builders(); builderFailures(); atlas(); atlasFailures(); }
        finally { logger.removeAppender(appender); logger.setLevel(oldLevel); logger.setAdditive(oldAdditive); appender.stop(); }
        if (Arrays.asList(args).contains("--record")) {
            require(!kotlin, "Only the original Java implementation can record a golden");
            String hash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)));
            require(hash.equals("db3662a772ad746bcdbfe81b2e44fed09ea084d73f11b29022be9b11e9f4d79c"), "Wrong frozen Java baseline JAR");
            Files.writeString(Path.of(args[0]), String.join("\n", records) + "\n");
        } else {
            List<String> expected = Files.readAllLines(Path.of(args[0]));
            require(expected.size() == records.size(), "Record count changed: " + records.size());
            for (int i = 0; i < records.size(); i++) require(expected.get(i).equals(records.get(i)), "Record " + i + " differs\nJava: " + expected.get(i) + "\nActual: " + records.get(i));
        }
        System.out.println("PASS: model helpers, " + assertions + " assertions / " + records.size() + " Java records; builder aliases, partial failures, UV raw bits and warning order (no GPU)");
    }

    private static void builders() {
        for (String render : List.of("exterior", "exteriortranslucent", "interior", "interiortranslucent", "light", "lighttranslucent")) {
            RawMeshBuilder builder = new RawMeshBuilder(4, render, null);
            var mesh = builder.getMesh(); var material = mesh.materialProp; var attr = material.attrState;
            require(builder.endVertex() == builder, "endVertex lost fluent identity");
            require(mesh.vertices.getFirst().position == null && mesh.vertices.getFirst().normal == null, "First vertex acquired non-Java defaults");
            require(builder.getMesh() == mesh && material.texture == null, "Builder mesh or nullable texture changed");
            require(builder.color(300, -1, 17, -128) == builder && builder.lightMapUV((short) 0xABCD, (short) 0xFEDC) == builder, "Attribute methods lost fluent identity");
            require(builder.normal(1f, -2f, Float.NaN) == builder && builder.uv(-0f, Float.POSITIVE_INFINITY) == builder, "Vertex methods lost fluent identity");
            var vector = new Vector3f(1f, 2f, 3f);
            require(builder.vertex(vector) == builder, "Position method lost fluent identity");
            builder.endVertex(); require(mesh.vertices.get(1).position == vector, "Vector argument copied");
            vector.add(7f, 7f, 7f);
            builder.vertex(Double.MAX_VALUE, -0d, Double.NaN).endVertex();
            builder.vertex((Vector3f) null).endVertex();
            records.add("builder-" + render + "\t" + state(mesh));
            var vertices = mesh.vertices; var faces = mesh.faces;
            require(builder.reset() == builder && mesh.vertices == vertices && mesh.faces == faces && vertices.isEmpty() && faces.isEmpty(), "Reset replaced live lists");
            require(mesh.materialProp == material && material.attrState == attr, "Reset replaced attributes");
            builder.endVertex(); Vertex defaultVertex = mesh.vertices.getFirst();
            require(defaultVertex.position != null && defaultVertex.normal != null && defaultVertex.position != defaultVertex.normal, "Reset default vectors changed");
            records.add("reset-" + render + "\t" + state(mesh));
        }
        for (int size : new int[] {-3, -1, 0, 1, 2, 3, 4, 5, 8}) {
            RawMeshBuilder builder = new RawMeshBuilder(size, "exterior", OLD);
            for (int index = 0; index < 16; index++) {
                builder.vertex(index + 0.25, -index, index * 0.1).normal(0, 1, 0).uv(index * 0.125f, -index * 0.25f);
                String failure = failure(builder::endVertex);
                records.add("face-" + size + "-" + index + "\t" + failure + "\t" + state(builder.getMesh()));
            }
        }
        require(failure(() -> new RawMeshBuilder(3, null, OLD)).equals("NullPointerException"), "Null render type accepted");
        require(failure(() -> new RawMeshBuilder(3, "unknown", OLD)).equals("IllegalArgumentException"), "Unknown render type accepted");
        RawMeshBuilder overridden = new RawMeshBuilder(3, "exterior", OLD) { @Override public RawMesh getMesh() { return null; } };
        require(overridden.vertex(1, 2, 3).endVertex().reset() == overridden && overridden.getMesh() == null, "Builder internally invoked virtual getMesh");
    }

    private static void builderFailures() {
        RawMeshBuilder builder = new RawMeshBuilder(1, "exterior", OLD);
        RawMesh mesh = builder.getMesh(); mesh.faces = null;
        require(failure(builder::endVertex).equals("ArrayIndexOutOfBoundsException"), "Null faces failed before triangulation");
        require(mesh.vertices.size() == 1, "Triangulation failure lost appended vertex");
        records.add("null-faces-triangulation\t" + state(mesh));
        builder = new RawMeshBuilder(3, "exterior", OLD); mesh = builder.getMesh();
        var meshForClear = mesh;
        mesh.faces = new ArrayList<>() { @Override public void clear() { throw new IllegalStateException("clear"); } };
        Vector3f pending = new Vector3f(3, 4, 5); builder.vertex(pending).uv(0.3f, 0.4f);
        require(failure(builder::reset).equals("IllegalStateException") && meshForClear.vertices.isEmpty(), "Reset failure ordering changed");
        mesh.faces = new ArrayList<>(); builder.endVertex();
        require(mesh.vertices.getFirst().position == pending, "Failed reset replaced pending vertex");
        records.add("failed-reset\t" + state(mesh));
        builder = new RawMeshBuilder(3, "exterior", OLD); mesh = builder.getMesh();
        mesh.vertices = new ArrayList<>() { @Override public boolean add(Vertex vertex) { super.add(vertex); throw new UnsupportedOperationException(); } };
        require(failure(builder::endVertex).equals("UnsupportedOperationException"), "Append failure was swallowed");
        Vertex pendingAfterFailure = mesh.vertices.getFirst(); mesh.vertices = new ArrayList<>(); builder.endVertex();
        require(mesh.vertices.getFirst() == pendingAfterFailure, "Failed append reset pending vertex too soon");
        records.add("failed-add\t" + state(mesh));
        builder = new RawMeshBuilder(3, "exterior", OLD); mesh = builder.getMesh();
        mesh.vertices = null;
        require(failure(builder::endVertex).equals("NullPointerException"), "Null vertex list accepted");
        require(failure(builder::reset).equals("NullPointerException"), "Null vertex list reset accepted");
        mesh.vertices = new ArrayList<>(); mesh.faces = null; builder.endVertex().endVertex();
        require(failure(builder::endVertex).equals("NullPointerException") && mesh.vertices.size() == 3, "Null faces lost appended vertex");
        records.add("null-faces-valid\t" + state(mesh));
    }

    private static void atlas() {
        float[] uv = {0f, -0f, 0.25f, 0.5f, 1f, -1f, 2f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        int[] dimensions = {0, 1, -1, 8, 16, 127, Integer.MAX_VALUE, Integer.MIN_VALUE};
        Random random = new Random(0x61746C6173L);
        for (int scenario = 0; scenario < 64; scenario++) {
            int[] d = new int[12];
            for (int i = 0; i < d.length; i++) d[i] = dimensions[random.nextInt(dimensions.length)];
            AtlasSprite sprite = new AtlasSprite(scenario % 3 == 0 ? null : SHEET, d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9], d[10], d[11], (scenario & 1) != 0);
            RawMesh mesh = new RawMesh(new MaterialProp()); mesh.materialProp.texture = OLD;
            for (int i = 0; i < uv.length; i++) { Vertex vertex = new Vertex(); vertex.u = uv[i]; vertex.v = uv[(i + scenario) % uv.length]; mesh.vertices.add(vertex); }
            var vertices = mesh.vertices; var material = mesh.materialProp;
            warnings.clear(); sprite.applyToMesh(mesh);
            require(mesh.vertices == vertices && mesh.materialProp == material && material.texture == sprite.sheet, "Atlas changed ownership");
            require(warnings.size() <= 1, "Atlas emitted more than one bleeding warning");
            records.add("atlas-" + scenario + "\t" + uvState(mesh) + "\t" + material.texture + "\t" + warnings);
        }
        AtlasSprite sprite = regularSprite(); RawMesh mesh = uvMesh();
        Vertex aliased = mesh.vertices.getFirst(); mesh.vertices.add(aliased); warnings.clear(); sprite.applyToMesh(mesh);
        records.add("atlas-aliased\t" + uvState(mesh) + "\t" + warnings);
        sprite.sheet = null; sprite.sheetWidth = 64; sprite.sheetHeight = -4; sprite.frameX = Integer.MAX_VALUE;
        sprite.frameY = -3; sprite.frameWidth = 3; sprite.frameHeight = 4; sprite.spriteX = -1; sprite.spriteY = 1;
        sprite.spriteWidth = 8; sprite.spriteHeight = 2; sprite.sourceWidth = 16; sprite.sourceHeight = 12; sprite.rotated = !sprite.rotated;
        warnings.clear(); sprite.applyToMesh(mesh);
        records.add("atlas-mutated-fields\t" + uvState(mesh) + "\t" + mesh.materialProp.texture + "\t" + warnings);
        RawMesh unrotated = uvMesh(), rotated = uvMesh(); sprite = regularSprite(); sprite.applyToMesh(unrotated); sprite.rotated = true; sprite.applyToMesh(rotated);
        require(uvState(unrotated).equals(uvState(rotated)), "Previously unused rotated flag changed geometry");
    }

    private static void atlasFailures() {
        AtlasSprite sprite = regularSprite();
        require(failure(() -> sprite.applyToMesh(null)).equals("NullPointerException"), "Null mesh accepted");
        RawMesh mesh = uvMesh(); mesh.vertices.add(null); warnings.clear();
        require(failure(() -> sprite.applyToMesh(mesh)).equals("NullPointerException"), "Null vertex accepted");
        require(mesh.materialProp.texture == OLD && warnings.isEmpty(), "Partial atlas failure changed texture or logged warning");
        records.add("atlas-null-vertex\t" + uvState(mesh));
        RawMesh noMaterial = new RawMesh((MaterialProp) null); Vertex vertex = new Vertex(); vertex.u = 2; vertex.v = 3; noMaterial.vertices.add(vertex);
        warnings.clear(); require(failure(() -> sprite.applyToMesh(noMaterial)).equals("NullPointerException"), "Null material accepted");
        require(warnings.isEmpty(), "Null material logged before texture read");
        records.add("atlas-null-material\t" + uvState(noMaterial));
        RawMesh noVertices = uvMesh(); noVertices.vertices = null;
        require(failure(() -> sprite.applyToMesh(noVertices)).equals("NullPointerException") && noVertices.materialProp.texture == OLD, "Null list changed texture");
        RawMesh empty = new RawMesh(new MaterialProp()); empty.materialProp.texture = OLD; sprite.sheet = null; warnings.clear(); sprite.applyToMesh(empty);
        require(empty.materialProp.texture == null && warnings.isEmpty(), "Empty atlas failed to update nullable texture");
    }

    private static AtlasSprite regularSprite() { return new AtlasSprite(SHEET, 128, 64, 32, 16, 32, 16, 2, 3, 8, 4, 16, 16, false); }
    private static RawMesh uvMesh() {
        RawMesh mesh = new RawMesh(new MaterialProp()); mesh.materialProp.texture = OLD;
        for (float value : new float[] {0.25f, 0.5f, 1f}) { Vertex vertex = new Vertex(); vertex.u = value; vertex.v = value; mesh.vertices.add(vertex); }
        return mesh;
    }
    private static String uvState(RawMesh mesh) {
        StringBuilder out = new StringBuilder();
        for (Vertex vertex : mesh.vertices) out.append(vertex == null ? "null" : bits(vertex.u) + ":" + bits(vertex.v)).append(';');
        return out.toString();
    }
    private static String state(RawMesh mesh) {
        StringBuilder out = new StringBuilder();
        for (Vertex vertex : mesh.vertices) out.append(vector(vertex.position)).append('/').append(vector(vertex.normal)).append('/').append(bits(vertex.u)).append(':').append(bits(vertex.v)).append(':').append(vertex.color).append(':').append(vertex.light).append(';');
        out.append('|');
        if (mesh.faces == null) out.append("null"); else for (Face face : mesh.faces) out.append(Arrays.toString(face.vertices));
        var material = mesh.materialProp;
        return out + "|" + material.shaderName + ":" + material.translucent + ":" + material.writeDepthBuf + ":" + material.cutoutHack + ":" + material.texture + ":" + material.attrState.color + ":" + material.attrState.lightmapUV;
    }
    private static String vector(Vector3f vector) { return vector == null ? "null" : bits(vector.x()) + "," + bits(vector.y()) + "," + bits(vector.z()); }
    private static String bits(float value) { return Integer.toHexString(Float.floatToRawIntBits(value)); }
    private static String failure(Runnable action) { try { action.run(); return "OK"; } catch (RuntimeException failure) { return failure.getClass().getSimpleName(); } }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
