package cn.zbx1425.mtrsteamloco.data;

import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.util.InclusiveRange;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class DynamicResource {

    private static MultiPackResourceManager MPRM;
    private static DynamicPack DYNAMIC_PACK;
    private static final Set<String> ADDED_NAMESPACES = new HashSet<>();

    public static void addResourcesClient(Identifier loc, IoSupplier<InputStream> funGetStream) {
        addResources((MultiPackResourceManager) ((ReloadableResourceManager) Minecraft.getInstance().getResourceManager()).resources,
                loc, funGetStream);
    }

    // The manager seam also keeps the real resource lookup test independent of a client window.
    static void addResources(MultiPackResourceManager manager, Identifier loc, IoSupplier<InputStream> funGetStream) {
        if (MPRM != manager) {
            MPRM = manager;
            DYNAMIC_PACK = new DynamicPack();
            MPRM.packs = new ArrayList<>(MPRM.packs);
            MPRM.packs.add(DYNAMIC_PACK);
            ADDED_NAMESPACES.clear();
        }
        DYNAMIC_PACK.addResource(PackType.CLIENT_RESOURCES, loc, funGetStream);
        if (ADDED_NAMESPACES.add(loc.getNamespace())) {
            MPRM.namespacedManagers.computeIfAbsent(loc.getNamespace(), namespace -> new FallbackResourceManager(PackType.CLIENT_RESOURCES, namespace))
                    .push(DYNAMIC_PACK);
        }
    }

    private static class DynamicPack extends AbstractPackResources {
        private static final String NAME = "ANTE Virtual Dynamic Pack";
        private final byte[] metadata;
        private final Map<PackType, Map<Identifier, IoSupplier<InputStream>>> resources = new HashMap<>();
        private final Map<PackType, Set<String>> namespaces = new HashMap<>();

        DynamicPack() {
            super(new PackLocationInfo(NAME, Component.literal(NAME), PackSource.DEFAULT, Optional.empty()));
            final PackMetadataSection section = new PackMetadataSection(Component.literal(NAME),
                    new InclusiveRange<>(SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES)));
            final JsonObject root = new JsonObject();
            root.add(PackMetadataSection.CLIENT_TYPE.name(), PackMetadataSection.CLIENT_TYPE.codec().encodeStart(JsonOps.INSTANCE, section).getOrThrow());
            metadata = root.toString().getBytes(StandardCharsets.UTF_8);
        }

        void addResource(PackType packType, Identifier loc, IoSupplier<InputStream> funGetStream) {
            resources.computeIfAbsent(packType, type -> new HashMap<>()).put(loc, funGetStream);
            namespaces.computeIfAbsent(packType, type -> new HashSet<>()).add(loc.getNamespace());
        }

        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return path.length == 1 && PackResources.PACK_META.equals(path[0]) ? () -> new ByteArrayInputStream(metadata) : null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType packType, Identifier loc) {
            final Map<Identifier, IoSupplier<InputStream>> map = resources.get(packType);
            if (map == null) return null;
            return map.get(loc);
        }

        @Override
        public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput output) {
            final Map<Identifier, IoSupplier<InputStream>> map = resources.get(type);
            if (map == null) return;
            final String prefix = path.isEmpty() || path.endsWith("/") ? path : path + "/";
            for (Map.Entry<Identifier, IoSupplier<InputStream>> entry : map.entrySet()) {
                final Identifier loc = entry.getKey();
                if (loc.getNamespace().equals(namespace) && loc.getPath().startsWith(prefix)) output.accept(loc, entry.getValue());
            }
        }

        @Override
        public Set<String> getNamespaces(PackType packType) {
            return Set.copyOf(namespaces.getOrDefault(packType, Set.of()));
        }

        @Override
        public void close() {
        }
    }
}
