package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcerext.model.*;
import cn.zbx1425.sowcerext.model.loader.NmbModelLoader;
import cn.zbx1425.sowcerext.model.loader.ObjModelLoader;
import cn.zbx1425.sowcerext.reuse.AtlasManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Real AES and de.javagl parsers with headless ResourceManager input, never GPU/game startup. */
public final class ModelLoadersCompatibilityCheck {
    private static int assertions;
    private static final String JAVA_BASELINE_SHA256 = "DB3662A772AD746BCDBFE81B2E44FED09EA084D73F11B29022BE9B11E9F4D79C";
    private static final List<String> records = new ArrayList<>();
    private static final Identifier LOCATION = Identifier.parse("test:models/train.obj");
    private static final String GEOMETRY = "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nvt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\nvn 0 0 1\n";
    private static final String QUAD = "f 1/1/1 2/2/1 3/3/1 4/4/1\n";
    private static final String TRIANGLE = "f 1 2 3\n";

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        boolean kotlin = isKotlin(NmbModelLoader.class);
        boolean record = Arrays.asList(args).contains("--record");
        boolean packaged = Arrays.asList(args).contains("--packaged");
        require(!Files.isDirectory(source) || kotlin, "A classes directory must contain the current Kotlin implementation");
        for (Class<?> type : List.of(NmbModelLoader.class, ObjModelLoader.class)) {
            require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong source for " + type);
            require(isKotlin(type) == kotlin, "Mixed Java/Kotlin loader implementations: " + type);
            type.getConstructor().newInstance();
        }
        if (record) {
            require(!kotlin && Files.isRegularFile(source), "Only the original Java JAR may record the baseline");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(source)) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            }
            require(JAVA_BASELINE_SHA256.equalsIgnoreCase(hex(digest.digest())), "Unrecognized Java baseline SHA-256; refusing to overwrite the golden");
        }
        if (packaged) {
            require(kotlin && Files.isRegularFile(source), "Packaged checks must execute a finished Kotlin JAR");
            for (Class<?> type : List.of(RawModel.class, RawMesh.class, Vertex.class, Face.class, MaterialProp.class, AtlasManager.class,
                    Class.forName("vendor.cn.zbx1425.sowcerext.de.javagl.obj.ObjReader"))) {
                require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Packaged dependency came from another artifact: " + type);
            }
        }
        // These subclasses compile only when the original non-final Java static contract survives.
        require(HidingNmb.serializeCalls == 0 && HidingObj.loadCalls == 0, "Unexpected static dispatch");
        nmbRoundTrips(); nmbFailures(); objInputs(); objResources(); objExport();
        if (record) {
            Files.writeString(Path.of(args[0]), String.join("\n", records) + "\n");
        } else {
            List<String> expected = Files.readAllLines(Path.of(args[0]));
            require(expected.size() == records.size(), "Record count differs: " + records.size());
            for (int i = 0; i < expected.size(); i++) require(expected.get(i).equals(records.get(i)), "Record " + i + " differs\nJava: " + expected.get(i) + "\nActual: " + records.get(i));
        }
        System.out.println("PASS: NMB/OBJ loaders, " + assertions + " assertions / " + records.size() + " Java records; actual AES/plaintext, parsers, material options, aliases, I/O ownership and failure ordering (no GPU)");
    }

    private static RawModel model() {
        MaterialProp material = new MaterialProp(); material.texture = Identifier.parse("test:textures/a.png");
        RawMesh mesh = new RawMesh(material); mesh.setRenderType("interiortranslucent");
        material.attrState.setColor(0x12345678);
        for (int i = 0; i < 3; i++) { Vertex vertex = new Vertex(new Vector3f(i & 1, i >> 1, -0f), new Vector3f(0, 0, 1)); vertex.u = i / 2f; vertex.v = 1 - vertex.u; mesh.vertices.add(vertex); }
        mesh.faces.add(new Face(new int[] {0, 1, 2})); RawModel model = new RawModel(); model.append(mesh); return model;
    }
    private static byte[] plaintext(RawModel model) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream(); model.serializeTo(new DataOutputStream(out)); return out.toByteArray();
    }
    private static byte[] encrypt(byte[] plain) throws Exception {
        byte[] key = new byte[32]; for (int i = 0; i < key.length; i++) key[i] = (byte) (i * 7 + 3);
        Cipher cipher = cipher(Cipher.ENCRYPT_MODE, key); byte[] encrypted = cipher.doFinal(plain);
        ByteArrayOutputStream out = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(out);
        dos.writeBytes("ZBXNMB10"); dos.writeInt(1); dos.writeInt(0); dos.write(key); dos.writeInt(encrypted.length); dos.write(encrypted); return out.toByteArray();
    }
    private static Cipher cipher(int mode, byte[] key) throws Exception {
        Cipher result = Cipher.getInstance("AES/CBC/PKCS5Padding"); result.init(mode, new SecretKeySpec(key, "AES"), new IvParameterSpec(Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(key), 16))); return result;
    }
    private static void nmbRoundTrips() throws Exception {
        for (boolean raw : new boolean[] {false, true}) {
            RawModel original = model(); byte[] plain = plaintext(original); TrackOutput output = new TrackOutput(-1);
            NmbModelLoader.serializeModel(original, output, raw);
            require(output.closed == 0 && output.flushed == 0, "NMB serializer took ownership of output");
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(output.bytes.toByteArray()));
            require(new String(input.readNBytes(8), StandardCharsets.UTF_8).equals("ZBXNMB10") && input.readInt() == 1 && input.readInt() == 0, "NMB header changed");
            byte[] key = input.readNBytes(32); int length = input.readInt(); byte[] encrypted = input.readNBytes(length);
            require(length % 16 == 0 && Arrays.equals(cipher(Cipher.DECRYPT_MODE, key).doFinal(encrypted), plain), "Real AES decrypt did not recover exact model bytes");
            if (raw) { for (int i = 0; i < 16; i++) require(input.readInt() == 0, "Raw appendix padding changed"); require(Arrays.equals(input.readAllBytes(), plain), "Raw appendix differs from encrypted content"); }
            else require(input.available() == 0, "Unexpected NMB trailer");
            TrackInput stream = new TrackInput(output.bytes.toByteArray());
            RawModel loaded = NmbModelLoader.loadModel(manager(Map.of(LOCATION, stream)), LOCATION, null);
            require(stream.closed == 1 && loaded.sourceLocation == LOCATION, "NMB close/location identity changed");
            require(Arrays.equals(plaintext(loaded), plain), "NMB geometry round-trip changed");
            records.add("nmb-write-" + raw + "\t" + length + "\t" + hex(plain) + "\t" + stream.closed);
        }
        byte[] data = encrypt(plaintext(model())); Arrays.fill(data, 0, 8, (byte) 'X'); Arrays.fill(data, 8, 16, (byte) 0xEF);
        TrackInput stream = new TrackInput(data); List<String> callbacks = new ArrayList<>();
        RawModel loaded = NmbModelLoader.loadModel(manager(Map.of(LOCATION, stream)), LOCATION, new AtlasManager() {
            @Override public void applyToMesh(RawMesh mesh) { require(stream.closed == 1, "NMB atlas ran before input closed"); callbacks.add(mesh.materialProp.texture.toString()); mesh.vertices.getFirst().u = 0.375f; }
        });
        records.add("nmb-ignored-header-atlas\t" + digest(loaded) + "\t" + callbacks);
        TrackInput first = new TrackInput(encrypt(plaintext(new RawModel()))), second = new TrackInput(data);
        var manager = manager(Map.of(LOCATION, first), List.of(first, second));
        require(NmbModelLoader.loadModel(manager, LOCATION, null).meshList.isEmpty() && first.opens == 1 && second.opens == 0, "NMB resource priority changed");
    }
    private static void nmbFailures() throws Exception {
        byte[] valid = encrypt(plaintext(model()));
        for (int size : new int[] {0, 7, 8, 11, 12, 15, 16, 17, 31, 47, 48, 51, 52, valid.length - 1}) {
            TrackInput input = new TrackInput(Arrays.copyOf(valid, size));
            String failure = failure(() -> NmbModelLoader.loadModel(manager(Map.of(LOCATION, input)), LOCATION, null));
            require(input.closed == (size < 16 ? 0 : 1), "NMB header/finally ownership boundary changed at " + size);
            records.add("nmb-short-" + size + "\t" + failure + "\t" + input.closed);
        }
        byte[] negative = valid.clone(); Arrays.fill(negative, 48, 52, (byte) 0xFF);
        TrackInput input = new TrackInput(negative);
        records.add("nmb-negative-length\t" + failure(() -> NmbModelLoader.loadModel(manager(Map.of(LOCATION, input)), LOCATION, null)) + "\t" + input.closed);
        for (byte[] bytes : new byte[][] {valid, negative}) {
            TrackInput closeFailure = new TrackInput(bytes); closeFailure.failClose = true;
            Throwable error = thrown(() -> NmbModelLoader.loadModel(manager(Map.of(LOCATION, closeFailure)), LOCATION, null));
            require(error instanceof IOException && "close-failure".equals(error.getMessage()) && error.getCause() == null && error.getSuppressed().length == 0, "Close no longer replaces NMB decrypt failure");
            records.add("nmb-close-failure\t" + describe(error));
        }
        TrackInput invalidModel = new TrackInput(encrypt(new byte[] {0, 0, 0, 1}));
        records.add("nmb-invalid-model\t" + failure(() -> NmbModelLoader.loadModel(manager(Map.of(LOCATION, invalidModel)), LOCATION, null)) + "\t" + invalidModel.closed);
        TrackInput atlasInput = new TrackInput(valid);
        require(failure(() -> NmbModelLoader.loadModel(manager(Map.of(LOCATION, atlasInput)), LOCATION, new AtlasManager() { @Override public void applyToMesh(RawMesh mesh) { throw new IllegalStateException("atlas"); } })).startsWith("IllegalStateException"), "Atlas failure wrapped as crypto error");
        require(atlasInput.closed == 1, "Atlas failure leaked NMB stream");
        records.add("nmb-missing\t" + failure(() -> NmbModelLoader.loadModel(manager(Map.of()), LOCATION, null)));
        records.add("nmb-null-manager\t" + failure(() -> NmbModelLoader.loadModel(null, LOCATION, null)));
        TrackInput noLocation = new TrackInput(valid);
        RawModel nullable = NmbModelLoader.loadModel(manager(Collections.singletonMap(null, noLocation)), null, null);
        require(nullable.sourceLocation == null, "Null source location rejected");
        for (int limit : new int[] {0, 7, 10, 48, 53}) {
            TrackOutput output = new TrackOutput(limit);
            records.add("nmb-output-failure-" + limit + "\t" + failure(() -> NmbModelLoader.serializeModel(model(), output, true)) + "\t" + output.bytes.size() + ":" + output.closed + ":" + output.flushed);
        }
        RawModel failing = new RawModel() { @Override public void serializeTo(DataOutputStream stream) throws IOException { throw new IOException("model-failure"); } };
        TrackOutput output = new TrackOutput(-1);
        Throwable error = thrown(() -> NmbModelLoader.serializeModel(failing, output, false));
        require("model-failure".equals(error.getMessage()) && error.getCause() == null && output.bytes.size() == 0, "Model failure wrapped or wrote header");
        records.add("nmb-null-output\t" + failure(() -> NmbModelLoader.serializeModel(model(), null, false)));
        records.add("nmb-null-model\t" + failure(() -> NmbModelLoader.serializeModel(null, output, false)));
    }

    private static void objInputs() throws Exception {
        String[] options = {"_", "body", "body#interior", "body#LIGHTTRANSLUCENT,FLIPV", "body#exterior,flipv=1", "body#exterior,flipv=0", "body#exterior,flipv=1,", "body#exterior,flipv=1=0", "body#exterior,,flipv", "body#", "body#,,,", "body#unknown"};
        for (String option : options) {
            TrackInput obj = text(GEOMETRY + "usemtl " + option + "\n" + QUAD), mtl = text("");
            records.add("obj-option-" + option + "\t" + result(() -> ObjModelLoader.loadModel(obj, mtl, LOCATION, null)));
            require(obj.closed == 0 && mtl.closed == 0, "OBJ stream overload took ownership");
        }
        String materials = "newmtl body#interior\nKd 0.25 0.5 0.75\nd 0.5\nmap_Kd ../Textures/Body.TGA\n";
        for (String mtl : new String[] {materials, "newmtl unrelated\nKd 1 0 0\n", "newmtl body#interior\n", materials + "newmtl body#interior\nKd 0.1 0.2 0.3\nd 1\nmap_Kd test:override.png\n"}) {
            records.add("obj-material\t" + result(() -> ObjModelLoader.loadModel(text(GEOMETRY + "usemtl body#interior\n" + QUAD), text(mtl), LOCATION, null)));
        }
        for (String geometry : new String[] {"", "v 0 0 0\nv 1 0 0\nv 0 1 0\nf 1 2 3\n", GEOMETRY + "f -4/-4/-1 -3/-3/-1 -2/-2/-1\n", GEOMETRY + "f 1 1 1\n", "v invalid 0 0\n", GEOMETRY + "f 0 2 3\n", GEOMETRY + "f 1 2 99\n"}) {
            records.add("obj-geometry\t" + result(() -> ObjModelLoader.loadModel(text("usemtl _\n" + geometry), text(""), LOCATION, null)));
        }
        String groups = GEOMETRY + "g Cab-A\nusemtl body#interior\n" + QUAD + "g Wheel:B\nusemtl _\n" + TRIANGLE;
        Map<String, RawModel> grouped = ObjModelLoader.loadModels(text(groups), text(""), LOCATION, null);
        records.add("obj-groups\t" + groups(grouped, false)); grouped.put("test", new RawModel()); require(grouped.size() >= 2, "OBJ groups no longer mutable");
        records.add("obj-null-location\t" + result(() -> ObjModelLoader.loadModel(text(groups), text(""), null, null)));
        records.add("obj-groups-null-location\t" + failure(() -> ObjModelLoader.loadModels(text(groups), text(""), null, null)));
        records.add("obj-null-input\t" + failure(() -> ObjModelLoader.loadModel((InputStream) null, text(""), LOCATION, null)));
        TrackInput readBeforeNull = text(GEOMETRY + TRIANGLE);
        records.add("obj-null-mtl\t" + failure(() -> ObjModelLoader.loadModel(readBeforeNull, null, LOCATION, null)) + "\t" + readBeforeNull.consumed + ":" + readBeforeNull.closed);
        List<String> atlasEvents = new ArrayList<>();
        RawModel atlasModel = ObjModelLoader.loadModel(text(groups), text(""), LOCATION, new AtlasManager() { @Override public void applyToMesh(RawMesh mesh) { atlasEvents.add(mesh.materialProp.texture + ":" + mesh.faces.size()); mesh.vertices.getFirst().position.add(2, 0, 0); } });
        records.add("obj-atlas\t" + digest(atlasModel) + "\t" + atlasEvents);
        TrackInput failureInput = text(groups); failureInput.failAfter = 5;
        records.add("obj-read-failure\t" + failure(() -> ObjModelLoader.loadModel(failureInput, text(""), LOCATION, null)) + "\t" + failureInput.closed);
    }
    private static void objResources() throws Exception {
        // The bundled reader replaces the previous mtllib declaration; it does not accumulate it.
        String objText = "mtllib first.mtl\nmtllib second.mtl\n" + GEOMETRY + "g Cab\nusemtl body\n" + QUAD;
        for (boolean multiple : new boolean[] {false, true}) {
            TrackInput obj = text(objText), first = text("newmtl body\nKd 0.2 0.3 0.4\nd 1\nmap_Kd first.png\n"), second = text("newmtl body\nKd 0.5 0.6 0.7\nd 0.8\nmap_Kd second.png\n");
            var manager = manager(Map.of(LOCATION, obj, Identifier.parse("test:models/first.mtl"), first, Identifier.parse("test:models/second.mtl"), second));
            records.add("obj-resource-" + multiple + "\t" + (multiple ? groups(ObjModelLoader.loadModels(manager, LOCATION, null), false) : digest(ObjModelLoader.loadModel(manager, LOCATION, null))));
            require(obj.opens == 1 && first.opens == 0 && second.opens == 1 && obj.closed == 0 && first.closed == 0 && second.closed == 0, "Resource stream ownership or MTL resolution changed");
        }
        records.add("obj-resource-missing\t" + result(() -> ObjModelLoader.loadModel(manager(Map.of()), LOCATION, null)));
        Path directory = Files.createTempDirectory("ante-model-loader-"); Path external = directory.resolve("Fixture.OBJ");
        Files.writeString(external, GEOMETRY + "g Cab-A\nusemtl body\n" + QUAD);
        try {
            var models = ObjModelLoader.loadExternalModels(external.toString(), null);
            records.add("obj-external\t" + groups(models, true));
            Files.move(external, directory.resolve("Renamed.OBJ")); // Windows also verifies the owned file handle is closed.
            Files.delete(directory.resolve("Renamed.OBJ"));
        } finally { Files.deleteIfExists(external); Files.delete(directory); }
        records.add("obj-external-null\t" + failure(() -> ObjModelLoader.loadExternalModels(null, null)));
    }
    private static void objExport() throws Exception {
        Locale old = Locale.getDefault(); Path directory = Files.createTempDirectory("ante-obj-export-");
        Path obj = directory.resolve("model.obj"), mtl = directory.resolve("model.mtl");
        try {
            for (Locale locale : List.of(Locale.US, Locale.GERMANY)) for (boolean normals : new boolean[] {false, true}) {
                Locale.setDefault(locale); LinkedHashMap<String, RawModel> models = new LinkedHashMap<>();
                models.put("Group-A", model()); models.put("Group-B", model());
                ObjModelLoader.saveModels(models, obj, mtl, normals);
                records.add("obj-export-" + locale + "-" + normals + "\t" + exported(obj) + "\t" + exported(mtl));
            }
            Locale.setDefault(Locale.US);
            for (String render : List.of("exterior", "exteriortranslucent", "interior", "interiortranslucent", "light", "lighttranslucent")) {
                RawModel model = model(); model.meshList.values().iterator().next().setRenderType(render);
                ObjModelLoader.saveModels(Map.of("One", model), obj, mtl, true);
                records.add("obj-export-render-" + render + "\t" + exported(mtl));
            }
            RawModel noTexture = model(); noTexture.meshList.values().iterator().next().materialProp.texture = null;
            ObjModelLoader.saveModels(Collections.singletonMap(null, noTexture), obj, mtl, false);
            records.add("obj-export-null-key-texture\t" + exported(obj) + "\t" + exported(mtl));
            records.add("obj-export-null-models\t" + failure(() -> ObjModelLoader.saveModels(null, obj, mtl, false)) + "\t" + exported(obj));
            RawModel badShader = model(); badShader.meshList.values().iterator().next().materialProp.shaderName = null;
            records.add("obj-export-null-shader\t" + failure(() -> ObjModelLoader.saveModels(Map.of("Bad", badShader), obj, mtl, false)) + "\t" + exported(obj));
            records.add("obj-export-null-mtl\t" + failure(() -> ObjModelLoader.saveModels(Map.of(), obj, null, false)));
            records.add("obj-export-root-mtl\t" + failure(() -> ObjModelLoader.saveModels(Map.of(), obj, directory.getRoot(), false)));
            records.add("obj-export-second-open-failure\t" + failure(() -> ObjModelLoader.saveModels(Map.of(), obj, directory.resolve("missing/model.mtl"), false)) + "\t" + Files.size(obj));
        } finally { Locale.setDefault(old); Files.deleteIfExists(obj); Files.deleteIfExists(mtl); Files.delete(directory); }
    }

    private static String exported(Path path) throws IOException {
        String value = Files.readString(path).replace("\r\n", "\n");
        require(value.startsWith("# Generated by YLM-ANTE ") || value.startsWith("# Generated by MTR-ANTE ") || value.isEmpty(), "Missing export version heading");
        // Only the display brand changed; keep comparing geometry with the frozen Java export.
        return Base64.getEncoder().encodeToString(value.replaceFirst("# Generated by (?:MTR|YLM)-ANTE [^\\n]*", "# Generated by MTR-ANTE <version>").getBytes(StandardCharsets.UTF_8));
    }
    private static String digest(RawModel model) throws Exception { return model.sourceLocation + ":" + hex(plaintext(model)); }
    private static String groups(Map<String, RawModel> models, boolean external) throws Exception {
        List<String> values = new ArrayList<>();
        for (var entry : new TreeMap<>(models).entrySet()) {
            RawModel model = entry.getValue(); String source = String.valueOf(model.sourceLocation);
            if (external) { require(source.startsWith("mtrsteamloco-external:"), "Wrong external namespace"); source = "mtrsteamloco-external:<dir>/" + source.substring(source.indexOf("fixture.obj/")); }
            values.add(entry.getKey() + "=" + source + ":" + hex(plaintext(model)));
        }
        return values.toString();
    }
    private static ResourceManager manager(Map<Identifier, TrackInput> streams) { return manager(streams, null); }
    private static ResourceManager manager(Map<Identifier, TrackInput> streams, List<TrackInput> stack) {
        return (ResourceManager) Proxy.newProxyInstance(ModelLoadersCompatibilityCheck.class.getClassLoader(), new Class<?>[] {ResourceManager.class}, (proxy, method, args) -> {
            TrackInput input = args == null || args.length == 0 ? null : streams.get(args[0]);
            return switch (method.getName()) {
                case "getResourceStack" -> stack == null ? input == null ? List.of() : List.of(resource(input)) : stack.stream().map(ModelLoadersCompatibilityCheck::resource).toList();
                case "getResource" -> Optional.ofNullable(input == null ? null : resource(input));
                case "toString" -> "ModelLoaderResourceManager";
                default -> throw new UnsupportedOperationException(method.toString());
            };
        });
    }
    private static Resource resource(TrackInput input) { return new Resource(null, () -> { input.opens++; return input; }); }
    private static TrackInput text(String text) { return new TrackInput(text.getBytes(StandardCharsets.US_ASCII)); }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }
    private static boolean isKotlin(Class<?> type) { return Arrays.stream(type.getDeclaredAnnotations()).anyMatch(a -> a.annotationType().getName().equals("kotlin.Metadata")); }
    private static String result(ModelCall action) throws Exception { try { return digest(action.run()); } catch (Exception exception) { return describe(exception); } }
    private static String failure(Call action) { try { action.run(); return "OK"; } catch (Exception exception) { return describe(exception); } }
    private static Throwable thrown(Call action) { try { action.run(); throw new AssertionError("Expected failure"); } catch (Exception exception) { return exception; } }
    private static String describe(Throwable error) { return error.getClass().getSimpleName() + ":cause=" + (error.getCause() == null ? "none" : error.getCause().getClass().getSimpleName()) + ":suppressed=" + error.getSuppressed().length; }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    @FunctionalInterface private interface Call { void run() throws Exception; }
    @FunctionalInterface private interface ModelCall { RawModel run() throws Exception; }
    private static final class TrackInput extends InputStream {
        private final ByteArrayInputStream input; int opens, consumed, closed, failAfter = -1; boolean failClose;
        TrackInput(byte[] bytes) { input = new ByteArrayInputStream(bytes); }
        @Override public int read() throws IOException { if (failAfter >= 0 && consumed >= failAfter) throw new IOException("read-failure"); int result = input.read(); if (result >= 0) consumed++; return result; }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (failAfter >= 0 && consumed >= failAfter) throw new IOException("read-failure");
            int count = input.read(bytes, offset, failAfter < 0 ? length : Math.min(length, failAfter - consumed)); if (count > 0) consumed += count; return count;
        }
        @Override public void close() throws IOException { closed++; if (failClose) throw new IOException("close-failure"); }
    }
    private static final class TrackOutput extends OutputStream {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(); final int limit; int closed, flushed;
        TrackOutput(int limit) { this.limit = limit; }
        @Override public void write(int value) throws IOException { if (limit >= 0 && bytes.size() >= limit) throw new IOException("write-failure"); bytes.write(value); }
        @Override public void flush() { flushed++; }
        @Override public void close() { closed++; }
    }
    private static class HidingNmb extends NmbModelLoader { static int serializeCalls; public static void serializeModel(RawModel model, OutputStream output, boolean raw) { serializeCalls++; } }
    private static class HidingObj extends ObjModelLoader { static int loadCalls; public static RawModel loadModel(InputStream obj, InputStream mtl, Identifier location, AtlasManager atlas) { loadCalls++; return null; } }
}
