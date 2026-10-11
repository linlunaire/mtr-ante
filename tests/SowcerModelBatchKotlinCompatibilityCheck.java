package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.BatchManager;
import cn.zbx1425.sowcer.batch.EnqueueProp;
import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.batch.ShaderProp;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.model.Mesh;
import cn.zbx1425.sowcer.model.Model;
import cn.zbx1425.sowcer.model.VertArrays;
import cn.zbx1425.sowcer.object.IndexBuf;
import cn.zbx1425.sowcer.object.InstanceBuf;
import cn.zbx1425.sowcer.object.VertArray;
import cn.zbx1425.sowcer.object.VertBuf;
import cn.zbx1425.sowcer.shader.BlazeRenderType;
import cn.zbx1425.sowcer.util.DrawContext;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import cn.zbx1425.sowcer.vertex.VertAttrState;
import mtr.mappings.RenderBufferSource;
import mtr.mappings.RetainedGeometry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Real Kotlin model/batch objects, Java callers, heap upload ownership; no GPU required. */
public final class SowcerModelBatchKotlinCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            for (Class<?> type : new Class<?>[]{Mesh.class, Model.class, VertArrays.class, BatchManager.class,
                    BatchManager.RenderCall.class, EnqueueProp.class, MaterialProp.class, ShaderProp.class}) {
                require(java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()
                        .equals(java.nio.file.Path.of(args[0]).toRealPath()), "Unexpected implementation source: " + type);
            }
        }
        javaSurfaceAndMutableDefaults();
        materialFramingAndLegacy();
        materialCopyAndNulls();
        orderedCloseAndFailure();
        retainedBufferOwnershipAndTextures();
        queuedCallSnapshotsAndFailure();
        System.out.println("PASS: Kotlin model/batch layer via Java, static hiding/mutable defaults/IOException ABI, NMB material bytes and legacy defaults, copy/nullable contracts, ordered close failures, real retained-buffer ownership, texture copies and queued snapshot/retry/clear semantics (no GPU)");
    }

    private static void javaSurfaceAndMutableDefaults() throws Exception {
        for (var type : new Class<?>[]{Mesh.class, Model.class, VertArrays.class, BatchManager.class, EnqueueProp.class, MaterialProp.class, ShaderProp.class}) {
            require(!Modifier.isFinal(type.getModifiers()), "Former Java class became final: " + type);
        }
        require(Modifier.isFinal(BatchManager.RenderCall.class.getModifiers()), "RenderCall lost final contract");
        require(!Modifier.isFinal(VertArrays.class.getMethod("createAll", Model.class, VertAttrMapping.class, InstanceBuf.class).getModifiers()), "Static factory became final");
        require(StaticVertArrays.createAll(null, null, null) instanceof StaticVertArrays, "Static Java factory hiding failed");
        require(Arrays.equals(MaterialProp.class.getConstructor(DataInputStream.class).getExceptionTypes(), new Class<?>[]{IOException.class}), "Material constructor checked exception declaration changed");
        require(Arrays.equals(MaterialProp.class.getMethod("serializeTo", DataOutputStream.class).getExceptionTypes(), new Class<?>[]{IOException.class}), "Material writer checked exception declaration changed");
        require(Mesh.class.getMethod("close").getExceptionTypes().length == 0, "Mesh close acquired a checked exception");
        require(!Modifier.isFinal(Model.class.getField("meshList").getModifiers()) && Modifier.isFinal(VertArrays.class.getField("meshList").getModifiers()), "Model/list field finality changed");
        var oldEnqueue = EnqueueProp.DEFAULT;
        var oldShader = ShaderProp.DEFAULT;
        try {
            EnqueueProp.DEFAULT = null; ShaderProp.DEFAULT = null;
            require(EnqueueProp.DEFAULT == null && ShaderProp.DEFAULT == null, "Mutable default fields rejected null");
            EnqueueProp.DEFAULT = new EnqueueProp(null); ShaderProp.DEFAULT = new ShaderProp();
            require(EnqueueProp.DEFAULT.attrState == null && ShaderProp.DEFAULT.viewMatrix == null, "Default nullable state changed");
        } finally { EnqueueProp.DEFAULT = oldEnqueue; ShaderProp.DEFAULT = oldShader; }
    }

    private static void materialFramingAndLegacy() throws Exception {
        byte[] encoded = encode(new MaterialProp());
        String expected = "{\"version\":2,\"shaderName\":\"\",\"texture\":null,\"color\":null,\"lightmapUV\":null,\"translucent\":false,\"writeDepthBuf\":true,\"billboard\":false,\"cutoutHack\":false}";
        try (var input = new DataInputStream(new ByteArrayInputStream(encoded))) {
            int length = input.readInt();
            require(length == encoded.length - 4, "NMB length prefix changed");
            require(new String(input.readNBytes(length), StandardCharsets.UTF_8).equals(expected), "NMB JSON field order/default bytes changed");
        }
        var material = new MaterialProp("材质");
        material.texture = Identifier.parse("ante:textures/test.png");
        material.attrState.setColor(0x80123456).setLightmapUV(0x00100020);
        material.translucent = true; material.writeDepthBuf = false; material.cutoutHack = true;
        material.sheetElementsU = 3; material.sheetElementsV = 4;
        material.setMatixProcess(VertAttrState.BILLBOARD);
        byte[] rich = encode(material);
        var decoded = new MaterialProp(new DataInputStream(new ByteArrayInputStream(rich)));
        require(decoded.shaderName.equals("材质") && decoded.texture.equals(material.texture), "UTF-8 or Identifier material round trip changed");
        require(decoded.attrState.color.equals(material.attrState.color) && decoded.attrState.lightmapUV.equals(material.attrState.lightmapUV), "Packed material attribute round trip changed");
        require(decoded.translucent && !decoded.writeDepthBuf && decoded.cutoutHack && decoded.useMatixProcess(), "Material pipeline flags changed");
        require(decoded.attrState.matrixModel == VertAttrState.BILLBOARD, "Billboard did not recover the shared process");
        require(decoded.sheetElementsU == 0 && decoded.sheetElementsV == 0, "Previously omitted sheet fields entered NMB serialization");
        var legacy = decodeJson("{\"shaderName\":\"\",\"texture\":null,\"color\":null,\"lightmapUV\":null}");
        require(!legacy.translucent && !legacy.writeDepthBuf && !legacy.cutoutHack && !legacy.useMatixProcess(), "Missing legacy booleans changed defaults");
        try { new MaterialProp(new DataInputStream(new ByteArrayInputStream(new byte[0]))); throw new AssertionError("Truncated input stopped throwing IOException"); }
        catch (IOException expectedFailure) { }
        var failure = new IOException("output closed");
        try {
            material.serializeTo(new DataOutputStream(new java.io.OutputStream() {
                @Override public void write(int value) throws IOException { throw failure; }
            }));
            throw new AssertionError("Writer swallowed IOException");
        } catch (IOException actual) { require(actual == failure, "Writer wrapped the original IOException"); }
    }

    private static void materialCopyAndNulls() {
        require(new MaterialProp((String) null).shaderName.equals(""), "Shader constructor stopped normalizing null");
        var material = new MaterialProp("shader");
        material.attrState.setPosition(new Vector3f(1F, 2F, 3F));
        material.setMatixProcess(VertAttrState.BILLBOARD);
        material.sheetElementsU = 7; material.sheetElementsV = 9;
        material.cutoutHack = true; material.texture = Identifier.parse("ante:textures/a.png");
        var copy = material.copy();
        require(copy.equals(material) && copy.hashCode() == material.hashCode(), "Material copy value equality changed");
        require(copy.attrState != material.attrState && copy.attrState.position != material.attrState.position && copy.attrState.matrixModel == material.attrState.matrixModel, "Material copy ownership changed");
        material.attrState.position.add(10F, 0F, 0F);
        require(copy.attrState.position.x() == 1F && copy.sheetElementsU == 7 && copy.sheetElementsV == 9, "Material copy lost state");
        require(!new MaterialProp().equals(new MaterialSubclass()), "Material equality lost exact class check");
        material.shaderName = null; material.texture = null; material.attrState = null;
        require(material.toString().equals("{null: null}"), "Nullable public material string behavior changed");
        require(material.hashCode() == Objects.hash(null, null, null, false, true, true, 7, 9), "Nullable material hash contract changed");
        var shader = new ShaderProp();
        require(shader.setViewMatrix(null) == shader && shader.hashCode() == Objects.hash((Object) null), "Nullable shader chaining/hash changed");
        require(shader.equals(new ShaderProp()) && !shader.equals(new ShaderSubclass()), "Shader equality changed");
    }

    private static void orderedCloseAndFailure() {
        List<String> events = new ArrayList<>();
        var failure = new IllegalStateException("first close");
        var mesh = new Mesh(new VertBuf() {
            @Override public void close() { events.add("vertex"); throw failure; }
        }, new IndexBuf(0, 0x1405) {
            @Override public void close() { events.add("index"); }
        }, null);
        try { mesh.close(); throw new AssertionError("Mesh close swallowed failure"); }
        catch (IllegalStateException actual) { require(actual == failure, "Mesh close wrapped failure"); }
        require(events.equals(List.of("vertex")), "Mesh close changed first-failure ordering");
        events.clear();
        var model = new Model();
        model.meshList = new ArrayList<>();
        model.meshList.add(new Mesh(null, null, null) { @Override public void close() { events.add("first"); throw failure; } });
        model.meshList.add(new Mesh(null, null, null) { @Override public void close() { events.add("second"); } });
        try { model.close(); throw new AssertionError("Model close swallowed failure"); } catch (IllegalStateException actual) { require(actual == failure, "Model close wrapped failure"); }
        require(events.equals(List.of("first")), "Model close changed first-failure ordering");
        model.meshList = null;
        try { model.close(); throw new AssertionError("Null public mesh list stopped failing on use"); } catch (NullPointerException expected) { }
        events.clear();
        var arrays = new VertArrays();
        arrays.meshList.add(new VertArray() { @Override public void close() { events.add("first"); throw failure; } });
        arrays.meshList.add(new VertArray() { @Override public void close() { events.add("second"); } });
        try { arrays.close(); throw new AssertionError("Array close swallowed failure"); } catch (IllegalStateException actual) { require(actual == failure, "Array close wrapped failure"); }
        require(events.equals(List.of("first")), "Array close changed first-failure ordering");
    }

    private static void retainedBufferOwnershipAndTextures() {
        var vertex = new VertBuf(); var index = new IndexBuf(0, 0x1405);
        var material = new MaterialProp(); material.texture = Identifier.parse("ante:textures/train/a.png");
        var model = new Model(); model.meshList.add(new Mesh(vertex, index, material));
        var arrays = VertArrays.createAll(model, null, null);
        var copy = arrays.copyForMaterialChanges();
        require(copy.meshList.getFirst() != arrays.meshList.getFirst(), "Material copy reused the vertex array");
        arrays.replaceTexture(null, Identifier.parse("ante:unmatched.png"));
        require(material.texture.getPath().equals("textures/train/a.png"), "Null texture matcher replaced a texture");
        arrays.replaceTexture("a.png", Identifier.parse("ante:textures/b.png"));
        require(material.texture.getPath().equals("textures/b.png") && copy.meshList.getFirst().materialProp.texture.getPath().equals("textures/train/a.png"), "Filename replacement or material-copy independence changed");
        arrays.replaceAllTexture(null); require(material.texture == null, "Null texture replacement rejected");
        arrays.setMatixProcess(VertAttrState.BILLBOARD); require(material.useMatixProcess(), "Matrix process was not propagated");
        arrays.setMatixProcess(null); require(!material.useMatixProcess(), "Null process was not propagated");
        model.close();
        require(vertex.id != 0 && index.id != 0, "Closing model invalidated retained arrays");
        arrays.close(); arrays.close();
        require(vertex.id != 0 && index.id != 0, "Closing one array invalidated a material copy");
        copy.close(); copy.close();
        require(vertex.id == 0 && index.id == 0, "Final retained owner did not release buffers");
        require(VertArrays.createAll(new Model(), null, null).meshList.isEmpty(), "Empty model began requiring an unused mapping");
    }

    private static void queuedCallSnapshotsAndFailure() {
        var manager = new BatchManager();
        manager.enqueue(new VertArrays(), null, null);
        manager.drawAll(null, new DrawContext());
        var fake = new VertArray() {
            @Override public RetainedDraw captureRetained(VertAttrState enqueue, ShaderProp shader) { return null; }
            @Override public List<Face> capture(VertAttrState enqueue, ShaderProp shader) { return List.of(); }
        };
        fake.materialProp = new MaterialProp();
        manager.enqueue(fake, new EnqueueProp(null), null);
        var failure = new IllegalStateException("profiler failed");
        int[] visits = {0};
        var failing = new DrawContext() {
            @Override public void recordDrawCall(BatchManager.RenderCall call) {
                require(call.vertArray == fake && call.faceCount == 0 && !call.instanced, "RenderCall public snapshot changed");
                visits[0]++; throw failure;
            }
        };
        for (int attempt = 0; attempt < 2; attempt++) {
            try { manager.drawAll(null, failing); throw new AssertionError("Draw callback failure swallowed"); }
            catch (IllegalStateException actual) { require(actual == failure, "Draw callback failure wrapped"); }
        }
        require(visits[0] == 2, "Failed draw cleared pending calls");
        manager.clear(); manager.drawAll(null, new DrawContext());

        var type = BlazeRenderType.entityCutout(MaterialProp.WHITE_TEXTURE_LOCATION);
        var geometry = new RetainedGeometry(type, 6, consumer -> { });
        var retained = new VertArray() {
            @Override public RetainedDraw captureRetained(VertAttrState enqueue, ShaderProp shader) { return new RetainedDraw(geometry, new org.joml.Matrix4f()); }
            @Override public List<Face> capture(VertAttrState enqueue, ShaderProp shader) { throw new AssertionError("Retained geometry fell back to CPU capture"); }
        };
        retained.materialProp = new MaterialProp();
        manager.enqueue(retained, new EnqueueProp(null), null);
        var context = new DrawContext();
        try (var source = RenderBufferSource.begin(Vec3.ZERO)) {
            manager.drawAll(null, context);
            manager.drawAll(null, context);
        }
        context.resetFrameProfiler();
        require(context.drawCallCount == 1 && context.singleFaceCount == 2 && context.batchCount == 1, "Retained count or successful queue drain changed");
    }

    private static byte[] encode(MaterialProp material) throws IOException {
        var bytes = new ByteArrayOutputStream();
        material.serializeTo(new DataOutputStream(bytes));
        return bytes.toByteArray();
    }
    private static MaterialProp decodeJson(String content) throws IOException {
        var bytes = new ByteArrayOutputStream();
        var output = new DataOutputStream(bytes); byte[] utf8 = content.getBytes(StandardCharsets.UTF_8);
        output.writeInt(utf8.length); output.write(utf8);
        return new MaterialProp(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
    private static final class StaticVertArrays extends VertArrays {
        public static VertArrays createAll(Model model, VertAttrMapping mapping, InstanceBuf instances) { return new StaticVertArrays(); }
    }
    private static final class MaterialSubclass extends MaterialProp { }
    private static final class ShaderSubclass extends ShaderProp { }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
