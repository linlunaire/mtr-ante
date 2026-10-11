package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.math.*;
import cn.zbx1425.sowcer.model.*;
import cn.zbx1425.sowcer.util.DrawContext;
import cn.zbx1425.sowcer.vertex.*;
import cn.zbx1425.sowcerext.model.*;
import cn.zbx1425.sowcerext.model.integration.*;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.rendertype.RenderType;
import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Actual aggregate, raw geometry, material operations and deferred ownership, without a GPU. */
public final class RawModelCompatibilityCheck {
    private static int assertions;
    private static final List<String> EVENTS = new ArrayList<>();
    private static final RuntimeException FAILURE = new RuntimeException("raw model upload");
    private static Supplier<Model> activeSupplier;
    private static Model lastResult;
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath(); boolean kotlin = Files.isDirectory(source);
        require(Path.of(RawModel.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong aggregate source");
        require(Arrays.stream(RawModel.class.getDeclaredAnnotations()).anyMatch(a -> a.annotationType().getName().equals("kotlin.Metadata")) == kotlin, "Wrong aggregate language");
        List<String> records = new ArrayList<>();
        geometry(records); materials(records); uploads(records); draw(records); nullable();
        String actual = String.join("\n", records) + "\n";
        if (Arrays.asList(args).contains("--record")) { require(!kotlin, "Record only Java"); Files.writeString(Path.of(args[0]), actual); }
        else {
            List<String> expected = Files.readAllLines(Path.of(args[0])); require(expected.size() == records.size(), "Aggregate record count changed");
            for (int i = 0; i < records.size(); i++) require(expected.get(i).equals(records.get(i)), "Aggregate differs at " + i + "\nJava: " + expected.get(i) + "\nActual: " + records.get(i));
        }
        System.out.println("PASS: raw model, " + assertions + " assertions / " + records.size() + " Java records; geometry, material/wire/copy semantics, synchronized deferred upload, retry and draw dispatch (no GPU)");
    }
    private static RawMesh mesh(int index) {
        MaterialProp material = new MaterialProp(); material.texture = Identifier.parse("ante:part/" + index + ".png");
        RawMesh mesh = new RawMesh(material);
        for (int i = 0; i < 4; i++) { Vertex vertex = new Vertex(new Vector3f(i & 1, i >> 1, index), new Vector3f(0f, 0f, 1f)); vertex.u = i / 4f; vertex.v = 0.5f; vertex.color = index + i; vertex.light = 123; mesh.vertices.add(vertex); }
        mesh.faces.add(new Face(new int[] {0, 1, 3, 2})); return mesh;
    }
    private static RawModel model() { RawModel result = new RawModel(); result.meshList = new LinkedHashMap<>(); return result; }
    private static void geometry(List<String> records) throws Exception {
        for (int seed = 0; seed < 32; seed++) {
            RawModel model = model(); RawMesh source = mesh(seed); model.append(source);
            RawMesh appended = model.meshList.values().iterator().next();
            require(appended != source && appended.materialProp == source.materialProp && appended.vertices.getFirst() == source.vertices.getFirst() && appended.faces.getFirst() != source.faces.getFirst(), "Append ownership changed");
            model.append(source); require(model.getVertexCount() == 8 && model.getFaceCount() == 2, "Same-material append changed");
            model.append(List.of(mesh(seed + 100))); model.sourceLocation = Identifier.parse("ante:source");
            RawModel deep = model.copy(), shallow = model.copyForMaterialChanges();
            RawMesh deepMesh = deep.meshList.get(appended.materialProp), shallowMesh = shallow.meshList.get(appended.materialProp);
            require(deep.sourceLocation == model.sourceLocation && shallow.sourceLocation == model.sourceLocation, "Source alias changed");
            require(deepMesh.vertices != appended.vertices && deepMesh.vertices.getFirst() != appended.vertices.getFirst() && deepMesh.faces.getFirst() != appended.faces.getFirst(), "Deep copy shares geometry");
            require(shallowMesh.vertices == appended.vertices && shallowMesh.faces == appended.faces && shallowMesh.materialProp != appended.materialProp, "Material copy geometry alias changed");
            model.applyTranslation(seed * 0.125f, -0.5f, 1f);
            model.applyRotation(new Vector3f(0f, 1f, 0f), seed * 7f);
            model.applyScale(seed % 2 == 0 ? -2f : 2f, 0.5f, 3f);
            model.applyMirror(true, false, seed % 3 == 0, false, true, false);
            model.applyUVMirror(true, seed % 2 == 0);
            model.applyShear(new Vector3f(1f, 0f, 0f), new Vector3f(0f, 1f, 0f), 0.125f);
            model.applyMatrix(new Matrix4f(new org.joml.Matrix4f().translation(0.1f, 0.2f, 0.3f)));
            model.triangulate(); model.generateNormals(); model.distinct();
            RawModel transformed = model(); transformed.appendTransformed(model, new Matrix4f(), 0x12345678, 876);
            require(transformed.getVertexCount() == model.getVertexCount() && transformed.getFaceCount() == model.getFaceCount(), "Transformed append count changed");
            require(transformed.meshList.values().stream().flatMap(m -> m.vertices.stream()).allMatch(v -> v.color == 0x12345678 && v.light == 876), "Transformed append attributes changed");
            byte[] bytes = encode(model);
            RawModel restored = new RawModel(input(bytes));
            require(restored.sourceLocation == null && restored.getVertexCount() == model.getVertexCount() && restored.getFaceCount() == model.getFaceCount(), "Model binary header/counts changed");
            records.add("geometry-" + seed + "\t" + model.getVertexCount() + ":" + model.getFaceCount() + ":" + HexFormat.of().formatHex(bytes));
        }
        RawModel aggregate = model(); RawModel next = model(); next.append(mesh(0)); aggregate.append(next);
        require(aggregate.getVertexCount() == 4, "Model append overload changed");
        for (int count : new int[] {0, -1, Integer.MIN_VALUE}) { byte[] header = new byte[] {(byte) (count >>> 24), (byte) (count >>> 16), (byte) (count >>> 8), (byte) count}; require(new RawModel(input(header)).meshList.isEmpty(), "Legacy negative model count rejected"); }
        for (int length : new int[] {0, 1, 2, 3}) rejects(EOFException.class, () -> new RawModel(input(new byte[length])));
    }
    private static void materials(List<String> records) throws Exception {
        RawModel model = model(); RawMesh first = mesh(1), second = mesh(2); model.meshList.put(first.materialProp, first); model.meshList.put(second.materialProp, second);
        first.materialProp.attrState.color = 55; first.materialProp.attrState.lightmapUV = 66;
        for (String mode : List.of("interior", "exteriortranslucent", "light", "lighttranslucent", "reset")) {
            model.setAllRenderType(mode);
            records.add("material-" + mode + "\t" + HexFormat.of().formatHex(encode(model)));
        }
        require(first.materialProp.attrState.color == 55 && first.materialProp.attrState.lightmapUV == 66 && first.materialProp.shaderName.equals(""), "Material reset lost original values");
        model.clearAttrState(VertAttrType.COLOR); require(first.materialProp.attrState.color == null && first.materialProp.attrState.lightmapUV == 66, "Clearing attribute changed others");
        Identifier replacement = Identifier.parse("ante:changed");
        model.replaceTexture("1.png", replacement); require(first.materialProp.texture == replacement && second.materialProp.texture != replacement, "Basename texture replacement changed");
        model.replaceTexture(null, null); require(first.materialProp.texture == replacement, "Null old texture no longer ignored");
        model.replaceAllTexture(null); require(first.materialProp.texture == null && second.materialProp.texture == null, "Nullable replacement changed");
        Function<Matrix4f, Matrix4f> function = value -> value; model.setMatixProcess(function);
        require(first.materialProp.attrState.matrixModel == function && second.materialProp.attrState.matrixModel == function, "Matrix callback copied/lost");
        model.setMatixProcess(null); require(!first.materialProp.attrState.useMatixProcess, "Null callback state changed");
        RawMesh late = mesh(3); model.meshList.put(late.materialProp, late);
        rejects(IllegalArgumentException.class, () -> model.setAllRenderType("reset"));
        rejects(NullPointerException.class, () -> model.setAllRenderType(null));
    }
    private static void uploads(List<String> records) throws Exception {
        RawModel model = model(); UploadProbe empty = new UploadProbe("empty", false), a = new UploadProbe("a", true), b = new UploadProbe("b", true);
        model.meshList.put(empty.materialProp, empty); model.meshList.put(a.materialProp, a); model.meshList.put(b.materialProp, b);
        EVENTS.clear(); Model immediate = model.upload(null);
        require(immediate.meshList.equals(List.of(a.uploaded, b.uploaded)) && EVENTS.equals(List.of("immediate:a", "immediate:b")), "Immediate order/empty filtering changed");
        EVENTS.clear(); activeSupplier = model.uploadAsync(null);
        require(EVENTS.equals(List.of("prepare:a", "prepare:b")), "Deferred capture performed upload eagerly");
        require(Arrays.stream(activeSupplier.getClass().getDeclaredMethods()).anyMatch(method -> method.getName().equals("get") && Modifier.isSynchronized(method.getModifiers())), "Supplier lost synchronized dispatch");
        model.meshList.clear(); b.failOnce = true;
        try { activeSupplier.get(); throw new AssertionError("Deferred failure swallowed"); } catch (RuntimeException error) { require(error == FAILURE, "Deferred failure replaced"); }
        require(a.uploaded.closes == 0, "Legacy retry acquired close ownership");
        Model success = activeSupplier.get(); require(activeSupplier.get() == success && success.meshList.equals(List.of(a.uploaded, b.uploaded)), "Deferred cache/retry/mesh identity changed");
        records.add("deferred\t" + EVENTS);
        int priorEvents = EVENTS.size();
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Model>> futures = new ArrayList<>(); for (int i = 0; i < 16; i++) futures.add(executor.submit(activeSupplier::get));
            for (Future<Model> future : futures) require(future.get(5, TimeUnit.SECONDS) == success, "Concurrent cache returned a second model");
        } finally { executor.shutdownNow(); require(executor.awaitTermination(5, TimeUnit.SECONDS), "Fixture worker did not exit"); }
        require(EVENTS.size() == priorEvents, "Cached parallel calls uploaded again");
        RawModel cold = model(); UploadProbe coldProbe = new UploadProbe("cold", true); cold.meshList.put(coldProbe.materialProp, coldProbe);
        EVENTS.clear(); activeSupplier = cold.uploadAsync(null); CountDownLatch start = new CountDownLatch(1);
        ExecutorService coldExecutor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Model>> futures = new ArrayList<>();
            for (int i = 0; i < 16; i++) futures.add(coldExecutor.submit(() -> { start.await(); return activeSupplier.get(); }));
            start.countDown(); Model first = futures.getFirst().get(5, TimeUnit.SECONDS);
            for (Future<Model> future : futures) require(future.get(5, TimeUnit.SECONDS) == first, "Concurrent first upload created multiple models");
            require(EVENTS.equals(List.of("prepare:cold", "get:cold")), "Concurrent first upload executed supplier twice");
        } finally { start.countDown(); coldExecutor.shutdownNow(); require(coldExecutor.awaitTermination(5, TimeUnit.SECONDS), "Cold fixture worker did not exit"); }
        RawModel nullable = model(); UploadProbe nullMesh = new UploadProbe("null", true); nullMesh.returnNull = true; nullable.meshList.put(nullMesh.materialProp, nullMesh);
        activeSupplier = nullable.uploadAsync(null); require(activeSupplier.get().meshList.size() == 1 && activeSupplier.get().meshList.getFirst() == null, "Nullable mesh result rejected");
        lastResult = success;
    }
    private static void draw(List<String> records) {
        for (int mask = 0; mask < 16; mask++) {
            final int flags = mask;
            MaterialProp material = new MaterialProp() { @Override public RenderType getBlazeRenderType() { EVENTS.add("render-type"); return null; } };
            material.translucent = (mask & 8) != 0; material.attrState.color = (mask & 1) != 0 ? 0x81234567 : null;
            material.attrState.lightmapUV = (mask & 2) != 0 ? 555 : null; material.attrState.overlayUV = (mask & 4) != 0 ? 0xABCD1234 : null;
            Matrix4f matrix = new Matrix4f(); DrawContext context = new DrawContext();
            RawMesh mesh = new RawMesh(material) {
                @Override public void writeBlazeBuffer(FaceList faces, Matrix4f transform, int color, int light, int overlay, DrawContext drawContext) {
                    require(faces == null && transform == matrix && drawContext == context, "Draw argument identity changed"); EVENTS.add("draw:" + color + ":" + light + ":" + overlay);
                }
            };
            RawModel model = model(); model.meshList.put(material, mesh); EVENTS.clear();
            model.writeBlazeBuffer(new BufferSourceProxy(null) { @Override public FaceList getBuffer(RenderType type, boolean sorting) { require(type == null && sorting == ((flags & 8) != 0), "Draw buffer arguments changed"); EVENTS.add("buffer"); return null; } }, matrix, 999, 888, context);
            records.add("draw-" + mask + "\t" + EVENTS);
        }
        RawModel mutable = model(); RawMesh[] replacement = {null};
        MaterialProp material = new MaterialProp() { @Override public RenderType getBlazeRenderType() { mutable.meshList.put(this, replacement[0]); return null; } };
        replacement[0] = new RawMesh(material) { @Override public void writeBlazeBuffer(FaceList faces, Matrix4f matrix, int color, int light, int overlay, DrawContext context) { EVENTS.add("replacement"); } };
        mutable.meshList.put(material, new RawMesh(material)); EVENTS.clear();
        BufferSourceProxy proxy = new BufferSourceProxy(null) { @Override public FaceList getBuffer(RenderType type, boolean sorting) { EVENTS.add("buffer"); return null; } };
        mutable.writeBlazeBuffer(proxy, null, 0, 0, null);
        require(EVENTS.equals(List.of("buffer", "replacement")), "Material callback's live mesh replacement ignored");
        replacement[0] = null; EVENTS.clear();
        rejects(NullPointerException.class, () -> mutable.writeBlazeBuffer(proxy, null, 0, 0, null));
        require(EVENTS.equals(List.of("buffer")), "Null mesh failed before buffer callback");
    }
    private static void nullable() {
        RawModel empty = model(); empty.applyMatrix(null); empty.applyRotation(null, 0); empty.applyShear(null, null, 0); empty.clearAttrState(null); empty.setAllRenderType(null); empty.writeBlazeBuffer(null, null, 0, 0, null);
        require(empty.upload(null).meshList.isEmpty() && empty.uploadAsync(null).get().meshList.isEmpty(), "Empty aggregate rejected unused null arguments");
        RawMesh mesh = mesh(123); int[] calls = {0};
        mesh.materialProp.attrState = new VertAttrState() { @Override public void clearAttr(VertAttrType type) { require(type == null, "Nullable attribute was replaced"); calls[0]++; } };
        empty.meshList.put(mesh.materialProp, mesh); empty.clearAttrState(null); require(calls[0] == 1, "Nullable attribute did not reach override"); empty.meshList.clear();
        rejects(NullPointerException.class, () -> empty.append((RawMesh) null)); rejects(NullPointerException.class, () -> empty.append((Collection<RawMesh>) null)); rejects(NullPointerException.class, () -> empty.append((RawModel) null));
        empty.meshList = null; rejects(NullPointerException.class, empty::getVertexCount);
        require(lastResult.meshList.size() == 2, "Later operations mutated prior upload");
    }
    public static final class CloseProbe extends Mesh { int closes; CloseProbe() { super(null, null, null); } @Override public void close() { closes++; } }
    public static final class UploadProbe extends RawMesh {
        final String name; final CloseProbe uploaded = new CloseProbe(); boolean failOnce, returnNull;
        UploadProbe(String name, boolean nonempty) { super(new MaterialProp()); this.name = name; materialProp.texture = Identifier.parse("ante:" + name); if (nonempty) faces.add(new Face(new int[] {0, 0, 0})); }
        @Override public Mesh upload(VertAttrMapping mapping) { require(mapping == null, "Immediate mapping changed"); EVENTS.add("immediate:" + name); return uploaded; }
        @Override public Supplier<Mesh> uploadAsync(VertAttrMapping mapping) { require(mapping == null, "Deferred mapping changed"); EVENTS.add("prepare:" + name); return () -> { require(Thread.holdsLock(activeSupplier), "Deferred uploads are not protected by supplier lock"); EVENTS.add("get:" + name); if (failOnce) { failOnce = false; throw FAILURE; } return returnNull ? null : uploaded; }; }
    }
    private static DataInputStream input(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }
    private static byte[] encode(RawModel model) throws IOException { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); model.serializeTo(new DataOutputStream(bytes)); return bytes.toByteArray(); }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void rejects(Class<? extends Throwable> expected, Action action) { assertions++; Throwable caught = null; try { action.run(); } catch (Throwable error) { caught = error; } if (caught == null || caught.getClass() != expected) throw new AssertionError("Expected " + expected + ", got " + caught, caught); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
