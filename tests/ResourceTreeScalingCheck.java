package cn.zbx1425.mtrsteamloco.compatibility;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;

/** Counts actual selected Tree map probes/frequency visits; no timing thresholds. */
public final class ResourceTreeScalingCheck {
    private static long work;
    public static final class CountedMap<K, V> extends HashMap<K, V> {
        @Override public V get(Object key) { work++; return super.get(key); }
        @Override public V put(K key, V value) { work++; return super.put(key, value); }
    }
    public static int frequency(Collection<?> values, Object value) {
        work += values.size();
        return Collections.frequency(values, value);
    }

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[0]).toRealPath();
        boolean baseline = args.length == 2 && args[1].equals("--baseline");
        String treeName = "cn.zbx1425.mtrsteamloco.data.Tree";
        ClassLoader loader = new ClassLoader(ResourceTreeScalingCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.equals(treeName) && !name.startsWith(treeName + "$")) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) try {
                        ClassReader reader = new ClassReader(read(source, name));
                        ClassWriter writer = new ClassWriter(0);
                        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                            @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, method, descriptor, signature, exceptions)) {
                                    @Override public void visitTypeInsn(int opcode, String type) {
                                        super.visitTypeInsn(opcode, opcode == Opcodes.NEW && type.equals("java/util/HashMap") ? countedMapName() : type);
                                    }
                                    @Override public void visitMethodInsn(int opcode, String owner, String method, String descriptor, boolean isInterface) {
                                        if (opcode == Opcodes.INVOKESPECIAL && owner.equals("java/util/HashMap") && method.equals("<init>")) owner = countedMapName();
                                        if (owner.equals("java/util/Collections") && method.equals("frequency")) owner = ResourceTreeScalingCheck.class.getName().replace('.', '/');
                                        super.visitMethodInsn(opcode, owner, method, descriptor, isInterface);
                                    }
                                };
                            }
                        }, 0);
                        byte[] bytes = writer.toByteArray(); type = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException error) { throw new ClassNotFoundException(name, error); }
                    if (resolve) resolveClass(type); return type;
                }
            }
        };
        Class<?> rootClass = Class.forName(treeName + "$Root", true, loader);
        Class<?> branchClass = Class.forName(treeName + "$Branch", true, loader);
        var append = branchClass.getMethod("addLeaf", String.class, String.class, Object.class);
        var duplicate = branchClass.getDeclaredMethod("dealWithDuplicateNames"); duplicate.setAccessible(true);
        var merge = branchClass.getMethod("mergeLevel");
        for (int size : new int[]{128, 1024, 4096}) {
            Object root = rootClass.getConstructor(String.class).newInstance("Root");
            for (int index = 0; index < size; index++) append.invoke(root, "key" + index, "Same", index);
            work = 0; duplicate.invoke(root); long duplicateWork = work;
            work = 0; Map<?, ?> result = (Map<?, ?>) merge.invoke(root); long mergeWork = work;
            if (result.size() != size) throw new AssertionError("Scale fixture lost leaves");
            System.out.println("TREE_WORK: " + size + " nodes, duplicate=" + duplicateWork + ", merge=" + mergeWork + (baseline ? " (original Java)" : " (linear gate)"));
            if (baseline) {
                if (duplicateWork != (long) size * size || mergeWork < (long) size * size) throw new AssertionError("Original frequency-scan control was not exercised");
            } else if (duplicateWork == 0 || duplicateWork > size * 4L || mergeWork == 0 || mergeWork > size * 6L) {
                throw new AssertionError("Duplicate-name work exceeded linear gate");
            }
        }
    }
    private static String countedMapName() { return CountedMap.class.getName().replace('.', '/'); }
    private static byte[] read(Path source, String name) throws IOException {
        String entry = name.replace('.', '/') + ".class";
        if (Files.isDirectory(source)) return Files.readAllBytes(source.resolve(entry));
        try (JarFile jar = new JarFile(source.toFile()); var input = jar.getInputStream(jar.getJarEntry(entry))) { return input.readAllBytes(); }
    }
}
