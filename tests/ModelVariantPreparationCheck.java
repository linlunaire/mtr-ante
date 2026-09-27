package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.model.Model;
import cn.zbx1425.sowcer.vertex.VertAttrState;
import cn.zbx1425.sowcerext.model.*;
import cn.zbx1425.sowcerext.reuse.AtlasManager;
import cn.zbx1425.sowcerext.reuse.ModelManager;
import com.google.gson.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import org.objectweb.asm.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Actual registry entrypoints; only resource acquisition/client fields/GPU uploads are adapted. */
public final class ModelVariantPreparationCheck {
    private static final String JAVA_SHA = "DB3662A772AD746BCDBFE81B2E44FED09EA084D73F11B29022BE9B11E9F4D79C";
    private static final String PREFIX = "cn.zbx1425.mtrsteamloco.data.";
    private static final String MODULE = "cn.zbx1425.sowcerext.model.ModelVariantPreparation";
    private static final RuntimeException GPU_CAPTURE = new RuntimeException("headless GPU capture");
    private static final List<String> EVENTS = new ArrayList<>();
    public static ModelManager modelManager;
    public static AtlasManager atlasManager;
    public static ResourceManager resourceManager;
    private static Template template;
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        boolean baseline = Arrays.asList(args).contains("--java-baseline"), record = Arrays.asList(args).contains("--record");
        Path moduleSource = source;
        for (String argument : args) if (argument.startsWith("--module-source=")) moduleSource = Path.of(argument.substring("--module-source=".length())).toRealPath();
        if (baseline || record) {
            require(baseline && Files.isRegularFile(source), "Only the original Java release can run or record this baseline");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(source)) { byte[] bytes = new byte[8192]; int count; while ((count = input.read(bytes)) >= 0) digest.update(bytes, 0, count); }
            require(HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(JAVA_SHA), "Wrong original Java SHA-256");
        }
        for (Class<?> dependency : List.of(RawModel.class, RawMesh.class, Vertex.class, Face.class,
                MaterialProp.class, Vector3f.class, ModelManager.class, AtlasManager.class)) {
            Path actual = Path.of(dependency.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath();
            require(actual.equals(source) || (Files.isDirectory(source) && actual.equals(moduleSource)),
                    "Geometry/resource dependency escaped selected artifact: " + dependency.getName() + " at " + actual);
        }
        ClassLoader loader = registryLoader(source, moduleSource);
        List<String> records = new ArrayList<>();
        for (String kind : List.of("EyeCandy", "Rail")) {
            Class<?> registry = Class.forName(PREFIX + (kind.equals("EyeCandy") ? "EyeCandyRegistry" : "RailModelRegistry"), true, loader);
            require(registry.getClassLoader() == loader, "Registry escaped selected artifact");
            Method method = registry.getDeclaredMethod("loadFromJson", ResourceManager.class, String.class, JsonObject.class, String.class); method.setAccessible(true);
            List<Case> cases = cases();
            for (int i = 0; i < cases.size(); i++) {
                Case scenario = cases.get(i); template = template(); String before = model(template); EVENTS.clear();
                modelManager = manager(); atlasManager = new AtlasManager() { @Override public void load(ResourceManager manager, Identifier id) { EVENTS.add("atlas:" + id); } };
                JsonObject definition = scenario.definition.deepCopy(); String inputJson = definition.toString();
                Throwable error = null;
                try { method.invoke(null, resourceManager, scenario.key, definition, "group"); }
                catch (InvocationTargetException invocation) { error = invocation.getCause(); }
                require(definition.toString().equals(inputJson), kind + " modified the definition");
                require(model(template).equals(before), kind + " modified the cached template");
                require(template.copies <= 1, "Registry copied more than once");
                if (template.lastCopy != null) assertExclusive(template, template.lastCopy);
                records.add(kind + "-" + i + "\t" + describe(error) + "\t" + EVENTS + "\t" + template.copies + "\t" + (template.lastCopy == null ? "none" : model(template.lastCopy)));
            }
        }
        if (record) Files.writeString(Path.of(args[0]), String.join("\n", records) + "\n");
        else {
            List<String> expected = Files.readAllLines(Path.of(args[0])); require(expected.size() == records.size(), "Registry oracle size changed");
            for (int i = 0; i < records.size(); i++) require(expected.get(i).equals(records.get(i)), "Registry differs at " + i + "\nJava: " + expected.get(i) + "\nActual: " + records.get(i));
        }
        if (!baseline) interfaceChecks(moduleSource, Files.readAllLines(Path.of(args[0])));
        System.out.println("PASS: model variant preparation, " + assertions + " assertions / " + records.size() + " original-Java registry records; definition/geometry order, one-copy ownership and failure isolation (no GPU)");
    }

    private static List<Case> cases() {
        List<Case> result = new ArrayList<>();
        for (int mask = 0; mask < 64; mask++) {
            JsonObject definition = definition();
            if ((mask & 1) != 0) definition.addProperty("textureId", "test:textures/repaint.png");
            if ((mask & 2) != 0) definition.addProperty("flipV", true);
            if ((mask & 4) != 0) definition.add("translation", json("[0.25,-0.5,1.5]"));
            if ((mask & 8) != 0) definition.add("rotation", json("[17,-90,31]"));
            if ((mask & 16) != 0) definition.add("scale", json("[-2,0.5,1.25]"));
            if ((mask & 32) != 0) definition.add("mirror", json("[true,false,true]"));
            result.add(new Case("variant", definition));
        }
        for (String overrides : List.of(
                "{\"atlasIndex\":\"test:atlas.json\",\"flipV\":false}",
                "{\"translation\":[-0.0,0.0,0.0],\"scale\":[0,-1,1],\"mirror\":[true,true,true]}",
                "{\"textureId\":\"INVALID:bad\"}", "{\"textureId\":null}", "{\"flipV\":null}",
                "{\"translation\":[1,2]}", "{\"translation\":null}",
                "{\"translation\":[1,2,3],\"rotation\":[15,25]}",
                "{\"rotation\":[10,20,\"bad\"]}", "{\"rotation\":false}",
                "{\"rotation\":[15,30,60],\"scale\":[2,3]}",
                "{\"mirror\":[true,false]}", "{\"translation\":[\"NaN\",\"Infinity\",\"-Infinity\"]}",
                "{\"rotation\":[\"NaN\",0,0],\"scale\":[-0.0,1,1]}")) {
            JsonObject definition = definition(); json(overrides).getAsJsonObject().entrySet().forEach(entry -> definition.add(entry.getKey(), entry.getValue())); result.add(new Case("variant", definition));
        }
        for (String key : Arrays.asList(null, "", "a/b", "Upper", "with space")) result.add(new Case(key, definition()));
        return result;
    }
    private static JsonObject definition() { return json("{\"model\":\"test:models/shared.obj\",\"name\":\"test.variant\"}").getAsJsonObject(); }
    private static JsonElement json(String value) { return JsonParser.parseString(value); }

    private static Template template() {
        Template result = new Template(); result.sourceLocation = Identifier.parse("test:models/shared.obj");
        for (int part = 0; part < 2; part++) {
            MaterialProp material = new MaterialProp(); material.texture = Identifier.parse(part == 0 ? "test:textures/default.png" : "test:textures/unchanged.png");
            RawMesh mesh = new RawMesh(material); mesh.setRenderType(part == 0 ? "exterior" : "interiortranslucent"); material.attrState.color = 0x81234567 + part;
            for (int i = 0; i < 3; i++) { Vertex vertex = new Vertex(new Vector3f((i & 1) + part * 2f, (i >> 1) - 0.25f, 0.5f), new Vector3f(0, 0, 1)); vertex.u = i * 0.25f; vertex.v = 0.125f + i * 0.25f; mesh.vertices.add(vertex); }
            mesh.faces.add(new Face(new int[] {0, 1, 2})); result.meshList.put(material, mesh);
        }
        return result;
    }
    private static ModelManager manager() {
        return new ModelManager() {
            @Override public RawModel loadRawModel(ResourceManager resources, Identifier location, AtlasManager atlas) { EVENTS.add("load:" + location); return template; }
            @Override public ModelCluster uploadVertArrays(RawModel raw) { require(raw == template.lastCopy, "Eye-candy uploaded a different variant"); EVENTS.add("gpu:cluster"); throw GPU_CAPTURE; }
            @Override public Model uploadModel(RawModel raw) { require(raw == template.lastCopy, "Rail uploaded a different variant"); EVENTS.add("gpu:model"); throw GPU_CAPTURE; }
        };
    }

    private static ClassLoader registryLoader(Path source, Path moduleSource) {
        return new ClassLoader(ModelVariantPreparationCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!List.of(PREFIX + "EyeCandyRegistry", PREFIX + "RailModelRegistry", PREFIX + "RailModelProperties").contains(name)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> existing = findLoadedClass(name);
                    if (existing == null) {
                        try {
                            String entry = name.replace('.', '/') + ".class";
                            Path selected = Files.isDirectory(source) && !Files.exists(source.resolve(entry)) ? moduleSource : source;
                            byte[] original = read(selected, entry); ClassWriter writer = new ClassWriter(0); int[] replaced = {0};
                            new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                                @Override public MethodVisitor visitMethod(int access, String methodName, String descriptor, String signature, String[] exceptions) {
                                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, methodName, descriptor, signature, exceptions)) {
                                        @Override public void visitFieldInsn(int opcode, String owner, String field, String type) {
                                            if (opcode == Opcodes.GETSTATIC && ((owner.equals("cn/zbx1425/mtrsteamloco/MainClient") && (field.equals("modelManager") || field.equals("atlasManager"))) || (owner.equals("cn/zbx1425/mtrsteamloco/render/integration/MtrModelRegistryUtil") && field.equals("resourceManager")))) {
                                                owner = ModelVariantPreparationCheck.class.getName().replace('.', '/'); replaced[0]++;
                                            }
                                            super.visitFieldInsn(opcode, owner, field, type);
                                        }
                                    };
                                }
                            }, 0);
                            require(replaced[0] > 0, "Expected external singleton seam in " + name);
                            byte[] adapted = writer.toByteArray(); existing = defineClass(name, adapted, 0, adapted.length);
                        } catch (IOException exception) { throw new ClassNotFoundException(name, exception); }
                    }
                    if (resolve) resolveClass(existing); return existing;
                }
            }
        };
    }
    private static byte[] read(Path source, String entry) throws IOException {
        if (Files.isDirectory(source)) return Files.readAllBytes(source.resolve(entry));
        try (JarFile jar = new JarFile(source.toFile()); InputStream input = jar.getInputStream(jar.getJarEntry(entry))) { return input.readAllBytes(); }
    }

    private static void interfaceChecks(Path moduleSource, List<String> oracle) throws Exception {
        Class<?> module = Class.forName(MODULE), policy = Class.forName(MODULE + "$Geometry");
        require(Path.of(module.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(moduleSource), "Wrong preparation module provenance");
        require(Arrays.stream(module.getDeclaredAnnotations()).anyMatch(annotation -> annotation.annotationType().getName().equals("kotlin.Metadata")), "Preparation module is not Kotlin");
        Object apply = policy.getField("APPLY").get(null), ignore = policy.getField("IGNORE").get(null);
        Method prepare = module.getMethod("prepare", RawModel.class, String.class, JsonObject.class, policy);
        for (Object geometry : List.of(apply, ignore)) for (int i = 0; i < 64; i++) {
            Template base = template(); String before = model(base); JsonObject definition = cases().get(i).definition; String jsonBefore = definition.toString();
            EVENTS.clear(); RawModel first = prepare(prepare, base, "variant", definition, geometry);
            require(base.copies == 1 && model(base).equals(before), "Preparation must deep-copy exactly once without mutating template");
            require(first.sourceLocation.toString().equals("test:models/shared.obj/variant"), "Variant upload identity changed");
            // Eye-candy captures immediately after preparation, before any consumer-side changes.
            // IGNORE is the corresponding texture/UV-only original-Java case, not a new algorithm.
            String javaPreparedModel = oracle.get(geometry == apply ? i : i & 3).split("\t", 5)[4];
            require(model(first).equals(javaPreparedModel), "Public preparation interface differs from original Java model");
            assertExclusive(base, first);
            String firstBefore = model(first); RawModel same = prepare(prepare, base, "variant", definition, geometry);
            require(model(same).equals(firstBefore) && base.copies == 2, "Repeated preparation accumulated transforms");
            RawModel other = prepare(prepare, base, "two", definition, geometry); assertExclusive(first, other);
            other.meshList.values().iterator().next().vertices.getFirst().position.add(900, 800, 700);
            other.meshList.values().iterator().next().materialProp.texture = null;
            require(model(first).equals(firstBefore) && model(base).equals(before), "Variants share mutable geometry/materials");
            require(definition.toString().equals(jsonBefore), "Preparation mutated definition");
        }
        for (Case scenario : cases().subList(64, cases().size())) {
            Template base = template(); String before = model(base); EVENTS.clear();
            try { prepare(prepare, base, scenario.key, scenario.definition, apply); }
            catch (InvocationTargetException expected) { require(expected.getCause() instanceof RuntimeException, "Unexpected checked preparation error"); }
            require(base.copies == 1 && model(base).equals(before), "Failure must not rewrite template or copy twice");
        }
        Template base = template(); base.failCopy = true; EVENTS.clear();
        String beforeFailure = model(base);
        try { prepare(prepare, base, "one", definition(), apply); throw new AssertionError("Copy failure swallowed"); }
        catch (InvocationTargetException error) { require(error.getCause() == base.copyFailure && base.copies == 1, "Copy failure identity/count changed"); }
        require(model(base).equals(beforeFailure), "Copy failure changed template");
        JsonObject malformedGeometry = definition(); malformedGeometry.add("rotation", JsonNull.INSTANCE);
        require(prepare(prepare, template(), "rail", malformedGeometry, ignore) != null, "Rail policy inspected ignored geometry");
        Template retry = template(); JsonObject malformedRotation = definition(); malformedRotation.add("rotation", json("[10,20,\"bad\"]"));
        try { prepare(prepare, retry, "variant", malformedRotation, apply); throw new AssertionError("Malformed geometry accepted"); }
        catch (InvocationTargetException error) { require(error.getCause() instanceof NumberFormatException, "Malformed geometry failure changed"); }
        require(model(prepare(prepare, retry, "variant", definition(), apply)).equals(model(prepare(prepare, template(), "variant", definition(), apply)))
                && retry.copies == 2, "Failed partial preparation leaked into the next variant");
        Template withoutLocation = template(); withoutLocation.sourceLocation = null;
        require(prepare(prepare, withoutLocation, null, definition(), apply).sourceLocation.toString().equals("minecraft:null/null"), "Nullable source/key identity changed");
        Template nullDefinition = template(); String beforeNullDefinition = model(nullDefinition);
        try { prepare(prepare, nullDefinition, "variant", null, apply); throw new AssertionError("Null definition accepted"); }
        catch (InvocationTargetException error) { require(error.getCause() instanceof NullPointerException && nullDefinition.copies == 1, "Null definition copy/failure ordering changed"); }
        require(model(nullDefinition).equals(beforeNullDefinition), "Null definition changed template");
        for (Object geometry : List.of(apply, ignore)) {
            Template attributed = template();
            int[] callbackCalls = {0};
            java.util.function.Function<Matrix4f, Matrix4f> callback = matrix -> { callbackCalls[0]++; return matrix; };
            for (RawMesh mesh : attributed.meshList.values()) {
                VertAttrState state = mesh.materialProp.attrState;
                state.position = new Vector3f(1, 2, 3); state.normal = new Vector3f(4, 5, 6);
                state.matrixModel = callback; state.useMatixProcess = true;
            }
            RawModel first = prepare(prepare, attributed, "first", cases().get(63).definition, geometry);
            RawModel other = prepare(prepare, attributed, "other", cases().get(63).definition, geometry);
            require(attributed.copies == 2, "Attributed variants did not copy once per request");
            assertExclusive(attributed, first); assertExclusive(attributed, other); assertExclusive(first, other);
            for (RawModel model : List.of(attributed, first, other)) for (RawMesh mesh : model.meshList.values()) {
                VertAttrState state = mesh.materialProp.attrState;
                require(state.position.x() == 1 && state.position.y() == 2 && state.position.z() == 3
                        && state.normal.x() == 4 && state.normal.y() == 5 && state.normal.z() == 6, "Attribute vector values changed during preparation");
                require(state.matrixModel == callback && state.useMatixProcess, "Existing transform callback sharing contract changed");
            }
            for (RawMesh mesh : other.meshList.values()) {
                mesh.materialProp.attrState.position.add(100, 200, 300);
                mesh.materialProp.attrState.normal.add(400, 500, 600);
            }
            for (RawModel model : List.of(attributed, first)) for (RawMesh mesh : model.meshList.values()) {
                VertAttrState state = mesh.materialProp.attrState;
                require(state.position.x() == 1 && state.position.y() == 2 && state.position.z() == 3
                        && state.normal.x() == 4 && state.normal.y() == 5 && state.normal.z() == 6, "Attribute vector mutation escaped its variant");
            }
            require(callbackCalls[0] == 0, "Preparation invoked a render-time transform callback");
        }
    }
    private static RawModel prepare(Method method, RawModel template, String key, JsonObject definition, Object geometry) throws Exception { return (RawModel) method.invoke(null, template, key, definition, geometry); }
    private static void assertExclusive(RawModel first, RawModel second) {
        require(first != second && first.meshList != second.meshList, "Model aliases template");
        Set<Object> mutable = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : first.meshList.entrySet()) {
            mutable.add(entry.getKey()); mutable.add(entry.getKey().attrState);
            VertAttrState state = entry.getKey().attrState;
            if (state.position != null) mutable.add(state.position);
            if (state.normal != null) mutable.add(state.normal);
            RawMesh mesh = entry.getValue(); mutable.add(mesh); mutable.add(mesh.vertices); mutable.add(mesh.faces);
            for (Vertex vertex : mesh.vertices) { mutable.add(vertex); mutable.add(vertex.position); mutable.add(vertex.normal); }
            for (Face face : mesh.faces) { mutable.add(face); mutable.add(face.vertices); }
        }
        for (var entry : second.meshList.entrySet()) {
            RawMesh mesh = entry.getValue(); require(!mutable.contains(entry.getKey()) && !mutable.contains(entry.getKey().attrState) && !mutable.contains(mesh) && !mutable.contains(mesh.vertices) && !mutable.contains(mesh.faces), "Shared mesh/material storage");
            VertAttrState state = entry.getKey().attrState;
            require((state.position == null || !mutable.contains(state.position)) && (state.normal == null || !mutable.contains(state.normal)), "Shared attribute vector storage");
            for (Vertex vertex : mesh.vertices) require(!mutable.contains(vertex) && !mutable.contains(vertex.position) && !mutable.contains(vertex.normal), "Shared vertex storage");
            for (Face face : mesh.faces) require(!mutable.contains(face) && !mutable.contains(face.vertices), "Shared face storage");
        }
    }
    private static String model(RawModel model) throws Exception {
        List<String> meshes = new ArrayList<>();
        for (RawMesh mesh : model.meshList.values()) { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); mesh.serializeTo(new DataOutputStream(bytes)); meshes.add(HexFormat.of().formatHex(bytes.toByteArray())); }
        Collections.sort(meshes); return model.sourceLocation + ":" + meshes;
    }
    private static String describe(Throwable error) { return error == null ? "OK" : error == GPU_CAPTURE ? "GPU_CAPTURE" : error.getClass().getSimpleName(); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    private record Case(String key, JsonObject definition) { }
    public static final class Template extends RawModel {
        int copies; RawModel lastCopy; boolean failCopy; final RuntimeException copyFailure = new RuntimeException("copy failure");
        @Override public RawModel copy() { copies++; EVENTS.add("copy"); if (failCopy) throw copyFailure; return lastCopy = super.copy(); }
    }
}
