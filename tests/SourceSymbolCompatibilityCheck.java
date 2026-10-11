package cn.zbx1425.mtrsteamloco.compatibility;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

/** Compiles the supported API surface against the actual Minecraft/MTR classpath. */
public final class SourceSymbolCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        String sourceCode = """
                package cn.zbx1425.mtrsteamloco.gui;
                import net.minecraft.client.Minecraft;
                import net.minecraft.client.gui.screens.Screen;
                import net.minecraft.client.gui.GuiGraphicsExtractor;
                import net.minecraft.world.level.Level;
                import net.minecraft.resources.Identifier;
                import net.minecraft.world.phys.Vec3;
                import net.minecraft.network.FriendlyByteBuf;
                public class SymbolProbe {
                    static void widgets(net.minecraft.client.gui.components.Button button, GuiGraphicsExtractor graphics,
                            net.minecraft.client.gui.Font font, Minecraft minecraft, Screen screen, Runnable task) {
                        button.extractRenderState(graphics, 0, 0, 0);
                        graphics.text(font, "text", 0, 0, -1);
                        graphics.centeredText(font, "text", 0, 0, -1);
                        minecraft.gui.setScreen(screen);
                        Minecraft.getInstance().schedule(task);
                    }
                    static void fill(GuiGraphicsExtractor graphics) { graphics.fill(0, 0, 10, 10, -1); }
                    static Identifier dimension(Level level) { return level.dimension().identifier(); }
                    static boolean side(Level level) { return level.isClientSide(); }
                    static void message(net.minecraft.world.entity.player.Player player) {
                        player.sendOverlayMessage(net.minecraft.network.chat.Component.literal("progress"));
                    }
                    static Vec3 camera() { return Minecraft.getInstance().gameRenderer.mainCamera().position(); }
                    static void open(Minecraft minecraftClient, Screen screen) {
                        minecraftClient.gui.setScreen(screen);
                        Minecraft.getInstance().gui.setScreen(screen);
                    }
                    static Screen current() { return Minecraft.getInstance().gui.screen(); }
                    static Identifier packet(FriendlyByteBuf packet) {
                        packet.writeIdentifier(Identifier.parse("mtrsteamloco:test"));
                        return packet.readIdentifier();
                    }
                }
                """;
        Path output = Files.createTempDirectory(Path.of("."), "symbol-probe-");
        try {
            Path source = output.resolve("SymbolProbe.java");
            Files.writeString(source, sourceCode);
            int status = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-proc:none", "--release", "25", "-classpath", System.getProperty("java.class.path"),
                    "-d", output.toString(), source.toString());
            require(status == 0, "Supported symbol probe does not compile against Minecraft 26.2");
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("PASS: actual 26.2 GUI, widget, side, overlay-message, dimension, screen, camera and packet symbol compilation");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
