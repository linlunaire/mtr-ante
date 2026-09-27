package cn.zbx1425.mtrsteamloco.compatibility;

import mtr.data.RailActionsCompatibilityCheck;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;
import org.objectweb.asm.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.transformers.MixinClassWriter;

/** Actual Mixin dispatch followed by the same Java-baseline queue behavior corpus. */
public final class RailActionsWeavingCheck {
    public static void main(String[] args) throws Exception {
        if (args.length == 3) {
            verify(Path.of(args[1]), RailActionsCompatibilityCheck.TARGET);
            verify(Path.of(args[2]), "cn.zbx1425.mtrsteamloco.mixin.RailwayDataRailActionsModuleMixin");
            verify(Path.of(args[2]), "cn.zbx1425.mtrsteamloco.data.RailActionsModuleExtraSupplier");
        }
        System.setProperty("mixin.service", CameraWeavingCheck.HeadlessService.class.getName());
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment(); environment.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("rail-actions-weaving.mixins.json");
        var service = (CameraWeavingCheck.HeadlessService) MixinService.getService();
        var node = service.getClassNode(RailActionsCompatibilityCheck.TARGET);
        if (!service.transformerFactory().createTransformer().transformClass(environment, RailActionsCompatibilityCheck.TARGET, node)) throw new AssertionError("Queue not woven");
        var writer = new MixinClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS); node.accept(writer);
        RailActionsCompatibilityCheck.compare(args[0], RailActionsCompatibilityCheck.run(writer.toByteArray(), true));
        System.out.println("PASS: actual rail-action Mixin weaving, 21 Java-baseline scenarios and live queue/map getters");
    }
    private static void verify(Path source, String name) throws Exception {
        String entry = name.replace('.', '/') + ".class";
        try (JarFile jar = new JarFile(source.toFile()); var expected = jar.getInputStream(jar.getJarEntry(entry)); var actual = RailActionsWeavingCheck.class.getClassLoader().getResourceAsStream(entry)) {
            if (actual == null || !Arrays.equals(expected.readAllBytes(), actual.readAllBytes())) throw new AssertionError("Stale packaged class " + name);
        }
    }
}
