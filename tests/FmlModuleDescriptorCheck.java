package cn.zbx1425.mtrsteamloco.compatibility;

import net.neoforged.fml.classloading.JarContentsModule;
import net.neoforged.fml.classloading.JarContentsModuleFinder;
import net.neoforged.fml.jarcontents.JarContents;
import net.neoforged.fml.jarmoduleinfo.JarModuleInfo;

import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolutionException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Exercises FML's actual multi-release package/service scanner, without starting FML or Minecraft. */
public final class FmlModuleDescriptorCheck {
    private static final Set<String> SDK_MODULES = Set.of(
            "org.graalvm.word", "org.graalvm.collections", "org.graalvm.nativeimage", "org.graalvm.truffle.compiler");

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: FmlModuleDescriptorCheck <candidate.jar> [known-broken.jar]");
        }
        long sdkCount = SDK_MODULES.stream().filter(name -> ModuleLayer.boot().findModule(name).isPresent()).count();
        if (args.length == 2 && sdkCount != SDK_MODULES.size()) {
            throw new IllegalStateException("Run this integration check on GraalVM with --add-modules " + String.join(",", SDK_MODULES));
        }
        Configuration parent = ModuleLayer.boot().configuration();
        if (args.length == 2) {
            try {
                resolve(parent, Path.of(args[1]), false);
                throw new AssertionError("Known-broken artifact unexpectedly passed the FML module boundary");
            } catch (ResolutionException expected) {
                if (!expected.getMessage().contains("mtrsteamloco") || !expected.getMessage().contains("org.graalvm.")) {
                    throw new AssertionError("Baseline failed for a different reason", expected);
                }
                System.out.println("EXPECTED BASELINE FAILURE: " + expected.getMessage());
            }
        }
        resolve(parent, Path.of(args[0]), true);
        System.out.println("PASS: actual FML scanner, JPMS resolution, named-module GraalJS and host interop with "
                + sdkCount + " system GraalVM SDK modules (no game/weaving/GPU)");
    }

    private static void resolve(Configuration parent, Path artifact, boolean executeScript) throws Exception {
        try (JarContents contents = JarContents.ofPath(artifact);
             JarContents consumerContents = JarContents.empty(Path.of("fml-descriptor-fixture-neoforge.jar"))) {
            // FML derives the automatic module name of a mod from its mod id, not
            // from the artifact filename. Keep that name while using its real scanner.
            ModuleDescriptor.Builder builder = ModuleDescriptor.newAutomaticModule("mtrsteamloco");
            JarModuleInfo.scanAutomaticModule(contents, builder);
            ModuleDescriptor descriptor = builder.build();
            if (descriptor.provides().stream().noneMatch(service ->
                    service.service().equals("com.oracle.truffle.api.provider.TruffleLanguageProvider"))) {
                throw new AssertionError("FML failed to discover packaged language service providers");
            }
            JarContentsModuleFinder finder = new JarContentsModuleFinder(List.of(
                    new JarContentsModule(contents, descriptor),
                    new JarContentsModule(consumerContents, ModuleDescriptor.newAutomaticModule("neoforge").build())));
            Configuration configuration = Configuration.resolveAndBind(finder, List.of(parent), ModuleFinder.of(), Set.of("mtrsteamloco", "neoforge"));
            System.out.println("Resolved " + artifact.getFileName() + ": " + descriptor.packages().size()
                    + " FML-scanned packages, " + descriptor.provides().size() + " service types");
            if (executeScript) {
                ModuleLayer layer = ModuleLayer.defineModulesWithOneLoader(configuration, List.of(ModuleLayer.boot()),
                        ClassLoader.getPlatformClassLoader()).layer();
                checkNamedModuleScript(layer.findLoader("mtrsteamloco"));
            }
        }
    }

    private static void checkNamedModuleScript(ClassLoader loader) throws Exception {
        ClassLoader previousLoader = Thread.currentThread().getContextClassLoader();
        String previousRuntime = System.getProperty("truffle.TruffleRuntime");
        try {
            // Match ANTE's existing interpreter mixin without claiming to weave it.
            System.setProperty("truffle.TruffleRuntime", "com.oracle.truffle.api.impl.DefaultTruffleRuntime");
            Thread.currentThread().setContextClassLoader(loader);
            Class<?> contextClass = loader.loadClass("org.graalvm.polyglot.Context");
            if (!contextClass.getModule().isNamed() || !contextClass.getModule().getName().equals("mtrsteamloco")) {
                throw new AssertionError("Context was not loaded from the ANTE named module");
            }
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
                        "load('nashorn:mozilla_compat.js'); var Map = Java.type('java.util.HashMap'); var map = new Map(); map.put('answer', 6 * 7); map.get('answer');");
                if (!result.getClass().getMethod("asInt").invoke(result).equals(42)) {
                    throw new AssertionError("Named-module JavaScript/legacy resource/host interop failed");
                }
            }
            System.out.println("PASS: Context is in named module mtrsteamloco; service discovery, legacy JS resource and Java.type return 42");
        } finally {
            Thread.currentThread().setContextClassLoader(previousLoader);
            if (previousRuntime == null) System.clearProperty("truffle.TruffleRuntime");
            else System.setProperty("truffle.TruffleRuntime", previousRuntime);
        }
    }
}
