package cn.zbx1425.mtrsteamloco.compatibility;

import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Static linkage only: this does not replace a loader weaving/startup test. */
public final class MixinTargetCompatibilityCheck {
    private static final Map<String, ClassNode> classes = new HashMap<>();
    private static final List<String> errors = new ArrayList<>();
    private static int checks, mixins;

    public static void main(String[] args) throws Exception {
        var config = JsonParser.parseString(Files.readString(Path.of(args[0]))).getAsJsonObject();
        String prefix = config.get("package").getAsString().replace('.', '/') + "/";
        for (String side : List.of("mixins", "client", "server")) {
            for (var entry : config.getAsJsonArray(side)) {
                ClassNode mixin = read(prefix + entry.getAsString().replace('.', '/'));
                var annotation = annotation(mixin.visibleAnnotations, mixin.invisibleAnnotations, "Mixin");
                List<String> targets = new ArrayList<>();
                for (Object value : list(value(annotation, "value"))) targets.add(((Type) value).getInternalName());
                for (Object value : list(value(annotation, "targets"))) targets.add(value.toString().replace('.', '/'));
                require(!targets.isEmpty(), mixin.name + " has no target");
                for (String targetName : targets) inspect(mixin, read(targetName));
                mixins++;
            }
        }
        if (mixins > 1) inspectFrameDispatch();
        errors.forEach(error -> System.err.println("MIXIN TARGET: " + error));
        if (!errors.isEmpty()) throw new AssertionError(errors.size() + " static mixin target failures");
        System.out.println("PASS: " + mixins + " configured mixins, " + checks + " target/member/injection checks against actual class files (not loader weaving)");
    }

    private static void inspectFrameDispatch() throws Exception {
        ClassNode mixin = read("cn/zbx1425/mtrsteamloco/mixin/RenderTrainsMixin");
        MethodNode head = mixin.methods.stream().filter(method -> method.name.equals("renderHead")).findFirst().orElseThrow();
        var injection = annotation(head.visibleAnnotations, head.invisibleAnnotations, "Inject");
        require(Boolean.TRUE.equals(value(injection, "cancellable")), "Seat extraction must not consume the block-entity frame queue");
        List<AbstractInsnNode> code = new ArrayList<>();
        for (var instruction : head.instructions) if (instruction.getOpcode() >= 0) code.add(instruction);
        require(code.size() >= 5 && code.get(0) instanceof VarInsnNode entity && entity.getOpcode() == Opcodes.ALOAD && entity.var == 0
                && code.get(1) instanceof JumpInsnNode branch && branch.getOpcode() == Opcodes.IFNULL
                && code.get(2) instanceof VarInsnNode callback && callback.var == 4
                && code.get(3) instanceof MethodInsnNode cancel && cancel.name.equals("cancel")
                && code.get(4).getOpcode() == Opcodes.RETURN,
                "RenderTrains must skip non-null seat passes before exchange; render globally after block-entity extraction");
        ClassNode extraction = read("mtr/mixin/LevelExtractionMixin");
        MethodNode extract = extraction.methods.stream().filter(method -> method.name.equals("mtr$extract")).findFirst().orElseThrow();
        var at = (AnnotationNode) list(value(annotation(extract.visibleAnnotations, extract.invisibleAnnotations, "Inject"), "at")).getFirst();
        require("TAIL".equals(value(at, "value")), "MTR global extraction no longer follows vanilla block-entity extraction");
    }

    private static void inspect(ClassNode mixin, ClassNode target) throws Exception {
        for (FieldNode field : mixin.fields) {
            if (annotation(field.visibleAnnotations, field.invisibleAnnotations, "Shadow") != null) {
                require(fields(target).stream().anyMatch(candidate -> candidate.name.equals(field.name) && candidate.desc.equals(field.desc)
                        && isStatic(candidate.access) == isStatic(field.access)), mixin.name + " shadow field " + field.name + field.desc + " in " + target.name);
            }
        }
        for (MethodNode method : mixin.methods) {
            String label = mixin.name.substring(mixin.name.lastIndexOf('/') + 1) + "." + method.name;
            if (annotation(method.visibleAnnotations, method.invisibleAnnotations, "Overwrite") != null) {
                require(target.methods.stream().anyMatch(candidate -> candidate.name.equals(method.name) && candidate.desc.equals(method.desc)
                        && isStatic(candidate.access) == isStatic(method.access)), label + " overwrite " + method.desc + " in " + target.name);
            }
            if (annotation(method.visibleAnnotations, method.invisibleAnnotations, "Shadow") != null) {
                String name = method.name.replaceFirst("^shadow\\$", "");
                require(methods(target).stream().anyMatch(candidate -> candidate.name.equals(name) && candidate.desc.equals(method.desc)
                        && isStatic(candidate.access) == isStatic(method.access)), label + " shadow " + method.desc + " in " + target.name);
            }
            for (String kind : List.of("Accessor", "Invoker")) {
                var accessor = annotation(method.visibleAnnotations, method.invisibleAnnotations, kind);
                if (accessor == null) continue;
                String name = (String) value(accessor, "value");
                if (name == null || name.isEmpty()) {
                    name = method.name.replaceFirst("^(get|is|set|invoke|call)", "");
                    name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
                }
                final String memberName = name;
                if (kind.equals("Accessor")) {
                    Type returned = Type.getReturnType(method.desc);
                    String desc = returned.equals(Type.VOID_TYPE) ? Type.getArgumentTypes(method.desc)[0].getDescriptor() : returned.getDescriptor();
                    require(fields(target).stream().anyMatch(field -> field.name.equals(memberName) && field.desc.equals(desc)
                            && isStatic(field.access) == isStatic(method.access)), label + " accessor " + memberName + desc + " in " + target.name);
                } else {
                    boolean constructor = memberName.equals("<init>");
                    String expected = constructor ? Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(method.desc)) : method.desc;
                    require((constructor ? target.methods : methods(target)).stream().anyMatch(candidate -> candidate.name.equals(memberName) && candidate.desc.equals(expected)
                            && (constructor || isStatic(candidate.access) == isStatic(method.access))), label + " invoker " + memberName + method.desc + " in " + target.name);
                }
            }
            for (String kind : List.of("Inject", "Redirect", "ModifyArg", "ModifyArgs", "ModifyVariable", "ModifyConstant")) {
                var injection = annotation(method.visibleAnnotations, method.invisibleAnnotations, kind);
                if (injection == null) continue;
                for (Object selector : list(value(injection, "method"))) {
                    String selected = selector.toString();
                    int paren = selected.indexOf('(');
                    String name = paren < 0 ? selected : selected.substring(0, paren);
                    boolean allOverloads = name.endsWith("*");
                    String memberName = allOverloads ? name.substring(0, name.length() - 1) : name;
                    List<MethodNode> matches = target.methods.stream().filter(candidate -> candidate.name.equals(memberName)
                            && (paren < 0 || candidate.desc.equals(selected.substring(paren)))).toList();
                    require(!matches.isEmpty(), label + " -> missing " + target.name + "." + selected);
                    for (MethodNode candidate : matches) {
                        if (kind.equals("Inject")) {
                            Type[] handlerArgs = Type.getArgumentTypes(method.desc), targetArgs = Type.getArgumentTypes(candidate.desc);
                            int callback = -1;
                            for (int i = 0; i < handlerArgs.length; i++) if (handlerArgs[i].getClassName().startsWith("org.spongepowered.asm.mixin.injection.callback.CallbackInfo")) { callback = i; break; }
                            require(callback == 0 || callback == targetArgs.length && Arrays.equals(Arrays.copyOf(handlerArgs, callback), targetArgs),
                                    label + " callback arguments " + method.desc + " != " + candidate.desc);
                            require(isStatic(method.access) == isStatic(candidate.access), label + " callback static mismatch");
                        }
                        if (!allOverloads) for (Object point : list(value(injection, "at"))) inspectAt(label, List.of(candidate), (AnnotationNode) point, null);
                    }
                    // A wildcard injector may find its instruction in only one
                    // overload (NeoForge's two-argument Camera method delegates).
                    if (allOverloads) for (Object point : list(value(injection, "at"))) {
                        inspectAt(label, matches, (AnnotationNode) point, (Integer) value(injection, "allow"));
                    }
                }
            }
        }
    }

    private static void inspectAt(String label, List<MethodNode> methods, AnnotationNode at, Integer allow) {
        String target = (String) value(at, "target");
        if (target == null || target.isEmpty()) return;
        String kind = (String) value(at, "value");
        if (!List.of("INVOKE", "INVOKE_ASSIGN", "FIELD").contains(kind)) return;
        Integer ordinal = (Integer) value(at, "ordinal");
        int selected = 0;
        for (MethodNode method : methods) {
            int found = 0;
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call && target.equals("L" + call.owner + ";" + call.name + call.desc)) found++;
                if (instruction instanceof FieldInsnNode field && target.equals("L" + field.owner + ";" + field.name + ":" + field.desc)) found++;
            }
            selected += ordinal == null || ordinal < 0 ? found : found > ordinal ? 1 : 0;
        }
        String methodNames = methods.stream().map(method -> method.name + method.desc).toList().toString();
        require(selected > 0, label + " @At(" + kind + ") " + target + " not found in " + methodNames);
        if (allow != null && allow > 0) require(selected <= allow, label + " has " + selected + " injection points, allows " + allow);
    }

    private static ClassNode read(String name) throws Exception {
        if (classes.containsKey(name)) return classes.get(name);
        try (InputStream input = MixinTargetCompatibilityCheck.class.getClassLoader().getResourceAsStream(name + ".class")) {
            if (input == null) throw new IllegalStateException("Missing class " + name);
            ClassNode result = new ClassNode(); new ClassReader(input).accept(result, 0); classes.put(name, result); return result;
        }
    }
    private static List<MethodNode> methods(ClassNode node) throws Exception {
        List<MethodNode> result = new ArrayList<>(node.methods);
        if (node.superName != null && !node.superName.equals("java/lang/Object")) result.addAll(methods(read(node.superName)));
        return result;
    }
    private static List<FieldNode> fields(ClassNode node) throws Exception {
        List<FieldNode> result = new ArrayList<>(node.fields);
        if (node.superName != null && !node.superName.equals("java/lang/Object")) result.addAll(fields(read(node.superName)));
        return result;
    }
    private static AnnotationNode annotation(List<AnnotationNode> visible, List<AnnotationNode> invisible, String name) {
        for (var annotations : Arrays.asList(visible, invisible)) if (annotations != null)
            for (var annotation : annotations) if (annotation.desc.endsWith("/" + name + ";")) return annotation;
        return null;
    }
    private static Object value(AnnotationNode annotation, String key) {
        if (annotation == null || annotation.values == null) return null;
        for (int i = 0; i < annotation.values.size(); i += 2) if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
        return null;
    }
    private static List<?> list(Object value) { return value == null ? List.of() : value instanceof List<?> values ? values : List.of(value); }
    private static boolean isStatic(int access) { return (access & Opcodes.ACC_STATIC) != 0; }
    private static void require(boolean condition, String error) { checks++; if (!condition) errors.add(error); }
}
