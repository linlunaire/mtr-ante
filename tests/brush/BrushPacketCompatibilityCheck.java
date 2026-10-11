package cn.zbx1425.mtrsteamloco.compatibility;

import mtr.mappings.ItemStackUtilities;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Executes the unmodified released packet and brush-update methods; no game, connection or GPU. */
public final class BrushPacketCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        try {
            Class.forName("net.neoforged.fml.loading.LoadingModList")
                .getMethod("of", List.class, List.class, List.class, List.class, Map.class)
                .invoke(null, List.of(), List.of(), List.of(), List.of(), Map.of());
        } catch (ClassNotFoundException ignored) { /* Fabric has no NeoForge loader. */ }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        try {
            // NeoForge's ModifyRegistriesEvent normally marks these built-ins for ID syncing.
            Class<?> baseRegistry = Class.forName("net.neoforged.neoforge.registries.BaseMappedRegistry");
            Method sync = baseRegistry.getDeclaredMethod("setSync", boolean.class);
            sync.setAccessible(true);
            sync.invoke(BuiltInRegistries.ITEM, true);
            sync.invoke(BuiltInRegistries.DATA_COMPONENT_TYPE, true);
        } catch (ClassNotFoundException ignored) { /* Vanilla/Fabric registries need no NeoForge startup flags. */ }
        BrushTestBindings.registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ClassLoader loader = BrushTestBindings.loader(Path.of(args[0]));
        final var packetClass = loader.loadClass("cn.zbx1425.mtrsteamloco.network.PacketUpdateHoldingItem");
        final var send = packetClass.getMethod("sendUpdateC2S", InteractionHand.class);
        final var receive = packetClass.getMethod("receiveUpdateC2S", loader.loadClass("net.minecraft.server.MinecraftServer"),
            loader.loadClass("net.minecraft.server.level.ServerPlayer"), FriendlyByteBuf.class);
        Object server = loader.loadClass("net.minecraft.server.MinecraftServer").getConstructor().newInstance();
        Object serverPlayer = loader.loadClass("net.minecraft.server.level.ServerPlayer").getConstructor().newInstance();
        String mode = args.length > 1 ? args[1] : "all";
        if (!mode.equals("brush")) {
            for (var hand : InteractionHand.values()) {
                BrushTestBindings.mainHand = item();
                BrushTestBindings.offHand = item();
                ItemStack expected = BrushTestBindings.itemInHand(hand).copy();
                invoke(send, null, hand);
                require(BrushTestBindings.sentId.toString().equals("mtrsteamloco:update_holding_item"), "Packet ID changed");
                try (var packet = new Buffer(BrushTestBindings.receivedBuffer())) {
                    require(packet.value.readBoolean() == (hand == InteractionHand.MAIN_HAND), "Hand marker changed");
                    ItemStack decoded = ItemStack.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(packet.value, BrushTestBindings.registries));
                    require(ItemStack.matches(expected, decoded), "Item count/components changed");
                    require(!packet.value.isReadable(), "Unread bytes in holding-item packet");
                }
                try (var packet = new Buffer(BrushTestBindings.receivedBuffer())) {
                    invoke(receive, null, server, serverPlayer, packet.value);
                    require(BrushTestBindings.receivedSlot == (hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND), "Wrong server inventory slot");
                    require(ItemStack.matches(expected, BrushTestBindings.receivedItem), "Server lost item components");
                    require(!packet.value.isReadable(), "Server left packet bytes unread");
                }
                require(BrushTestBindings.sentBuffer.refCnt() == 0, "Outgoing buffer leaked");
            }
            System.out.println("PASS: released send/receive, both hands, real ItemStack registry codecs, custom components, unchanged ID/wire layout and buffer ownership");
        }
        if (!mode.equals("packet")) {
            final var update = loader.loadClass("cn.zbx1425.mtrsteamloco.gui.BrushEditRailScreen")
                .getMethod("updateBrushTag", Consumer.class);
            for (var hand : InteractionHand.values()) {
                BrushTestBindings.mainHand = hand == InteractionHand.MAIN_HAND ? item() : ItemStack.EMPTY;
                BrushTestBindings.offHand = hand == InteractionHand.OFF_HAND ? item() : ItemStack.EMPTY;
                Consumer<CompoundTag> modifier = tag -> tag.putFloat("VerticalCurveRadius", 4000F);
                Throwable failure = null;
                try { invoke(update, null, modifier); } catch (Throwable ex) { failure = ex; }
                var custom = ItemStackUtilities.getCustomData(BrushTestBindings.itemInHand(hand));
                require(custom.getCompound("NTERailBrush").getFloat("VerticalCurveRadius") == 4000F,
                    "Vertical curve edit did not persist to the held brush component (" + hand + ")");
                if (failure != null) throw new AssertionError("Brush edit failed", failure);
                require(custom.getString("Unrelated").equals("preserve-me"), "Brush edit lost unrelated custom data");
                try (var packet = new Buffer(BrushTestBindings.receivedBuffer())) {
                    invoke(receive, null, server, serverPlayer, packet.value);
                    require(ItemStack.matches(BrushTestBindings.itemInHand(hand), BrushTestBindings.receivedItem), "Server did not receive edited brush settings");
                }
                invoke(update, null, (Consumer<CompoundTag>) tag -> tag.remove("VerticalCurveRadius"));
                require(!ItemStackUtilities.getCustomData(BrushTestBindings.itemInHand(hand)).getCompound("NTERailBrush").contains("VerticalCurveRadius"), "Disabling vertical curve did not persist");
            }
            System.out.println("PASS: actual brush-update method persists enable/disable to both hands and synchronizes edited components without losing unrelated data");
        }
    }

    private static ItemStack item() {
        var item = new ItemStack(Items.BRUSH);
        var custom = new CompoundTag();
        custom.putString("Unrelated", "preserve-me");
        var brush = new CompoundTag();
        brush.putString("ModelKey", "test:rail");
        custom.put("NTERailBrush", brush);
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
        item.set(DataComponents.CUSTOM_NAME, Component.literal("轨道编辑测试"));
        return item;
    }

    private static Object invoke(Method method, Object instance, Object... args) throws Exception {
        try { return method.invoke(instance, args); }
        catch (InvocationTargetException ex) {
            if (ex.getCause() instanceof Exception cause) throw cause;
            if (ex.getCause() instanceof Error cause) throw cause;
            throw ex;
        }
    }

    private record Buffer(FriendlyByteBuf value) implements AutoCloseable {
        @Override public void close() { value.release(); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
