package cn.zbx1425.mtrsteamloco.network;

import cn.zbx1425.mtrsteamloco.Main;
import io.netty.buffer.Unpooled;
import mtr.RegistryClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

public class PacketUpdateHoldingItem {

    public static Identifier PACKET_UPDATE_HOLDING_ITEM = Identifier.fromNamespaceAndPath(Main.MOD_ID, "update_holding_item");

    public static void sendUpdateC2S(InteractionHand hand) {
        final var player = Minecraft.getInstance().player;
        assert player != null;
        final FriendlyByteBuf packet = encode(hand, player.getItemInHand(hand), player.registryAccess());
        try {
            RegistryClient.sendToServer(PACKET_UPDATE_HOLDING_ITEM, packet);
        } finally {
            packet.release();
        }
    }

    public static void sendUpdateC2S() {
        sendUpdateC2S(InteractionHand.MAIN_HAND);
    }

    public static void receiveUpdateC2S(MinecraftServer server, ServerPlayer player, FriendlyByteBuf packet) {
        final boolean isMainHand = packet.readBoolean();
        player.setItemSlot(isMainHand ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, decodeItem(packet, player.registryAccess()));
    }

    static FriendlyByteBuf encode(InteractionHand hand, ItemStack item, RegistryAccess registries) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.writeBoolean(hand == InteractionHand.MAIN_HAND);
            ItemStack.OPTIONAL_STREAM_CODEC.encode(new RegistryFriendlyByteBuf(packet, registries), item);
            return packet;
        } catch (RuntimeException | Error exception) {
            packet.release();
            throw exception;
        }
    }

    static ItemStack decodeItem(FriendlyByteBuf packet, RegistryAccess registries) {
        return ItemStack.OPTIONAL_STREAM_CODEC.decode(new RegistryFriendlyByteBuf(packet, registries));
    }
}
