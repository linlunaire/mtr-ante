package cn.zbx1425.mtrsteamloco.data;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceMetadata;

import com.mojang.serialization.Codec;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Runs actual 26.2 resource-manager lookup without creating Minecraft or an OpenGL window. */
public final class DynamicResourceCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        final Identifier overridden = id("existing", "textures/test.txt");
        final Identifier untouched = id("existing", "textures/base.txt");
        final PackResources baseline = new FixturePack(Map.of(overridden, "base", untouched, "untouched"));
        try (MultiPackResourceManager first = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(baseline))) {
            final Identifier added = id("new_namespace", "textures/added.txt");
            DynamicResource.addResources(first, added, stream("new resource"));
            require(first.getResource(added).isPresent(), "An existing dynamic resource must be returned by the actual manager");
            require(read(first.getResource(added).orElseThrow()).equals("new resource"), "Dynamic stream contents changed");
            final PackResources dynamic = first.listPacks().filter(pack -> pack != baseline).findFirst().orElseThrow();
            require(dynamic.getResource(PackType.SERVER_DATA, added) == null, "An absent pack type must return null instead of throwing");
            require(dynamic.getResource(PackType.CLIENT_RESOURCES, id("new_namespace", "missing.txt")) == null, "An absent resource must return null");
            require(first.getResource(id("absent", "missing.txt")).isEmpty(), "An absent namespace must remain absent");
            require(first.getNamespaces().contains("new_namespace"), "New namespaces must become visible to the manager");

            DynamicResource.addResources(first, overridden, stream("dynamic override"));
            require(read(first.getResource(overridden).orElseThrow()).equals("dynamic override"), "The dynamic pack must keep highest priority");
            require(read(first.getResource(untouched).orElseThrow()).equals("untouched"), "Unmodified lower-priority resources must remain available");
            require(first.getResourceStack(overridden).size() == 2, "Both original and dynamic resources must remain in the stack");
            DynamicResource.addResources(first, overridden, stream("replacement"));
            require(read(first.getResource(overridden).orElseThrow()).equals("replacement"), "Repeated additions must replace the supplier");
            require(first.getResourceStack(overridden).size() == 2 && first.listPacks().count() == 2,
                    "Repeated additions must not push the same dynamic pack twice");

            final Identifier nested = id("new_namespace", "textures/sub/nested.txt");
            final Identifier sibling = id("new_namespace", "textures_extra/sibling.txt");
            DynamicResource.addResources(first, nested, stream("nested"));
            DynamicResource.addResources(first, sibling, stream("sibling"));
            final Map<Identifier, IoSupplier<InputStream>> listed = new HashMap<>();
            dynamic.listResources(PackType.CLIENT_RESOURCES, "new_namespace", "textures", listed::put);
            require(listed.keySet().equals(Set.of(added, nested)), "Directory listing must exclude sibling prefixes and other namespaces");
            listed.clear();
            dynamic.listResources(PackType.CLIENT_RESOURCES, "new_namespace", "textures/", listed::put);
            require(listed.keySet().equals(Set.of(added, nested)), "A directory with a trailing slash must list the same resources");
            require(first.listResources("textures", loc -> loc.getNamespace().equals("new_namespace") && loc.getPath().endsWith("added.txt")).keySet().equals(Set.of(added)),
                    "Manager listing must preserve the requested resource predicate");
            listed.clear();
            dynamic.listResources(PackType.SERVER_DATA, "new_namespace", "", listed::put);
            require(listed.isEmpty(), "An absent pack type must list no resources");
            require(dynamic.getNamespaces(PackType.SERVER_DATA).isEmpty(), "An absent pack type must expose no namespaces");

            require(dynamic.getRootResource("pack.png") == null && dynamic.getRootResource("assets", "pack.mcmeta") == null,
                    "Only root pack.mcmeta may be synthesized");
            final PackMetadataSection metadata = dynamic.getMetadataSection(PackMetadataSection.CLIENT_TYPE);
            require(metadata != null && metadata.supportedFormats().isValueInRange(SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES)),
                    "Metadata must accept the running 26.2 resource-pack format");
            require(metadata.description().getString().equals("ANTE Virtual Dynamic Pack"), "Pack description changed");
            try (InputStream input = dynamic.getRootResource(PackResources.PACK_META).get()) {
                require(ResourceMetadata.fromJsonStream(input).getSection(PackMetadataSection.CLIENT_TYPE).orElseThrow().equals(metadata),
                        "Root pack.mcmeta and typed metadata must decode identically");
            }
            require(dynamic.getMetadataSection(new MetadataSectionType<>("absent", Codec.STRING)) == null, "Unknown metadata must be absent");

            try (MultiPackResourceManager reloaded = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(baseline))) {
                final Identifier afterReload = id("after_reload", "textures/new.txt");
                DynamicResource.addResources(reloaded, afterReload, stream("reloaded"));
                require(reloaded.getResource(added).isEmpty() && !reloaded.getNamespaces().contains("new_namespace"), "Reload must not leak old dynamic resources or namespaces");
                require(read(reloaded.getResource(overridden).orElseThrow()).equals("base"), "Reload must not retain old dynamic overrides");
                require(read(reloaded.getResource(afterReload).orElseThrow()).equals("reloaded"), "The reloaded manager must accept new dynamic resources");
                require(first.getResource(afterReload).isEmpty() && read(first.getResource(added).orElseThrow()).equals("new resource"),
                        "New resource additions must not mutate the previous manager's dynamic pack");
                require(reloaded.listPacks().count() == 2 && first.listPacks().count() == 2, "Reload must attach exactly one pack to each manager");
            }
        }
        System.out.println("DynamicResource compatibility checks passed (lookup, priority, namespaces, listing, reload isolation, metadata).");
    }

    private static Identifier id(String namespace, String path) {
        return Identifier.fromNamespaceAndPath(namespace, path);
    }

    private static IoSupplier<InputStream> stream(String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(bytes);
    }

    private static String read(Resource resource) throws Exception {
        try (InputStream input = resource.open()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class FixturePack extends AbstractPackResources {
        private final Map<Identifier, String> contents;

        FixturePack(Map<Identifier, String> contents) {
            super(new PackLocationInfo("baseline", Component.literal("Baseline"), PackSource.DEFAULT, Optional.empty()));
            this.contents = contents;
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... path) { return null; }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, Identifier loc) {
            return type == PackType.CLIENT_RESOURCES && contents.containsKey(loc) ? stream(contents.get(loc)) : null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type == PackType.CLIENT_RESOURCES) contents.forEach((loc, value) -> {
                if (loc.getNamespace().equals(namespace) && (path.isEmpty() || loc.getPath().startsWith(path + "/"))) output.accept(loc, stream(value));
            });
        }

        @Override
        public Set<String> getNamespaces(PackType type) { return type == PackType.CLIENT_RESOURCES ? Set.of("existing") : Set.of(); }

        @Override
        public void close() { }
    }
}
