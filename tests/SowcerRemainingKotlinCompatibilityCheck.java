package cn.zbx1425.sowcer.object;

import cn.zbx1425.sowcer.ContextCapability;
import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.model.Mesh;
import cn.zbx1425.sowcer.shader.BlazeRenderType;
import cn.zbx1425.sowcer.shader.PatchingResourceProvider;
import cn.zbx1425.sowcer.shader.ShaderManager;
import cn.zbx1425.sowcer.util.AttrUtil;
import cn.zbx1425.sowcer.util.DrawContext;
import cn.zbx1425.sowcer.util.GlStateTracker;
import cn.zbx1425.sowcer.util.OffHeapAllocator;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import cn.zbx1425.sowcer.vertex.VertAttrSrc;
import cn.zbx1425.sowcer.vertex.VertAttrType;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mtr.mappings.RetainedGeometry;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceProvider;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/** Executes unchanged against the original Java JAR and migrated Kotlin classes; no GPU/window. */
public final class SowcerRemainingKotlinCompatibilityCheck {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            for (Class<?> type : new Class<?>[]{ContextCapability.class, VertBuf.class, IndexBuf.class, InstanceBuf.class,
                    VertArray.class, VertArray.RetainedDraw.class, VertArray.Face.class, VertArray.Value.class,
                    BlazeRenderType.class, ShaderManager.class, PatchingResourceProvider.class, AttrUtil.class,
                    DrawContext.class, GlStateTracker.class, OffHeapAllocator.class}) {
                require(java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()
                        .equals(java.nio.file.Path.of(args[0]).toRealPath()), "Unexpected implementation source: " + type);
            }
        }
        javaSurface();
        recordsMatchJava();
        bufferOwnershipAndDispatch();
        attributesAndProfiler();
        preparationScopes();
        shaderPatchingAndStreams();
        pipelinesAndReloads();
        offHeapOwnership();
        System.out.println("PASS: SOWCER remaining Kotlin/Java compatibility " + assertions + " assertions: JVM modifiers, record semantics, upload snapshots/refcounts/virtual dispatch, packed attributes/profiler, nested scopes, shader patch streams, pipeline cache/reload and native allocation ownership (no GPU/window)");
    }

    private static void javaSurface() throws Exception {
        for (var type : new Class<?>[]{ContextCapability.class, VertBuf.class, IndexBuf.class, InstanceBuf.class,
                VertArray.class, BlazeRenderType.class, ShaderManager.class, PatchingResourceProvider.class,
                AttrUtil.class, DrawContext.class, OffHeapAllocator.class}) {
            require(!Modifier.isFinal(type.getModifiers()), "Java class became final: " + type);
        }
        require(Modifier.isFinal(GlStateTracker.class.getModifiers()), "State tracker lost final modifier");
        require(Modifier.isVolatile(VertBuf.class.getField("id").getModifiers()), "Buffer ID is no longer volatile");
        require(Modifier.isVolatile(GlStateTracker.class.getField("isStateProtected").getModifiers()), "State flag is no longer volatile");
        for (var method : VertBuf.class.getDeclaredMethods()) {
            if (List.of("snapshot", "snapshotUpload", "retain", "release", "close").contains(method.getName()) ||
                    method.getName().equals("upload") && method.getParameterCount() == 3) {
                require(Modifier.isSynchronized(method.getModifiers()), "Lost monitor contract: " + method);
                require(!Modifier.isFinal(method.getModifiers()), "Lost buffer virtual dispatch: " + method);
            }
        }
        require(!Modifier.isSynchronized(VertBuf.class.getMethod("upload", ByteBuffer.class, int.class).getModifiers()), "Convenience upload acquired a monitor");
        require(Arrays.equals(ShaderManager.class.getMethod("reloadShaders", ResourceManager.class).getExceptionTypes(), new Class<?>[]{IOException.class}), "Shader IOException declaration changed");
        require(StaticAttr.argbToBgr(1) == 17 && StaticShader.patchVertexShaderSource(null).equals("hidden"), "Java static method hiding was restricted");
        ContextCapability.backendDescription = null;
        ContextCapability.contextVersion = 999;
        ContextCapability.supportVertexAttribDivisor = false;
        ContextCapability.checkContextVersion();
        require(ContextCapability.contextVersion == 0 && ContextCapability.supportVertexAttribDivisor, "Capability reset changed");
        require(ContextCapability.backendDescription.equals("Blaze3D (not initialized)") && !ContextCapability.isGL4ES, "No-device backend detection changed");
        var emptyProvider = new PatchingResourceProvider(null);
        expect(NullPointerException.class, () -> emptyProvider.getResource(Identifier.parse("ante:missing.vsh")));
    }

    private static void recordsMatchJava() throws Exception {
        for (var type : new Class<?>[]{VertArray.RetainedDraw.class, VertArray.Face.class, VertArray.Value.class}) {
            require(type.isRecord(), "Lost java.lang.Record contract: " + type);
            for (var component : type.getRecordComponents()) {
                require(!Modifier.isFinal(component.getAccessor().getModifiers()), "Record accessor finality changed: " + component);
            }
            require(Modifier.isFinal(type.getMethod("equals", Object.class).getModifiers()) &&
                    Modifier.isFinal(type.getMethod("hashCode").getModifiers()) &&
                    Modifier.isFinal(type.getMethod("toString").getModifiers()), "Generated record method modifiers changed");
        }
        require(Arrays.stream(VertArray.Value.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList()
                .equals(List.of("x", "y", "z", "nx", "ny", "nz", "u", "v", "color", "light", "overlay")), "Record component order changed");
        for (float x : new float[]{0f, -0f, 1.25f, Float.NaN, Float.intBitsToFloat(0x7FA12345), Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            var actual = value(x);
            var expected = new Value(x, -0f, 3f, 0f, 1f, 0f, .25f, .75f, 0x81234567, 0x00F00020, 0x00010002);
            require(actual.hashCode() == expected.hashCode(), "Java record float hash changed: " + x);
            require(actual.toString().equals(expected.toString()), "Java record text changed: " + actual);
            require(actual.equals(value(x)) && !actual.equals(expected) && !actual.equals(null), "Record equality changed");
        }
        require(!value(0f).equals(value(-0f)), "Signed zero collapsed in record equality");
        require(value(Float.NaN).equals(value(Float.intBitsToFloat(0x7FA12345))), "NaN record equivalence changed");
        var actualFace = new VertArray.Face(value(1), value(2), value(3));
        var expectedFace = new Face(actualFace.a(), actualFace.b(), actualFace.c());
        require(actualFace.hashCode() == expectedFace.hashCode() && actualFace.toString().equals(expectedFace.toString()), "Face record value semantics changed");
        require(actualFace.distanceSquared() == 117f, "Triangle centroid distance arithmetic changed");
        var events = new ArrayList<String>();
        actualFace.emit(consumer(events));
        require(events.equals(List.of("position:1.0", "color", "uv", "light", "overlay", "normal",
                "position:2.0", "color", "uv", "light", "overlay", "normal",
                "position:3.0", "color", "uv", "light", "overlay", "normal")), "Vertex consumer call order changed: " + events);
        var nullableFace = new VertArray.Face(null, null, null);
        require(nullableFace.hashCode() == 0 && nullableFace.toString().equals("Face[a=null, b=null, c=null]"), "Nullable record constructor changed");
        expect(NullPointerException.class, nullableFace::distanceSquared);
        var pose = new org.joml.Matrix4f().translation(1, 2, 3);
        var actualDraw = new VertArray.RetainedDraw(null, pose);
        var expectedDraw = new RetainedDraw(null, pose);
        require(actualDraw.hashCode() == expectedDraw.hashCode() && actualDraw.toString().equals(expectedDraw.toString()), "Retained draw record semantics changed");
        require(new VertArray.RetainedDraw(null, null).equals(new VertArray.RetainedDraw(null, null)), "Nullable retained record changed");
    }

    private static VertArray.Value value(float x) { return new VertArray.Value(x, -0f, 3f, 0f, 1f, 0f, .25f, .75f, 0x81234567, 0x00F00020, 0x00010002); }

    private static void bufferOwnershipAndDispatch() throws Exception {
        var buffer = new TrackedBuffer();
        var source = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        source.putInt(0, 0x12345678).position(7).limit(9);
        buffer.upload(source, 8, VertBuf.USAGE_DYNAMIC_DRAW);
        require(source.position() == 7 && source.limit() == 9, "Upload mutated source cursor");
        var first = buffer.snapshot();
        require(first.capacity() == 8 && first.order() == ByteOrder.LITTLE_ENDIAN && first.getInt(0) == 0x12345678, "Upload snapshot layout changed");
        require(first.isReadOnly(), "Upload snapshot became mutable");
        expect(ReadOnlyBufferException.class, () -> first.put(0, (byte) 0));
        source.putInt(0, 0);
        require(first.getInt(0) == 0x12345678, "Snapshot aliases caller input");
        long revision = buffer.snapshotUpload().revision();
        buffer.upload(source, VertBuf.USAGE_STREAM_DRAW);
        require(buffer.snapshot().capacity() == 16 && buffer.snapshotUpload().revision() == revision + 1, "Convenience upload stopped using full capacity or revision changed");
        expect(IllegalArgumentException.class, () -> buffer.upload(null, -1, 0));
        expect(IllegalArgumentException.class, () -> buffer.upload(source, 17, 0));
        expect(IllegalArgumentException.class, () -> buffer.upload(source, 0, 0));
        buffer.retain();
        buffer.close();
        require(buffer.id != 0 && buffer.snapshot().capacity() == 16 && buffer.retains == 1 && buffer.releases == 1, "Owner close invalidated retained data or bypassed virtual release");
        expect(IllegalStateException.class, () -> buffer.upload(null, -1, 0));
        buffer.close();
        require(buffer.releases == 1, "Close is not idempotent");
        buffer.release();
        require(buffer.id == 0, "Last release retained buffer ID");
        expect(IllegalStateException.class, buffer::snapshot);
        expect(IllegalStateException.class, buffer::retain);
        expect(IllegalStateException.class, buffer::release);

        var vertices = new TrackedBuffer(); vertices.upload(ByteBuffer.allocate(0), VertBuf.USAGE_STATIC_DRAW);
        var indices = new IndexBuf(1, 0x1401); indices.upload(ByteBuffer.wrap(new byte[]{0, 0, 0}), VertBuf.USAGE_STATIC_DRAW);
        var builder = new VertAttrMapping.Builder();
        for (var type : VertAttrType.values()) builder.set(type, VertAttrSrc.GLOBAL);
        var material = new MaterialProp();
        var array = new VertArray(); array.create(new Mesh(vertices, indices, material), builder.build(), null);
        require(vertices.retains == 1, "Kotlin array bypassed same-package Java retain override");
        var retained = array.captureRetained(null, ShaderProp.DEFAULT);
        require(retained != null && vertices.uploadSnapshots == 1, "Retained capture bypassed Java snapshotUpload override");
        require(array.captureRetained(null, ShaderProp.DEFAULT).geometry() == retained.geometry(), "Unchanged upload invalidated retained geometry cache");
        indices.setFaceCount(0);
        require(array.capture(null, null).isEmpty(), "Empty capture started dereferencing shader");
        array.close(); vertices.close(); indices.close();
        require(vertices.releases == 2 && vertices.id == 0 && indices.id == 0, "Array buffer ownership changed");
        expect(IllegalStateException.class, () -> array.capture(null, null));
        var instances = new InstanceBuf(3);
        require(instances.size == 3, "Instance constructor field changed");
        instances.close();
    }

    private static void attributesAndProfiler() {
        require(AttrUtil.exchangeLightmapUVBits(0x1234FEDC) == 0xFEDC1234, "Packed UV sign extension changed");
        require(AttrUtil.argbToBgr(0x12345678) == 0xFF785634 && AttrUtil.rgbaToArgb(0x12345678) == 0x78123456, "Packed color order changed");
        var source = new org.joml.Matrix4f().translation(4, 5, 6).rotateXYZ(.3f, .4f, .5f).scale(2, 3, 4);
        var rotation = new Matrix4f(new org.joml.Matrix4f(source)); AttrUtil.zeroRotation(rotation);
        require(rotation.asMoj().equals(new org.joml.Matrix4f().translation(4, 5, 6), 0f), "Zero rotation lost translation");
        var translation = new Matrix4f(new org.joml.Matrix4f(source)); AttrUtil.zeroTranslation(translation);
        var expected = new org.joml.Matrix4f(source).m30(0).m31(0).m32(0).m33(1);
        require(translation.asMoj().equals(expected, 0f), "Zero translation changed linear transform");
        var context = new DrawContext();
        context.debugInfo.add(null);
        context.recordBatches(7); context.recordBatches(-2); context.recordBlazeAction(12);
        expect(NullPointerException.class, () -> context.recordDrawCall(null));
        context.resetFrameProfiler();
        require(context.batchCount == 5 && context.blazeFaceCount == 12 && context.drawCallCount == 1, "Profiler update/failure ordering changed");
        context.resetFrameProfiler();
        require(context.batchCount == 0 && context.blazeFaceCount == 0 && context.drawCallCount == 0 && context.debugInfo.size() == 1, "Profiler reset/debugInfo semantics changed");
        context.debugInfo = null; context.resetFrameProfiler();
        require(context.debugInfo == null, "Public mutable debug list lost null contract");
    }

    private static void preparationScopes() throws Exception {
        expect(IllegalStateException.class, GlStateTracker::assertProtected);
        expect(IllegalStateException.class, GlStateTracker::restore);
        GlStateTracker.capture(); GlStateTracker.capture(); GlStateTracker.assertProtected();
        require(GlStateTracker.isStateProtected, "Nested capture lost legacy state flag");
        GlStateTracker.restore(); GlStateTracker.assertProtected();
        require(GlStateTracker.isStateProtected, "Nested restore ended outer scope");
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try {
                expect(IllegalStateException.class, GlStateTracker::assertProtected);
                GlStateTracker.capture(); GlStateTracker.assertProtected(); GlStateTracker.restore();
            } catch (Throwable thrown) { failure.set(thrown); }
        });
        thread.start(); thread.join();
        if (failure.get() != null) throw new AssertionError("Cross-thread scope test", failure.get());
        GlStateTracker.assertProtected();
        GlStateTracker.restore();
        require(!GlStateTracker.isStateProtected, "Final restore left legacy flag set");
    }

    private static void shaderPatchingAndStreams() throws Exception {
        String shader = "uniform mat4 ModelViewMat;\nivec2 uv;\nvoid main() { Position = PositionExtra; Normal = NormalExtra; ModelViewMat; }";
        ContextCapability.isGL4ES = false;
        String patched = PatchingResourceProvider.patchVertexShaderSource(shader);
        require(patched.contains("in mat4 ModelMat;") && patched.contains("ivec2 uv;") && patched.contains("PositionExtra") && patched.contains("NormalExtra"), "Shader patch declarations/word boundaries changed");
        require(patched.contains("(ModelViewMat * ModelMat * vec4(Position, 1.0)).xyz") && patched.contains("normalize(mat3(ModelViewMat * ModelMat) * Normal)") && patched.endsWith("mat4(1.0); }"), "Shader model/view replacement changed");
        ContextCapability.isGL4ES = true;
        require(PatchingResourceProvider.patchVertexShaderSource(shader).contains("vec2 uv;"), "GL4ES declaration patch changed");
        ContextCapability.isGL4ES = false;
        expect(ArrayIndexOutOfBoundsException.class, () -> PatchingResourceProvider.patchVertexShaderSource("no entry"));
        expect(ArrayIndexOutOfBoundsException.class, () -> PatchingResourceProvider.patchVertexShaderSource("void main"));
        require(PatchingResourceProvider.patchVertexShaderSource("head void main first void main second").equals("head void main first "), "Java split/truncation semantics changed");

        var source = new TrackingStream(shader, false);
        var requests = new ArrayList<Identifier>();
        Resource original = new Resource(null, () -> source);
        var provider = new PatchingResourceProvider(id -> { requests.add(id); return Optional.of(original); });
        var transformed = provider.getResource(Identifier.parse("ante:test_modelmat_modelmat.vsh")).orElseThrow();
        require(requests.equals(List.of(Identifier.parse("ante:test.vsh"))) && source.closes == 1, "Patch resource path/close changed");
        require(transformed.source() == null, "Resource pack identity changed");
        var first = transformed.open();
        require(new String(first.readAllBytes(), StandardCharsets.UTF_8).equals(patched), "Patched resource content changed");
        require(first == transformed.open() && transformed.open().read() == -1, "Existing single-stream resource supplier changed");
        var passStream = new TrackingStream("passthrough", false);
        var passResource = new Resource(null, () -> passStream);
        require(new PatchingResourceProvider(id -> Optional.of(passResource)).getResource(Identifier.parse("ante:test.fsh")).orElseThrow() == passResource && passStream.closes == 0, "Passthrough ownership changed");
        var failClose = new TrackingStream(shader, true);
        require(new PatchingResourceProvider(id -> Optional.of(new Resource(null, () -> failClose)))
                .getResource(Identifier.parse("ante:test.vsh")).isEmpty() && failClose.closes == 1, "IOException close suppression changed");
        require(new PatchingResourceProvider(ResourceProvider.EMPTY).getResource(Identifier.parse("ante:test.vsh")).isEmpty(), "Missing resource changed");
        var jsonStream = new TrackingStream("{\"vertex\":\"sample\",\"attributes\":[\"Position\"]}", false);
        var json = new PatchingResourceProvider(id -> Optional.of(new Resource(null, () -> jsonStream)))
                .getResource(Identifier.parse("ante:shader.json")).orElseThrow();
        require(new String(json.open().readAllBytes(), StandardCharsets.UTF_8).equals("{\"vertex\":\"sample_modelmat\",\"attributes\":[\"Position\",\"Dummy0\",\"Dummy1\",\"Dummy2\",\"Dummy3\",\"Dummy4\",\"ModelMat\"]}") && jsonStream.closes == 1, "Legacy JSON padding/framing changed");
    }

    private static void pipelinesAndReloads() throws Exception {
        var texture = Identifier.parse("ante:textures/test.png");
        for (String shader : new String[]{"rendertype_entity_cutout", "rendertype_entity_translucent_cull", "rendertype_beacon_beam"}) {
            for (boolean blending : new boolean[]{false, true}) for (boolean depth : new boolean[]{false, true}) {
                var type = BlazeRenderType.material(texture, shader, blending, depth);
                require(type == BlazeRenderType.material(texture, shader, blending, depth), "Material cache identity changed");
                require(type.primitiveTopology() == PrimitiveTopology.TRIANGLES && type.outputTarget() == OutputTarget.MAIN_TARGET && type.sortOnUpload() == blending, "Material topology/target/sorting changed");
                require(type.pipeline().getDepthStencilState().writeDepth() == depth, "Material depth flag changed");
            }
        }
        require(BlazeRenderType.entityCutout(texture) == BlazeRenderType.entityCutout(texture) &&
                BlazeRenderType.entityTranslucentCull(texture) == BlazeRenderType.entityTranslucentCull(texture) &&
                BlazeRenderType.beaconBeam(texture, true) == BlazeRenderType.beaconBeam(texture, true), "Memoized factory identity changed");
        // The BiFunction memoizer stores a non-null Pair even when the texture is null.
        // Beam RenderSetup does not dereference the texture until the later render preparation.
        require(BlazeRenderType.beaconBeam(null, false) == BlazeRenderType.beaconBeam(null, false), "Nullable beam factory cache contract changed");
        require(BlazeRenderType.material(null, "rendertype_beacon_beam", false, true) ==
                BlazeRenderType.material(null, "rendertype_beacon_beam", false, true), "Nullable beam material cache contract changed");
        expect(NullPointerException.class, () -> BlazeRenderType.entityCutout(null));
        expect(NullPointerException.class, () -> BlazeRenderType.entityTranslucentCull(null));
        expect(NullPointerException.class, () -> BlazeRenderType.material(texture, null, true, true));
        var manager = new ShaderManager();
        require(manager.isReady(), "Required shader set is empty");
        var requested = new ArrayList<Identifier>();
        ResourceManager resources = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> {
            if (method.getName().equals("getResourceOrThrow")) { requested.add((Identifier) args[0]); return new Resource(null, () -> new ByteArrayInputStream(new byte[0])); }
            throw new AssertionError("Unexpected resource operation: " + method);
        });
        manager.reloadShaders(resources);
        require(!requested.isEmpty() && requested.size() == requested.stream().distinct().count(), "Shader reload duplicates or skips source paths");
        var expected = new java.util.LinkedHashSet<Identifier>();
        for (var type : List.of(BlazeRenderType.entityCutout(MaterialProp.WHITE_TEXTURE_LOCATION), BlazeRenderType.entityTranslucentCull(MaterialProp.WHITE_TEXTURE_LOCATION),
                BlazeRenderType.beaconBeam(MaterialProp.WHITE_TEXTURE_LOCATION, false), BlazeRenderType.beaconBeam(MaterialProp.WHITE_TEXTURE_LOCATION, true))) {
            expected.add(type.pipeline().getVertexShader().withPrefix("shaders/").withSuffix(".vsh"));
            expected.add(type.pipeline().getFragmentShader().withPrefix("shaders/").withSuffix(".fsh"));
        }
        require(requested.equals(new ArrayList<>(expected)), "Shader reload order changed");
        var failure = new java.io.FileNotFoundException("reload sentinel");
        ResourceManager broken = (ResourceManager) Proxy.newProxyInstance(ResourceManager.class.getClassLoader(), new Class<?>[]{ResourceManager.class}, (proxy, method, args) -> { throw failure; });
        try { manager.reloadShaders(broken); throw new AssertionError("Missing shader no longer throws"); } catch (IOException caught) { require(caught == failure, "Shader reload exception identity changed"); }
        var material = new MaterialProp(); require(manager.material(material) == material.getBlazeRenderType(), "Shader manager material delegation changed");
    }

    private static void offHeapOwnership() {
        ByteBuffer memory = OffHeapAllocator.allocate(16);
        try {
            require(memory.isDirect() && memory.capacity() == 16 && memory.position() == 0, "Native allocation shape changed");
            for (int i = 0; i < 16; i++) memory.put(i, (byte) (i + 7));
            memory.position(9);
            memory = OffHeapAllocator.resize(memory, 32);
            require(memory.isDirect() && memory.capacity() == 32 && memory.position() == 0, "Native resize shape changed");
            for (int i = 0; i < 16; i++) require(memory.get(i) == (byte) (i + 7), "Resize used cursor instead of base address");
            memory.position(11);
        } finally { OffHeapAllocator.free(memory); }
    }

    private static VertexConsumer consumer(List<String> events) {
        return new VertexConsumer() {
            public VertexConsumer addVertex(float x, float y, float z) { events.add("position:" + x); return this; }
            public VertexConsumer setColor(int r, int g, int b, int a) { events.add("color"); return this; }
            public VertexConsumer setColor(int color) { events.add("color"); return this; }
            public VertexConsumer setUv(float u, float v) { events.add("uv"); return this; }
            public VertexConsumer setUv1(int u, int v) { events.add("overlay"); return this; }
            public VertexConsumer setUv2(int u, int v) { events.add("light"); return this; }
            public VertexConsumer setNormal(float x, float y, float z) { events.add("normal"); return this; }
            public VertexConsumer setLineWidth(float width) { return this; }
        };
    }

    // Same-package Java overrides prove the original virtual calls still occur through Kotlin.
    private static final class TrackedBuffer extends VertBuf {
        int retains, releases, uploadSnapshots;
        @Override protected synchronized void retain() { retains++; super.retain(); }
        @Override protected synchronized void release() { releases++; super.release(); }
        @Override protected synchronized Upload snapshotUpload() { uploadSnapshots++; return super.snapshotUpload(); }
    }
    private static final class StaticAttr extends AttrUtil { public static int argbToBgr(int color) { return 17; } }
    private static final class StaticShader extends PatchingResourceProvider {
        StaticShader() { super(null); }
        public static String patchVertexShaderSource(String input) { return "hidden"; }
    }
    private static final class TrackingStream extends ByteArrayInputStream {
        int closes;
        final boolean failClose;
        TrackingStream(String value, boolean failClose) { super(value.getBytes(StandardCharsets.UTF_8)); this.failClose = failClose; }
        @Override public void close() throws IOException { closes++; if (failClose) throw new IOException("close sentinel"); super.close(); }
    }
    private record Value(float x, float y, float z, float nx, float ny, float nz, float u, float v, int color, int light, int overlay) { }
    private record Face(VertArray.Value a, VertArray.Value b, VertArray.Value c) { }
    private record RetainedDraw(RetainedGeometry geometry, org.joml.Matrix4f pose) { }
    private static void expect(Class<? extends Throwable> type, Runnable action) {
        try { action.run(); throw new AssertionError("Expected " + type.getName()); }
        catch (Throwable thrown) { if (!type.isInstance(thrown)) throw new AssertionError("Expected " + type.getName() + ", got " + thrown, thrown); assertions++; }
    }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
