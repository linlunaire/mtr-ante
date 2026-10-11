package cn.zbx1425.mtrsteamloco.compatibility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

/** Applies actual ANTE mixins to production MTR bytes. No target classes, world or GPU are initialized. */
public final class ItemModelWeavingCheck {
    private static final String MIXINS = "cn/zbx1425/mtrsteamloco/mixin/";
    private static final String USE_ON = "(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;";
    private static final String END_CLICK = "(Lnet/minecraft/world/item/context/UseOnContext;Lnet/minecraft/core/BlockPos;Lnet/minecraft/nbt/CompoundTag;)V";
    private static final String USE = "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResult;";
    private static final Map<String, String> INVOKERS = Map.ofEntries(
            Map.entry("invokeRender", "render"),
            Map.entry("invokeRenderWindowPositions", "renderWindowPositions"),
            Map.entry("invokeRenderDoorPositions", "renderDoorPositions"),
            Map.entry("invokeRenderHeadPosition1", "renderHeadPosition1"),
            Map.entry("invokeRenderHeadPosition2", "renderHeadPosition2"),
            Map.entry("invokeRenderEndPosition1", "renderEndPosition1"),
            Map.entry("invokeRenderEndPosition2", "renderEndPosition2"),
            Map.entry("invokeGetWindowPositions", "getWindowPositions"),
            Map.entry("invokeGetDoorPositions", "getDoorPositions"),
            Map.entry("invokeGetEndPositions", "getEndPositions"));

    public static void main(String[] args) throws Exception {
        List<String> options = Arrays.asList(args);
        require(options.stream().allMatch(List.of("--allow-java-model-baseline", "--missing-shadow", "--missing-invoker")::contains), "Unknown weaving check option");
        System.setProperty("mixin.service", CameraWeavingCheck.HeadlessService.class.getName());
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.CLIENT);
        Mixins.addConfiguration("item-model-weaving.mixins.json");
        var service = (CameraWeavingCheck.HeadlessService) MixinService.getService();
        var transformer = service.transformerFactory().createTransformer();

        ClassNode creative = service.getClassNode("mtr.item.ItemWithCreativeTabBase");
        ClassNode node = service.getClassNode("mtr.item.ItemNodeModifierBase");
        ClassNode rail = service.getClassNode("mtr.item.ItemRailModifier");
        for (ClassNode item : List.of(creative, node, rail)) require(isKotlin(item), "Expected migrated Kotlin item, got " + item.name);
        if (options.contains("--missing-shadow")) {
            require(node.fields.removeIf(field -> field.name.equals("isConnector")), "Shadow fault injection did not remove isConnector");
        }
        for (ClassNode item : List.of(creative, node, rail)) {
            require(transformer.transformClass(environment, item.name.replace('/', '.'), item), "Item mixin did not transform " + item.name);
        }
        checkCreativeItem(creative);
        checkNodeItem(node);
        checkRailItem(rail);
        System.out.println("PASS: actual Sponge item weaving preserves brush use, cancellable direct-node selection, rail path mode and original Shadow fields");

        ClassNode model = service.getClassNode("mtr.model.ModelSimpleTrainBase");
        if (!options.contains("--allow-java-model-baseline")) require(isKotlin(model), "Expected migrated Kotlin ModelSimpleTrainBase");
        if (options.contains("--missing-invoker")) {
            require(model.methods.removeIf(method -> method.name.equals("getEndPositions") && method.desc.equals("()[I")), "Invoker fault injection did not remove getEndPositions");
        }
        ClassNode accessor = service.getClassNode(MIXINS + "ModelSimpleTrainBaseAccessor");
        require(transformer.transformClass(environment, model.name.replace('/', '.'), model), "Model accessor did not transform target");
        require(model.interfaces.contains(MIXINS + "ModelSimpleTrainBaseAccessor"), "Woven model does not implement ANTE accessor");
        int invokers = 0;
        for (MethodNode contract : accessor.methods) {
            if (!INVOKERS.containsKey(contract.name)) continue;
            String target = INVOKERS.get(contract.name);
            MethodNode bridge = method(model, contract.name, contract.desc);
            require((bridge.access & Opcodes.ACC_PUBLIC) != 0 && (bridge.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_STATIC)) == 0,
                    "Invoker is not a concrete public instance bridge: " + contract.name);
            require(calls(bridge).size() == 1, "Invoker must delegate once: " + contract.name);
            MethodInsnNode call = calls(bridge).getFirst();
            require(call.owner.equals(model.name) && call.name.equals(target) && call.desc.equals(contract.desc),
                    "Invoker descriptor/target drift: " + contract.name + " -> " + call.owner + "." + call.name + call.desc);
            method(model, target, contract.desc);
            invokers++;
        }
        require(invokers == 10, "Expected all 10 production model invokers, got " + invokers);
        System.out.println("PASS: actual Sponge model accessor weaving, all 10 public bridges delegate to original descriptors; no game/world/GPU initialized");
    }

    private static void checkCreativeItem(ClassNode type) {
        MethodNode use = method(type, "useOn", USE_ON);
        require(mergedFrom(use, "ItemWithCreativeTabBaseMixin"), "Brush useOn was not supplied by actual ANTE mixin");
        require(callCount(use, "cn/zbx1425/mtrsteamloco/network/PacketScreen", "sendScreenBlockS2C") == 1, "Missing direct-node screen path");
        require(callCount(use, "cn/zbx1425/mtrsteamloco/network/PacketScreen", "sendScreenS2C") == 1, "Missing rail editor screen path");
        require(callCount(use, "cn/zbx1425/mtrsteamloco/gui/BrushEditRailScreen", "applyBrushToPickedRail") == 1, "Missing client brush action");
        require(calls(use).stream().anyMatch(call -> call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("useOn") && call.desc.equals(USE_ON)), "Unrelated item use no longer reaches Item super implementation");
    }

    private static void checkNodeItem(ClassNode type) {
        MethodNode end = method(type, "onEndClick", END_CLICK);
        List<MethodInsnNode> handlers = calls(end).stream().filter(call -> call.owner.equals(type.name) && call.name.contains("onEndClick") && !call.desc.equals(END_CLICK)).toList();
        require(handlers.size() == 1, "Expected one cancellable direct-node handler in onEndClick");
        MethodInsnNode call = handlers.getFirst();
        MethodNode handler = method(type, call.name, call.desc);
        require(mergedFrom(handler, "ItemNodeModifierBaseMixin"), "Node handler not supplied by actual ANTE mixin");
        int handlerIndex = end.instructions.indexOf(call), cancelledIndex = -1, earlyReturn = -1, originalConnect = -1;
        for (int index = 0; index < end.instructions.size(); index++) {
            var instruction = end.instructions.get(index);
            if (instruction instanceof MethodInsnNode invoke) {
                if (invoke.owner.equals("org/spongepowered/asm/mixin/injection/callback/CallbackInfo") && invoke.name.equals("isCancelled")) cancelledIndex = index;
                if (invoke.owner.equals(type.name) && invoke.name.equals("onConnect")) originalConnect = index;
            }
            if (cancelledIndex >= 0 && instruction.getOpcode() == Opcodes.RETURN && earlyReturn < 0) earlyReturn = index;
        }
        require(handlerIndex < cancelledIndex && cancelledIndex < earlyReturn && earlyReturn < originalConnect, "Cancellable HEAD handler no longer bypasses original node connection logic");
        require(callCount(handler, "org/spongepowered/asm/mixin/injection/callback/CallbackInfo", "cancel") == 1, "Node handler lost cancellation");
        require(calls(handler).stream().anyMatch(invoke -> invoke.name.equals("onConnect") && invoke.owner.equals(type.name)), "Node handler lost connection dispatch");
        boolean fieldRead = false;
        for (var instruction : handler.instructions) if (instruction instanceof FieldInsnNode field && field.owner.equals(type.name) && field.name.equals("isConnector") && field.desc.equals("Z")) fieldRead = true;
        require(fieldRead && type.fields.stream().filter(field -> field.name.equals("isConnector") && field.desc.equals("Z")).count() == 1, "Node Shadow isConnector did not bind to one original field");
    }

    private static void checkRailItem(ClassNode type) {
        MethodNode use = method(type, "use", USE);
        require(mergedFrom(use, "ItemRailModifierMixin"), "Rail mode use() override not supplied by actual ANTE mixin");
        MethodNode connect = type.methods.stream().filter(method -> method.name.equals("onConnect")).findFirst().orElseThrow();
        require(mergedFrom(connect, "ItemRailModifierMixin"), "Rail connection override not supplied by actual ANTE mixin");
        require(callCount(connect, "cn/zbx1425/mtrsteamloco/data/RailExtraSupplier", "changePathMode") == 2, "Both rail directions must receive ANTE path mode");
        require(callCount(connect, "cn/zbx1425/mtrsteamloco/data/RailExtraSupplier", "isStraightOnly") == 2, "Both cable-car rails must retain ANTE straightness validation");
        require(callCount(connect, "mtr/packet/PacketTrainDataGuiServer", "createRailS2C") == 1, "Woven rail creation lost synchronization");
        for (String name : List.of("isOneWay", "railType")) require(type.fields.stream().filter(field -> field.name.equals(name)).count() == 1, "Rail Shadow did not bind once: " + name);
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static long callCount(MethodNode method, String owner, String name) {
        return calls(method).stream().filter(call -> call.owner.equals(owner) && call.name.equals(name)).count();
    }

    private static MethodNode method(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream().filter(method -> method.name.equals(name) && method.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing woven method " + owner.name + "." + name + descriptor));
    }

    private static boolean mergedFrom(MethodNode method, String mixin) {
        if (method.visibleAnnotations == null) return false;
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")) continue;
            for (int index = 0; index < annotation.values.size(); index += 2) {
                if (annotation.values.get(index).equals("mixin") && annotation.values.get(index + 1).equals((MIXINS + mixin).replace('/', '.'))) return true;
            }
        }
        return false;
    }

    private static boolean isKotlin(ClassNode type) {
        return type.visibleAnnotations != null && type.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.equals("Lkotlin/Metadata;"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
