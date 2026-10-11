package cn.zbx1425.mtrsteamloco.compatibility;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.zip.ZipFile;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.accesstransformer.api.AccessTransformerEngine;
import org.antlr.v4.runtime.CharStreams;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

/** Runs the released factory with a private vanilla state field and the JAR's actual NeoForge ATs. */
public final class RenderTypeAccessCheck {
    private static final String FACTORY = "cn.zbx1425.sowcer.shader.BlazeRenderType";
    private static final String RENDER_TYPE = "net.minecraft.client.renderer.RenderType";

    public static void premain(String jarPath, Instrumentation instrumentation) throws Exception {
        var engine = AccessTransformerEngine.newEngine();
        try (var jar = new ZipFile(Path.of(jarPath).toFile())) {
            var at = jar.getEntry("META-INF/accesstransformer.cfg");
            if (at != null) {
                try (var input = jar.getInputStream(at)) {
                    engine.loadAT(CharStreams.fromStream(input, StandardCharsets.UTF_8));
                }
            }
        }
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                    ProtectionDomain domain, byte[] bytes) {
                if (name == null) return null;
                boolean composite = name.equals("net/minecraft/client/renderer/RenderType$CompositeRenderType");
                if (!composite && !engine.getTargets().contains(Type.getObjectType(name))) return null;
                var node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                // Restore the exact private vanilla field hidden by Loom's development widening.
                if (composite) {
                    var state = node.fields.stream().filter(field -> field.name.equals("state")).findFirst().orElseThrow();
                    state.access = (state.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) | Opcodes.ACC_PRIVATE;
                }
                engine.transform(node, Type.getObjectType(name));
                var writer = new ClassWriter(0);
                node.accept(writer);
                return writer.toByteArray();
            }
        });
    }

    public static void main(String[] args) throws Exception {
        // NeoForge's registry bootstrap normally gets its mod list from the launcher.
        var loadingMods = Class.forName("net.neoforged.fml.loading.LoadingModList");
        loadingMods.getMethod("of", java.util.List.class, java.util.List.class, java.util.List.class,
                java.util.List.class, java.util.Map.class).invoke(null, java.util.List.of(), java.util.List.of(),
                java.util.List.of(), java.util.List.of(), java.util.Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        try (var loader = new URLClassLoader(new java.net.URL[]{Path.of(args[0]).toUri().toURL()},
                RenderTypeAccessCheck.class.getClassLoader()) {
                @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    if (!name.equals(FACTORY)) return super.loadClass(name, resolve);
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) type = findClass(name);
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
            }) {
            var factory = loader.loadClass(FACTORY);
            var vanilla = loader.loadClass(RENDER_TYPE);
            var texture = ResourceLocation.fromNamespaceAndPath("mtrsteamloco", "test/texture.png");
            for (String name : new String[]{"entityCutout", "entityTranslucentCull", "beaconBeam"}) {
                for (boolean translucent : name.equals("beaconBeam") ? new boolean[]{false, true} : new boolean[]{false}) {
                    var parameters = name.equals("beaconBeam")
                            ? new Class<?>[]{ResourceLocation.class, boolean.class} : new Class<?>[]{ResourceLocation.class};
                    var arguments = name.equals("beaconBeam") ? new Object[]{texture, translucent} : new Object[]{texture};
                    var method = factory.getMethod(name, parameters);
                    Object result;
                    try {
                        result = method.invoke(null, arguments);
                    } catch (InvocationTargetException ex) {
                        throw new AssertionError("Released BlazeRenderType." + name + " failed", ex.getCause());
                    }
                    require(vanilla.getMethod("mode").invoke(result).toString().equals("TRIANGLES"),
                            name + " changed the triangle rendering mode");
                    require(method.invoke(null, arguments) == result, name + " lost memoization");
                    var state = result.getClass().getDeclaredField("state");
                    state.setAccessible(true);
                    var original = vanilla.getMethod(name, parameters).invoke(null, arguments);
                    require(state.get(result) == state.get(original), name + " changed the vanilla render state");
                    System.out.println("PASS: released " + name + (name.equals("beaconBeam") ? "(" + translucent + ")" : "")
                            + " can read state and preserves triangles, memoization and vanilla state");
                }
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
