package cn.zbx1425.mtrsteamloco.network;

import cn.zbx1425.mtrsteamloco.Main;
import cn.zbx1425.mtrsteamloco.item.BlockItemEyeCandy;
import io.netty.buffer.Unpooled;
import mtr.RegistryClient;
import mtr.data.RailwayData;
import mtr.mappings.ItemStackUtilities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

public class PacketUpdateHoldingItem {

    public static ResourceLocation PACKET_UPDATE_HOLDING_ITEM = ResourceLocation.fromNamespaceAndPath(Main.MOD_ID, "update_holding_item");

    public static void sendUpdateC2S(InteractionHand hand) {
        final var player = Minecraft.getInstance().player;
        if (player == null) return;
        final FriendlyByteBuf packet = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.writeBoolean(hand == InteractionHand.MAIN_HAND);
            ItemStack.STREAM_CODEC.encode(new RegistryFriendlyByteBuf(packet, player.registryAccess()), player.getItemInHand(hand));
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
        final ItemStack incoming = ItemStack.STREAM_CODEC.decode(new RegistryFriendlyByteBuf(packet, player.registryAccess()));
        server.execute(() -> {
            if (RailwayData.hasNoPermission(player)) return;
            final ItemStack held = player.getItemInHand(isMainHand ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
            if (held.isEmpty() || held.getCount() != incoming.getCount() || !ItemStack.isSameItem(held, incoming)) return;

            final String editableTag;
            if (held.is(mtr.Items.BRUSH.get())) editableTag = "NTERailBrush";
            else if (held.getItem() instanceof BlockItemEyeCandy) editableTag = "BlockEntityTag";
            else if (held.is(Main.COMPOUND_CREATOR.get())) editableTag = "tasks";
            else if (held.is(Main.ROUTE_PATH_CREATOR.get())) editableTag = "ANTE-Data";
            else return;

            // The client edits only this tool's settings, not item identity, count or other components.
            final ItemStack components = incoming.copy();
            if (held.has(DataComponents.CUSTOM_DATA)) components.set(DataComponents.CUSTOM_DATA, held.get(DataComponents.CUSTOM_DATA));
            else components.remove(DataComponents.CUSTOM_DATA);
            if (!ItemStack.isSameItemSameComponents(held, components)) return;

            final CompoundTag saved = ItemStackUtilities.getCustomData(held);
            final CompoundTag edited = ItemStackUtilities.getCustomData(incoming);
            if (edited.contains(editableTag)) saved.put(editableTag, edited.get(editableTag).copy());
            else saved.remove(editableTag);
            final ItemStack updated = held.copy();
            ItemStackUtilities.setCustomData(updated, saved);
            player.setItemSlot(isMainHand ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND, updated);
        });
    }

}
