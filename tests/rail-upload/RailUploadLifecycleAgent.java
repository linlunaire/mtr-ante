package cn.zbx1425.mtrsteamloco.compatibility;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

/** Registers boundary instrumentation before the test verifier resolves Minecraft world types. */
public final class RailUploadLifecycleAgent {
    private static final String HOOK = "cn/zbx1425/mtrsteamloco/compatibility/RailUploadLifecycleCheck";
    public static void premain(String unused, Instrumentation instrumentation) {
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> redefining,
                    ProtectionDomain domain, byte[] bytes) {
                boolean allocator = name.equals("cn/zbx1425/sowcer/util/OffHeapAllocator");
                boolean buffer = name.equals("cn/zbx1425/sowcer/object/VertBuf");
                boolean level = name.equals("net/minecraft/world/level/Level")
                        || name.equals("net/minecraft/world/level/BlockAndTintGetter");
                boolean clientLevel = name.equals("net/minecraft/client/multiplayer/ClientLevel");
                boolean rail = name.equals("mtr/data/Rail");
                if (!name.equals("cn/zbx1425/mtrsteamloco/data/RailModelRegistry")
                        && !name.equals("cn/zbx1425/mtrsteamloco/ClientConfig") && !allocator && !buffer && !level && !clientLevel && !rail) return null;
                ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, 0);
                if (rail) {
                    node.interfaces.add("cn/zbx1425/mtrsteamloco/data/RailExtraSupplier");
                    var model = new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "getModelKey", "()Ljava/lang/String;", null, null);
                    model.instructions.add(new org.objectweb.asm.tree.LdcInsnNode("test")); model.instructions.add(new InsnNode(Opcodes.ARETURN));
                    node.methods.add(model);
                    var reverse = new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "getRenderReversed", "()Z", null, null);
                    reverse.instructions.add(new InsnNode(Opcodes.ICONST_0)); reverse.instructions.add(new InsnNode(Opcodes.IRETURN));
                    node.methods.add(reverse);
                    var roll = new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "getRollAngleMap", "()Ljava/util/Map;", null, null);
                    roll.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "java/util/Collections", "emptyMap", "()Ljava/util/Map;", false));
                    roll.instructions.add(new InsnNode(Opcodes.ARETURN)); node.methods.add(roll);
                }
                for (var method : node.methods) {
                    boolean position = rail && method.name.equals("getPosition");
                    boolean brightness = level && method.name.equals("getBrightness");
                    boolean constructor = buffer && method.name.equals("<init>") && method.desc.equals("()V");
                    boolean upload = buffer && method.name.equals("upload") && method.desc.equals("(Ljava/nio/ByteBuffer;I)V");
                    boolean close = buffer && method.name.equals("close");
                    boolean allocate = allocator && method.name.equals("allocate");
                    boolean free = allocator && method.name.equals("free");
                    boolean stats = allocator && (method.name.equals("allocatedBytes") || method.name.equals("peakAllocatedBytes"));
                    boolean initialization = !level && !buffer && !rail && method.name.equals("<clinit>");
                    if (initialization || method.name.equals("getProperty") || brightness || constructor || upload || close || allocate || free || stats || position) {
                        method.instructions.clear(); method.tryCatchBlocks.clear(); method.localVariables = null;
                        if (method.name.equals("getProperty")) {
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "property",
                                    "()Lcn/zbx1425/mtrsteamloco/data/RailModelProperties;", false));
                            method.instructions.add(new InsnNode(Opcodes.ARETURN));
                        } else if (position) {
                            method.instructions.add(new VarInsnNode(Opcodes.DLOAD, 1));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "railPosition", "(D)Lnet/minecraft/world/phys/Vec3;", false));
                            method.instructions.add(new InsnNode(Opcodes.ARETURN));
                        } else if (brightness) {
                            method.instructions.add(new org.objectweb.asm.tree.IntInsnNode(Opcodes.BIPUSH, 7));
                            method.instructions.add(new InsnNode(Opcodes.IRETURN));
                        } else if (allocate || free) {
                            method.instructions.add(new VarInsnNode(allocate ? Opcodes.ILOAD : Opcodes.ALOAD, 0));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, method.name, method.desc, false));
                            method.instructions.add(new InsnNode(allocate ? Opcodes.ARETURN : Opcodes.RETURN));
                        } else if (stats) {
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, method.name, "()J", false));
                            method.instructions.add(new InsnNode(Opcodes.LRETURN));
                        } else if (constructor) {
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false));
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "create", "()I", false));
                            method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, name, "id", "I"));
                            method.instructions.add(new InsnNode(Opcodes.RETURN));
                        } else if (upload || close) {
                            method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                            method.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, name, "id", "I"));
                            if (upload) {
                                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
                                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "upload", "(ILjava/nio/ByteBuffer;)V", false));
                            } else {
                                method.instructions.add(new MethodInsnNode(Opcodes.INVOKESTATIC, HOOK, "delete", "(I)V", false));
                                method.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                                method.instructions.add(new InsnNode(Opcodes.ICONST_0));
                                method.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, name, "id", "I"));
                            }
                            method.instructions.add(new InsnNode(Opcodes.RETURN));
                        } else method.instructions.add(new InsnNode(Opcodes.RETURN));
                    }
                }
                // Preserve frames in untouched Minecraft methods; computing their hierarchy loads registries too early.
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer); return writer.toByteArray();
            }
        });
    }
}
