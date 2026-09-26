package cn.zbx1425.mtrsteamloco.compatibility;

import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.objectweb.asm.ClassWriter;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.transformers.MixinClassWriter;

/** Execute constructor and the actual boarding hook up to the no-player guard; no window or server. */
public final class RidingStateWeavingCheck {
    public static void main(String[] args) throws Exception {
        System.setProperty("mixin.service", CameraWeavingCheck.HeadlessService.class.getName());
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("riding-state-weaving.mixins.json");
        var service = (CameraWeavingCheck.HeadlessService) MixinService.getService();
        String name = "mtr.data.VehicleRidingClient";
        var node = service.getClassNode(name);
        require(service.transformerFactory().createTransformer().transformClass(environment, name, node), "Riding Mixin not applied");
        var writer = new MixinClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);
        byte[] bytes = writer.toByteArray();
        Class<?> riding = new ClassLoader(RidingStateWeavingCheck.class.getClassLoader()) {
            Class<?> defineRiding() { return defineClass(name, bytes, 0, bytes.length); }
        }.defineRiding();
        UUID rider = new UUID(0, 1);
        Object first = riding.getConstructor(Set.class, Identifier.class).newInstance(Set.of(rider), Identifier.parse("test:riding"));
        riding.getMethod("setPositions", Vec3[].class).invoke(first, (Object) new Vec3[]{Vec3.ZERO, new Vec3(16, 0, 0)});
        // The real ANTE hook records yaw/pitch before looking up the player. A null
        // player fixture then exits both ANTE and MTR without starting the client.
        riding.getMethod("setOffsets", UUID.class, double.class, double.class, double.class,
                float.class, float.class, double.class, int.class,
                boolean.class, boolean.class, boolean.class, boolean.class,
                float.class, float.class, boolean.class, boolean.class, Runnable.class)
                .invoke(first, rider, 0D, 64D, 0D, 0.2F, 0.1F, 16D, 3,
                        false, false, true, true, 0F, 0F, true, true,
                        (Runnable) () -> { throw new AssertionError("No player callback expected"); });
        require((float) riding.getMethod("getRoll", int.class).invoke(first, 0) == 0F, "Initial roll must be zero");
        require((float) riding.getMethod("getRoll", int.class).invoke(first, -1) == 0F, "Negative index must be safe");
        Object second = riding.getConstructor(Set.class, Identifier.class).newInstance(Set.of(), Identifier.parse("test:riding2"));
        for (String fieldName : List.of("prevYaw", "prevPitch")) {
            var field = riding.getDeclaredField(fieldName);
            field.setAccessible(true);
            require(field.get(first) instanceof Map<?, ?> firstMap && firstMap.containsKey(rider), "Boarding did not retain " + fieldName);
            require(field.get(second) instanceof Map<?, ?> secondMap && secondMap.isEmpty(), "Riders shared " + fieldName);
        }
        var rotation = riding.getDeclaredField("prevRotation");
        rotation.setAccessible(true);
        require(rotation.get(first) != null && rotation.get(second) != null, "Initial camera rotation is null");
        System.out.println("PASS: actual riding Mixin construction and boarding metadata before player access; independent maps, neutral roll and rotation");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
