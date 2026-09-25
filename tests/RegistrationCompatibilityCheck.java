package cn.zbx1425.mtrsteamloco.compatibility;

import mtr.RegistryObject;
import mtr.mappings.RegistrationContext;
import net.minecraft.SharedConstants;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Standalone: real MC 26.2 libraries and MTR's common dev JAR. */
public final class RegistrationCompatibilityCheck {

    public static void main(String[] args) throws Exception {
        final Path root = Path.of(args.length == 0 ? "." : args[0]);
        final String main = read(root, "common/src/main/java/cn/zbx1425/mtrsteamloco/Main.java");
        final Map<String, String> expected = new LinkedHashMap<>();
        final Matcher registrations = Pattern.compile("registries\\.register(?:BlockAndItem|Block|Item)\\(\"([^\"]+)\",\\s*([A-Z0-9_]+)").matcher(main);
        while (registrations.find()) {
            final String previous = expected.put(registrations.group(2), registrations.group(1));
            require(previous == null || previous.equals(registrations.group(1)), "Conflicting ANTE registration ids");
        }
        require(expected.size() == 10, "Review the ANTE registration fixtures after adding/removing blocks or items");
        require(main.contains("MOD_ID = \"mtrsteamloco\""), "ANTE namespace fixture changed");
        final String transformed = main;
        final Map<String, String> actual = new LinkedHashMap<>();
        final Matcher fields = Pattern.compile("RegistryObject<(?:Block|Item|ItemWithCreativeTabBase)>\\s+([A-Z0-9_]+)\\s*=\\s*new RegistryObject<>\\(\\(\\) -> mtr\\.mappings\\.RegistrationContext\\.construct\\(net\\.minecraft\\.resources\\.Identifier\\.fromNamespaceAndPath\\(MOD_ID, \"([^\"]+)\"\\), ").matcher(transformed);
        while (fields.find()) actual.put(fields.group(1), fields.group(2));
        require(actual.equals(expected), "Generated contexts do not exactly match Main.init registrations");
        require(!transformed.contains("new RegistryObject<>(\""), "ANTE must not use MTR's namespace-bound constructor");
        require(transformed.contains("BLOCK_EYE_CANDY.get()"), "Nested eye-candy block construction was lost");
        require(transformed.contains("BLOCK_DIRECT_NODE.get()"), "Nested direct-node block construction was lost");
        checkServerS2CRegistration(main);
        checkRegistrationLineEndings(main);

        for (String type : new String[]{"EyeCandy", "DirectNode"}) {
            final String source = read(root, "common/src/main/java/cn/zbx1425/mtrsteamloco/item/BlockItem" + type + ".java");
            final String result = source;
            require(!result.contains("RegistryUtilities.createItemProperties("), type + " block item has no explicit item id");
            require(result.contains("RegistrationContext.blockItemProperties(net.minecraft.resources.Identifier.fromNamespaceAndPath("), type + " block item has no namespace-aware properties");
            require(result.contains("MOD_ID, \"" + expected.get(type.equals("EyeCandy") ? "ITEM_EYE_CANDY" : "ITEM_DIRECT_NODE") + "\"), block)"), type + " block item changed its id");
        }
        for (String platform : new String[]{"fabric", "neoforge"}) {
            final String packageName = platform.equals("neoforge") ? "forge" : platform;
            final String result = read(root, platform + "/src/main/java/cn/zbx1425/mtrsteamloco/" + packageName + "/RegistriesWrapperImpl.java");
            require(result.contains("RegistrationContext.blockItemProperties(net.minecraft.resources.Identifier.fromNamespaceAndPath(Main.MOD_ID, id), block.get())"), platform + " generated block item uses the wrong namespace");
            require(!result.contains("RegistryUtilities.createItemProperties("), platform + " generated block item bypasses explicit properties");
        }

        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Vanilla registries are frozen. Exercise the real constructor properties
        // without unfreezing them or substituting fake Minecraft implementations.
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            final Identifier id = id(entry.getValue());
            if (entry.getKey().startsWith("BLOCK_")) {
                final RegistryObject<BlockBehaviour.Properties> object = new RegistryObject<>(() -> RegistrationContext.construct(id, () -> RegistrationContext.blockProperties(BlockBehaviour.Properties.of())));
                require(description(object.get()).equals("block.mtrsteamloco." + entry.getValue()), "Block registration namespace changed: " + entry.getKey());
            } else {
                final RegistryObject<Item.Properties> object = new RegistryObject<>(() -> RegistrationContext.construct(id, RegistrationContext::itemProperties));
                require(object.get().effectiveModel().equals(id), "Item model namespace changed: " + entry.getKey());
                require(description(object.get()).equals("item.mtrsteamloco." + entry.getValue()), "Item translation namespace changed: " + entry.getKey());
            }
        }
        final AtomicInteger calls = new AtomicInteger();
        final RegistryObject<Item.Properties> lazy = new RegistryObject<>(() -> RegistrationContext.construct(id("cached"), () -> {
            calls.incrementAndGet();
            return RegistrationContext.itemProperties();
        }));
        require(calls.get() == 0 && lazy.get() == lazy.get() && calls.get() == 1, "Wrapping suppliers broke RegistryObject laziness or caching");
        RegistrationContext.construct(id("outer_item"), () -> {
            final RegistryObject<BlockBehaviour.Properties> nested = new RegistryObject<>(() -> RegistrationContext.construct(id("inner_block"), () -> RegistrationContext.blockProperties(BlockBehaviour.Properties.of())));
            try {
                require(description(nested.get()).equals("block.mtrsteamloco.inner_block"), "Nested block inherited its item's id");
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            require(RegistrationContext.itemProperties().effectiveModel().equals(id("outer_item")), "Nested construction did not restore the outer id");
            try {
                RegistrationContext.construct(id("throwing"), () -> { throw new IllegalStateException("expected"); });
                throw new AssertionError("Supplier exception was swallowed");
            } catch (IllegalStateException expectedFailure) {
                require(expectedFailure.getMessage().equals("expected"), "Unexpected supplier exception");
            }
            require(RegistrationContext.itemProperties().effectiveModel().equals(id("outer_item")), "Throwing nested construction did not restore the outer id");
            final AtomicReference<Throwable> failure = new AtomicReference<>();
            final Thread thread = Thread.ofPlatform().start(() -> {
                try { expectNoContext(); } catch (Throwable exception) { failure.set(exception); }
            });
            try { thread.join(); } catch (InterruptedException exception) { throw new AssertionError(exception); }
            require(failure.get() == null, "Registration context leaked into another thread: " + failure.get());
            return null;
        });
        expectNoContext();
        try {
            RegistrationContext.construct(id("failure"), () -> { throw new IllegalStateException("expected"); });
            throw new AssertionError("Supplier exception was swallowed");
        } catch (IllegalStateException expectedFailure) {
            require(expectedFailure.getMessage().equals("expected"), "Unexpected supplier exception");
        }
        expectNoContext();
        for (String path : new String[]{"eye_candy", "direct_node", "departure_bell"}) {
            final Item.Properties properties = RegistrationContext.blockItemProperties(id(path), Blocks.STONE);
            require(properties.effectiveModel().equals(id(path)), "Block item model id changed: " + path);
            require(description(properties).equals(Blocks.STONE.getDescriptionId()), "Block item lost its block description: " + path);
        }
        System.out.println("PASS: 10 ANTE supplier ids, four block-item constructor hooks, early S2C source hook, lazy cache and real 26.2 registration properties/context isolation (not loader networking runtime)");
    }

    private static void checkServerS2CRegistration(String source) {
        // Git may check out the same Java source with CRLF on Windows.
        source = source.replace("\r\n", "\n");
        require(source.contains("public static void init(RegistriesWrapper registries) {\n\t\tmtr.mappings.NetworkUtilities.registerServerS2CTypes(dev.architectury.platform.Platform.getEnvironment(),\n\t\t\t\tPacketVersionCheck.PACKET_VERSION_CHECK, PacketScreen.PACKET_SHOW_SCREEN, PacketRoutePathCreator.ROUTE_S2C);"),
                "Dedicated-server S2C types must be registered at the start of Main.init");
        final int registration = source.indexOf("registerServerS2CTypes(");
        final int joinCallback = source.indexOf("registerPlayerJoinEvent(");
        require(joinCallback >= 0 && registration < joinCallback, "S2C registration happens after the first send callback");
    }

    private static void checkRegistrationLineEndings(String source) {
        final String lf = source.replace("\r\n", "\n");
        for (String candidate : new String[]{lf, lf.replace("\n", "\r\n")}) {
            checkServerS2CRegistration(candidate);
            expectRegistrationFailure(candidate.replace("PacketRoutePathCreator.ROUTE_S2C);", ");"));
            expectRegistrationFailure(candidate.replace("public static void init(RegistriesWrapper registries) {",
                    "public static void init(RegistriesWrapper registries) { registerPlayerJoinEvent(player -> {});"));
        }
    }

    private static void expectRegistrationFailure(String source) {
        boolean rejected = false;
        try { checkServerS2CRegistration(source); } catch (AssertionError expected) { rejected = true; }
        require(rejected, "Source check accepted missing S2C types or registration after a send callback");
    }

    private static String description(Object properties) throws Exception {
        final var method = properties.getClass().getDeclaredMethod("effectiveDescriptionId");
        method.setAccessible(true);
        return (String) method.invoke(properties);
    }

    private static void expectNoContext() {
        try {
            RegistrationContext.itemProperties();
            throw new AssertionError("Registration context leaked outside construction");
        } catch (NullPointerException expected) {
            require(expected.getMessage().contains("without its registration identifier"), "Unexpected missing-context error");
        }
    }

    private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("mtrsteamloco", path); }
    private static String read(Path root, String path) throws Exception { return Files.readString(root.resolve(path)); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
