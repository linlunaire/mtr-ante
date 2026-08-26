package cn.zbx1425.mtrsteamloco.network;

import cn.zbx1425.mtrsteamloco.Main;
import io.netty.buffer.Unpooled;
import mtr.RegistryClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

public class PacketUpdateHoldingItem {

    public static ResourceLocation PACKET_UPDATE_HOLDING_ITEM = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "update_holding_item");

    public static void sendUpdateC2S(InteractionHand hand) {
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        assert Minecraft.getInstance().player != null;
        if (hand == InteractionHand.OFF_HAND) packet.writeBoolean(false);
        else packet.writeBoolean(true);
        ItemStack.STREAM_CODEC.encode(new RegistryFriendlyByteBuf(packet, RegistryAccess.EMPTY), Minecraft.getInstance().player.getItemInHand(hand));
        RegistryClient.sendToServer(PACKET_UPDATE_HOLDING_ITEM, packet);
    }

    public static void sendUpdateC2S() {
        sendUpdateC2S(InteractionHand.MAIN_HAND);
    }

    public static void receiveUpdateC2S(MinecraftServer server, ServerPlayer player, FriendlyByteBuf packet) {
        boolean isMainHand = packet.readBoolean();
        player.setItemSlot(isMainHand ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, ItemStack.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(packet, RegistryAccess.EMPTY)));
    }

}
