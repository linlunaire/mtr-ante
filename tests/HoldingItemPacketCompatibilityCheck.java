package cn.zbx1425.mtrsteamloco.network;

import io.netty.buffer.Unpooled;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

/** Calls the production holding-item packet encoder and decoder, without a client/server launch. */
public final class HoldingItemPacketCompatibilityCheck {
    public static void main(String[] args) {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(pending -> pending.apply());
        final RegistryAccess registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        final ItemStack item = new ItemStack(Items.DIAMOND, 3);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("线路编辑测试"));
        final CompoundTag custom = new CompoundTag();
        custom.putString("prefabId", "test_model");
        custom.putLongArray("ids", new long[]{Long.MIN_VALUE, Long.MAX_VALUE});
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));

        for (InteractionHand hand : InteractionHand.values()) {
            roundTrip(hand, item, registries);
            roundTrip(hand, ItemStack.EMPTY, registries);
        }

        final FriendlyByteBuf emptyRegistryPacket = new FriendlyByteBuf(Unpooled.buffer());
        try {
            try {
                ItemStack.STREAM_CODEC.encode(new RegistryFriendlyByteBuf(emptyRegistryPacket, RegistryAccess.EMPTY), item);
                throw new AssertionError("Empty-registry negative control unexpectedly encoded a nonempty item");
            } catch (IllegalStateException expected) {
                require(expected.getMessage().contains("minecraft:item"), "Unexpected negative-control failure: " + expected);
            }
        } finally {
            emptyRegistryPacket.release();
        }
        System.out.println("PASS: production holding-item packet, both hands, empty/nonempty stacks, item count/custom components and empty-registry negative control");
    }

    private static void roundTrip(InteractionHand hand, ItemStack original, RegistryAccess registries) {
        final FriendlyByteBuf packet = PacketUpdateHoldingItem.encode(hand, original, registries);
        try {
            require(packet.readBoolean() == (hand == InteractionHand.MAIN_HAND), "Changed hand marker");
            final ItemStack decoded = PacketUpdateHoldingItem.decodeItem(packet, registries);
            require(ItemStack.matches(original, decoded), "Item/count/components changed");
            require(!packet.isReadable(), "Unread packet bytes");
        } finally {
            packet.release();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
