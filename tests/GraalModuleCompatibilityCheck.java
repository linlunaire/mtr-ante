package cn.zbx1425.mtrsteamloco.compatibility;

import java.io.IOException;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Reproduces FML's JPMS package-resolution boundary, without starting a game. */
public final class GraalModuleCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        Path artifact = Path.of(args[0]);
        // The JDK scanner includes multi-release classes and service metadata.
        ModuleDescriptor packaged = ModuleFinder.of(artifact).findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Missing module artifact: " + artifact)).descriptor();
        if (!packaged.isAutomatic()) throw new AssertionError("ANTE must remain an automatic mod module");
        Set<String> packages = packaged.packages();
        // Real modules on GraalVM; exported-package fixtures keep the regression
        // effective on ordinary JDKs too, including the less common SDK packages.
        Map<String, Set<String>> sdkPackages = Map.of(
                "org.graalvm.word", Set.of("org.graalvm.word", "org.graalvm.word.impl"),
                "org.graalvm.collections", Set.of("org.graalvm.collections"),
                "org.graalvm.nativeimage", Set.of("org.graalvm.nativeimage", "com.oracle.svm.core.annotate"),
                "org.graalvm.truffle.compiler", Set.of("com.oracle.truffle.compiler", "com.oracle.truffle.compiler.hotspot", "com.oracle.truffle.compiler.hotspot.libgraal"));
        ModuleFinder system = ModuleFinder.ofSystem();
        ModuleFinder sdk = finder(sdkPackages.entrySet().stream().map(entry -> system.find(entry.getKey()).orElseGet(() -> {
            var builder = ModuleDescriptor.newModule(entry.getKey());
            entry.getValue().forEach(builder::exports);
            return reference(builder.build());
        })).toArray(ModuleReference[]::new));
        Configuration boot = ModuleLayer.boot().configuration();
        ModuleFinder missingSdk = finder(sdk.findAll().stream().filter(ref -> boot.findModule(ref.descriptor().name()).isEmpty()).toArray(ModuleReference[]::new));
        Configuration parent = Configuration.resolve(missingSdk, List.of(boot), system, sdkPackages.keySet());
        ModuleReference ante = reference(ModuleDescriptor.newAutomaticModule("mtrsteamloco").packages(packages).build());
        // All automatic FML mod modules read the resolved parent modules. One
        // consumer is enough to reproduce the user's neoforge split-package error.
        ModuleReference consumer = reference(ModuleDescriptor.newAutomaticModule("neoforge").build());
        parent.resolve(finder(ante, consumer), ModuleFinder.of(), Set.of("mtrsteamloco", "neoforge"));
        // Qualified exports may not fail this small graph, but must not be
        // duplicated either: other real FML modules can read those packages.
        for (ModuleReference module : ModuleFinder.compose(system, sdk).findAll()) {
            Set<String> overlap = new HashSet<>(module.descriptor().packages());
            overlap.retainAll(packages);
            if (!overlap.isEmpty()) throw new AssertionError("System module " + module.descriptor().name() + " overlaps ANTE: " + overlap);
        }
        System.out.println("PASS: ANTE artifact resolves beside GraalVM SDK modules; no system-module package overlap (real JPMS resolver, no game)");
    }

    private static ModuleReference reference(ModuleDescriptor descriptor) {
        return new ModuleReference(descriptor, URI.create("memory:/" + descriptor.name())) {
            @Override public ModuleReader open() throws IOException { throw new IOException("Descriptor-only resolution fixture"); }
        };
    }
    private static ModuleFinder finder(ModuleReference... references) {
        Set<ModuleReference> all = Set.of(references);
        return new ModuleFinder() {
            @Override public Optional<ModuleReference> find(String name) { return all.stream().filter(ref -> ref.descriptor().name().equals(name)).findFirst(); }
            @Override public Set<ModuleReference> findAll() { return all; }
        };
    }
}
