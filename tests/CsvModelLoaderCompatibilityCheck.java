package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcerext.model.RawMesh;
import cn.zbx1425.sowcerext.model.RawModel;
import cn.zbx1425.sowcerext.model.loader.CsvModelLoader;
import cn.zbx1425.sowcerext.reuse.AtlasManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Deterministic CPU corpus; golden is captured only from the pinned pre-Kotlin CSV source. */
public final class CsvModelLoaderCompatibilityCheck {
    private static final String JAVA_BLOB = "86ef037dfb87ea6bf57e8cc5bd17f926db264d0b";
    private static final Identifier LOCATION = Identifier.parse("test:models/train.csv");
    private static final String TRIANGLE = "AddVertex,0,0,0\nAddVertex,2,0,0\nAddVertex,0,1,0\nAddFace,0,1,2\n";
    private static final List<String> records = new ArrayList<>();
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        boolean baseline = Arrays.asList(args).contains("--java-baseline"), record = Arrays.asList(args).contains("--record");
        require(Path.of(CsvModelLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong CSV implementation source");
        require(isKotlin(CsvModelLoader.class) != baseline, "Wrong Java/Kotlin implementation");
        require(!record || baseline && source.getFileName().toString().equals(JAVA_BLOB), "Golden recording requires the pinned Java source output");
        CsvModelLoader.class.getConstructor().newInstance();
        require(HidingCsv.calls == 0, "Static hiding contract changed");
        if (Arrays.asList(args).contains("--packaged")) {
            require(Files.isRegularFile(source) && !baseline, "Packaged check requires a finished Kotlin JAR");
            for (Class<?> dependency : List.of(RawModel.class, RawMesh.class, AtlasManager.class)) {
                require(Path.of(dependency.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong packaged dependency: " + dependency);
            }
        }
        Locale savedLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            geometry(); commands(); resources();
        } finally { Locale.setDefault(savedLocale); }
        if (record) Files.writeString(Path.of(args[0]), String.join("\n", records) + "\n");
        else {
            List<String> expected = Files.readAllLines(Path.of(args[0]));
            require(expected.size() == records.size(), "CSV golden size differs: " + records.size());
            for (int i = 0; i < expected.size(); i++) require(expected.get(i).equals(records.get(i)), "CSV record " + i + " differs\nJava: " + expected.get(i) + "\nActual: " + records.get(i));
        }
        System.out.println("PASS: CSV loader " + assertions + " assertions / " + records.size() + " Java-source golden records; topology, commands, recovery, locale, resources and atlas ordering (no GPU)");
    }

    private static void geometry() throws Exception {
        for (String input : List.of("", "\r\n\r\n; comment", ",,,\n", TRIANGLE, TRIANGLE.replace("AddFace", "AddFace2"),
                "AddVertex,0,0,0,0,0,1\nAddVertex,2,0,0,0,0,1\nAddVertex,0,1,0,0,0,1\nAddFace,0,1,2\n",
                TRIANGLE.replace("AddFace,0,1,2", "AddVertex,2,1,0\nAddFace,0,1,3,2"),
                "CreateMeshBuilder\n" + TRIANGLE + "CreateMeshBuilder\n" + TRIANGLE,
                "AddVertex,8,8,8\nCreateMeshBuilder\n" + TRIANGLE,
                TRIANGLE + "AddFace,0,1,99\nAddFace,0,-1,2\n", "Cube\n", "Cube,2\n", "Cube,2,0,0\n", "Cube,1,2,3\n",
                "Cube,-1,-2,-3\n", "Cube,0,0,0\n", "Cube,NaN,1,2\n")) capture("geometry", input);
        for (String input : List.of("Cylinder", "Cylinder,3,1,1,2", "Cylinder,4,1,2,-2", "Cylinder,5,-1,-2,0",
                "Cylinder,6,0,1,2", "Cylinder,6,1,0,2", "Cylinder,4.5,1,1,2", "Cylinder,0,1,1,2",
                "Cylinder,-3,1,1,2", "Cylinder,1,1,1,2", "Cylinder,2,-1,-1,2", "Cylinder,3,0,0,1")) capture("cylinder", input);
        for (String input : List.of(TRIANGLE + "SetIsGLCoords,true", TRIANGLE + "SetIsGLCoords,false",
                TRIANGLE + "SetTextureCoordinates,0,0.25,0.75\nSetTextureCoordinates,1,0.5,0.625",
                TRIANGLE + "SetTextureCoordinates,0,0.25,bad\nSetTextureCoordinates,-1,1,1\nSetTextureCoordinates,9,1,1")) capture("coords", input);
    }

    private static void commands() throws Exception {
        String twoMeshes = TRIANGLE + "LoadTexture,first.png\nCreateMeshBuilder\n" + TRIANGLE + "LoadTexture,second.png\n";
        for (String command : List.of("SetColor", "SetColor,18,,86,128", "SetColor,300,-1,0,7,ignored", "SetColorAll,1,2,3,4",
                "Translate,1,2,3", "TranslateAll,1,,3", "Scale,2,3,4", "ScaleAll,-2,3,4", "Rotate,0,1,0,1.5", "RotateAll,1,0,0,-1.5",
                "Shear,1,0,0,0,1,0,0.25", "ShearAll,1,0,0,0,1,0,0.25", "Mirror,1,0,0", "Mirror,1,0,0,0,1,0", "MirrorAll,0,1,0",
                "UVMirror,1,0", "UVMirrorAll,0,1", "SetRenderType,interior", "SetRenderTypeAll,lighttranslucent", "SetBillboard",
                "LoadTexture,../Textures/Glass.TGA", "LoadTexture,test:Other/Body.png", "GenerateNormals")) capture("command", twoMeshes + command);
        for (String command : List.of("AddVertex", "AddVertex,bad,0,0", "AddVertex,1,2,3,4", "AddFace,0,1", "AddFace,0,x,2",
                "SetTextureCoordinates", "SetTextureCoordinates,x,0,0", "LoadTexture", "SetRenderType", "SetIsGLCoords",
                "Scale,bad", "Translate,bad", "Rotate,bad", "Shear,bad", "SetColor,bad", "Mirror,bad", "UVMirror,bad",
                "UnknownCommand,7", "SetEmissiveColor,1,2,3", "SetEmissiveColorAll,1,2,3", "SetBlendMode", "SetWrapMode", "SetDecalTransparentColor", "EnableCrossfading")) {
            capture("recover-" + command, TRIANGLE + command + "\nTranslate,1,2,3\n");
        }
        for (String input : List.of(TRIANGLE.replace("\n", "\r\n"), "  " + TRIANGLE.toUpperCase(Locale.ROOT).replace("\n", " ; comment\n\t"),
                TRIANGLE.replace("AddVertex,0,0,0", "AddVertex,0,0,0,,"), TRIANGLE + "SetColor,1,2,3,,,\n",
                TRIANGLE + "\u2003Translate,8,9,10\u2003", TRIANGLE + "SetColor,1,2,3;trailing;comment\n")) capture("syntax", input);
        for (Locale locale : List.of(Locale.US, Locale.forLanguageTag("tr-TR"))) {
            Locale.setDefault(locale);
            capture("locale-" + locale, "CREATEMESHBUILDER\n" + TRIANGLE + "MIRROR,1,0,0\nSETISGLCOORDS,TRUE\nLOADTEXTURE,FILE.PNG");
        }
        Locale.setDefault(Locale.US);
    }

    private static void resources() throws Exception {
        TrackInput first = text(TRIANGLE), second = text("Cube,3");
        RawModel priority = CsvModelLoader.loadModel(manager(List.of(first, second)), LOCATION, null);
        require(first.opens == 1 && second.opens == 0 && first.closed == 0, "CSV resource priority/ownership changed");
        records.add("resource-priority\t" + digest(priority));
        records.add("resource-missing\t" + digest(CsvModelLoader.loadModel(manager(List.of()), LOCATION, null)));
        records.add("null-manager\t" + outcome(() -> CsvModelLoader.loadModel(null, LOCATION, null)));
        TrackInput nullable = text(TRIANGLE);
        RawModel noLocation = CsvModelLoader.loadModel(manager(List.of(nullable)), null, null);
        require(noLocation.sourceLocation == null, "Null source location rejected");
        records.add("null-location\t" + digest(noLocation));
        TrackInput readFailure = text(TRIANGLE); readFailure.failAfter = 4;
        records.add("resource-read-failure\t" + outcome(() -> CsvModelLoader.loadModel(manager(List.of(readFailure)), LOCATION, null)) + ":" + readFailure.closed);
        TrackInput bom = new TrackInput(("\ufeff" + TRIANGLE).getBytes(StandardCharsets.UTF_8));
        records.add("resource-bom\t" + digest(CsvModelLoader.loadModel(manager(List.of(bom)), LOCATION, null)));
        TrackInput input = text(TRIANGLE + "LoadTexture,first.png\nCreateMeshBuilder\n" + TRIANGLE + "LoadTexture,second.png");
        List<String> atlasCalls = new ArrayList<>();
        RawModel atlased = CsvModelLoader.loadModel(manager(List.of(input)), LOCATION, new AtlasManager() {
            @Override public void applyToMesh(RawMesh mesh) {
                require(input.closed == 0 && input.consumed == input.data.length, "Atlas ownership/read order changed");
                atlasCalls.add(mesh.materialProp.texture + ":" + mesh.vertices.get(1).position.x());
                mesh.vertices.getFirst().u = 0.25f;
            }
        });
        records.add("atlas\t" + digest(atlased) + "\t" + atlasCalls);
        records.add("atlas-failure\t" + outcome(() -> CsvModelLoader.loadModel(manager(List.of(text(TRIANGLE))), LOCATION, new AtlasManager() {
            @Override public void applyToMesh(RawMesh mesh) { throw new IllegalStateException("atlas"); }
        })));
        records.add("atlas-error\t" + outcome(() -> CsvModelLoader.loadModel(manager(List.of(text(TRIANGLE))), LOCATION, new AtlasManager() {
            @Override public void applyToMesh(RawMesh mesh) { throw new AssertionError("atlas"); }
        })));
    }

    private static void capture(String name, String input) throws Exception {
        TrackInput stream = text(input);
        records.add(name + "\t" + outcome(() -> CsvModelLoader.loadModel(manager(List.of(stream)), LOCATION, null)));
        require(stream.opens == 1 && stream.closed == 0, "CSV stream ownership changed");
    }
    private static String digest(RawModel model) throws Exception {
        // Material grouping is a HashMap, whose order is not part of the format.
        // Freeze each mesh's byte-precise topology/attributes without freezing map order.
        List<String> meshes = new ArrayList<>();
        for (RawMesh mesh : model.meshList.values()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); mesh.serializeTo(new DataOutputStream(bytes));
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
            meshes.add(hash + ":" + mesh.materialProp.attrState.useMatixProcess);
        }
        Collections.sort(meshes);
        return model.sourceLocation + ":" + meshes;
    }
    private static String outcome(ModelCall action) throws Exception {
        try { return digest(action.run()); }
        catch (Throwable error) { return error.getClass().getSimpleName() + ":cause=" + (error.getCause() == null ? "none" : error.getCause().getClass().getSimpleName()) + ":suppressed=" + error.getSuppressed().length; }
    }
    private static ResourceManager manager(List<TrackInput> inputs) {
        return (ResourceManager) Proxy.newProxyInstance(CsvModelLoaderCompatibilityCheck.class.getClassLoader(), new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
            if (method.getName().equals("getResourceStack")) return inputs.stream().map(input -> new Resource(null, () -> { input.opens++; return input; })).toList();
            throw new UnsupportedOperationException(method.toString());
        });
    }
    private static TrackInput text(String text) { return new TrackInput(text.getBytes(StandardCharsets.UTF_8)); }
    private static boolean isKotlin(Class<?> type) { return Arrays.stream(type.getDeclaredAnnotations()).anyMatch(a -> a.annotationType().getName().equals("kotlin.Metadata")); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    @FunctionalInterface private interface ModelCall { RawModel run() throws Exception; }
    private static final class TrackInput extends InputStream {
        private final byte[] data; private final ByteArrayInputStream input; int opens, consumed, closed, failAfter = -1;
        TrackInput(byte[] bytes) { data = bytes; input = new ByteArrayInputStream(bytes); }
        @Override public int read() throws IOException { if (failAfter >= 0 && consumed >= failAfter) throw new IOException("read-failure"); int value = input.read(); if (value >= 0) consumed++; return value; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (failAfter >= 0 && consumed >= failAfter) throw new IOException("read-failure");
            int count = input.read(bytes, offset, failAfter < 0 ? length : Math.min(length, failAfter - consumed)); if (count > 0) consumed += count; return count;
        }
        @Override public void close() { closed++; }
    }
    private static class HidingCsv extends CsvModelLoader {
        static int calls;
        public static RawModel loadModel(ResourceManager manager, Identifier location, AtlasManager atlas) { calls++; return null; }
    }
}
