package cn.zbx1425.mtrsteamloco.compatibility;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.helpers.NOPLogger;

/** Selects real shape/rolling code; adapters supply only configuration and logging. */
public final class ShapeRollingCompatibilityCheck {
    public static boolean enableRolling = true;
    public static final Logger LOGGER = NOPLogger.NOP_LOGGER;
    public static int parseCalls;
    public static void parsed() { parseCalls++; }
    private static final String PREFIX = "cn.zbx1425.mtrsteamloco.data.";
    private static final String SCENARIO = "cn.zbx1425.mtrsteamloco.compatibility.ShapeRollingScenario";

    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        boolean kotlin = Files.isDirectory(source), record = Arrays.asList(args).contains("--record"), cache = Arrays.asList(args).contains("--cache");
        Map<String, byte[]> definitions = new HashMap<>();
        if (kotlin) {
            try (var paths = Files.list(source.resolve(PREFIX.replace('.', '/')))) {
                for (Path path : paths.filter(path -> selected(PREFIX + path.getFileName().toString().replace(".class", ""))).toList()) definitions.put(PREFIX + path.getFileName().toString().replace(".class", ""), Files.readAllBytes(path));
            }
        } else try (JarFile jar = new JarFile(source.toFile())) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement(); String name = entry.getName().replace('/', '.').replace(".class", "");
                if (entry.getName().endsWith(".class") && selected(name)) try (var input = jar.getInputStream(entry)) { definitions.put(name, input.readAllBytes()); }
            }
        }
        for (String type : List.of("ShapeSerializer", "Rolling", "Rolling$Rotation")) {
            byte[] bytes = definitions.get(PREFIX + type); if (bytes == null) throw new AssertionError("Missing selected type " + type);
            boolean[] metadata = {false}; new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String desc, boolean visible) { if (desc.equals("Lkotlin/Metadata;")) metadata[0] = true; return null; }
            }, ClassReader.SKIP_CODE);
            if (metadata[0] != kotlin) throw new AssertionError("Wrong language " + type);
        }
        for (var definition : definitions.entrySet()) {
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(definition.getValue()).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, signature, exceptions)) {
                        @Override public void visitCode() {
                            super.visitCode();
                            if (name.equals("parseShape")) super.visitMethodInsn(Opcodes.INVOKESTATIC, support(), "parsed", "()V", false);
                        }
                        @Override public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                            if (owner.equals("cn/zbx1425/mtrsteamloco/ClientConfig") && name.equals("enableRolling") || owner.equals("cn/zbx1425/mtrsteamloco/Main") && name.equals("LOGGER")) owner = support();
                            super.visitFieldInsn(opcode, owner, name, desc);
                        }
                    };
                }
            }, 0);
            definition.setValue(writer.toByteArray());
        }
        ClassLoader loader = new ClassLoader(ShapeRollingCompatibilityCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = definitions.get(name);
                if (bytes == null && (name.equals(SCENARIO) || name.startsWith(SCENARIO + "$"))) try (var input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                    if (input == null) throw new ClassNotFoundException(name); bytes = input.readAllBytes();
                } catch (IOException error) { throw new ClassNotFoundException(name, error); }
                if (bytes == null) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name); if (type == null) type = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(type); return type;
                }
            }
        };
        String actual;
        try { actual = (String) Class.forName(SCENARIO, true, loader).getMethod("run", boolean.class).invoke(null, cache); }
        catch (InvocationTargetException error) { throw new AssertionError("Selected shape/rolling failed", error.getCause()); }
        if (!cache) {
            if (record) { if (kotlin) throw new AssertionError("Only record original Java"); Files.writeString(Path.of(args[0]), actual); }
            else if (!Files.readString(Path.of(args[0])).replace("\r\n", "\n").equals(actual)) throw new AssertionError("Shape/rolling differ from Java golden:\n" + actual);
        }
        System.out.println("PASS: shape/rolling " + (cache ? "cached validation reuses geometry" : "original-Java geometry/rotation records"));
    }
    private static String support() { return ShapeRollingCompatibilityCheck.class.getName().replace('.', '/'); }
    private static boolean selected(String name) { return List.of("ShapeSerializer", "Rolling").stream().anyMatch(type -> name.equals(PREFIX + type) || name.startsWith(PREFIX + type + "$")); }
}
