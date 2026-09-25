package cn.zbx1425.mtrsteamloco.compatibility;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.jar.JarFile;

/** Runs with only the JDK and the finished artifact, not Gradle's compile dependencies. */
public final class ReleaseArtifactCompatibilityCheck {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path path = Path.of(args[0]);
        String platform = args[1];
        try (JarFile jar = new JarFile(path.toFile())) {
            HashSet<String> names = new HashSet<>();
            jar.stream().forEach(entry -> {
                require(names.add(entry.getName()), "Duplicate JAR entry " + entry.getName());
                require(!entry.getName().startsWith("mtr/"), "MTR classes must remain external: " + entry.getName());
            });
            String metadata = read(jar, platform.equals("fabric") ? "fabric.mod.json" : "META-INF/neoforge.mods.toml");
            require(metadata.contains(args[2]) && metadata.contains("26.2") && !metadata.contains("${"), "Unexpanded/incorrect loader metadata");
            String mixins = read(jar, "mtrsteamloco.mixins.json");
            require(mixins.contains("JAVA_25") && mixins.contains("HudPreviewMixin"), "Shared resources replaced the 26.2 mixin overlay");
            require(!mixins.contains("refmap"), "26.2 must not use an old mapped refmap");
            String access = read(jar, "mtrsteamloco.accesswidener");
            require(access.startsWith("accessWidener v2 official"), "Wrong access widener namespace");
            if (platform.equals("neoforge")) read(jar, "META-INF/accesstransformer.cfg");
            else read(jar, "mtrsteamloco_fabric.mixins.json");
            for (String language : new String[]{"de_de", "en_us", "is_is", "ja_jp", "ko_kr", "pt_pt", "ru_ru", "zh_cn", "zh_hk", "zh_tw"}) {
                String text = read(jar, "assets/mtrsteamloco/lang/" + language + ".json");
                require(text.contains("key.category.mtrsteamloco.keybinding"), "Missing new key category in " + language);
            }
            read(jar, "assets/mtrsteamloco/items/eye_candy.json");
            for (String entry : new String[]{
                    "org/graalvm/polyglot/Context.class", "com/oracle/truffle/js/lang/JavaScriptLanguage.class",
                    "com/oracle/truffle/runtime/hotspot/HotSpotTruffleRuntimeAccess.class",
                    "cn/zbx1425/mtrsteamloco/vendor/graal/org/graalvm/word/WordBase.class",
                    "cn/zbx1425/mtrsteamloco/vendor/graal/org/graalvm/collections/EconomicMap.class",
                    "cn/zbx1425/mtrsteamloco/vendor/graal/org/graalvm/nativeimage/ImageInfo.class",
                    "cn/zbx1425/mtrsteamloco/vendor/graal/com/oracle/svm/core/annotate/TargetClass.class",
                    "cn/zbx1425/mtrsteamloco/vendor/graal/com/oracle/truffle/compiler/TruffleCompiler.class",
                    "META-INF/services/com.oracle.truffle.api.provider.TruffleLanguageProvider",
                    "cn/zbx1425/mtrsteamloco/vendor/me/shedaniel/clothconfig2/api/ConfigBuilder.class",
                    "vendor/cn/zbx1425/mtrsteamloco/com/github/stuxuhai/jpinyin/PinyinHelper.class"}) {
                require(jar.getJarEntry(entry) != null, "Missing runtime dependency " + entry);
            }
        }
        // ANTE's runtime mixin selects this interpreter. This isolated test does not weave mixins.
        System.setProperty("truffle.TruffleRuntime", "com.oracle.truffle.api.impl.DefaultTruffleRuntime");
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{path.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Thread.currentThread().setContextClassLoader(loader);
            try (JarFile jar = new JarFile(path.toFile())) {
                for (var entry : jar.stream().filter(e -> e.getName().startsWith("META-INF/services/") && !e.isDirectory()).toList()) {
                    Class<?> service = loader.loadClass(entry.getName().substring("META-INF/services/".length()));
                    for (String line : read(jar, entry.getName()).lines().toList()) {
                        String name = line.split("#", 2)[0].trim();
                        if (!name.isEmpty()) require(service.isAssignableFrom(loader.loadClass(name)), "Broken relocated service provider: " + name);
                    }
                }
            }
            Class<?> imageInfo = loader.loadClass("cn.zbx1425.mtrsteamloco.vendor.graal.org.graalvm.nativeimage.ImageInfo");
            require(imageInfo.getField("PROPERTY_IMAGE_CODE_KEY").get(null).equals("org.graalvm.nativeimage.imagecode"), "Relocation changed the public Native Image property name");
            Class<?> pinyin = loader.loadClass("cn.zbx1425.mtrsteamloco.util.PinyinUtils");
            require(pinyin.getMethod("getPinyin", String.class).invoke(null, "铁路A").equals("tielua"), "Relocated Pinyin/dictionary failed");
            require(pinyin.getMethod("getPinyinInitials", String.class).invoke(null, "铁路A").equals("tla"), "Relocated initials failed");
            Class<?> contextClass = loader.loadClass("org.graalvm.polyglot.Context");
            Object builder = contextClass.getMethod("newBuilder", String[].class).invoke(null, (Object) new String[]{"js"});
            Class<?> builderClass = builder.getClass();
            builderClass.getMethod("allowExperimentalOptions", boolean.class).invoke(builder, true);
            builderClass.getMethod("option", String.class, String.class).invoke(builder, "engine.WarnInterpreterOnly", "false");
            builderClass.getMethod("option", String.class, String.class).invoke(builder, "js.nashorn-compat", "true");
            Class<?> hostAccess = loader.loadClass("org.graalvm.polyglot.HostAccess");
            builderClass.getMethod("allowHostAccess", hostAccess).invoke(builder, hostAccess.getField("ALL").get(null));
            builderClass.getMethod("allowHostClassLookup", Predicate.class).invoke(builder, (Predicate<String>) name -> name.startsWith("java.util."));
            try (AutoCloseable context = (AutoCloseable) builderClass.getMethod("build").invoke(builder)) {
                Object result = contextClass.getMethod("eval", String.class, CharSequence.class).invoke(context, "js",
                        "load('nashorn:mozilla_compat.js'); var Map = Java.type('java.util.HashMap'); var m = new Map(); m.put('answer', 6 * 7); m.get('answer');");
                require(result.getClass().getMethod("asInt").invoke(result).equals(42), "Packaged JS/legacy compatibility/host interop failed");
                Object callback = contextClass.getMethod("eval", String.class, CharSequence.class).invoke(context, "js", "(function(x) { return x + 1; })");
                try (var executor = Executors.newSingleThreadExecutor()) {
                    Object workerResult = executor.submit(() -> callback.getClass().getMethod("execute", Object[].class)
                            .invoke(callback, (Object) new Object[]{41})).get();
                    require(workerResult.getClass().getMethod("asInt").invoke(workerResult).equals(42), "Packaged JS callback failed after sequential handoff to another thread");
                }
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        System.out.println("PASS: " + platform + " release contents and isolated packaged GraalJS/Pinyin runtime; " + assertions + " assertions (no loader weaving/game/GPU)");
    }

    private static String read(JarFile jar, String name) throws Exception {
        var entry = jar.getJarEntry(name);
        require(entry != null, "Missing resource " + name);
        try (var input = jar.getInputStream(entry)) { return new String(input.readAllBytes(), StandardCharsets.UTF_8); }
    }
    private static void require(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }
}
