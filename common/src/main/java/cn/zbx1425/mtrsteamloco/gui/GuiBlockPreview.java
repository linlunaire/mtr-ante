package cn.zbx1425.mtrsteamloco.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/** Block-state geometry, not the block item's default model, submitted to vanilla's deferred GUI renderer. */
public final class GuiBlockPreview {
    private GuiBlockPreview() { }

    public static void extract(GuiGraphicsExtractor graphics, BlockState block, int x, int y) {
        if (block.isAir()) return;
        if (block.getRenderShape() != RenderShape.MODEL) {
            // Chests/banners and other special models are now provided by their client item definitions.
            graphics.fakeItem(new ItemStack(block.getBlock()), x, y);
            return;
        }
        var minecraft = Minecraft.getInstance();
        var model = minecraft.getModelManager().getBlockStateModelSet().get(block);
        var state = capture(block, model, minecraft.getBlockColors().getTintSources(block));
        extract(graphics, state, x, y);
    }

    public static void extract(GuiGraphicsExtractor graphics, TrackingItemStackRenderState state, int x, int y) {
        if (!state.isEmpty()) graphics.guiRenderState.addItem(new GuiItemRenderState(
                new Matrix3x2f(graphics.pose()), state, x, y, graphics.scissorStack.peek()));
    }

    public static TrackingItemStackRenderState capture(BlockState block, BlockStateModel model, List<BlockTintSource> tints) {
        var state = new TrackingItemStackRenderState();
        state.displayContext = ItemDisplayContext.GUI;
        var parts = new ArrayList<BlockStateModelPart>();
        model.collectParts(RandomSource.create(42), parts);
        var quads = new ArrayList<BakedQuad>();
        for (var part : parts) {
            for (var direction : Direction.values()) quads.addAll(part.getQuads(direction));
            quads.addAll(part.getQuads(null));
        }
        if (quads.isEmpty()) return state;
        var layer = state.newLayer();
        layer.prepareQuadList().addAll(quads);
        layer.setUsesBlockLight(true);
        layer.setParticleMaterial(model.particleMaterial());
        // GUI items apply T(8,8) S(16,-16,16) T(-.5,-.5,-.5) first.
        // Restore the editor's T(0,16) S(15.5,-15.5,15.5) Rx(3 degrees) preview.
        layer.setLocalTransform(new Matrix4f().translation(0, 0, 0.5F)
                .scale(15.5F / 16).rotateX((float) Math.toRadians(3)));
        var extents = new ArrayList<Vector3fc>();
        int maxTint = -1;
        for (var quad : quads) {
            maxTint = Math.max(maxTint, quad.materialInfo().tintIndex());
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) extents.add(new Vector3f(quad.position(i)));
        }
        var capturedExtents = extents.toArray(Vector3fc[]::new);
        layer.setExtents(() -> capturedExtents);
        var colors = new ArrayList<Integer>();
        for (int i = 0; i <= maxTint; i++) {
            int color = i < tints.size() ? tints.get(i).color(block) | 0xFF000000 : -1;
            layer.tintLayers().add(color);
            colors.add(color);
        }
        state.appendModelIdentityElement(new Identity(block, model, List.copyOf(colors)));
        if (model.hasMaterialFlag(BakedQuad.FLAG_ANIMATED)) state.setAnimated();
        var bounds = state.getModelBoundingBox();
        state.setOversizedInGui(bounds.minX < -0.5 || bounds.maxX > 0.5 || bounds.minY < -0.5 || bounds.maxY > 0.5);
        return state;
    }

    private record Identity(BlockState block, BlockStateModel model, List<Integer> tints) { }
}
