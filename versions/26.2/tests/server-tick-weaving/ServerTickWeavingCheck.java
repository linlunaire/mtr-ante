package cn.zbx1425.mtrsteamloco.compatibility;

import java.util.List;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

/** Verifies actual composed call boundaries. Runtime world behavior is covered by the isolated source check. */
public final class ServerTickWeavingCheck {
    public static void main(String[] args) throws Exception {
        boolean baseline = args.length > 0 && args[0].equals("baseline");
        System.setProperty("mixin.service", CameraWeavingCheck.HeadlessService.class.getName());
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("server-tick-weaving.mixins.json");
        var service = (CameraWeavingCheck.HeadlessService) MixinService.getService();
        var transformer = service.transformerFactory().createTransformer();
        ClassNode train = service.getClassNode("mtr.data.Train");
        require(transformer.transformClass(environment, "mtr.data.Train", train), "Train was not transformed");
        int carCallers = 0, doorHandlers = 0, simulationCalls = 0;
        for (MethodNode method : train.methods) {
            int scans = 0, skips = 0;
            for (var instruction : method.instructions) {
                if (!(instruction instanceof MethodInsnNode call) || !call.owner.equals(train.name)) continue;
                if (call.name.equals("scanDoors")) scans++;
                if (call.name.equals("skipScanBlocks")) skips++;
                if (call.name.equals("_calculateCar")) simulationCalls++;
            }
            if (scans > 0) {
                require(List.of("calculateCar", "_calculateCar").contains(method.name), "Unexpected unguarded scanDoors caller: " + method.name);
                require(scans == 2, "Car scan must retain both sides: " + method.name);
                int expected = baseline && method.name.equals("_calculateCar") ? 0 : 1;
                require(skips == expected, "Car scan guard count changed: " + method.name + " = " + skips + ", expected " + expected);
                carCallers++;
            }
            if (method.name.contains("onScanDoors")) {
                require(skips == 1, "Injected door handler must retain the fallback guard for other MTR callers");
                doorHandlers++;
            }
        }
        require(carCallers == 2 && doorHandlers == 1 && simulationCalls == 1,
                "Both car entry points and the injected simulation must remain connected");

        ClassNode railway = service.getClassNode("mtr.data.RailwayData");
        require(transformer.transformClass(environment, "mtr.data.RailwayData", railway), "RailwayData was not transformed");
        MethodNode simulation = railway.methods.stream().filter(method -> method.name.equals("simulateTrains") && method.desc.equals("()V")).findFirst().orElseThrow();
        boolean insideRotation = false, anteRailRange = false;
        int mapAllocations = 0, mapClears = 0;
        for (var instruction : simulation.instructions) {
            if (instruction instanceof MethodInsnNode call) {
                if (call.name.equals("getViewDistance")) anteRailRange = true;
                if (call.name.equals("startTick")) insideRotation = true;
                if (call.name.equals("resetOccupied")) break;
                if (insideRotation && call.owner.equals("java/util/Map") && call.name.equals("clear")) mapClears++;
            }
            if (insideRotation && instruction instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW && type.desc.equals("java/util/HashMap")) mapAllocations++;
        }
        require(anteRailRange, "Expected ANTE same-signature simulateTrains replacement was not applied");
        require(mapClears == (baseline ? 1 : 2) && mapAllocations == (baseline ? 1 : 0), "Occupation rotation changed: HashMaps=" + mapAllocations + ", clears=" + mapClears);
        System.out.println("PASS: actual Sponge Train/RailwayData weaving, both door callers and ANTE simulation connected; "
                + (baseline ? "baseline guards/allocation characterized" : "one ANTE car guard with legacy fallback, no new occupation map per tick")
                + "; no Minecraft world launched");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
