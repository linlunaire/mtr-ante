package cn.zbx1425.mtrsteamloco.compatibility;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

/** Real RouteMixin weaving with independently verified target/mixin code sources. */
public final class RouteWeavingCheck {
    private static final String ROUTE = "mtr/data/Route";
    private static final String MIXIN = "cn/zbx1425/mtrsteamloco/mixin/RouteMixin";
    private static final String INTERFACE = "cn/zbx1425/mtrsteamloco/data/IRoute";
    private static final String SCENARIO = "cn.zbx1425.mtrsteamloco.compatibility.RouteWeavingScenario";

    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("golden targetArtifact mixinArtifact [--java-baseline] [--record] [--missing-method|--missing-field]");
        List<String> options = Arrays.asList(args).subList(3, args.length);
        require(options.stream().allMatch(List.of("--java-baseline", "--record", "--missing-method", "--missing-field")::contains), "Unknown weaving option");
        boolean baseline = options.contains("--java-baseline");
        Map<String, byte[]> definitions = routeClasses(Path.of(args[1]));
        verifySource(ROUTE, Path.of(args[1]), !baseline);
        verifySource(MIXIN, Path.of(args[2]), !baseline);
        verifySource(INTERFACE, Path.of(args[2]), !baseline);

        System.setProperty("mixin.service", CameraWeavingCheck.HeadlessService.class.getName());
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("route-weaving.mixins.json");
        var service = (CameraWeavingCheck.HeadlessService) MixinService.getService();
        ClassNode route = service.getClassNode(ROUTE);
        if (options.contains("--missing-method")) require(route.methods.removeIf(method -> method.name.equals("writePacket")), "Missing negative-control method");
        if (options.contains("--missing-field")) require(route.fields.removeIf(field -> field.name.equals("platformIds")), "Missing negative-control field");
        require(service.transformerFactory().createTransformer().transformClass(environment, ROUTE.replace('/', '.'), route), "Production RouteMixin did not weave");
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        route.accept(writer);
        definitions.put(ROUTE.replace('/', '.'), writer.toByteArray());
        ClassLoader loader = new ClassLoader(RouteWeavingCheck.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = definitions.get(name);
                if (bytes == null && (name.equals(SCENARIO) || name.startsWith(SCENARIO + "$"))) {
                    try (InputStream input = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (input == null) throw new ClassNotFoundException(name);
                        bytes = input.readAllBytes();
                    } catch (java.io.IOException failure) { throw new ClassNotFoundException(name, failure); }
                }
                if (bytes == null) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> result = findLoadedClass(name);
                    if (result == null) result = defineClass(name, bytes, 0, bytes.length);
                    if (resolve) resolveClass(result);
                    return result;
                }
            }
        };
        String actual;
        try { actual = (String) Class.forName(SCENARIO, true, loader).getMethod("run").invoke(null); }
        catch (InvocationTargetException failure) { throw new AssertionError("Actual woven route failed", failure.getCause()); }
        if (options.contains("--record")) {
            require(baseline, "Never generate Java goldens from Kotlin");
            Files.writeString(Path.of(args[0]), actual);
        } else require(Files.readString(Path.of(args[0])).replace("\r\n", "\n").equals(actual), "Woven route differs from Java golden");
        System.out.println("PASS: actual Sponge " + (baseline ? "Java baseline" : "Kotlin") + " RouteMixin, all constructors, path-list identity, platform append/turn-back filtering, save/packet tails and malformed-data partial retention");
    }

    private static void verifySource(String name, Path source, boolean kotlin) throws Exception {
        byte[] selected = read(source, name + ".class"), actual;
        try (var input = RouteWeavingCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            require(input != null, "Missing runtime class: " + name); actual = input.readAllBytes();
        }
        require(Arrays.equals(selected, actual), "Wrong runtime source: " + name);
        ClassNode node = new ClassNode(); new ClassReader(actual).accept(node, ClassReader.SKIP_CODE);
        boolean metadata = node.visibleAnnotations != null && node.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.equals("Lkotlin/Metadata;"));
        require(metadata == kotlin, "Unexpected source language: " + name);
    }
    private static Map<String, byte[]> routeClasses(Path source) throws Exception {
        Map<String, byte[]> result = new HashMap<>();
        if (Files.isDirectory(source)) {
            try (var files = Files.list(source.resolve("mtr/data"))) {
                for (Path file : files.filter(path -> routeEntry("mtr/data/" + path.getFileName())).toList()) {
                    result.put("mtr.data." + file.getFileName().toString().replace(".class", ""), Files.readAllBytes(file));
                }
            }
        } else try (JarFile jar = new JarFile(source.toFile())) {
            for (var entries = jar.entries(); entries.hasMoreElements();) {
                var entry = entries.nextElement();
                if (routeEntry(entry.getName())) try (var input = jar.getInputStream(entry)) {
                    result.put(entry.getName().replace('/', '.').replace(".class", ""), input.readAllBytes());
                }
            }
        }
        return result;
    }
    private static boolean routeEntry(String name) { return name.equals(ROUTE + ".class") || name.startsWith(ROUTE + "$") && name.endsWith(".class"); }
    private static byte[] read(Path source, String entry) throws Exception {
        if (Files.isDirectory(source)) return Files.readAllBytes(source.resolve(entry));
        try (JarFile jar = new JarFile(source.toFile()); var input = jar.getInputStream(jar.getJarEntry(entry))) { return input.readAllBytes(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
