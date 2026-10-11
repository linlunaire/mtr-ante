package cn.zbx1425.mtrsteamloco.data;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.model.Model;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import cn.zbx1425.sowcer.vertex.VertAttrType;
import cn.zbx1425.sowcerext.model.RawMesh;
import cn.zbx1425.sowcerext.model.RawModel;
import cn.zbx1425.sowcerext.model.Vertex;
import cn.zbx1425.sowcerext.reuse.ModelManager;
import cn.zbx1425.mtrsteamloco.scripting.ScriptHolderBase;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import org.objectweb.asm.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

/** Actual constructor/geometry/manager; only the client singleton and GPU upload are adapted. */
public final class RailModelPropertiesCompatibilityCheck {
    public static ModelManager modelManager;
    private static final String TARGET = "cn.zbx1425.mtrsteamloco.data.RailModelProperties";
    private static final RuntimeException FAILURE = new RuntimeException("rail model fixture");
    private static final List<String> EVENTS = new ArrayList<>();
    private static Constructor<?> constructor;
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        boolean kotlin = Files.isDirectory(source) || Arrays.asList(args).contains("--kotlin");
        String entry = TARGET.replace('.', '/') + ".class";
        byte[] original;
        if (Files.isDirectory(source)) original = Files.readAllBytes(source.resolve(entry));
        else try (JarFile jar = new JarFile(source.toFile()); var input = jar.getInputStream(jar.getJarEntry(entry))) { original = input.readAllBytes(); }
        boolean[] metadata = {false}; int[] adapters = {0};
        ClassWriter writer = new ClassWriter(0);
        new ClassReader(original).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals("Lkotlin/Metadata;")) metadata[0] = true;
                return super.visitAnnotation(descriptor, visible);
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                        if (opcode == Opcodes.GETSTATIC && owner.equals("cn/zbx1425/mtrsteamloco/MainClient") && name.equals("modelManager") && descriptor.equals("Lcn/zbx1425/sowcerext/reuse/ModelManager;")) {
                            owner = RailModelPropertiesCompatibilityCheck.class.getName().replace('.', '/'); adapters[0]++;
                        }
                        super.visitFieldInsn(opcode, owner, name, descriptor);
                    }
                };
            }
        }, 0);
        require(metadata[0] == kotlin, "Wrong selected language");
        require(adapters[0] == 1, "Client manager seam changed");
        ClassLoader loader = new ClassLoader(RailModelPropertiesCompatibilityCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(TARGET)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) { byte[] bytes = writer.toByteArray(); type = defineClass(name, bytes, 0, bytes.length); }
                    if (resolve) resolveClass(type); return type;
                }
            }
        };
        constructor = Class.forName(TARGET, true, loader).getConstructor(String.class, MutableComponent.class,
                RawModel.class, float.class, float.class, ScriptHolderBase.class, String.class);
        List<String> records = new ArrayList<>();
        for (String key : Arrays.asList(null, "", "key/part")) for (String group : Arrays.asList(null, "", "Group")) {
            modelManager = null;
            Object result = make(key, null, null, Float.NaN, -0.0f, group);
            require(get(result, "rawModel") == null && get(result, "uploadedModel") == null && get(result, "script") == null && get(result, "name") == null, "Null-model defaults changed");
            require(get(result, "boundingBox").equals(0L) && Float.isNaN((float) get(result, "repeatInterval")) && bits((float) get(result, "yOffset")).equals("80000000"), "Null-model scalar bits changed");
            String path = (String) get(result, "path");
            result.getClass().getField("key").set(result, "changed"); result.getClass().getField("group").set(result, null);
            require(get(result, "path").equals(path), "Precomputed path became reactive");
            for (String field : List.of("path", "name", "key", "group", "rawModel", "uploadedModel", "boundingBox", "script")) result.getClass().getField(field).set(result, null);
            records.add("null\t" + path);
        }
        float[] edges = {0f, -0f, -12f, 0.75f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int index = 0; index < 64 + edges.length; index++) {
            modelManager = new ModelManager(); EVENTS.clear();
            RawProbe raw = new RawProbe();
            MaterialProp material = new MaterialProp(); material.attrState.color = 0x12345678; material.attrState.lightmapUV = 777;
            RawMesh mesh = new RawMesh(material); raw.meshList.put(material, mesh);
            Random random = new Random(262111L + index);
            int count = index < edges.length ? (index == 0 ? 0 : 3) : 1 + index;
            for (int vertex = 0; vertex < count; vertex++) mesh.vertices.add(new Vertex(new Vector3f(random.nextFloat() * 20 - 10, index < edges.length ? edges[index] : random.nextFloat() * 40 - 20, random.nextFloat() * 6), new Vector3f(0f, 1f, 0f)));
            float offset = index < edges.length ? edges[index] : random.nextFloat() * 8 - 4;
            float repeat = index % 2 == 0 ? -0f : 3.25f;
            var name = Component.literal("rail");
            Object result = make("rail", name, raw, repeat, offset, "group");
            require(get(result, "name") == name && get(result, "rawModel") == raw && get(result, "uploadedModel") == raw.uploaded, "Constructor lost reference identity");
            require(bits((float) get(result, "repeatInterval")).equals(bits(repeat)) && bits((float) get(result, "yOffset")).equals(bits(offset)), "Constructor changed scalar bits");
            require(EVENTS.equals(List.of("clear:COLOR", "rotate:3f13b646:3c8efa35", "upload")), "Transformation/upload order changed: " + EVENTS);
            require(material.attrState.color == null && material.attrState.lightmapUV == 777, "Attribute clearing affected unrelated state");
            require(raw.uploads == 1 && modelManager.uploadedModels.containsValue(raw.uploaded), "Real manager upload/cache changed");
            StringBuilder geometry = new StringBuilder();
            for (Vertex vertex : mesh.vertices) geometry.append(bits(vertex.position.x())).append(':').append(bits(vertex.position.y())).append(':').append(bits(vertex.position.z())).append(':').append(bits(vertex.normal.x())).append(':').append(bits(vertex.normal.y())).append(':').append(bits(vertex.normal.z())).append(';');
            records.add("geometry-" + index + "\t" + Long.toHexString((long) get(result, "boundingBox")) + ":" + geometry);
        }
        modelManager = new ModelManager(); EVENTS.clear();
        RawProbe cached = new RawProbe(); cached.sourceLocation = Identifier.parse("ante:cached");
        Object first = make("a", null, cached, 1f, 0f, "g"), second = make("b", null, cached, 2f, 0f, "g");
        require(get(first, "uploadedModel") == get(second, "uploadedModel") && cached.uploads == 1 && EVENTS.size() == 5, "Cached upload no longer retains repeated raw transformations");
        records.add("cache\t" + EVENTS);
        for (String failure : List.of("clear", "rotate", "upload")) {
            modelManager = new ModelManager(); EVENTS.clear(); RawProbe raw = new RawProbe(); raw.failure = failure;
            try { make(null, null, raw, 1f, 2f, null); throw new AssertionError("Failure swallowed"); }
            catch (InvocationTargetException error) { require(error.getCause() == FAILURE, "Failure identity changed"); }
            records.add("failure-" + failure + "\t" + EVENTS);
        }
        String actual = String.join("\n", records) + "\n";
        if (Arrays.asList(args).contains("--record")) { require(!kotlin, "Record only original Java"); Files.writeString(Path.of(args[0]), actual); }
        else require(Files.readString(Path.of(args[0])).replace("\r\n", "\n").equals(actual), "Rail-model properties differ from original Java");
        System.out.println("PASS: rail-model properties, " + assertions + " assertions / " + records.size() + " Java records; actual geometry, nullable fields, upload ordering/cache and failure identity (no GPU)");
    }
    private static Object make(String key, MutableComponent name, RawModel raw, float repeat, float offset, String group) throws Exception {
        return constructor.newInstance(key, name, raw, repeat, offset, null, group);
    }
    private static Object get(Object value, String field) throws Exception { return value.getClass().getField(field).get(value); }
    private static String bits(float value) { return Integer.toHexString(Float.floatToIntBits(value)); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
    public static final class RawProbe extends RawModel {
        final Model uploaded = new Model(); int uploads; String failure = "";
        @Override public void clearAttrState(VertAttrType type) { EVENTS.add("clear:" + type); if (failure.equals("clear")) throw FAILURE; super.clearAttrState(type); }
        @Override public void applyRotation(Vector3f axis, float angle) {
            EVENTS.add("rotate:" + bits(axis.x()) + ":" + bits(angle));
            require(bits(axis.x()).equals(bits(axis.y())) && bits(axis.y()).equals(bits(axis.z())), "Rotation axis changed");
            if (failure.equals("rotate")) throw FAILURE; super.applyRotation(axis, angle);
        }
        @Override public Model upload(VertAttrMapping mapping) { EVENTS.add("upload"); uploads++; if (failure.equals("upload")) throw FAILURE; require(mapping == ModelManager.DEFAULT_MAPPING, "Upload mapping changed"); return uploaded; }
    }
}
