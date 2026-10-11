package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.render.PreviewProjection;
import cn.zbx1425.mtrsteamloco.render.rail.RailDebugGeometry;
import cn.zbx1425.mtrsteamloco.scripting.util.client.IScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.mappings.RenderBufferSource;
import mtr.mappings.RenderSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class WorldAndScriptCompatibilityCheck {
    private static int assertions;
    public static void main(String[] args) throws Exception {
        projection();
        boundingBox();
        screenInput();
        cn.zbx1425.mtrsteamloco.render.integration.TrainModelCaptureCompatibilityCheck.run();
        System.out.println("PASS: preview clip projection, 12 real delayed rail-box edges and legacy script callbacks/vanilla input fallback; " + assertions + " assertions (no window/GPU)");
    }

    private static void projection() {
        for (boolean zeroToOne : new boolean[]{false, true}) {
            Matrix4f original = new Matrix4f().perspective(1.2F, 1.7F, 0.05F, 1000, zeroToOne);
            Matrix4f copy = new Matrix4f(original);
            Matrix4f result = PreviewProjection.apply(original);
            Vector4f point = new Vector4f(2, 1, -10, 1);
            Vector4f oldClip = original.transform(new Vector4f(point));
            Vector4f newClip = result.transform(new Vector4f(point));
            near(newClip.x, 0.8F * oldClip.x + 0.5F * oldClip.w);
            near(newClip.y, 0.8F * oldClip.y);
            near(newClip.z, oldClip.z); near(newClip.w, oldClip.w);
            require(original.equals(copy), "Preview mutated the reusable projection");
        }
    }

    private static void boundingBox() {
        PoseStack pose = new PoseStack(); pose.translate(5, 6, 7);
        RenderSnapshot snapshot;
        try (var source = RenderBufferSource.begin(Vec3.ZERO)) {
            RailDebugGeometry.renderLineBox(pose, source.getBuffer(RenderTypes.lines()), new AABB(1, 2, 3, 4, 5, 6), 1, 0, 1, 1);
            snapshot = source.snapshot();
        }
        pose.setIdentity();
        var storage = new SubmitNodeStorage();
        snapshot.submit(pose, storage, new CameraRenderState());
        List<Vector3f> points = new ArrayList<>(), normals = new ArrayList<>();
        storage.drainPhases(phase -> phase.sortInto((node, blending) -> {
            require(node instanceof CustomFeatureRenderer.Submit, "Rail box not captured as geometry");
            var geometry = (CustomFeatureRenderer.Submit) node;
            require(geometry.renderType() == RenderTypes.lines(), "Rail box lost line material");
            geometry.customGeometryRenderer().render(geometry.pose(), new VertexConsumer() {
                public VertexConsumer addVertex(float x, float y, float z) { points.add(new Vector3f(x, y, z)); return this; }
                public VertexConsumer setColor(int color) { require(color == 0xFFFF00FF, "Box parity color/alpha changed"); return this; }
                public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(a << 24 | r << 16 | g << 8 | b); }
                public VertexConsumer setUv(float u, float v) { return this; }
                public VertexConsumer setUv1(int u, int v) { return this; }
                public VertexConsumer setUv2(int u, int v) { return this; }
                public VertexConsumer setNormal(float x, float y, float z) { normals.add(new Vector3f(x, y, z)); return this; }
                public VertexConsumer setLineWidth(float width) { near(width, 1); return this; }
            });
        }));
        require(points.size() == 24 && new HashSet<>(points).size() == 8, "Rail box must have 12 edges and 8 corners");
        HashSet<String> edges = new HashSet<>();
        for (int i = 0; i < points.size(); i += 2) {
            Vector3f a = points.get(i), b = points.get(i + 1), delta = new Vector3f(b).sub(a);
            near(delta.length(), 3);
            require(delta.normalize().equals(normals.get(i)) && normals.get(i).equals(normals.get(i + 1)), "Line direction differs from endpoints");
            require(a.x >= 6 && b.x <= 9 && a.y >= 8 && b.y <= 11 && a.z >= 10 && b.z <= 13, "Captured box transform changed");
            edges.add(a + "/" + b);
        }
        require(edges.size() == 12, "Duplicate box edge");
    }

    private static void screenInput() throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe) unsafeField.get(null);
        var instanceField = Minecraft.class.getDeclaredField("instance"); instanceField.setAccessible(true);
        Object previous = instanceField.get(null);
        instanceField.set(null, unsafe.allocateInstance(Minecraft.class));
        try {
            var screen = new IScreen.WithTexture(Component.literal("test"));
            List<String> callbacks = new ArrayList<>();
            screen.keyPressResponder = (s, key, scan, mods) -> { callbacks.add("key:" + key + ":" + scan + ":" + mods); return true; };
            screen.keyReleasedFunction = (s, key, scan, mods) -> { callbacks.add("up:" + key + ":" + scan + ":" + mods); return true; };
            screen.mouseClickedFunction = (s, x, y, button) -> { callbacks.add("click:" + x + ":" + y + ":" + button); return true; };
            screen.mouseDraggedFunction = (s, x, y, button, dx, dy) -> { callbacks.add("drag:" + dx + ":" + dy); return true; };
            screen.mouseReleasedFunction = (s, x, y, button) -> { callbacks.add("release:" + button); return true; };
            StringBuilder chars = new StringBuilder();
            screen.charTypedFunction = (s, c, mods) -> { chars.append(c); return true; };
            var key = new KeyEvent(65, 30, 3);
            var mouse = new MouseButtonEvent(4, 5, new MouseButtonInfo(1, 2));
            require(screen.keyPressed(key) && screen.keyReleased(key), "Script keys not consumed");
            require(screen.mouseClicked(mouse, true) && screen.mouseDragged(mouse, 2, 3) && screen.mouseReleased(mouse), "Script mouse not consumed");
            require(screen.charTyped(new CharacterEvent(0x1F687)) && chars.toString().equals("🚇"), "Legacy char callback truncated a supplementary code point");
            require(callbacks.equals(List.of("key:65:30:3", "up:65:30:3", "click:4.0:5.0:1", "drag:2.0:3.0", "release:1")), "Script callback argument mapping changed");
            var child = screen._addWidget(new InputProbe());
            screen.setFocused(child);
            screen.charTypedFunction = (s, c, mods) -> false;
            require(screen.charTyped(new CharacterEvent(0x1F687)) && child.codepoint == 0x1F687 && child.characters == 1, "Vanilla fallback lost Unicode or ran twice");
        } finally {
            instanceField.set(null, previous);
        }
    }

    private static final class InputProbe extends AbstractWidget {
        int codepoint, characters;
        InputProbe() { super(0, 0, 10, 10, Component.empty()); }
        public boolean charTyped(CharacterEvent event) { codepoint = event.codepoint(); characters++; return true; }
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {}
        protected void updateWidgetNarration(NarrationElementOutput output) {}
    }
    private static void near(float actual, float expected) { require(Math.abs(actual - expected) < 0.0001F, actual + " != " + expected); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
