package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.gui.WidgetScrollList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;
import java.lang.reflect.Field;
import java.util.ArrayList;

public final class GuiWidgetCompatibilityCheck {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        var list = new InspectableList();
        var first = new Child(0); var second = new Child(20); var last = new Child(80);
        list.children.add(first); list.children.add(second); list.children.add(last);
        require(list.maxOffset() == 60, "Scroll content height");
        require(list.mouseScrolled(15, 25, 0, -1) && list.offset() == 20, "Wheel event/interval");
        require(!list.mouseScrolled(0, 0, 0, -1) && list.offset() == 20, "Outside wheel changed offset");
        require(list.mouseClicked(click(15, 25), true) && second.clicks == 1 && first.clicks == 0, "Scrolled child click translation/dispatch");
        require(second.last.x() == 5 && second.last.y() == 25 && second.doubleClick && second.last.modifiers() == 2, "Event details lost");
        require(list.keyPressed(new KeyEvent(65, 30, 2)) && second.keys == 1, "Focused child keyboard event");
        require(list.charTyped(new CharacterEvent(0x1F683)) && second.codepoint == 0x1F683, "Unicode codepoint truncated");
        require(!list.mouseClicked(click(15, 80), false) && last.clicks == 0, "Clipped child accepted click");
        require(list.mouseReleased(click(300, 300)) && second.releases == 1, "Release outside viewport not forwarded");
        require(list.mouseClicked(click(113, 25), false), "Scrollbar click not consumed");
        require(list.mouseDragged(click(113, 200), 0, 175) && list.offset() == 60, "Scrollbar drag lower clamp");
        require(list.mouseDragged(click(113, 0), 0, -200) && list.offset() == 0, "Scrollbar drag upper clamp");
        list.mouseReleased(click(113, 0));
        list.scrollTo(60); list.children.remove(last); list.setHeight(50);
        require(list.offset() == 0, "Resize/content shrink retained stale offset");
        list.children.add(last); list.setHeight(40); list.scrollTo(20);

        var state = new GuiRenderState();
        var graphics = extractor(state);
        var originalPose = new Matrix3x2f(graphics.pose());
        list.extractRenderState(graphics, 15, 25, 0);
        require(graphics.pose().equals(originalPose), "GUI pose stack leaked");
        require(graphics.containsPointInScissor(0, 0), "Nested scissor was not restored");
        require(second.renderedX == 10 && second.renderedY == 0, "Scroll pose not extracted");
        var elements = new ArrayList<Object>();
        state.forEachElement(elements::add, GuiRenderState.TraverseRange.ALL);
        require(elements.size() >= 5, "Background, content or scrollbar GUI elements missing");
        second.fail = true;
        try { list.extractRenderState(graphics, 15, 25, 0); throw new AssertionError("Expected child failure"); }
        catch (IllegalStateException expected) { require(expected.getMessage().equals("fixture"), "Unexpected render failure"); }
        require(graphics.pose().equals(originalPose) && graphics.containsPointInScissor(0, 0), "Child failure leaked pose/scissor");
        textLayout();
        blockPreview();
        System.out.println("PASS: real 26.2 widget input, child dispatch, scroll bounds, Unicode, GUI state extraction and nested clip/pose cleanup; " + assertions + " assertions (headless CPU fixture, no window)");
    }

    private static MouseButtonEvent click(double x, double y) { return new MouseButtonEvent(x, y, new MouseButtonInfo(0, 2)); }

    private static void blockPreview() throws Exception {
        var type = net.minecraft.client.renderer.rendertype.RenderTypes.solidMovingBlock();
        var material = new net.minecraft.client.resources.model.geometry.BakedQuad.MaterialInfo(null,
                net.minecraft.client.renderer.chunk.ChunkSectionLayer.SOLID, type, 1, true, 0);
        var quad = new net.minecraft.client.resources.model.geometry.BakedQuad(
                new org.joml.Vector3f(0, 0, 0), new org.joml.Vector3f(0, 1, 0),
                new org.joml.Vector3f(1, 1, 1), new org.joml.Vector3f(1, 0, 1),
                0, 1, 2, 3, net.minecraft.core.Direction.SOUTH, material);
        var faceList = new ArrayList<net.minecraft.client.resources.model.geometry.BakedQuad>(); faceList.add(quad);
        var faces = new ArrayList<net.minecraft.core.Direction>();
        var part = new net.minecraft.client.renderer.block.dispatch.BlockStateModelPart() {
            public java.util.List<net.minecraft.client.resources.model.geometry.BakedQuad> getQuads(net.minecraft.core.Direction face) {
                faces.add(face); return faceList;
            }
            public boolean useAmbientOcclusion() { return true; }
            public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial() { return null; }
            public int materialFlags() { return 0; }
        };
        var model = new net.minecraft.client.renderer.block.dispatch.BlockStateModel() {
            public void collectParts(net.minecraft.util.RandomSource random, java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts) { parts.add(part); }
            public net.minecraft.client.resources.model.sprite.Material.Baked particleMaterial() { return null; }
            public int materialFlags() { return 0; }
        };
        java.util.List<net.minecraft.client.color.block.BlockTintSource> tints = java.util.List.of(block -> 0x123400, block -> 0x123401);
        var preview = cn.zbx1425.mtrsteamloco.gui.GuiBlockPreview.capture(null, model, tints);
        require(faces.size() == 7 && faces.contains(null) && faces.containsAll(java.util.List.of(net.minecraft.core.Direction.values())),
                "Block preview omitted culled or unculled block-state faces");
        require(preview.usesBlockLight() && !preview.isAnimated(), "Static block preview lighting/cache policy");
        var repeated = cn.zbx1425.mtrsteamloco.gui.GuiBlockPreview.capture(null, model, tints);
        require(preview.getModelIdentity().equals(repeated.getModelIdentity()), "Same state/model should share its GUI atlas entry");
        var recolored = cn.zbx1425.mtrsteamloco.gui.GuiBlockPreview.capture(null, model, java.util.List.of(block -> 0x993400, block -> 0x993401));
        require(!preview.getModelIdentity().equals(recolored.getModelIdentity()), "Different tint reused stale GUI atlas entry");
        var untinted = cn.zbx1425.mtrsteamloco.gui.GuiBlockPreview.capture(null, model, java.util.List.of());
        require(untinted.layers[0].tintLayers().getInt(1) == -1, "Missing block tint provider must default to opaque white");
        faceList.clear();

        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        pose.translate(108, 208, 0); pose.scale(16, -16, 16);
        var original = new org.joml.Matrix4f(pose.last().pose());
        var nodes = new net.minecraft.client.renderer.SubmitNodeStorage();
        preview.submit(pose, nodes, 0x00F000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, 0);
        require(pose.last().pose().equals(original), "Preview submission leaked pose");
        var output = new ArrayList<net.minecraft.client.renderer.feature.ItemFeatureRenderer.Submit>();
        nodes.drainPhases(phase -> phase.sortInto((node, blending) -> output.add((net.minecraft.client.renderer.feature.ItemFeatureRenderer.Submit) node)));
        require(output.size() == 1 && output.getFirst().quads().size() == 7, "Preview did not capture all block quads before deferred submission");
        var submit = output.getFirst();
        require(submit.displayContext() == net.minecraft.world.item.ItemDisplayContext.GUI && submit.quads().getFirst() == quad,
                "Preview lost state-specific geometry/materials");
        require(submit.tintLayers().length == 2 && submit.tintLayers()[1] == 0xFF123401 && submit.lightCoords() == 0x00F000F0,
                "Block preview tint indices, opacity or full-bright lighting changed");
        var expected = new org.joml.Matrix4f().translation(100, 216, 0).scale(15.5F, -15.5F, 15.5F).rotateX((float) Math.toRadians(3));
        require(submit.pose().pose().equals(expected, 0.00001F), "Block preview no longer matches legacy position/scale/rotation");
        require(preview.isOversizedInGui(), "Tilted block would be cropped to the 16x16 atlas slot");

        var guiState = new GuiRenderState();
        var graphics = extractor(guiState);
        graphics.pose().translate(4, 6);
        graphics.enableScissor(10, 10, 80, 80);
        cn.zbx1425.mtrsteamloco.gui.GuiBlockPreview.extract(graphics, preview, 20, 30);
        graphics.disableScissor(); graphics.pose().identity();
        var items = new ArrayList<net.minecraft.client.renderer.state.gui.GuiItemRenderState>();
        guiState.forEachItem(items::add);
        require(items.size() == 1 && items.getFirst().itemStackRenderState() == preview, "Block preview bypassed vanilla GUI item state");
        require(items.getFirst().pose().m20() == 4 && items.getFirst().pose().m21() == 6, "Deferred preview did not snapshot GUI pose");
        require(items.getFirst().scissorArea().equals(new ScreenRectangle(14, 16, 70, 70)), "Deferred preview lost the transformed scissor");
        require(graphics.containsPointInScissor(0, 0), "Block preview leaked scissor state");
        var empty = cn.zbx1425.mtrsteamloco.gui.GuiBlockPreview.capture(null, model, java.util.List.of());
        require(empty.isEmpty(), "An empty block model should not submit an item layer");
    }

    private static void textLayout() throws Exception {
        var drawing = new cn.zbx1425.mtrsteamloco.gui.IGraphics() { };
        var glyph = new net.minecraft.client.gui.font.glyphs.BakedSheetGlyph(com.mojang.blaze3d.font.GlyphInfo.simple(6),
                net.minecraft.client.gui.font.GlyphRenderTypes.createForColorTexture(net.minecraft.resources.Identifier.parse("ante:fixture")),
                null, 0, 1, 0, 1, 0, 6, 0, 8);
        var source = new net.minecraft.client.gui.GlyphSource() {
            public net.minecraft.client.gui.font.glyphs.BakedGlyph getGlyph(int codepoint) { return glyph; }
            public net.minecraft.client.gui.font.glyphs.BakedGlyph getRandomGlyph(net.minecraft.util.RandomSource random, int width) { return glyph; }
        };
        var font = new net.minecraft.client.gui.Font(new net.minecraft.client.gui.Font.Provider() {
            public net.minecraft.client.gui.GlyphSource glyphs(net.minecraft.network.chat.FontDescription description) { return source; }
            public net.minecraft.client.gui.font.glyphs.EffectGlyph effect() { return glyph; }
        });
        var state = new GuiRenderState();
        var graphics = extractor(state);
        var original = new Matrix3x2f(graphics.pose());
        var alignment = mtr.data.IGui.HorizontalAlignment.LEFT;
        float[] bounds = new float[4];
        drawing.drawStringWithFont(graphics, font, null, "A||中", alignment, mtr.data.IGui.VerticalAlignment.TOP, alignment,
                10, 20, -1, -1, 1, 0xFF112233, 0xFF445566, 2, false, mtr.data.IGui.MAX_LIGHT_GLOWING,
                (x1, y1, x2, y2) -> { bounds[0] = x1; bounds[1] = y1; bounds[2] = x2; bounds[3] = y2; });
        var texts = new ArrayList<net.minecraft.client.renderer.state.gui.GuiTextRenderState>();
        state.forEachText(texts::add);
        require(texts.size() == 2, "GUI text split/CJK layout did not queue two lines");
        require(texts.get(0).pose.m20() == 10 && texts.get(0).pose.m21() == 20, "GUI text origin");
        require(texts.get(1).pose.m00() == 2 && texts.get(1).pose.m21() == 20 + mtr.data.IGui.LINE_HEIGHT, "CJK scale/line position");
        require(bounds[0] == 10 && bounds[2] == 22 && bounds[3] - bounds[1] == mtr.data.IGui.LINE_HEIGHT * 3, "GUI text callback bounds");
        require(graphics.pose().equals(original), "Text layout leaked pose");
        state.reset();
        drawing.drawStringWithFont(graphics, font, null, "A", 10, 20, mtr.data.IGui.MAX_LIGHT_GLOWING);
        texts.clear(); state.forEachText(texts::add);
        require(texts.size() == 1, "Short GUI overload did not delegate to layout");
        require(graphics.pose().equals(original), "Short GUI text overload leaked pose");
        labelOpacity(font);
    }

    private static void labelOpacity(net.minecraft.client.gui.Font font) throws Exception {
        var singleton = net.minecraft.client.Minecraft.class.getDeclaredField("instance"); singleton.setAccessible(true);
        Object previous = singleton.get(null);
        Object fixture = allocate(net.minecraft.client.Minecraft.class);
        set(fixture, "font", font);
        singleton.set(null, fixture);
        try {
            var state = new GuiRenderState();
            var graphics = extractor(state);
            var label = new cn.zbx1425.mtrsteamloco.gui.WidgetLabel(20, 20, 100, Component.literal("Label"));
            label.color = 0x123456; // Legacy callers supply RGB, not necessarily ARGB.
            label.setAlpha(0.5F);
            label.extractWidgetRenderState(graphics, 0, 0, 0);
            var texts = new ArrayList<net.minecraft.client.renderer.state.gui.GuiTextRenderState>();
            state.forEachText(texts::add);
            var colorField = net.minecraft.client.renderer.state.gui.GuiTextRenderState.class.getDeclaredField("color"); colorField.setAccessible(true);
            require(texts.size() == 1 && colorField.getInt(texts.getFirst()) == 0x80123456, "Label opacity/RGB normalization missing from extracted text");
            state.reset(); label.setAlpha(0);
            label.extractWidgetRenderState(graphics, 0, 0, 0);
            texts.clear(); state.forEachText(texts::add);
            require(texts.isEmpty(), "Zero-opacity label must not reappear as opaque legacy RGB");
        } finally { singleton.set(null, previous); }
    }

    private static final class InspectableList extends WidgetScrollList {
        InspectableList() { super(10, 20, 100, 40); }
        double offset() { return getOffset(); }
        int maxOffset() { return getMaxOffset(); }
        void scrollTo(double value) { setOffset(value); }
    }

    private static final class Child extends AbstractWidget {
        int clicks, releases, keys, codepoint;
        MouseButtonEvent last;
        boolean doubleClick, fail;
        float renderedX, renderedY;
        Child(int y) { super(0, y, 100, 20, Component.empty()); }
        @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
            if (!isMouseOver(event.x(), event.y())) return false;
            clicks++; last = event; doubleClick = twice; return true;
        }
        @Override public boolean mouseReleased(MouseButtonEvent event) { releases++; return true; }
        @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) { return false; }
        @Override public boolean keyPressed(KeyEvent event) { keys++; return true; }
        @Override public boolean charTyped(CharacterEvent event) { codepoint = event.codepoint(); return true; }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
            if (fail) throw new IllegalStateException("fixture");
            renderedX = graphics.pose().m20(); renderedY = graphics.pose().m21();
            graphics.fill(getX(), getY(), getRight(), getBottom(), 0xFFFFFFFF);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { }
    }

    /** Initialize only CPU extraction state; real fill/scissor/pose methods run without booting Minecraft's window. */
    private static GuiGraphicsExtractor extractor(GuiRenderState state) throws Exception {
        var graphics = (GuiGraphicsExtractor) allocate(GuiGraphicsExtractor.class);
        set(graphics, "pose", new Matrix3x2fStack(16)); set(graphics, "guiRenderState", state);
        Class<?> scissors = Class.forName("net.minecraft.client.gui.GuiGraphicsExtractor$ScissorStack");
        var constructor = scissors.getDeclaredConstructor(ScreenRectangle.class); constructor.setAccessible(true);
        set(graphics, "scissorStack", constructor.newInstance(new ScreenRectangle(0, 0, 1000, 1000)));
        return graphics;
    }
    private static Object allocate(Class<?> type) throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        Field singleton = unsafeClass.getDeclaredField("theUnsafe"); singleton.setAccessible(true);
        return unsafeClass.getMethod("allocateInstance", Class.class).invoke(singleton.get(null), type);
    }
    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
