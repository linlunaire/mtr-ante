package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.render.RawItemGeometry;
import cn.zbx1425.mtrsteamloco.render.RawItemRenderState;
import cn.zbx1425.sowcer.shader.BlazeRenderType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.cuboid.CuboidModel;
import net.minecraft.client.resources.model.cuboid.ItemTransforms;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Real vanilla item states and delayed triangle nodes; no Minecraft singleton, GPU or loader mixins. */
public final class ItemRenderStateCompatibilityCheck {
    private static final int COLOR = 0x80123456, LIGHT = 0x00B00050, OVERLAY = 0x00090004;
    private static final int FIXED_LIGHT = 0x00F000F0, FIXED_OVERLAY = 0x000A0003;

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(VanillaRegistries.createLookup()).forEach(pending -> pending.apply());
        final ItemTransforms transforms;
        try (var stream = ItemRenderStateCompatibilityCheck.class.getClassLoader().getResourceAsStream("assets/minecraft/models/item/generated.json")) {
            require(stream != null, "Missing vanilla generated item transforms");
            transforms = CuboidModel.fromStream(new InputStreamReader(stream, StandardCharsets.UTF_8)).transforms();
        }
        final var constructor = CuboidItemModelWrapper.class.getDeclaredConstructor(List.class, QuadCollection.class, ModelRenderProperties.class, Matrix4fc.class);
        constructor.setAccessible(true);
        final ItemModel base = constructor.newInstance(List.of(), QuadCollection.EMPTY,
                new ModelRenderProperties(false, null, transforms), new Matrix4f().translation(-0.5F, -0.5F, -0.5F));
        final RenderType opaque = BlazeRenderType.entityCutout(Identifier.parse("mtrsteamloco:test/item.png"));
        final RenderType translucent = BlazeRenderType.entityTranslucentCull(Identifier.parse("mtrsteamloco:test/item.png"));
        final var builder = new RawItemGeometry.Builder();
        triangle(builder, translucent, true, 1, null, null);
        triangle(builder, translucent, true, 9, FIXED_LIGHT, FIXED_OVERLAY);
        triangle(builder, opaque, false, 2, null, null);
        final RawItemGeometry geometry = builder.build();
        // Mutation after extraction must not change existing item states.
        triangle(builder, opaque, false, 1000, null, null);
        ((Vector3f) geometry.extents()[0]).set(999);
        require(geometry.extents().length == 9 && geometry.extents()[0].x() == 1, "Snapshot retains mutable builder/extents data");
        for (ItemDisplayContext context : ItemDisplayContext.values()) checkContext(base, transforms, geometry, opaque, translucent, context);
        checkAppendBoundary(base, geometry);
        checkHook(Path.of(args[0]));
        System.out.println("PASS: real vanilla base item, all display contexts/left hands, raw transform/extents, nine immutable triangle vertices, material light/overlay, normals, transparency sorting, delayed replay and composite-layer isolation (no GPU/mixin runtime)");
    }

    private static void checkContext(ItemModel base, ItemTransforms transforms, RawItemGeometry geometry, RenderType opaque, RenderType translucent, ItemDisplayContext context) {
        final Matrix4f itemTransform = new Matrix4f().rotateY((float) Math.toRadians(-30)).scale(0.7F, 0.5F, 0.9F).translate(0, 0.3F, 0);
        final Matrix4f expectedTransform = new Matrix4f(itemTransform);
        final ItemModelResolver resolver = new ItemModelResolver(null) {
            @Override public void appendItemLayers(ItemStackRenderState state, ItemStack stack, ItemDisplayContext display, Level level, ItemOwner owner, int seed) {
                RawItemRenderState.append(base, state, stack, this, display, null, owner, seed, geometry, itemTransform);
            }
        };
        final ItemStackRenderState state = new ItemStackRenderState();
        resolver.updateForTopItem(state, new ItemStack(Items.STICK), context, null, null, 19);
        require(state.activeLayerCount == 1, "Expected exactly one raw layer");
        require(!state.usesBlockLight(), "Generated base model's front lighting changed");
        require(state.isAnimated(), "Mutable scripted geometry must bypass GUI item caching");
        itemTransform.identity().translate(1000, 1000, 1000);

        final PoseStack expected = legacyDisplayPose(transforms, context);
        expected.translate(0, -0.5F, 0);
        expected.mulPose(expectedTransform);
        final List<Vector3fc> extents = new ArrayList<>();
        state.visitExtents(value -> extents.add(new Vector3f(value)));
        require(extents.size() == 9, "Raw item culling extents missing in " + context);
        final Vector3fc[] sourceExtents = geometry.extents();
        for (int i = 0; i < extents.size(); i++) {
            final Vector3f expectedPoint = expected.last().pose().transformPosition(new Vector3f(sourceExtents[i]));
            close(extents.get(i), expectedPoint, "Extents/left-hand transform " + context);
        }

        final PoseStack pose = new PoseStack();
        pose.translate(10, 20, 30);
        final SubmitNodeStorage storage = new SubmitNodeStorage();
        state.submit(pose, storage, LIGHT, OVERLAY, 0);
        require(pose.last().pose().equals(new Matrix4f().translation(10, 20, 30)), "Item submission leaked its pose");
        pose.setIdentity();
        state.clear();
        final Matrix4f renderedPose = new Matrix4f().translation(10, 20, 30).mul(expected.last().pose());
        final Map<RenderType, List<Captured>> output = new HashMap<>();
        storage.drainPhases(phase -> phase.sortInto((node, blending) -> {
            require(node instanceof CustomFeatureRenderer.Submit, "Raw item submitted vanilla quads instead of triangles");
            final var geometryNode = (CustomFeatureRenderer.Submit) node;
            require(!output.containsKey(geometryNode.renderType()), "Same material was not batched");
            final List<Captured> vertices = new ArrayList<>();
            output.put(geometryNode.renderType(), vertices);
            geometryNode.customGeometryRenderer().render(geometryNode.pose(), new Capture(vertices));
        }));
        require(output.size() == 2 && output.get(opaque).size() == 3 && output.get(translucent).size() == 6,
                "Mutated builder/state changed submitted geometry");
        checkTriangle(output.get(opaque), 0, 2, renderedPose, LIGHT, OVERLAY);
        final float nearDistance = renderedPose.transformPosition(new Vector3f(1 + 0.25F / 3, 0.5F / 3, 0)).lengthSquared();
        final float farDistance = renderedPose.transformPosition(new Vector3f(9 + 0.25F / 3, 0.5F / 3, 0)).lengthSquared();
        final boolean farFirst = farDistance >= nearDistance;
        checkTriangle(output.get(translucent), farFirst ? 0 : 3, 9, renderedPose, FIXED_LIGHT, FIXED_OVERLAY);
        checkTriangle(output.get(translucent), farFirst ? 3 : 0, 1, renderedPose, LIGHT, OVERLAY);
    }

    private static void checkAppendBoundary(ItemModel base, RawItemGeometry geometry) {
        final var state = new ItemStackRenderState();
        final var unrelated = state.newLayer();
        final ItemModel multiLayerBase = (s, stack, resolver, context, level, owner, seed) -> {
            base.update(s, stack, resolver, context, level, owner, seed);
            base.update(s, stack, resolver, context, level, owner, seed);
            s.layers[s.activeLayerCount - 1].prepareQuadList().add(null); // Marker in the discarded base layer.
        };
        RawItemRenderState.append(multiLayerBase, state, new ItemStack(Items.STICK), new ItemModelResolver(null),
                ItemDisplayContext.GUI, null, null, 0, geometry, new Matrix4f());
        require(state.activeLayerCount == 2 && state.layers[0] == unrelated, "Outer composite's existing layers were overwritten");
        require(state.newLayer().prepareQuadList().isEmpty(), "Discarded base-layer data leaked into the next composite child");
    }

    private static PoseStack legacyDisplayPose(ItemTransforms transforms, ItemDisplayContext context) {
        // Legacy ItemTransform.apply did not include the block-model centering translation.
        // Reconstruct from the resource's values instead of duplicating the new API's behavior.
        final var transform = transforms.getTransform(context);
        final float sign = context.leftHand() ? -1 : 1;
        final var result = new PoseStack();
        result.translate(sign * transform.translation().x(), transform.translation().y(), transform.translation().z());
        result.mulPose(new org.joml.Quaternionf().rotationXYZ((float) Math.toRadians(transform.rotation().x()),
                (float) Math.toRadians(sign * transform.rotation().y()), (float) Math.toRadians(sign * transform.rotation().z())));
        result.scale(transform.scale().x(), transform.scale().y(), transform.scale().z());
        return result;
    }

    private static void checkHook(Path root) throws Exception {
        final String hook = Files.readString(root.resolve("versions/26.2/common/src/main/java/cn/zbx1425/mtrsteamloco/mixin/ItemRendererMixin.java"));
        final var target = java.util.regex.Pattern.compile("target =\\s*\"([^\"]+)\"").matcher(hook);
        require(target.find(), "Missing item extraction redirect descriptor");
        final String redirectTarget = target.group(1);
        final ClassNode resolver = new ClassNode();
        try (var input = ItemRenderStateCompatibilityCheck.class.getClassLoader().getResourceAsStream("net/minecraft/client/renderer/item/ItemModelResolver.class")) {
            new ClassReader(input).accept(resolver, 0);
        }
        final var append = resolver.methods.stream().filter(method -> method.name.equals("appendItemLayers")).findFirst().orElseThrow();
        int matches = 0;
        for (var instruction : append.instructions) {
            if (instruction instanceof MethodInsnNode call && redirectTarget.equals("L" + call.owner + ";" + call.name + call.desc)) matches++;
        }
        require(matches == 1, "Redirect must match exactly one real ItemModel.update invocation");
        require(resolver.fields.stream().anyMatch(field -> field.name.equals("modelManager") && field.desc.equals("Lnet/minecraft/client/resources/model/ModelManager;")),
                "Model-manager shadow field no longer matches Minecraft");
        require(hook.contains("@Mixin(ItemModelResolver.class)") && hook.contains("EyeCandyItemRenderer.update(modelManager"), "Extraction dispatch became disconnected");
        final String properties = Files.readString(root.resolve("versions/26.2/common/src/main/java/cn/zbx1425/mtrsteamloco/data/EyeCandyProperties.java"));
        final String registry = Files.readString(root.resolve("versions/26.2/common/src/main/java/cn/zbx1425/mtrsteamloco/data/EyeCandyRegistry.java"));
        require(properties.contains("public Identifier itemModelId;") && !properties.contains("BakedModel"), "Properties must store a reload-stable identifier");
        require(!registry.contains("getModelManager()") && !registry.contains("mappingItem("), "Registry reverted to stale baked lookups or double PNG remapping");
        require(registry.split("EyeCandyItemResources.clientItemId\\(loc\\)", -1).length == 3, "Both PNG and ordinary model branches must use the prepared item definition id");
        final var mixins = com.google.gson.JsonParser.parseString(Files.readString(root.resolve("common/src/main/resources/mtrsteamloco.mixins.json"))).getAsJsonObject();
        require(mixins.getAsJsonArray("client").asList().stream().anyMatch(value -> value.getAsString().equals("ItemRendererMixin")), "The migrated hook is not registered");
    }

    private static void triangle(RawItemGeometry.Builder builder, RenderType type, boolean sorted, float x, Integer light, Integer overlay) {
        builder.triangle(type, sorted, vertex(x, 0), vertex(x + 0.25F, 0), vertex(x, 0.5F), COLOR, light, overlay);
    }

    private static RawItemGeometry.Vertex vertex(float x, float y) {
        return new RawItemGeometry.Vertex(x, y, 0, 0.70710677F, 0.70710677F, 0, 0.25F, 0.75F);
    }

    private static void checkTriangle(List<Captured> vertices, int offset, float x, Matrix4f pose, int light, int overlay) {
        for (int i = 0; i < 3; i++) {
            final Captured v = vertices.get(offset + i);
            close(v.position, pose.transformPosition(new Vector3f(x + (i == 1 ? 0.25F : 0), i == 2 ? 0.5F : 0, 0)), "Vertex transform/order");
            final Vector3f normal = pose.normal(new org.joml.Matrix3f()).transform(new Vector3f(0.70710677F, 0.70710677F, 0)).normalize();
            close(v.normal, normal, "Normal transform");
            require(v.color == COLOR && v.light == light && v.overlay == overlay && v.u == 0.25F && v.v == 0.75F,
                    "Color/UV/material lighting differs from legacy raw meshes");
        }
    }

    private static void close(Vector3fc actual, Vector3fc expected, String message) {
        require(actual.equals(expected, 0.00005F), message + ": " + actual + " != " + expected);
    }

    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class Captured {
        Vector3f position, normal;
        int color, light, overlay;
        float u, v;
    }

    private static final class Capture implements VertexConsumer {
        private final List<Captured> vertices;
        private Captured current;
        Capture(List<Captured> vertices) { this.vertices = vertices; }
        @Override public VertexConsumer addVertex(float x, float y, float z) { current = new Captured(); current.position = new Vector3f(x, y, z); vertices.add(current); return this; }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) { return setColor(a << 24 | r << 16 | g << 8 | b); }
        @Override public VertexConsumer setColor(int color) { current.color = color; return this; }
        @Override public VertexConsumer setUv(float u, float v) { current.u = u; current.v = v; return this; }
        @Override public VertexConsumer setUv1(int u, int v) { current.overlay = u | v << 16; return this; }
        @Override public VertexConsumer setUv2(int u, int v) { current.light = u | v << 16; return this; }
        @Override public VertexConsumer setNormal(float x, float y, float z) { current.normal = new Vector3f(x, y, z); return this; }
        @Override public VertexConsumer setLineWidth(float width) { return this; }
    }
}
