package cn.zbx1425.mtrsteamloco.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.texture.atlas.SpriteSource;
import net.minecraft.client.renderer.texture.atlas.SpriteSources;
import net.minecraft.client.resources.model.ClientItemInfoLoader;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.Resource;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Exercises Minecraft's real client-item resource loader, without a Minecraft instance or GPU. */
public final class EyeCandyItemResourceCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ItemModels.bootstrap();
        SpriteSources.bootstrap();
        final var fixture = new FixturePack(Map.of(
                id("mtrsteamloco:eyecandies/sign.json"), """
                    {"model":"example:sign.obj","itemModel":"example:icons/sign.png"}
                    """,
                id("mtrsteamloco:eyecandies/group.json"), """
                    {"group":"Test", "First":{"itemModel":"example:sign"},
                     "Raw":{"itemModel":"example:raw.obj"}, "NoItem":{},
                     "DotFolder":{"itemModel":"example:folder.v2/sign"},
                     "Second":{"itemModel":"example:folder.png/sign.png"},
                     "Duplicate":{"itemModel":"example:icons/sign.png"}}
                    """,
                id("mtrsteamloco:eyecandies/scripts.json"), """
                    {"scriptFiles":[],"itemModel":"example:script_icon"}
                    """,
                id("other:eyecandies/ignored.json"), """
                    {"model":"x.obj","itemModel":"other:ignored"}
                    """,
                id("example:icons/sign.png"), "base png",
                id("example:folder.png/sign.png"), "second png"));
        final var higherPack = new FixturePack(Map.of(
                id("mtrsteamloco:eyecandies/group.json"), """
                    {"Third":{"itemModel":"example:upper"}}
                    """,
                id("example:icons/sign.png"), "override png",
                // The virtual item must not overwrite another pack's real item definition.
                id("example:items/sign.json"), """
                    {"model":{"type":"minecraft:model","model":"example:other_model"}}
                    """));
        try (var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(fixture, higherPack))) {
            require(!ClientItemInfoLoader.scheduleLoad(manager, Runnable::run).join().contents()
                    .containsKey(id("dynamic___item__example:icons/sign_png")),
                    "The unprepared fixture must reproduce the missing client-item definition");
            EyeCandyItemResources.prepare(PackType.CLIENT_RESOURCES, manager);
            final Map<Identifier, ClientItem> items = ClientItemInfoLoader.scheduleLoad(manager, Runnable::run).join().contents();
            require(items.containsKey(id("dynamic___item__example:icons/sign_png")),
                    "PNG eye-candy must be discoverable by the 26.2 client-item loader before model baking");
            require(items.size() == 7, "Expected six virtual items and the untouched real item: " + items.keySet());
            checkModel(items, "dynamic___item__example:icons/sign_png", "dynamic___item__example:item/icons/sign_png");
            checkModel(items, "dynamic___item__example:folder.png/sign_png", "dynamic___item__example:item/folder.png/sign_png");
            checkModel(items, "dynamic___model__example:sign", "example:item/sign");
            checkModel(items, "dynamic___model__example:folder.v2/sign", "example:item/folder.v2/sign");
            checkModel(items, "dynamic___model__example:script_icon", "example:item/script_icon");
            checkModel(items, "dynamic___model__example:upper", "example:item/upper");
            checkModel(items, "example:sign", "example:other_model");
            require(EyeCandyItemResources.clientItemId(id("example:raw.obj")) == null, "Raw models must stay on the ANTE path");
            require(EyeCandyItemResources.clientItemId(id("example:folder.png/sign.png")).equals(id("dynamic___item__example:folder.png/sign_png")),
                    "Only the final PNG suffix may be replaced");

            try (var reader = manager.getResourceOrThrow(id("dynamic___item__example:models/item/icons/sign_png.json")).openAsReader()) {
                final var model = CuboidModel.fromStream(reader);
                require(model.parent().equals(id("minecraft:item/generated")), "PNG must inherit generated geometry and display transforms");
                require(model.textureSlots().values().containsKey("layer0"), "Generated PNG lost its texture layer");
            }
            checkAtlas(manager, "override png");
            EyeCandyItemResources.prepare(PackType.CLIENT_RESOURCES, manager);
            require(manager.listPacks().count() == 3, "Repeated preparation added duplicate virtual packs");
            require(manager.getResourceStack(id("dynamic___item__example:items/icons/sign_png.json")).size() == 1,
                    "Repeated preparation pushed the same virtual pack twice");

            // A new reload must not reuse old definitions or old texture suppliers.
            try (var next = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(new FixturePack(Map.of(
                    id("mtrsteamloco:eyecandies/next.json"), """
                        {"model":"example:x.obj","itemModel":"example:icons/sign.png"}
                        """,
                    id("example:icons/sign.png"), "next png"))))) {
                EyeCandyItemResources.prepare(PackType.CLIENT_RESOURCES, next);
                final var nextItems = ClientItemInfoLoader.scheduleLoad(next, Runnable::run).join().contents();
                require(nextItems.size() == 1, "Removed prefabs leaked into a new reload");
                checkAtlas(next, "next png");
                checkAtlas(manager, "override png");
            }
        }
        try (var server = new MultiPackResourceManager(PackType.SERVER_DATA, List.of())) {
            EyeCandyItemResources.prepare(PackType.SERVER_DATA, server);
            require(server.listPacks().count() == 0, "Server data must not receive client item resources");
        }
        checkReloadHook();
        System.out.println("PASS: real client-item loader, six virtual models, PNG geometry/atlas discovery, pack priority, deduplication, reload isolation, server exclusion and static reload-hook contract (not loader weaving)");
    }

    private static void checkModel(Map<Identifier, ClientItem> items, String itemId, String modelId) {
        final Set<Identifier> dependencies = new HashSet<>();
        require(items.containsKey(id(itemId)), "Missing virtual item " + itemId);
        items.get(id(itemId)).model().resolveDependencies(dependencies::add);
        require(dependencies.equals(Set.of(id(modelId))), "Wrong model dependency for " + itemId + ": " + dependencies);
    }

    private static void checkAtlas(MultiPackResourceManager manager, String expectedBytes) throws Exception {
        // Use the actual vanilla items atlas's directory source, not a guessed texture prefix.
        try (var stream = EyeCandyItemResourceCompatibilityCheck.class.getClassLoader().getResourceAsStream("assets/minecraft/atlases/items.json")) {
            require(stream != null, "Minecraft items atlas fixture is missing");
            final var atlas = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            final var directory = atlas.getAsJsonArray("sources").asList().stream()
                    .filter(value -> value.getAsJsonObject().get("type").getAsString().equals("minecraft:directory")).findFirst().orElseThrow();
            final var source = SpriteSources.CODEC.parse(JsonOps.INSTANCE, directory).getOrThrow();
            final Map<Identifier, Resource> sprites = new HashMap<>();
            source.run(manager, new SpriteSource.Output() {
                @Override public void add(Identifier id, Resource resource) { sprites.put(id, resource); }
                @Override public void add(Identifier id, SpriteSource.DiscardableLoader loader) { throw new AssertionError("Expected direct directory resources"); }
                @Override public void removeAll(Predicate<Identifier> predicate) { sprites.keySet().removeIf(predicate); }
            });
            final Resource texture = sprites.get(id("dynamic___item__example:item/icons/sign_png"));
            require(texture != null, "The real items atlas directory source cannot discover the dynamic PNG");
            try (InputStream input = texture.open()) {
                require(new String(input.readAllBytes(), StandardCharsets.UTF_8).equals(expectedBytes), "Wrong texture pack priority or reload manager");
            }
        }
    }

    private static void checkReloadHook() throws Exception {
        final ClassNode hook = readClass("cn/zbx1425/mtrsteamloco/mixin/ModelBakeryMixin");
        final var mixin = hook.invisibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        require(mixin.values.contains(List.of(Type.getType(MultiPackResourceManager.class))), "Mixin no longer targets the prepared manager");
        final var method = hook.methods.stream().filter(m -> m.name.equals("ante$prepareItemResources")).findFirst().orElseThrow();
        final var injection = method.visibleAnnotations.stream().filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")).findFirst().orElseThrow();
        require(injection.values.contains(List.of("<init>")), "Hook must run at manager construction");
        final var ats = (List<?>) injection.values.get(injection.values.indexOf("at") + 1);
        require(((org.objectweb.asm.tree.AnnotationNode) ats.getFirst()).values.contains("TAIL"), "Hook must run after pack managers exist");
        require(method.desc.equals("(Lnet/minecraft/server/packs/PackType;Ljava/util/List;Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo;)V"), "Constructor callback signature changed");
        boolean prepares = false;
        for (var instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.owner.equals("cn/zbx1425/mtrsteamloco/data/EyeCandyItemResources") && call.name.equals("prepare")) prepares = true;
        }
        require(prepares, "The mixin must invoke the production resource preparation path");
        MultiPackResourceManager.class.getConstructor(PackType.class, List.class);
        final ClassNode reload = readClass("net/minecraft/server/packs/resources/ReloadableResourceManager");
        final var createReload = reload.methods.stream().filter(m -> m.name.equals("createReload")).findFirst().orElseThrow();
        boolean constructed = false, scheduled = false;
        for (var instruction : createReload.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.owner.equals("net/minecraft/server/packs/resources/MultiPackResourceManager") && call.name.equals("<init>")) constructed = true;
                if (call.owner.equals("net/minecraft/server/packs/resources/SimpleReloadInstance") && call.name.equals("create")) {
                    require(constructed, "Listeners started before dynamic resource preparation");
                    scheduled = true;
                }
            }
        }
        require(scheduled, "Recheck Minecraft's reload lifecycle; the inspected listener call changed");
    }

    private static ClassNode readClass(String name) throws Exception {
        try (InputStream input = EyeCandyItemResourceCompatibilityCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            final ClassNode node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static Identifier id(String value) { return Identifier.parse(value); }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class FixturePack extends AbstractPackResources {
        private final Map<Identifier, String> contents;

        FixturePack(Map<Identifier, String> contents) {
            super(new PackLocationInfo("fixture", Component.literal("fixture"), PackSource.DEFAULT, Optional.empty()));
            this.contents = contents;
        }

        @Override public IoSupplier<InputStream> getRootResource(String... path) { return null; }

        @Override public IoSupplier<InputStream> getResource(PackType type, Identifier loc) {
            return type == PackType.CLIENT_RESOURCES && contents.containsKey(loc)
                    ? () -> new ByteArrayInputStream(contents.get(loc).getBytes(StandardCharsets.UTF_8)) : null;
        }

        @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
            if (type == PackType.CLIENT_RESOURCES) contents.forEach((loc, value) -> {
                if (loc.getNamespace().equals(namespace) && (path.isEmpty() || loc.getPath().startsWith(path + "/"))) {
                    output.accept(loc, getResource(type, loc));
                }
            });
        }

        @Override public Set<String> getNamespaces(PackType type) {
            return type == PackType.CLIENT_RESOURCES
                    ? contents.keySet().stream().map(Identifier::getNamespace).collect(Collectors.toSet()) : Set.of();
        }

        @Override public void close() { }
    }
}
