package cn.zbx1425.mtrsteamloco.compatibility;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Compiles transformed API calls against the actual Minecraft/MTR classpath. */
public final class SourceSymbolCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        Map<String, Object> extensions = new HashMap<>();
        Binding binding = new Binding(Map.of("rootProject", Map.of("ext", extensions)));
        GroovyShell shell = new GroovyShell(binding);
        shell.evaluate("class GradleException extends RuntimeException { GradleException(String message) { super(message) } }\n"
                + Files.readString(root.resolve("versions/26.2/source-port.gradle")));
        Closure<?> transform = (Closure<?>) extensions.get("transformAnte26Source");
        String legacy = """
                package cn.zbx1425.mtrsteamloco.gui;
                import net.minecraft.client.Minecraft;
                import net.minecraft.client.gui.screens.Screen;
                import net.minecraft.client.gui.GuiGraphics;
                import net.minecraft.world.level.Level;
                import net.minecraft.resources.ResourceLocation;
                import net.minecraft.world.phys.Vec3;
                import net.minecraft.network.FriendlyByteBuf;
                public class SymbolProbe {
                    static void widgets(net.minecraft.client.gui.components.Button button, GuiGraphics graphics,
                            net.minecraft.client.gui.Font font, Minecraft minecraft, Screen screen, Runnable task) {
                        button.render(graphics, 0, 0, 0);
                        graphics.drawString(font, "text", 0, 0, -1);
                        graphics.drawCenteredString(font, "text", 0, 0, -1);
                        minecraft.setScreen(screen);
                        Minecraft.getInstance().tell(task);
                    }
                    static void fill(GuiGraphics graphics) { graphics.fill(0, 0, 10, 10, -1); }
                    static ResourceLocation dimension(Level level) { return level.dimension().location(); }
                    static boolean side(Level level) { return level.isClientSide; }
                    static void message(net.minecraft.world.entity.player.Player player) {
                        player.displayClientMessage(net.minecraft.network.chat.Component.literal("progress"), true);
                    }
                    static Vec3 camera() { return Minecraft.getInstance().gameRenderer.getMainCamera().getPosition(); }
                    static void open(Minecraft minecraftClient, Screen screen) {
                        minecraftClient.setScreen(screen);
                        Minecraft.getInstance().setScreen(screen);
                    }
                    static Screen current() { return Minecraft.getInstance().screen; }
                    static ResourceLocation packet(FriendlyByteBuf packet) {
                        packet.writeResourceLocation(ResourceLocation.parse("mtrsteamloco:test"));
                        return packet.readResourceLocation();
                    }
                }
                """;
        String converted = transform.call(legacy).toString();
        require(!converted.contains(".location()") && !converted.contains("getMainCamera()"), "Legacy API calls survived");
        String unrelated = "rail.getPosition(0); camera.getPosition(); screen.setScreen(null); minecraftClient.setScreen(null);";
        require(transform.call(unrelated).equals(unrelated), "Unrelated receivers changed");
        Path output = Files.createTempDirectory(Path.of("."), "symbol-probe-");
        try {
            Path source = output.resolve("SymbolProbe.java");
            Files.writeString(source, converted);
            int status = ToolProvider.getSystemJavaCompiler().run(null, null, null,
                    "-proc:none", "--release", "25", "-classpath", System.getProperty("java.class.path"),
                    "-d", output.toString(), source.toString());
            require(status == 0, "Transformed symbol probe does not compile against Minecraft 26.2");
        } finally {
            try (var paths = Files.walk(output)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("PASS: actual 26.2 GUI, widget, side, overlay-message, dimension, screen, camera and packet symbol compilation; unrelated receivers preserved");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
