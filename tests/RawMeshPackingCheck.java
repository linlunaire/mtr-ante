package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.vertex.*;
import cn.zbx1425.sowcerext.model.Vertex;
import cn.zbx1425.sowcerext.model.Face;
import com.sun.management.ThreadMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;

/** Selects only RawMesh; both versions share the current geometry dependencies to isolate this change. */
public final class RawMeshPackingCheck {
    private static final String TARGET = "cn.zbx1425.sowcerext.model.RawMesh";
    private static int enumCopies;
    private static volatile Object sink;
    public static VertAttrType[] countedValues() { enumCopies++; return VertAttrType.values(); }
    public static void main(String[] args) throws Exception {
        if (!ManagementFactory.getRuntimeMXBean().getInputArguments().contains("-Xint")) throw new AssertionError("Use -Xint to exclude escape-analysis artifacts");
        Path source = Path.of(args[0]).toRealPath(); boolean kotlin = Files.isDirectory(source), baseline = Arrays.asList(args).contains("--baseline");
        for (Class<?> type : List.of(Vertex.class, Face.class, VertAttrType.class, MaterialProp.class)) {
            if (Arrays.stream(type.getDeclaredAnnotations()).noneMatch(a -> a.annotationType().getName().equals("kotlin.Metadata"))) throw new AssertionError("Comparison must share current Kotlin dependencies: " + type);
        }
        Map<String, byte[]> definitions = new HashMap<>();
        if (kotlin) try (var paths = Files.list(source.resolve("cn/zbx1425/sowcerext/model"))) {
            for (Path path : paths.filter(p -> p.getFileName().toString().matches("RawMesh(?:\\$.*)?\\.class")).toList()) definitions.put("cn.zbx1425.sowcerext.model." + path.getFileName().toString().replace(".class", ""), Files.readAllBytes(path));
        } else try (JarFile jar = new JarFile(source.toFile())) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement(); String name = entry.getName().replace('/', '.').replace(".class", "");
                if (entry.getName().endsWith(".class") && (name.equals(TARGET) || name.startsWith(TARGET + "$"))) try (var input = jar.getInputStream(entry)) { definitions.put(name, input.readAllBytes()); }
            }
        }
        int[] callSites = {0}; boolean[] metadata = {false}; ClassWriter writer = new ClassWriter(0);
        new ClassReader(Objects.requireNonNull(definitions.get(TARGET))).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) { if (descriptor.equals("Lkotlin/Metadata;")) metadata[0] = true; return super.visitAnnotation(descriptor, visible); }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor output = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!name.equals("_uploadAsync")) return output;
                return new MethodVisitor(Opcodes.ASM9, output) {
                    @Override public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                        if (owner.equals("cn/zbx1425/sowcer/vertex/VertAttrType") && name.equals("values") && descriptor.equals("()[Lcn/zbx1425/sowcer/vertex/VertAttrType;")) { owner = RawMeshPackingCheck.class.getName().replace('.', '/'); name = "countedValues"; callSites[0]++; }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        if (metadata[0] != kotlin || callSites[0] != (kotlin ? 0 : 1)) throw new AssertionError("Wrong source language or enum-copy adapter sites");
        definitions.put(TARGET, writer.toByteArray());
        ClassLoader loader = new ClassLoader(RawMeshPackingCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = definitions.get(name); if (bytes == null) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) { Class<?> type = findLoadedClass(name); if (type == null) type = defineClass(name, bytes, 0, bytes.length); if (resolve) resolveClass(type); return type; }
            }
        };
        Class<?> rawMesh = Class.forName(TARGET, true, loader);
        var pack = rawMesh.getDeclaredMethod("_uploadAsync", VertAttrMapping.class); pack.setAccessible(true);
        VertAttrMapping.Builder builder = new VertAttrMapping.Builder();
        for (VertAttrType type : VertAttrType.values()) builder.set(type, type == VertAttrType.POSITION || type == VertAttrType.NORMAL ? VertAttrSrc.VERTEX_BUF : VertAttrSrc.GLOBAL);
        VertAttrMapping mapping = builder.build();
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!bean.isThreadAllocatedMemorySupported()) throw new AssertionError("Allocation counters unavailable");
        bean.setThreadAllocatedMemoryEnabled(true); long thread = Thread.currentThread().threadId(); bean.getThreadAllocatedBytes(thread);
        for (int size : new int[] {96, 768, 3072}) {
            Object mesh = rawMesh.getConstructor(MaterialProp.class).newInstance(new MaterialProp());
            @SuppressWarnings("unchecked") List<Vertex> vertices = (List<Vertex>) rawMesh.getField("vertices").get(mesh);
            @SuppressWarnings("unchecked") List<Face> faces = (List<Face>) rawMesh.getField("faces").get(mesh);
            for (int i = 0; i < size; i++) vertices.add(new Vertex(new Vector3f(i, i % 3, 0), new Vector3f(0f, 1f, 0f)));
            for (int i = 0; i < size; i += 3) faces.add(new Face(new int[] {i, i + 1, i + 2}));
            for (int i = 0; i < 4; i++) sink = pack.invoke(mesh, mapping);
            enumCopies = 0; long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < 8; i++) sink = pack.invoke(mesh, mapping);
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            System.out.println("MESH_PACKING: " + size + " vertices, " + enumCopies / 8 + " enum clones, " + allocated / 8.0 + " bytes/pack; selected " + (kotlin ? "Kotlin" : "Java") + " RawMesh with shared current dependencies (-Xint)");
            if (enumCopies != (baseline ? size * 8 : 0)) throw new AssertionError("Per-vertex enum cloning: " + enumCopies + " copies / 8 packs");
        }
    }
}
