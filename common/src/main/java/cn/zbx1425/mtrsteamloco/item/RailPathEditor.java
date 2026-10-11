package cn.zbx1425.mtrsteamloco.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import cn.zbx1425.mtrsteamloco.block.BlockDirectNode;
import cn.zbx1425.mtrsteamloco.mixin.RailwayDataAccessor;
import cn.zbx1425.mtrsteamloco.network.PacketScreen;
import mtr.CreativeModeTabs;
import mtr.data.Rail;
import mtr.data.RailType;
import mtr.data.RailwayData;
import mtr.item.ItemWithCreativeTabBase;
import mtr.mappings.Text;
import mtr.mappings.ItemStackUtilities;

import java.util.*;

public class RailPathEditor extends ItemWithCreativeTabBase {

    public RailPathEditor() {
        super(
            CreativeModeTabs.CORE, p -> p.stacksTo(1)
        );
    }

    @Override
    public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand usedHand) {
        ItemStack itemStack = player.getItemInHand(usedHand);
        if (level.isClientSide()) return net.minecraft.world.InteractionResult.SUCCESS.heldItemTransformedTo(itemStack);

        CompoundTag tag = ItemStackUtilities.getCustomData(itemStack);
        if (tag.contains("start") && tag.contains("end")) {
            BlockPos posStart = BlockPos.of(mtr.mappings.CompoundTagMapper.getLong(tag, "start"));
            BlockPos posEnd = BlockPos.of(mtr.mappings.CompoundTagMapper.getLong(tag, "end"));
            RailwayData railwayData = RailwayData.getInstance(level);
            boolean success = false;
            if (railwayData != null) { 
                Map<BlockPos, Rail> map = ((RailwayDataAccessor) (Object) railwayData).getRails().get(posStart);
                if (map != null) {
                    Rail rail = map.get(posEnd);
                    if (rail != null && player instanceof ServerPlayer sp) {
                        PacketScreen.sendScreenRailPathEditorS2C(sp, rail, posStart, posEnd);
                        success = true;
                    }
                }
            }
            if (!success) {
                player.sendOverlayMessage(Text.translatable("tooltip.mtrsteamloco.rail_path_editor.data_not_found"));
            }
        } else {
            player.sendOverlayMessage(Text.translatable("tooltip.mtrsteamloco.rail_path_editor.no_data"));
        }
        return net.minecraft.world.InteractionResult.SUCCESS.heldItemTransformedTo(itemStack);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        boolean success = updateRail(ctx);

        return InteractionResult.SUCCESS;
    }

    // 在服务器端
    private boolean updateRail(UseOnContext ctx) {
        if (ctx.getLevel().isClientSide()) return true;
        final RailwayData railwayData = RailwayData.getInstance(ctx.getLevel());
        if (railwayData == null) return false;
        BlockPos rPosStart = ctx.getClickedPos();
        Map<BlockPos, Rail> map = ((RailwayDataAccessor) (Object) railwayData).getRails().get(rPosStart);
        if (map == null) return false;
        
        Player player = ctx.getPlayer();
        if (player == null) return false;
        Optional<Map.Entry<BlockPos, Rail>> closestEntry = map.entrySet().stream().min(Comparator.comparingDouble(entry ->
                Mth.degreesDifferenceAbs((float) -Math.toDegrees(Math.atan2(entry.getKey().getX() - rPosStart.getX(), entry.getKey().getZ() - rPosStart.getZ())), player.getYRot())
        ));
        if (closestEntry.isEmpty()) return false;
        BlockPos posEnd = closestEntry.get().getKey();
        Rail rail = closestEntry.get().getValue();
        BlockPos posStart = rPosStart;
        if (rail.railType == RailType.NONE) {
            posStart = posEnd;
            posEnd = rPosStart;
        }
        ItemStack itemStack = ctx.getItemInHand();
        CompoundTag tag = ItemStackUtilities.getCustomData(itemStack);
        tag.putLong("start", posStart.asLong());
        tag.putLong("end", posEnd.asLong());
        ItemStackUtilities.setCustomData(itemStack, tag);
        if (ctx.getPlayer() != null) {
            if (ctx.getPlayer() instanceof ServerPlayer sp) {
                sp.setItemSlot(EquipmentSlot.MAINHAND, itemStack);

                sp.sendOverlayMessage(Text.translatable("tooltip.mtrsteamloco.rail_path_editor.success_update", posStart.getX(), posStart.getY(), posStart.getZ(), posEnd.getX(), posEnd.getY(), posEnd.getZ()));
                return true;
            }
        } 
        return false;
    }
}
