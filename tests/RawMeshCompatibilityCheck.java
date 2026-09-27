package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.math.*;
import cn.zbx1425.sowcer.model.Mesh;
import cn.zbx1425.sowcer.object.IndexBuf;
import cn.zbx1425.sowcer.object.VertBuf;
import cn.zbx1425.sowcer.util.DrawContext;
import cn.zbx1425.sowcer.vertex.*;
import cn.zbx1425.sowcerext.model.*;
import cn.zbx1425.sowcerext.model.integration.FaceList;
import java.io.*;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

/** Actual raw mesh transformations, packed uploads and mutable contracts; no graphics context. */
public final class RawMeshCompatibilityCheck {
    private static int assertions;
    private static final List<String> EVENTS = new ArrayList<>();
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath(); boolean kotlin = Files.isDirectory(source);
        require(Path.of(RawMesh.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong raw mesh source");
        require(Arrays.stream(RawMesh.class.getDeclaredAnnotations()).anyMatch(a -> a.annotationType().getName().equals("kotlin.Metadata")) == kotlin, "Wrong raw mesh language");
        List<String> records = new ArrayList<>();
        transforms(records); packing(records); mutation(records); rendering(records); asynchronous(); nullableOverrides(); failures();
        if (Arrays.asList(args).contains("--record")) { require(!kotlin, "Record only Java"); Files.writeString(Path.of(args[0]), String.join("\n", records) + "\n"); }
        else {
            List<String> expected = Files.readAllLines(Path.of(args[0])); require(expected.size() == records.size(), "Raw mesh record count changed");
            for (int i = 0; i < records.size(); i++) require(expected.get(i).equals(records.get(i)), "Raw mesh differs at " + i + "\nJava: " + expected.get(i) + "\nActual: " + records.get(i));
        }
        System.out.println("PASS: raw mesh, " + assertions + " assertions / " + records.size() + " Java records; transforms, all 128 attribute layouts, ownership, async snapshots, render dispatch and failures; assertions=" + RawMesh.class.desiredAssertionStatus() + " (no GPU)");
    }
    private static RawMesh raw() {
        RawMesh mesh = new RawMesh(new MaterialProp());
        mesh.materialProp.attrState.color = 0x12345678; mesh.materialProp.attrState.overlayUV = 0xABCD1234;
        mesh.materialProp.attrState.matrixModel = matrix -> new Matrix4f(new org.joml.Matrix4f().translation(2f, 3f, 4f));
        for (int i = 0; i < 4; i++) {
            Vertex vertex = new Vertex(new Vector3f(i & 1, i >> 1, i * 0.125f), new Vector3f(i == 0 ? 0f : 1f, i == 0 ? 0f : 2f, 0f));
            vertex.u = i * 0.25f; vertex.v = 1f - vertex.u; vertex.color = 0x81ABCDEF + i; vertex.light = 0xFFAA1234 + i; mesh.vertices.add(vertex);
        }
        mesh.faces.add(new Face(new int[] {0, 1, 3, 2})); return mesh;
    }
    private static VertAttrMapping mapping(int mask) {
        VertAttrMapping.Builder builder = new VertAttrMapping.Builder();
        for (VertAttrType type : VertAttrType.values()) builder.set(type, (mask & (1 << type.ordinal())) != 0 ? VertAttrSrc.VERTEX_BUF : VertAttrSrc.GLOBAL);
        return builder.build();
    }
    private static void transforms(List<String> records) {
        float[] scales = {0f, -0f, 1f, -2f, Float.MIN_VALUE, Float.MAX_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        for (int i = 0; i < scales.length; i++) {
            RawMesh mesh = raw(); mesh.applyScale(scales[i], 0.25f, -3f); records.add("scale-" + i + "\t" + geometry(mesh));
        }
        for (int mask = 0; mask < 64; mask++) {
            RawMesh mesh = raw(); mesh.applyMirror((mask & 1) != 0, (mask & 2) != 0, (mask & 4) != 0, (mask & 8) != 0, (mask & 16) != 0, (mask & 32) != 0);
            mesh.applyUVMirror((mask & 1) != 0, (mask & 2) != 0); records.add("mirror-" + mask + "\t" + geometry(mesh));
        }
        for (int mode = 0; mode < 5; mode++) {
            RawMesh mesh = raw();
            for (Vertex vertex : mesh.vertices) { vertex.normal = new Vector3f(0f, 0f, 0f); if (mode == 1) vertex.position = new Vector3f(0f, 0f, 0f); if (mode == 2) vertex.position = new Vector3f(Float.NaN, 0f, 0f); }
            if (mode == 3) mesh.faces.add(new Face(new int[] {0, 1}));
            if (mode == 4) mesh.faces.add(new Face(new int[] {0, 2, 3}));
            List<Vertex> old = mesh.vertices; mesh.generateNormals(); require(old != mesh.vertices, "Normal generation no longer replaces vertex list");
            records.add("normals-" + mode + "\t" + geometry(mesh));
        }
        RawMesh mesh = raw(); mesh.applyTranslation(0.25f, -0.5f, 2f); mesh.applyRotation(new Vector3f(0.577f, 0.577f, 0.577f), 27f);
        mesh.applyShear(new Vector3f(1f, -1f, 0.25f), new Vector3f(0.5f, 2f, -3f), -0.75f);
        mesh.applyMatrix(new Matrix4f(new org.joml.Matrix4f().rotateY(0.4f).translate(1f, 2f, 3f)));
        records.add("transform-chain\t" + geometry(mesh));
    }
    private static void packing(List<String> records) throws Exception {
        for (int mask = 0; mask < 128; mask++) {
            RawMesh raw = raw(); String before = geometry(raw); VertAttrMapping mapping = mapping(mask);
            try (Mesh mesh = raw.upload(mapping)) {
                require(before.equals(geometry(raw)), "Upload rewrote caller geometry");
                require(mesh.materialProp == raw.materialProp && mesh.indexBuf.faceCount == 2, "Immediate upload material/count changed");
                records.add("packed-" + mask + "\t" + mapping.strideVertex + ":" + hex(mesh.vertBuf.snapshot()) + ":" + hex(mesh.indexBuf.snapshot()));
            }
        }
        RawMesh raw = raw(); raw.triangulate(); ByteArrayOutputStream bytes = new ByteArrayOutputStream(); raw.serializeTo(new DataOutputStream(bytes));
        RawMesh restored = new RawMesh(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        require(restored.vertices.size() == 4 && restored.faces.size() == 2 && restored.vertices.getFirst().color == 0 && restored.vertices.getFirst().light == 0, "Legacy mesh format changed");
        records.add("wire\t" + HexFormat.of().formatHex(bytes.toByteArray()));
        byte[] encoded = bytes.toByteArray();
        for (int length : new int[] {0, 1, 3, 7, encoded.length - 1}) rejects(length == 7 ? com.google.gson.JsonSyntaxException.class : EOFException.class, () -> new RawMesh(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(encoded, length)))));
    }
    private static void mutation(List<String> records) {
        RawMesh raw = raw(), appended = new RawMesh(new MaterialProp()); appended.append(raw);
        require(appended.vertices.getFirst() == raw.vertices.getFirst() && appended.faces.getFirst() != raw.faces.getFirst(), "Append aliasing changed");
        appended.append(raw); records.add("append\t" + geometry(appended));
        RawMesh transformed = new RawMesh(new MaterialProp()); transformed.appendTransformed(raw, new Matrix4f(), 456, 789);
        require(transformed.vertices.getFirst() != raw.vertices.getFirst() && transformed.vertices.getFirst().color == 456, "Transformed append ownership changed");
        records.add("append-transformed\t" + geometry(transformed));
        RawMesh deep = raw.copy(), shallow = raw.copyForMaterialChanges();
        require(deep.materialProp != raw.materialProp && deep.vertices.getFirst() != raw.vertices.getFirst() && deep.faces.getFirst() != raw.faces.getFirst(), "Deep copy changed");
        require(shallow.vertices == raw.vertices && shallow.faces == raw.faces && shallow.materialProp != raw.materialProp, "Material copy lost aliases");
        List<Vertex> liveVertices = appended.vertices; List<Face> liveFaces = appended.faces; appended.triangulate(); appended.distinct();
        require(appended.vertices == liveVertices && appended.faces == liveFaces, "Triangulate/distinct replaced live lists");
        records.add("distinct\t" + geometry(appended)); appended.clear(); require(liveVertices.isEmpty() && liveFaces.isEmpty(), "Clear replaced public lists");
        for (String type : Arrays.asList("exterior", "exteriortranslucent", "interior", "interiortranslucent", "light", "lighttranslucent", "unknown", null)) {
            RawMesh mesh = raw(); var old = mesh.materialProp.attrState;
            if (type == null) rejects(NullPointerException.class, () -> mesh.setRenderType(null));
            else if (type.equals("unknown")) rejects(IllegalArgumentException.class, () -> mesh.setRenderType(type));
            else mesh.setRenderType(type);
            require(mesh.materialProp.attrState != old && mesh.materialProp.attrState.color.equals(old.color), "Render type stopped copying attributes before dispatch/failure");
            var material = mesh.materialProp;
            records.add("render-type-" + type + "\t" + material.shaderName + ":" + material.translucent + ":" + material.writeDepthBuf + ":" + material.cutoutHack + ":" + material.attrState.lightmapUV);
        }
        Function<Matrix4f, Matrix4f> process = value -> value; raw.setMatixProcess(process); require(raw.materialProp.attrState.matrixModel == process, "Matrix process identity changed");
        raw.setMatixProcess(null); require(!raw.materialProp.attrState.useMatixProcess, "Null process state changed");
        RawMesh counting = new RawMesh(new MaterialProp());
        for (int i = 0; i < 3; i++) { final int id = i; counting.vertices.add(new Vertex(new Vector3f(i, 0, 0)) { @Override public int hashCode() { EVENTS.add("hash:" + id); return super.hashCode(); } }); }
        counting.faces.add(new Face(new int[] {0, 1, 2})); counting.faces.add(new Face(new int[] {0, 1, 2})); EVENTS.clear(); counting.distinct();
        records.add("hash-order\t" + EVENTS);
    }
    private static void rendering(List<String> records) {
        RawMesh mesh = raw(); mesh.triangulate(); DrawContext context = new DrawContext(); List<String> emitted = new ArrayList<>();
        mesh.writeBlazeBuffer(new FaceList(null, false) {
            @Override public void addFace(Vertex[] vertices, int color, int light, int overlay) {
                StringJoiner line = new StringJoiner(";"); for (Vertex vertex : vertices) { require(vertex.color == 0 && vertex.light == 0, "Per-face draw unexpectedly copied packed attributes"); line.add(vertex(vertex)); }
                emitted.add(color + ":" + light + ":" + overlay + ":" + line);
            }
        }, new Matrix4f(new org.joml.Matrix4f().translation(1f, 2f, 3f)), 123, 456, 789, context);
        context.resetFrameProfiler(); require(context.blazeFaceCount == 2, "Draw profiling changed"); records.add("draw\t" + emitted);
        RawMesh quad = raw(); int[] size = {0}; FaceList sink = new FaceList(null, false) { @Override public void addFace(Vertex[] vertices, int c, int l, int o) { size[0] = vertices.length; } };
        if (RawMesh.class.desiredAssertionStatus()) rejects(AssertionError.class, () -> quad.writeBlazeBuffer(sink, new Matrix4f(), 0, 0, 0, new DrawContext()));
        else { quad.writeBlazeBuffer(sink, new Matrix4f(), 0, 0, 0, new DrawContext()); require(size[0] == 4, "Disabled assertions changed nontriangle dispatch"); }
    }
    private static void asynchronous() throws Exception {
        RawMesh mesh = raw(); VertAttrMapping mapping = mapping(127); Supplier<Mesh> supplier = mesh.uploadAsync(mapping);
        require(Arrays.stream(supplier.getClass().getDeclaredMethods()).anyMatch(m -> m.getName().equals("get") && Modifier.isSynchronized(m.getModifiers())), "Deferred upload lost synchronization");
        mesh.vertices.getFirst().position.add(999f, 0f, 0f); mesh.faces.clear(); mesh.materialProp.attrState.color = 0;
        CountDownLatch start = new CountDownLatch(1); ExecutorService executor = Executors.newFixedThreadPool(4); Mesh result = null;
        try {
            List<Future<Mesh>> futures = new ArrayList<>(); for (int i = 0; i < 16; i++) futures.add(executor.submit(() -> { start.await(); return supplier.get(); }));
            start.countDown(); result = futures.getFirst().get(5, TimeUnit.SECONDS);
            for (Future<Mesh> future : futures) require(future.get(5, TimeUnit.SECONDS) == result, "Concurrent upload created multiple meshes");
            require(result.materialProp != mesh.materialProp && result.materialProp.attrState.color == 0x12345678 && result.indexBuf.faceCount == 2 && result.vertBuf.snapshot().getFloat(mapping.pointers.get(VertAttrType.POSITION)) == 0f, "Deferred geometry/material snapshot changed");
        } finally { start.countDown(); executor.shutdownNow(); require(executor.awaitTermination(5, TimeUnit.SECONDS), "Upload worker did not exit"); if (result != null) result.close(); }
        require(supplier.get() == result, "Close changed legacy supplier identity");
        RawMesh callbacks = raw();
        callbacks.materialProp.attrState.matrixModel = matrix -> { callbacks.materialProp.attrState.color = 654321; return matrix; };
        Supplier<Mesh> afterPacking = callbacks.uploadAsync(mapping);
        callbacks.materialProp.attrState.color = -1;
        try (Mesh captured = afterPacking.get()) {
            require(captured.materialProp.attrState.color == 654321, "Deferred material must be captured after packing callbacks");
        }
    }
    private static void nullableOverrides() {
        RawMesh mesh = new RawMesh(new MaterialProp()); mesh.vertices.add(new Vertex(null, null)); int[] calls = {0};
        mesh.applyMatrix(new Matrix4f() {
            @Override public Vector3f transform(Vector3f value) { require(value == null, "Position null not forwarded"); calls[0]++; return null; }
            @Override public Vector3f transform3(Vector3f value) { require(value == null, "Normal null not forwarded"); calls[0]++; return null; }
        });
        require(calls[0] == 2 && mesh.vertices.getFirst().position == null && mesh.vertices.getFirst().normal == null, "Java matrix overrides lost nullable input/results");
    }
    private static void failures() {
        RawMesh mesh = raw(); rejects(IllegalStateException.class, () -> mesh.append(mesh)); rejects(IllegalStateException.class, () -> mesh.appendTransformed(mesh, null, 0, 0));
        mesh.faces.getFirst().vertices[0] = -1;
        rejects(IndexOutOfBoundsException.class, mesh::validateVertIndex); rejects(IndexOutOfBoundsException.class, () -> mesh.upload(mapping(127))); rejects(IndexOutOfBoundsException.class, () -> mesh.uploadAsync(mapping(127)));
        RawMesh empty = new RawMesh((MaterialProp) null); empty.clear(); empty.applyMatrix(null); empty.applyRotation(null, 0); empty.applyShear(null, null, 0);
        empty.writeBlazeBuffer(null, null, 0, 0, 0, new DrawContext()); require(empty.materialProp == null, "Nullable material rejected");
        rejects(NullPointerException.class, empty::copy); empty.vertices = null; rejects(NullPointerException.class, empty::clear);
        RawMesh source = raw(); IndexBuf untouched = new IndexBuf(99, 0x1405);
        untouched.upload(ByteBuffer.wrap(new byte[] {1, 2, 3, 4}), VertBuf.USAGE_STATIC_DRAW);
        try (Mesh external = new Mesh(new VertBuf(), untouched, source.materialProp)) {
            external.vertBuf.close();
            rejects(IllegalStateException.class, () -> source.upload(external, mapping(127)));
            require(untouched.id != 0 && untouched.faceCount == 99 && hex(untouched.snapshot()).equals("01020304"), "Failed external upload closed or changed caller-owned index buffer");
        }
    }
    private static String geometry(RawMesh mesh) { StringJoiner out = new StringJoiner(";"); for (Vertex vertex : mesh.vertices) out.add(vertex(vertex)); out.add("faces"); for (Face face : mesh.faces) out.add(Arrays.toString(face.vertices)); return out.toString(); }
    private static String vertex(Vertex v) { return bits(v.position.x()) + ":" + bits(v.position.y()) + ":" + bits(v.position.z()) + ":" + bits(v.normal.x()) + ":" + bits(v.normal.y()) + ":" + bits(v.normal.z()) + ":" + bits(v.u) + ":" + bits(v.v) + ":" + v.color + ":" + v.light; }
    private static String bits(float value) { return Integer.toHexString(Float.floatToIntBits(value)); }
    private static String hex(ByteBuffer source) { ByteBuffer copy = source.duplicate(); copy.clear(); byte[] bytes = new byte[copy.remaining()]; copy.get(bytes); return HexFormat.of().formatHex(bytes); }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void rejects(Class<? extends Throwable> type, Action action) { assertions++; Throwable caught = null; try { action.run(); } catch (Throwable error) { caught = error; } if (caught == null || caught.getClass() != type) throw new AssertionError("Expected " + type + ", got " + caught, caught); }
    private static void require(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
}
