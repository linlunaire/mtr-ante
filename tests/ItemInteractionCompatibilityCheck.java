package cn.zbx1425.mtrsteamloco.compatibility;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ItemInteractionCompatibilityCheck {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        for (String file : List.of("item/CompoundCreator", "item/BlockItemDirectNode", "item/BlockItemEyeCandy", "item/DisplacementTool",
                "item/RailPathEditor", "item/RoutePathCreator", "mixin/ItemRailModifierMixin")) {
            String migrated = Files.readString(root.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/" + file + ".java"));
            require(!migrated.contains("InteractionResultHolder"), "Removed result holder survived in " + file);
            if (migrated.contains("public void appendHoverText")) {
                require(migrated.contains("java.util.function.Consumer<Component> list"), "Legacy tooltip signature in " + file);
                require(migrated.contains("list.accept("), "Tooltip emissions lost in " + file);
            }
        }
        String sourceCode = """
                package cn.zbx1425.mtrsteamloco.item;
                import net.minecraft.world.InteractionResult;
                import net.minecraft.world.item.Item;
                import net.minecraft.world.item.ItemStack;
                import net.minecraft.world.item.TooltipFlag;
                import net.minecraft.world.level.Level;
                import net.minecraft.world.entity.player.Player;
                import net.minecraft.world.InteractionHand;
                import net.minecraft.network.chat.Component;
                import java.util.List;
                public class ItemApiProbe extends Item {
                    public ItemApiProbe(Properties properties) { super(properties); }
                    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
                        return success(player.getItemInHand(hand));
                    }
                    public static InteractionResult success(ItemStack stack) { return InteractionResult.SUCCESS.heldItemTransformedTo(stack); }
                    public static InteractionResult pass(ItemStack stack) { return InteractionResult.PASS; }
                    @Override public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext tooltipContext, net.minecraft.world.item.component.TooltipDisplay tooltipDisplay, java.util.function.Consumer<Component> list, TooltipFlag flag) {
                        list.accept(Component.literal("first"));
                        list.accept(Component.literal("second"));
                    }
                    public static void unrelated(List<String> list) { list.add("untouched"); }
                }
                """;
        Path output = Files.createTempDirectory(Path.of("."), "item-api-");
        try {
            Path source = output.resolve("ItemApiProbe.java"); Files.writeString(source, sourceCode);
            int status = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-proc:none", "--release", "25",
                    "-classpath", System.getProperty("java.class.path"), "-d", output.toString(), source.toString());
            require(status == 0, "Migrated Item.use/tooltip overrides do not compile against real Minecraft 26.2");
            SharedConstants.tryDetectVersion(); Bootstrap.bootStrap();
            BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(pending -> pending.apply());
            try (var loader = new URLClassLoader(new java.net.URL[]{output.toUri().toURL()}, ItemInteractionCompatibilityCheck.class.getClassLoader())) {
                var probe = loader.loadClass("cn.zbx1425.mtrsteamloco.item.ItemApiProbe");
                var stack = new ItemStack(Items.STICK, 3);
                var success = (InteractionResult.Success) probe.getMethod("success", ItemStack.class).invoke(null, stack);
                require(success.consumesAction() && success.heldItemTransformedTo() == stack && stack.getCount() == 3,
                        "Success lost the held stack/replacement or count");
                require(probe.getMethod("pass", ItemStack.class).invoke(null, stack) == InteractionResult.PASS, "Block hit no longer passes interaction through");
            }
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("PASS: seven real item/mixin sources, actual 26.2 use/tooltip override compilation and held-stack/pass semantics; " + assertions + " assertions");
    }

    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
