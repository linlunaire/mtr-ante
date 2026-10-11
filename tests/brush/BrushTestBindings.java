package cn.zbx1425.mtrsteamloco.compatibility;

import io.netty.buffer.Unpooled;
import mtr.RegistryObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

/** Data-only client/player/send boundaries; production packet, screen, components and codecs stay real. */
public final class BrushTestBindings implements Opcodes {
    public static RegistryAccess registries;
    public static ItemStack mainHand;
    public static ItemStack offHand;
    public static FriendlyByteBuf sentBuffer;
    public static byte[] sentBytes;
    public static ResourceLocation sentId;
    public static EquipmentSlot receivedSlot;
    public static ItemStack receivedItem;

    public static ItemStack itemInHand(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainHand : offHand;
    }

    public static RegistryAccess registryAccess() { return registries; }

    public static void send(ResourceLocation id, FriendlyByteBuf packet) {
        sentId = id;
        sentBuffer = packet;
        sentBytes = new byte[packet.readableBytes()];
        packet.getBytes(packet.readerIndex(), sentBytes);
    }

    public static void receive(EquipmentSlot slot, ItemStack item) {
        receivedSlot = slot;
        receivedItem = item;
    }

    public static FriendlyByteBuf receivedBuffer() {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(sentBytes));
    }

    public static ClassLoader loader(Path productionJar) throws Exception {
        final Map<String, byte[]> classes = new HashMap<>();
        final String minecraft = "net/minecraft/client/Minecraft";
        final String player = "net/minecraft/client/player/LocalPlayer";
        final String serverPlayer = "net/minecraft/server/level/ServerPlayer";
        final var mc = type(minecraft);
        mc.visitField(ACC_PUBLIC | ACC_STATIC, "instance", "L" + minecraft + ";", null, null).visitEnd();
        mc.visitField(ACC_PUBLIC, "player", "L" + player + ";", null, null).visitEnd();
        final var get = mc.visitMethod(ACC_PUBLIC | ACC_STATIC, "getInstance", "()L" + minecraft + ";", null, null);
        get.visitCode();
        get.visitFieldInsn(GETSTATIC, minecraft, "instance", "L" + minecraft + ";");
        get.visitInsn(ARETURN);
        get.visitMaxs(0, 0);
        get.visitEnd();
        classes.put(minecraft.replace('/', '.'), finish(mc));

        final var local = type(player);
        forward(local, ACC_PUBLIC, "getItemInHand", "itemInHand", "(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;");
        forward(local, ACC_PUBLIC, "registryAccess", "registryAccess", "()Lnet/minecraft/core/RegistryAccess;");
        for (var hand : InteractionHand.values()) {
            final var method = local.visitMethod(ACC_PUBLIC, hand == InteractionHand.MAIN_HAND ? "getMainHandItem" : "getOffhandItem", "()Lnet/minecraft/world/item/ItemStack;", null, null);
            method.visitCode();
            method.visitFieldInsn(GETSTATIC, "net/minecraft/world/InteractionHand", hand.name(), "Lnet/minecraft/world/InteractionHand;");
            method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BrushTestBindings.class), "itemInHand", "(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;", false);
            method.visitInsn(ARETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
        classes.put(player.replace('/', '.'), finish(local));

        final var server = type(serverPlayer);
        forward(server, ACC_PUBLIC, "registryAccess", "registryAccess", "()Lnet/minecraft/core/RegistryAccess;");
        forward(server, ACC_PUBLIC, "getItemInHand", "itemInHand", "(Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/item/ItemStack;");
        forward(server, ACC_PUBLIC, "setItemSlot", "receive", "(Lnet/minecraft/world/entity/EquipmentSlot;Lnet/minecraft/world/item/ItemStack;)V");
        classes.put(serverPlayer.replace('/', '.'), finish(server));

        final var executor = type("net/minecraft/server/MinecraftServer");
        final var execute = executor.visitMethod(ACC_PUBLIC, "execute", "(Ljava/lang/Runnable;)V", null, null);
        execute.visitCode();
        execute.visitVarInsn(ALOAD, 1);
        execute.visitMethodInsn(INVOKEINTERFACE, "java/lang/Runnable", "run", "()V", true);
        execute.visitInsn(RETURN);
        execute.visitMaxs(0, 0);
        execute.visitEnd();
        classes.put("net.minecraft.server.MinecraftServer", finish(executor));
        // This fixture represents a permitted player. Actual survival/adventure rules and
        // native queued execution are covered by the isolated health-runtime game fixture.
        final var permissions = type("mtr/data/RailwayData");
        final var denied = permissions.visitMethod(ACC_PUBLIC | ACC_STATIC, "hasNoPermission", "(L" + serverPlayer + ";)Z", null, null);
        denied.visitCode();
        denied.visitInsn(ICONST_0);
        denied.visitInsn(IRETURN);
        denied.visitMaxs(0, 0);
        denied.visitEnd();
        classes.put("mtr.data.RailwayData", finish(permissions));

        final var registry = type("mtr/RegistryClient");
        forward(registry, ACC_PUBLIC | ACC_STATIC, "sendToServer", "send", "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/network/FriendlyByteBuf;)V");
        classes.put("mtr.RegistryClient", finish(registry));
        final var items = type("mtr/Items");
        items.visitField(ACC_PUBLIC | ACC_STATIC, "BRUSH", "Lmtr/RegistryObject;", null, null).visitEnd();
        classes.put("mtr.Items", finish(items));

        try (var jar = new ZipFile(productionJar.toFile())) {
            for (String name : new String[]{"cn.zbx1425.mtrsteamloco.network.PacketUpdateHoldingItem", "cn.zbx1425.mtrsteamloco.gui.BrushEditRailScreen"}) {
                var entry = jar.getEntry(name.replace('.', '/') + ".class");
                if (entry == null) throw new IOException("Missing production class " + name);
                try (var input = jar.getInputStream(entry)) {
                    byte[] bytes = input.readAllBytes();
                    if (name.endsWith("BrushEditRailScreen")) {
                        // Remove unrelated GUI construction, not the replayed methods' instructions.
                        // Their real ItemStack/component and packet operations remain unchanged.
                        var node = new ClassNode();
                        new ClassReader(bytes).accept(node, 0);
                        node.methods.removeIf(method -> !method.name.equals("getBrushTag")
                            && !method.name.equals("updateBrushTag")
                            && !(method.name.equals("applyBrushToPickedRail") && method.desc.equals("(Lnet/minecraft/nbt/CompoundTag;Z)V")));
                        var writer = new ClassWriter(0);
                        node.accept(writer);
                        bytes = writer.toByteArray();
                    }
                    classes.put(name, bytes);
                }
            }
        }
        final var loader = new URLClassLoader(new URL[]{productionJar.toUri().toURL()}, BrushTestBindings.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!classes.containsKey(name)) return super.loadClass(name, resolve);
                synchronized (getClassLoadingLock(name)) {
                    Class<?> result = findLoadedClass(name);
                    if (result == null) {
                        byte[] bytes = classes.get(name);
                        result = defineClass(name, bytes, 0, bytes.length);
                    }
                    if (resolve) resolveClass(result);
                    return result;
                }
            }
        };
        var mcClass = loader.loadClass(minecraft.replace('/', '.'));
        Object instance = mcClass.getConstructor().newInstance();
        mcClass.getField("player").set(instance, loader.loadClass(player.replace('/', '.')).getConstructor().newInstance());
        mcClass.getField("instance").set(null, instance);
        loader.loadClass("mtr.Items").getField("BRUSH").set(null, new RegistryObject<>(() -> net.minecraft.world.item.Items.BRUSH));
        return loader;
    }

    private static ClassWriter type(String name) {
        final var writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(V21, ACC_PUBLIC, name, null, "java/lang/Object", null);
        final var ctor = writer.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(ALOAD, 0);
        ctor.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();
        return writer;
    }

    private static void forward(ClassWriter writer, int access, String name, String target, String descriptor) {
        final var method = writer.visitMethod(access, name, descriptor, null, null);
        method.visitCode();
        int slot = (access & ACC_STATIC) == 0 ? 1 : 0;
        for (var argument : Type.getArgumentTypes(descriptor)) {
            method.visitVarInsn(argument.getOpcode(ILOAD), slot);
            slot += argument.getSize();
        }
        method.visitMethodInsn(INVOKESTATIC, Type.getInternalName(BrushTestBindings.class), target, descriptor, false);
        method.visitInsn(Type.getReturnType(descriptor).getOpcode(IRETURN));
        method.visitMaxs(0, 0);
        method.visitEnd();
    }

    private static byte[] finish(ClassWriter writer) { writer.visitEnd(); return writer.toByteArray(); }
}
