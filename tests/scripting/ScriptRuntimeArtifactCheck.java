package cn.zbx1425.mtrsteamloco.compatibility;

import java.lang.module.ModuleFinder;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.zip.ZipFile;

/** Uses only the JDK and the finished artifact, never Gradle's Graal libraries. */
public final class ScriptRuntimeArtifactCheck {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path artifact = Path.of(args[0]);
        try (JarFile jar = new JarFile(artifact.toFile())) {
            Set<String> names = new HashSet<>();
            jar.stream().forEach(entry -> require(names.add(entry.getName()), "Duplicate entry: " + entry.getName()));
            require(jar.isMultiRelease(), "Missing Multi-Release manifest");
            for (String entry : List.of("org/graalvm/polyglot/io/FileSystem.class",
                    "org/graalvm/polyglot/Context.class", "com/oracle/truffle/js/lang/JavaScriptLanguage.class",
                    "com/oracle/truffle/runtime/hotspot/HotSpotTruffleRuntimeAccess.class",
                    "META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider",
                    "licenses/Graal-runtime-notices.txt", "licenses/GraalJS-24.2.2-third-party.txt")) {
                require(jar.getJarEntry(entry) != null, "Missing runtime dependency: " + entry);
            }
            require(jar.getJarEntry("module-info.class") == null, "Dependency module descriptor replaced the mod module");
        }
        var descriptor = ModuleFinder.of(artifact).findAll().iterator().next().descriptor();
        require(descriptor.isAutomatic(), "YLTE must remain an automatic mod module");
        for (var reference : ModuleFinder.ofSystem().findAll()) {
            Set<String> overlap = new HashSet<>(reference.descriptor().packages());
            overlap.retainAll(descriptor.packages());
            require(overlap.isEmpty(), "System module package overlap: " + reference.descriptor().name() + " " + overlap);
        }
        // Check SDK relocation even when this gate runs on a non-GraalVM JDK.
        for (String prefix : List.of("org.graalvm.word", "org.graalvm.collections", "org.graalvm.nativeimage",
                "com.oracle.svm.core.annotate", "com.oracle.truffle.compiler")) {
            require(descriptor.packages().stream().noneMatch(p -> p.equals(prefix) || p.startsWith(prefix + ".")),
                    "Unrelocated SDK package: " + prefix);
        }
        System.setProperty("truffle.TruffleRuntime", "com.oracle.truffle.api.impl.DefaultTruffleRuntime");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{artifact.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            try (JarFile jar = new JarFile(artifact.toFile())) {
                for (var entry : jar.stream().filter(e -> e.getName().startsWith("META-INF/services/") && !e.isDirectory()).toList()) {
                    Class<?> service = loader.loadClass(entry.getName().substring("META-INF/services/".length()));
                    try (var input = jar.getInputStream(entry)) {
                        for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                            String provider = line.split("#", 2)[0].trim();
                            if (!provider.isEmpty()) require(service.isAssignableFrom(loader.loadClass(provider)), "Broken service: " + provider);
                        }
                    }
                }
            }
            Class<?> imageInfo = loader.loadClass("cn.zbx1425.mtrsteamloco.vendor.graal.org.graalvm.nativeimage.ImageInfo");
            require(imageInfo.getField("PROPERTY_IMAGE_CODE_KEY").get(null).equals("org.graalvm.nativeimage.imagecode"), "Rewritten Native Image property");
            require(imageInfo.getField("PROPERTY_IMAGE_KIND_KEY").get(null).equals("org.graalvm.nativeimage.kind"), "Rewritten Native Image kind property");
            Class<?> contextClass = loader.loadClass("org.graalvm.polyglot.Context");
            Object builder = contextClass.getMethod("newBuilder", String[].class).invoke(null, (Object) new String[]{"js"});
            Class<?> builderClass = builder.getClass();
            builderClass.getMethod("allowExperimentalOptions", boolean.class).invoke(builder, true);
            var option = builderClass.getMethod("option", String.class, String.class);
            option.invoke(builder, "engine.WarnInterpreterOnly", "false");
            option.invoke(builder, "js.nashorn-compat", "true");
            option.invoke(builder, "js.ecmascript-version", "latest");
            Class<?> hostAccess = loader.loadClass("org.graalvm.polyglot.HostAccess");
            builderClass.getMethod("allowHostAccess", hostAccess).invoke(builder, hostAccess.getField("ALL").get(null));
            builderClass.getMethod("allowHostClassLookup", Predicate.class).invoke(builder, (Predicate<String>) name -> name.startsWith("java.util."));
            try (AutoCloseable context = (AutoCloseable) builderClass.getMethod("build").invoke(builder)) {
                Object result = contextClass.getMethod("eval", String.class, CharSequence.class).invoke(context, "js",
                        "load('nashorn:mozilla_compat.js'); importPackage(java.util); var m = new HashMap(); m.put('answer', 6 * 7); m.get('answer');");
                require(result.getClass().getMethod("asInt").invoke(result).equals(42), "Broken packaged JS/legacy host interop");
                Class<?> sourceClass = loader.loadClass("org.graalvm.polyglot.Source");
                int scripts = 0;
                for (int i = 1; i < args.length; i++) {
                    try (ZipFile zip = new ZipFile(args[i])) {
                        for (var entry : zip.stream().filter(e -> e.getName().endsWith(".js") && !e.isDirectory()).toList()) {
                            String source;
                            try (var input = zip.getInputStream(entry)) { source = new String(input.readAllBytes(), StandardCharsets.UTF_8); }
                            Object sourceBuilder = sourceClass.getMethod("newBuilder", String.class, CharSequence.class, String.class)
                                    .invoke(null, "js", source, Path.of(args[i]).getFileName() + "/" + entry.getName());
                            Object compiledSource = sourceBuilder.getClass().getMethod("buildLiteral").invoke(sourceBuilder);
                            contextClass.getMethod("parse", sourceClass).invoke(context, compiledSource);
                            scripts++;
                        }
                    }
                }
                System.out.println("Parsed resource-pack scripts: " + scripts + " (syntax only; no script execution/game/GPU)");
            }
        } finally { Thread.currentThread().setContextClassLoader(previous); }
        System.out.println("PASS: packaged GraalJS, services and SDK package boundary; " + assertions + " assertions");
    }

    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
