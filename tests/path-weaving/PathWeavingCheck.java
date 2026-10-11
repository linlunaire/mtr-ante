package cn.zbx1425.mtrsteamloco.compatibility;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.service.MixinService;

/** Real Sponge weaving and isolated execution of the production path integration; no world or GPU. */
public final class PathWeavingCheck {
    private static final String FINDER = "mtr/path/PathFinder";
    private static final String IMPLEMENTATION = FINDER + "$Companion";
    private static final String DATA = "mtr/path/PathData";
    private static final String BETTER = "cn/zbx1425/mtrsteamloco/path/BetterPathFinder";
    private static final String MIXIN = "cn/zbx1425/mtrsteamloco/mixin/PathFinderMixin";
    private static final String ACCESSOR = "cn/zbx1425/mtrsteamloco/mixin/PathDataAccessor";
    private static final String FIND = "(Ljava/util/List;Ljava/util/Map;Ljava/util/List;IIZ)I";
    private static final String APPEND = "(Ljava/util/List;Ljava/util/List;)V";

    public static void main(String[] args) throws Exception {
        List<String> options = Arrays.asList(args);
        require(options.stream().allMatch(List.of("--missing-method", "--missing-field", "--broken-java-bridge")::contains), "Unknown path weaving option");
        System.setProperty("mixin.service", CameraWeavingCheck.HeadlessService.class.getName());
        MixinBootstrap.init();
        var environment = MixinEnvironment.getDefaultEnvironment();
        environment.setSide(MixinEnvironment.Side.SERVER);
        Mixins.addConfiguration("path-weaving.mixins.json");
        var service = (CameraWeavingCheck.HeadlessService) MixinService.getService();
        var transformer = service.transformerFactory().createTransformer();

        ClassNode outer = service.getClassNode(FINDER);
        ClassNode implementation = service.getClassNode(IMPLEMENTATION);
        ClassNode data = service.getClassNode(DATA);
        for (ClassNode type : List.of(outer, implementation, data)) require(isKotlin(type), "Expected migrated Kotlin path target: " + type.name);
        ClassNode productionMixin = service.getClassNode(MIXIN);
        require(productionMixin.methods.stream().filter(method -> method.name.equals("findPath") || method.name.equals("appendPath"))
                .allMatch(method -> (method.access & Opcodes.ACC_STATIC) == 0), "Companion handlers must be instance methods");

        if (options.contains("--broken-java-bridge")) {
            MethodInsnNode call = calls(method(outer, "findPath", FIND)).stream().filter(invoke -> invoke.owner.equals(IMPLEMENTATION)).findFirst().orElseThrow();
            call.owner = "deliberately/bypassed/Implementation";
        }
        checkJavaBridge(outer, "findPath", FIND);
        checkJavaBridge(outer, "appendPath", APPEND);
        require(calls(method(implementation, "findPath", FIND)).stream().anyMatch(call -> call.owner.equals(IMPLEMENTATION)
                && call.name.equals("appendPath") && call.desc.equals(APPEND) && call.getOpcode() != Opcodes.INVOKESTATIC),
                "Kotlin's internal append call must reach the same intercepted Companion implementation");

        // Test-only counters observe real method entry, not substitute implementations.
        addCounter(implementation, "findPath", FIND, "originalFindCalls");
        addCounter(implementation, "appendPath", APPEND, "originalAppendCalls");
        if (options.contains("--missing-method")) require(implementation.methods.removeIf(method -> method.name.equals("findPath") && method.desc.equals(FIND)), "Could not remove method for negative control");
        if (options.contains("--missing-field")) require(data.fields.removeIf(field -> field.name.equals("savedRailBaseId")), "Could not remove field for negative control");

        require(!transformer.transformClass(environment, FINDER.replace('/', '.'), outer), "Finder outer class must not be injected a second time");
        require(transformer.transformClass(environment, IMPLEMENTATION.replace('/', '.'), implementation), "Production finder mixin did not weave Companion");
        checkHeadCancellation(implementation, "findPath", FIND, "originalFindCalls", Opcodes.IRETURN);
        checkHeadCancellation(implementation, "appendPath", APPEND, "originalAppendCalls", Opcodes.RETURN);
        require(transformer.transformClass(environment, DATA.replace('/', '.'), data), "Production PathData accessor did not weave");
        checkAccessors(data);

        ClassNode better = service.getClassNode(BETTER);
        require(isKotlin(better), "Expected migrated Kotlin ANTE finder");
        addCounter(better, "findPath", FIND, "observedFindCalls");
        addCounter(better, "appendPath", APPEND, "observedAppendCalls");
        Map<String, byte[]> definitions = new HashMap<>();
        for (ClassNode type : List.of(outer, implementation, data, service.getClassNode(DATA + "$Companion"), better)) definitions.put(type.name.replace('/', '.'), bytes(type));
        for (String name : List.of(BETTER + "$Companion", BETTER + "$PathPart")) definitions.put(name.replace('/', '.'), bytes(service.getClassNode(name)));
        execute(new WovenLoader(definitions));
        System.out.println("PASS: actual Sponge path weaving; Java static and direct Kotlin Companion calls each invoke ANTE once and cancel MTR; all four PathData accessors and three mutable fields execute correctly");
    }

    private static void checkJavaBridge(ClassNode outer, String name, String descriptor) {
        MethodNode bridge = method(outer, name, descriptor);
        require((bridge.access & Opcodes.ACC_STATIC) != 0 && (bridge.access & Opcodes.ACC_FINAL) == 0, "Original non-final static Java bridge changed: " + name);
        List<MethodInsnNode> delegates = calls(bridge).stream().filter(call -> call.name.equals(name) && call.desc.equals(descriptor)).toList();
        require(delegates.size() == 1 && delegates.getFirst().owner.equals(IMPLEMENTATION) && delegates.getFirst().getOpcode() != Opcodes.INVOKESTATIC,
                "Java bridge bypasses intercepted Kotlin implementation: " + name);
    }

    private static void checkHeadCancellation(ClassNode type, String name, String descriptor, String originalCounter, int returnOpcode) {
        MethodNode target = method(type, name, descriptor);
        List<MethodInsnNode> handlers = calls(target).stream().filter(call -> call.owner.equals(type.name) && call.name.contains(name)
                && call.desc.contains("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo")).toList();
        require(handlers.size() == 1, "Expected exactly one actual HEAD handler: " + name);
        MethodInsnNode invocation = handlers.getFirst();
        MethodNode handler = method(type, invocation.name, invocation.desc);
        require((handler.access & Opcodes.ACC_STATIC) == 0 && mergedFrom(handler, MIXIN), "Wrong production instance handler: " + name);
        require(calls(handler).stream().filter(call -> call.owner.equals(BETTER) && call.name.equals(name) && call.desc.equals(descriptor)).count() == 1, "ANTE implementation must execute once: " + name);
        require(calls(handler).stream().anyMatch(call -> call.owner.startsWith("org/spongepowered/asm/mixin/injection/callback/CallbackInfo") && call.name.equals("cancel")), "Handler lost cancellation: " + name);
        int handlerAt = target.instructions.indexOf(invocation), cancelledAt = -1, earlyReturnAt = -1, originalAt = -1;
        for (int index = 0; index < target.instructions.size(); index++) {
            var instruction = target.instructions.get(index);
            if (instruction instanceof MethodInsnNode call && call.name.equals("isCancelled") && call.owner.startsWith("org/spongepowered/asm/mixin/injection/callback/CallbackInfo")) cancelledAt = index;
            if (cancelledAt >= 0 && earlyReturnAt < 0 && instruction.getOpcode() == returnOpcode) earlyReturnAt = index;
            if (instruction instanceof FieldInsnNode field && field.owner.equals(type.name) && field.name.equals(originalCounter) && originalAt < 0) originalAt = index;
        }
        require(handlerAt < cancelledAt && cancelledAt < earlyReturnAt && earlyReturnAt < originalAt, "Cancellation must return before the original method body: " + name);
    }

    private static void checkAccessors(ClassNode data) {
        require(data.interfaces.contains(ACCESSOR), "PathData does not implement production accessor");
        checkAccessor(data, "getEndingPos", "()Lnet/minecraft/core/BlockPos;", "endingPos", "Lnet/minecraft/core/BlockPos;", Opcodes.GETFIELD);
        checkAccessor(data, "setDwellTime", "(I)V", "dwellTime", "I", Opcodes.PUTFIELD);
        checkAccessor(data, "setStopIndex", "(I)V", "stopIndex", "I", Opcodes.PUTFIELD);
        checkAccessor(data, "setSavedRailBaseId", "(J)V", "savedRailBaseId", "J", Opcodes.PUTFIELD);
        for (String fieldName : List.of("dwellTime", "stopIndex", "savedRailBaseId")) {
            FieldNode field = data.fields.stream().filter(value -> value.name.equals(fieldName)).findFirst().orElseThrow();
            require((field.access & Opcodes.ACC_FINAL) == 0, "@Mutable did not remove FINAL: " + fieldName);
        }
    }

    private static void checkAccessor(ClassNode data, String name, String descriptor, String fieldName, String fieldDescriptor, int opcode) {
        MethodNode bridge = method(data, name, descriptor);
        require((bridge.access & Opcodes.ACC_PUBLIC) != 0 && (bridge.access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) == 0, "Accessor is not concrete/public: " + name);
        List<FieldInsnNode> fields = new ArrayList<>();
        for (var instruction : bridge.instructions) if (instruction instanceof FieldInsnNode field) fields.add(field);
        require(fields.size() == 1 && fields.getFirst().owner.equals(DATA) && fields.getFirst().name.equals(fieldName)
                && fields.getFirst().desc.equals(fieldDescriptor) && fields.getFirst().getOpcode() == opcode, "Accessor field binding changed: " + name);
    }

    private static void execute(ClassLoader loader) throws Exception {
        Class<?> finder = Class.forName(FINDER.replace('/', '.'), true, loader);
        Class<?> implementation = Class.forName(IMPLEMENTATION.replace('/', '.'), true, loader);
        Class<?> better = Class.forName(BETTER.replace('/', '.'), true, loader);
        Class<?> data = Class.forName(DATA.replace('/', '.'), true, loader);
        var constructor = Arrays.stream(data.getConstructors()).filter(value -> value.getParameterCount() == 6).findFirst().orElseThrow();
        var positionConstructor = Class.forName("net.minecraft.core.BlockPos", true, loader).getConstructor(int.class, int.class, int.class);
        Object start = positionConstructor.newInstance(1, 2, 3), end = positionConstructor.newInstance(4, 5, 6);
        Object section = constructor.newInstance(null, 12L, 34, start, end, 56);
        Object duplicate = constructor.newInstance(null, 99L, 99, start, end, 99);
        Object reverse = constructor.newInstance(null, 67L, 89, end, start, 10);
        Object companion = finder.getField("Companion").get(null);
        for (boolean javaBridge : new boolean[]{true, false}) {
            better.getField("observedFindCalls").setInt(null, 0);
            better.getField("observedAppendCalls").setInt(null, 0);
            List<Object> path = new ArrayList<>();
            path.add(null);
            Class<?> owner = javaBridge ? finder : implementation;
            Object receiver = javaBridge ? null : companion;
            Object result = invoke(owner.getMethod("findPath", List.class, Map.class, List.class, int.class, int.class, boolean.class), receiver,
                    path, Map.of(), List.of(), 9, 256, true);
            require(result.equals(0) && path.isEmpty(), "Real empty route semantics changed");
            require(better.getField("observedFindCalls").getInt(null) == 1 && implementation.getField("originalFindCalls").getInt(null) == 0,
                    "Finder dispatch bypassed ANTE or fell through into MTR: javaBridge=" + javaBridge);
            List<Object> partial = new ArrayList<>();
            partial.add(null);
            invoke(owner.getMethod("appendPath", List.class, List.class), receiver, path, partial);
            require(path.size() == 1 && path.getFirst() == null, "Append was lost or duplicated");
            require(better.getField("observedAppendCalls").getInt(null) == 1 && implementation.getField("originalAppendCalls").getInt(null) == 0,
                    "Append dispatch bypassed ANTE or fell through into MTR: javaBridge=" + javaBridge);
            path.clear();
            path.add(section);
            invoke(owner.getMethod("appendPath", List.class, List.class), receiver, path, List.of(duplicate, reverse));
            require(path.size() == 2 && path.getFirst() == section && path.getLast() == reverse,
                    "Woven append must retain the old seam object, skip its duplicate and keep the reverse rail");
            invoke(owner.getMethod("appendPath", List.class, List.class), receiver, path, List.of());
            require(path.isEmpty() && better.getField("observedAppendCalls").getInt(null) == 3
                    && implementation.getField("originalAppendCalls").getInt(null) == 0,
                    "Woven empty append must clear the path without entering the original body");
        }
        require(data.getMethod("getEndingPos").invoke(section) == end, "Ending-position accessor did not read original field identity");
        Object nullSection = constructor.newInstance(null, 0L, 0, null, null, 0);
        require(data.getMethod("getEndingPos").invoke(nullSection) == null, "Ending-position accessor lost nullable legacy data");
        data.getMethod("setDwellTime", int.class).invoke(section, 78);
        data.getMethod("setStopIndex", int.class).invoke(section, 90);
        data.getMethod("setSavedRailBaseId", long.class).invoke(section, 1234567890123L);
        require(data.getField("dwellTime").getInt(section) == 78 && data.getField("stopIndex").getInt(section) == 90
                && data.getField("savedRailBaseId").getLong(section) == 1234567890123L, "Mutable accessors did not update public fields");
    }

    private static Object invoke(java.lang.reflect.Method method, Object receiver, Object... arguments) throws Exception {
        try { return method.invoke(receiver, arguments); }
        catch (InvocationTargetException error) { throw new AssertionError("Woven production method failed: " + method, error.getCause()); }
    }

    private static void addCounter(ClassNode type, String name, String descriptor, String counter) {
        require(type.fields.stream().noneMatch(field -> field.name.equals(counter)), "Counter collided with production field");
        type.fields.add(new FieldNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, counter, "I", null, null));
        InsnList instructions = new InsnList();
        instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, type.name, counter, "I"));
        instructions.add(new InsnNode(Opcodes.ICONST_1));
        instructions.add(new InsnNode(Opcodes.IADD));
        instructions.add(new FieldInsnNode(Opcodes.PUTSTATIC, type.name, counter, "I"));
        method(type, name, descriptor).instructions.insert(instructions);
    }

    private static byte[] bytes(ClassNode type) {
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        type.accept(writer);
        return writer.toByteArray();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        List<MethodInsnNode> result = new ArrayList<>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static MethodNode method(ClassNode type, String name, String descriptor) {
        return type.methods.stream().filter(value -> value.name.equals(name) && value.desc.equals(descriptor)).findFirst()
                .orElseThrow(() -> new AssertionError("Missing path method: " + type.name + "." + name + descriptor));
    }

    private static boolean mergedFrom(MethodNode method, String mixin) {
        if (method.visibleAnnotations == null) return false;
        for (AnnotationNode annotation : method.visibleAnnotations) if (annotation.desc.equals("Lorg/spongepowered/asm/mixin/transformer/meta/MixinMerged;")) {
            for (int index = 0; index < annotation.values.size(); index += 2) if (annotation.values.get(index).equals("mixin")
                    && annotation.values.get(index + 1).equals(mixin.replace('/', '.'))) return true;
        }
        return false;
    }

    private static boolean isKotlin(ClassNode type) {
        return type.visibleAnnotations != null && type.visibleAnnotations.stream().anyMatch(annotation -> annotation.desc.equals("Lkotlin/Metadata;"));
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class WovenLoader extends ClassLoader {
        private final Map<String, byte[]> definitions;
        private WovenLoader(Map<String, byte[]> definitions) { super(PathWeavingCheck.class.getClassLoader()); this.definitions = definitions; }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            byte[] bytes = definitions.get(name);
            if (bytes == null) return super.loadClass(name, resolve);
            synchronized (getClassLoadingLock(name)) {
                Class<?> type = findLoadedClass(name);
                if (type == null) type = defineClass(name, bytes, 0, bytes.length);
                if (resolve) resolveClass(type);
                return type;
            }
        }
    }
}
