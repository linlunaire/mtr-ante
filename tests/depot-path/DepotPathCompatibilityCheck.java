package cn.zbx1425.mtrsteamloco.compatibility;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.ProtectionDomain;
import java.security.cert.Certificate;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

/** Executes the actual generator and actual Route/PathData Mixins; only world height, siding and packet I/O are adapters. */
public final class DepotPathCompatibilityCheck {
    private static final String SCENARIO = "cn.zbx1425.mtrsteamloco.compatibility.DepotPathScenario";
    private static final String GENERATOR = "cn/zbx1425/mtrsteamloco/path/DepotPathGen";
    private static final String PLAN = "cn/zbx1425/mtrsteamloco/path/DepotRoutePlan";
    private static final String JAVA_SHA = "db3662a772ad746bcdbfe81b2e44fed09ea084d73f11b29022be9b11e9f4d79c";

    public static void main(String[] args) throws Exception {
        if (args.length < 2) throw new IllegalArgumentException("golden implementationSource [--record] [--through-depot] [--direct-plan] [--check-plan] [--packaged] [--missing-depot-method]");
        List<String> options = Arrays.asList(args).subList(2, args.length);
        require(options.stream().allMatch(option -> option.startsWith("--target=") || List.of("--record", "--through-depot", "--direct-plan", "--check-plan", "--packaged", "--missing-depot-method").contains(option)), "Unknown option");
        boolean throughDepot = options.contains("--through-depot");
        boolean directPlan = options.contains("--direct-plan");
        require(!directPlan || !throughDepot, "Direct plan and injected entry point are separate test modes");
        Path source = Path.of(args[1]).toRealPath();
        Map<String, byte[]> definitions = new HashMap<>();
        if (Files.isDirectory(source)) {
            Path packagePath = source.resolve("cn/zbx1425/mtrsteamloco/path");
            try (var files = Files.list(packagePath)) {
                for (Path file : files.filter(path -> selectedClass("cn/zbx1425/mtrsteamloco/path/" + path.getFileName())).toList()) {
                    definitions.put("cn.zbx1425.mtrsteamloco.path." + file.getFileName().toString().replace(".class", ""), Files.readAllBytes(file));
                }
            }
        } else try (JarFile jar = new JarFile(source.toFile())) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (selectedClass(entry.getName())) {
                    try (var input = jar.getInputStream(entry)) { definitions.put(entry.getName().replace('/', '.').replace(".class", ""), input.readAllBytes()); }
                }
            }
        }
        require(definitions.containsKey(GENERATOR.replace('/', '.')), "Missing generator in selected source");
        ClassNode generator = new ClassNode();
        new ClassReader(definitions.get(GENERATOR.replace('/', '.'))).accept(generator, 0);
        boolean kotlin = generator.visibleAnnotations != null && generator.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lkotlin/Metadata;"));
        require(kotlin == (Files.isDirectory(source) || options.contains("--packaged")), "Expected selected Kotlin implementation or original Java baseline JAR");
        if (kotlin) {
            require(definitions.containsKey(PLAN.replace('/', '.')), "Selected artifact has no DepotRoutePlan; parent fallback forbidden");
            ClassNode plan = new ClassNode(); new ClassReader(definitions.get(PLAN.replace('/', '.'))).accept(plan, 0);
            require(plan.visibleAnnotations != null && plan.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lkotlin/Metadata;")), "Selected route plan is not Kotlin");
        } else {
            require(!directPlan && !options.contains("--check-plan"), "Original Java has no extracted route plan");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(source)) { byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) >= 0) digest.update(buffer, 0, count); }
            require(HexFormat.of().formatHex(digest.digest()).equals(JAVA_SHA), "Wrong original Java depot baseline SHA-256");
        }

        System.setProperty("mixin.service", CameraWeavingCheck.HeadlessService.class.getName());
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("depot-path.mixins.json");
        var service = (CameraWeavingCheck.HeadlessService) MixinService.getService();
        var transformer = service.transformerFactory().createTransformer();
        List<String> targets = new ArrayList<>(List.of("mtr/data/Route", "mtr/data/Route$RoutePlatform", "mtr/data/Route$CircularState", "mtr/data/Route$Companion", "mtr/path/PathData", "mtr/path/PathData$Companion", "mtr/path/PathFinder", "mtr/path/PathFinder$Companion", "mtr/path/PathFinder$PathPart",
                "cn/zbx1425/mtrsteamloco/path/BetterPathFinder", "cn/zbx1425/mtrsteamloco/path/BetterPathFinder$Companion", "cn/zbx1425/mtrsteamloco/path/BetterPathFinder$PathPart"));
        if (throughDepot) {
            targets.add("mtr/data/Depot"); targets.add("mtr/data/Depot$Companion");
            Path target = Path.of(options.stream().filter(option -> option.startsWith("--target=")).findFirst().orElseThrow(() -> new IllegalArgumentException("Missing --target=MTR-artifact")).substring(9));
            byte[] selectedDepot;
            if (Files.isDirectory(target)) selectedDepot = Files.readAllBytes(target.resolve("mtr/data/Depot.class"));
            else try (JarFile jar = new JarFile(target.toFile()); var input = jar.getInputStream(jar.getJarEntry("mtr/data/Depot.class"))) { selectedDepot = input.readAllBytes(); }
            try (var input = DepotPathCompatibilityCheck.class.getClassLoader().getResourceAsStream("mtr/data/Depot.class")) {
                require(input != null && Arrays.equals(input.readAllBytes(), selectedDepot), "Wrong Depot runtime source");
            }
            byte[] selectedMixin;
            if (Files.isDirectory(source)) selectedMixin = Files.readAllBytes(source.resolve("cn/zbx1425/mtrsteamloco/mixin/DepotMixin.class"));
            else try (JarFile jar = new JarFile(source.toFile()); var input = jar.getInputStream(jar.getJarEntry("cn/zbx1425/mtrsteamloco/mixin/DepotMixin.class"))) { selectedMixin = input.readAllBytes(); }
            try (var input = DepotPathCompatibilityCheck.class.getClassLoader().getResourceAsStream("cn/zbx1425/mtrsteamloco/mixin/DepotMixin.class")) {
                require(input != null && Arrays.equals(input.readAllBytes(), selectedMixin), "Wrong DepotMixin runtime source");
            }
            Mixins.addConfiguration("depot-weaving.mixins.json");
        }
        for (String name : targets) {
            ClassNode node = service.getClassNode(name);
            if (name.equals("mtr/data/Depot")) require(node.visibleAnnotations != null && node.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.equals("Lkotlin/Metadata;")), "Depot is not Kotlin");
            if (name.equals("mtr/data/Depot") && options.contains("--missing-depot-method")) require(node.methods.removeIf(method -> method.name.equals("generateMainRoute")), "Missing negative-control target");
            boolean transformed = transformer.transformClass(environment, name.replace('/', '.'), node);
            if (name.equals("mtr/data/Depot") || name.equals("mtr/data/Route") || name.equals("mtr/path/PathData") || name.equals("mtr/path/PathFinder$Companion")) require(transformed, "Real mixin did not apply: " + name);
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            definitions.put(name.replace('/', '.'), writer.toByteArray());
        }
        definitions.put("mtr.packet.PacketTrainDataGuiServer", packetAdapter());
        definitions.put(SCENARIO + "$HeightOnlyLevel", heightAdapter());
        if (kotlin) for (var entry : definitions.entrySet()) {
            if (!classFamily(entry.getKey().replace('.', '/'), GENERATOR)) continue;
            ClassWriter writer = new ClassWriter(0);
            new ClassReader(entry.getValue()).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                        @Override public void visitMethodInsn(int opcode, String owner, String method, String desc, boolean isInterface) {
                            if (owner.equals("mtr/path/PathGenerationTask") && method.equals("publish")) owner = SCENARIO.replace('.', '/');
                            super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                        }
                    };
                }
            }, 0);
            entry.setValue(writer.toByteArray());
        }
        ProtectionDomain selectedDomain = new ProtectionDomain(new CodeSource(source.toUri().toURL(), (Certificate[]) null), null);
        ClassLoader loader = new ClassLoader(DepotPathCompatibilityCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = definitions.get(name);
                if (bytes == null && classFamily(name.replace('.', '/'), PLAN)) throw new ClassNotFoundException("Route plan escaped selected artifact: " + name);
                if (bytes == null && (name.equals(SCENARIO) || name.startsWith(SCENARIO + "$"))) {
                    try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        bytes = input.readAllBytes();
                    } catch (java.io.IOException error) { throw new ClassNotFoundException(name, error); }
                }
                if (bytes == null) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> type = findLoadedClass(name);
                    if (type == null) type = selectedClass(name.replace('.', '/') + ".class")
                            ? defineClass(name, bytes, 0, bytes.length, selectedDomain) : defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(type);
                    return type;
                }
            }
        };
        String actual;
        if (kotlin) {
            Class<?> selectedPlan = Class.forName(PLAN.replace('/', '.'), true, loader);
            require(selectedPlan.getClassLoader() == loader && Path.of(selectedPlan.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong loaded route plan provenance");
        }
        var run = Class.forName(SCENARIO, true, loader).getMethod("run", boolean.class, boolean.class);
        try { actual = (String) run.invoke(null, throughDepot, directPlan); }
        catch (InvocationTargetException failure) { throw new AssertionError("Actual depot generator failed", failure.getCause()); }
        if (options.contains("--record")) {
            require(!kotlin, "Never record a Java golden from Kotlin");
            Files.writeString(Path.of(args[0]), actual);
        } else {
            String expected = Files.readString(Path.of(args[0])).replace("\r\n", "\n");
            require((directPlan ? planProjection(expected) : expected).equals(actual), "Depot behavior changed from Java baseline\n" + actual);
            if (kotlin && !directPlan) {
                try { Class.forName(SCENARIO, true, loader).getMethod("workerContracts").invoke(null); }
                catch (InvocationTargetException failure) { throw new AssertionError("ANTE worker cancellation failed", failure.getCause()); }
            }
            if (options.contains("--check-plan")) {
                try { actual = (String) run.invoke(null, false, true); }
                catch (InvocationTargetException failure) { throw new AssertionError("Direct depot route plan failed", failure.getCause()); }
                require(planProjection(expected).equals(actual), "Direct plan differs from original Java projection\n" + actual);
            }
        }
        System.out.println("PASS: actual " + (kotlin ? "Kotlin" : "Java baseline") + (directPlan ? " depot plan interface" : " depot generator" + (throughDepot ? " via real DepotMixin injection" : " directly"))
                + ", 12 unchanged Java golden scenarios" + (options.contains("--check-plan") ? " plus direct plan interface" : "") + " (headless I/O adapters)");
    }

    private static boolean classFamily(String name, String family) { return name.equals(family) || name.startsWith(family + "$"); }
    private static boolean selectedClass(String entry) {
        if (!entry.endsWith(".class")) return false;
        String name = entry.substring(0, entry.length() - 6);
        return classFamily(name, GENERATOR) || classFamily(name, PLAN);
    }

    /** Project the real Java siding adapter capture, not a reimplementation of route assembly. */
    private static String planProjection(String golden) {
        Map<String, String[]> rows = new LinkedHashMap<>();
        golden.lines().forEach(line -> { String[] fields = line.split("\t", -1); rows.put(fields[0], fields); });
        List<String> result = new ArrayList<>();
        rows.forEach((name, row) -> {
            if (name.equals("uncached-missing")) result.add(name + "\tFAIL");
            else if (name.equals("no-routes")) result.add(name + "\t0:0:0:");
            else {
                // Filtering is outside this module; the filtered case uses identical route inputs.
                String[] capture = (name.equals("filtered") ? rows.get("same") : row)[2].split(":", 8);
                require(capture.length == 8 && capture[0].equals("1"), "No original Java main-path capture for " + name);
                result.add(name + "\t" + capture[1] + ":" + capture[2] + ":" + capture[3] + ":" + capture[7]);
            }
        });
        return String.join("\n", result) + "\n";
    }

    private static byte[] packetAdapter() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, "mtr/packet/PacketTrainDataGuiServer", null, "java/lang/Object", null);
        String descriptor = "(Lnet/minecraft/world/level/Level;JI)V";
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "generatePathS2C", descriptor, null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0); method.visitVarInsn(Opcodes.LLOAD, 1); method.visitVarInsn(Opcodes.ILOAD, 3);
        method.visitMethodInsn(Opcodes.INVOKESTATIC, SCENARIO.replace('.', '/'), "packet", descriptor, false);
        method.visitInsn(Opcodes.RETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] heightAdapter() {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V25, Opcodes.ACC_PUBLIC, SCENARIO.replace('.', '/') + "$HeightOnlyLevel", null, "net/minecraft/world/level/Level", null);
        var method = writer.visitMethod(Opcodes.ACC_PUBLIC, "getMaxY", "()I", null, null);
        method.visitCode(); method.visitLdcInsn(319); method.visitInsn(Opcodes.IRETURN); method.visitMaxs(0, 0); method.visitEnd(); writer.visitEnd();
        return writer.toByteArray();
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
