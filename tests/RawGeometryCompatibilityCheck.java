package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.batch.MaterialProp;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcerext.model.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Real vertex/face and RawMesh consumers, compared with the frozen Java release. */
public final class RawGeometryCompatibilityCheck {
    private static int assertions;
    private static final List<String> EVENTS = new ArrayList<>();
    private static final IOException IO_FAILURE = new IOException("geometry fixture");
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        boolean kotlin = Files.isDirectory(source) || Arrays.asList(args).contains("--kotlin");
        for (Class<?> type : List.of(Vertex.class, Face.class)) {
            require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source), "Wrong geometry source: " + type);
            require(Arrays.stream(type.getDeclaredAnnotations()).anyMatch(a -> a.annotationType().getName().equals("kotlin.Metadata")) == kotlin, "Wrong geometry language: " + type);
        }
        List<String> records = new ArrayList<>();
        vertices(records); faces(records); distinct(records); callbacks(records);
        String actual = String.join("\n", records) + "\n";
        if (Arrays.asList(args).contains("--record")) { require(!kotlin, "Record only original Java"); Files.writeString(Path.of(args[0]), actual); }
        else {
            List<String> expected = Files.readAllLines(Path.of(args[0]));
            require(expected.size() == records.size(), "Geometry record count changed");
            for (int i = 0; i < records.size(); i++) require(expected.get(i).equals(records.get(i)), "Geometry differs at " + i + "\nJava: " + expected.get(i) + "\nActual: " + records.get(i));
        }
        System.out.println("PASS: raw geometry, " + assertions + " assertions / " + records.size() + " Java records; float bits, binary I/O, aliasing, callback order, triangulation and actual mesh deduplication; Face assertions=" + Face.class.desiredAssertionStatus());
    }
    private static void vertices(List<String> records) throws Exception {
        Vertex empty = new Vertex();
        require(empty.position == null && empty.normal == null && empty.u == 0f && empty.v == 0f && empty.color == 0 && empty.light == 0, "Default vertex changed");
        require(empty.equals(empty) && !empty.equals(null) && !empty.equals(new Object()) && !empty.equals(new VertexChild()), "Exact class/self equality changed");
        rejects(NullPointerException.class, () -> empty.equals(new Vertex()));
        rejects(NullPointerException.class, empty::copy);
        records.add("default\t" + empty.hashCode());
        Vector3f position = new Vector3f(3f, 4f, 5f);
        Vertex single = new Vertex(position);
        require(single.position == position && single.normal.equals(new Vector3f(0f, 0f, 0f)), "Single-vector constructor changed");
        require(new Vertex((Vector3f) null).position == null && new Vertex(null, null).normal == null, "Nullable constructors rejected");
        int[] floatBits = {0, 0x80000000, 0x3f800000, 0xbf800000, 1, 0x7f7fffff, 0x7f800000, 0xff800000, 0x7fc00000, 0x7fa12345, 0xffc00123};
        for (int i = 0; i < 128; i++) {
            Random random = new Random(111262L + i);
            Vertex vertex = new Vertex(new Vector3f(random.nextFloat(), random.nextFloat(), random.nextFloat()), new Vector3f(0f, 1f, -0f));
            vertex.u = Float.intBitsToFloat(floatBits[i % floatBits.length]); vertex.v = Float.intBitsToFloat(floatBits[(i * 7) % floatBits.length]);
            vertex.color = random.nextInt(); vertex.light = random.nextInt();
            Vertex copied = vertex.copy();
            require(vertex.equals(copied) && copied.equals(vertex) && copied.hashCode() == vertex.hashCode(), "Copy value changed");
            require(copied.position != vertex.position && copied.normal != vertex.normal, "Copy shares vectors");
            require(vertex.hashCode() == Objects.hash(vertex.position, vertex.normal, vertex.u, vertex.v, vertex.color, vertex.light), "Java hash polynomial changed");
            copied.color ^= 1; require(!vertex.equals(copied), "Color omitted from equality"); copied.color ^= 1;
            copied.light ^= 1; require(!vertex.equals(copied), "Light omitted from equality"); copied.light ^= 1;
            copied.position.add(1f, 0f, 0f); require(!vertex.equals(copied), "Position omitted from equality");
            byte[] bytes = write(vertex::serializeTo);
            Vertex restored = new Vertex(input(bytes));
            require(restored.position.equals(vertex.position) && restored.normal.equals(vertex.normal) && Float.compare(restored.u, vertex.u) == 0 && Float.compare(restored.v, vertex.v) == 0, "Serialized vectors/UV changed");
            require(bytes.length == 32 && restored.color == 0 && restored.light == 0, "Legacy wire format unexpectedly includes color/light");
            records.add("vertex-" + i + "\t" + vertex.hashCode() + ":" + HexFormat.of().formatHex(bytes));
        }
        for (int left : floatBits) for (int right : floatBits) {
            Vertex a = new Vertex(position), b = new Vertex(position); a.u = Float.intBitsToFloat(left); b.u = Float.intBitsToFloat(right);
            require(a.equals(b) == (Float.compare(a.u, b.u) == 0), "UV NaN/zero equality changed");
            records.add("float-pair\t" + left + ":" + right + ":" + a.equals(b) + ":" + a.hashCode() + ":" + b.hashCode());
        }
        for (int length = 0; length < 32; length++) { byte[] truncated = new byte[length]; rejects(EOFException.class, () -> new Vertex(input(truncated))); }
        rejects(NullPointerException.class, () -> new Vertex((DataInputStream) null));
        Vertex noNormal = new Vertex(position, null);
        rejects(NullPointerException.class, noNormal::copy);
        require(noNormal.hashCode() == Objects.hash(position, null, 0f, 0f, 0, 0), "Null normal hash changed");
        ByteArrayOutputStream partial = new ByteArrayOutputStream();
        rejects(NullPointerException.class, () -> noNormal.serializeTo(new DataOutputStream(partial)));
        require(partial.size() == 12, "Null-normal write failure moved before position bytes");
        int[] writes = {0};
        try {
            single.serializeTo(new DataOutputStream(new OutputStream() {
                @Override public void write(int value) throws IOException { if (writes[0]++ == 7) throw IO_FAILURE; }
            })); throw new AssertionError("I/O failure swallowed");
        } catch (IOException error) { require(error == IO_FAILURE && writes[0] == 8, "I/O identity/order changed"); }
        EVENTS.clear();
        Vertex observed = new Vertex(new Vector3f(1f, 2f, 3f) { @Override public float x() { EVENTS.add("read-x"); return 1f; } });
        rejects(NullPointerException.class, () -> observed.serializeTo(null));
        require(EVENTS.equals(List.of("read-x")), "Null stream failed before evaluating virtual coordinate");
    }
    private static void faces(List<String> records) throws Exception {
        Face nil = new Face((int[]) null);
        require(nil.equals(new Face((int[]) null)) && nil.hashCode() == 0, "Nullable face equality/hash changed");
        nil.flip(); require(nil.vertices == null, "Null flip changed"); rejects(NullPointerException.class, nil::copy);
        rejects(NullPointerException.class, () -> Face.triangulate(null, false));
        for (int length = 0; length < 3; length++) { int[] shortFace = new int[length]; rejects(ArrayIndexOutOfBoundsException.class, () -> Face.triangulate(shortFace, true)); }
        for (int length = 3; length <= 40; length++) for (boolean doubleSided : new boolean[] {false, true}) {
            int[] input = new Random(262 + length).ints(length).toArray(), original = input.clone();
            Face face = new Face(input); require(face.vertices == input, "Face constructor copied caller array");
            Face copy = face.copy(); require(copy.vertices != input && copy.equals(face) && copy.hashCode() == face.hashCode(), "Face copy changed");
            require(!face.equals(new FaceChild(input)) && !face.equals(null) && !face.equals(new Object()), "Face exact class equality changed");
            List<Face> triangles = Face.triangulate(input, doubleSided);
            require(triangles.size() == (length - 2) * (doubleSided ? 2 : 1), "Wrong triangle count");
            for (int i = 0; i < input.length; i++) require(input[i] == original[doubleSided ? input.length - 1 - i : i], "Two-sided input mutation changed");
            StringJoiner values = new StringJoiner(";");
            for (Face triangle : triangles) {
                require(triangle.vertices.length == 3 && triangle.vertices != input, "Triangle aliases input");
                byte[] bytes = write(triangle::serializeTo); Face restored = new Face(input(bytes));
                require(bytes.length == 12 && restored.equals(triangle), "Triangle binary format changed");
                values.add(Arrays.toString(triangle.vertices));
            }
            records.add("face-" + length + "-" + doubleSided + "\t" + values + ":" + Arrays.toString(input));
            triangles.add(face); require(triangles.remove(triangles.size() - 1) == face, "Triangle result became immutable");
            face.flip(); require(face.vertices == input, "Flip replaced caller array");
        }
        for (int[] range : new int[][] {{0, 2}, {-3, 4}, {17, 26}, {Integer.MIN_VALUE, Integer.MIN_VALUE + 3}, {Integer.MAX_VALUE - 3, Integer.MAX_VALUE - 1}}) {
            for (boolean doubleSided : new boolean[] {false, true}) records.add("range\t" + range[0] + ":" + range[1] + ":" + doubleSided + ":" + Face.triangulate(range[0], range[1], doubleSided).stream().map(face -> Arrays.toString(face.vertices)).toList());
        }
        for (int[] range : new int[][] {{4, 3}, {1, 1}, {1, 2}, {Integer.MAX_VALUE - 2, Integer.MAX_VALUE}}) rejects(ArrayIndexOutOfBoundsException.class, () -> Face.triangulate(range[0], range[1], false));
        for (int length = 0; length < 12; length++) { byte[] truncated = new byte[length]; rejects(EOFException.class, () -> new Face(input(truncated))); }
        for (int length : new int[] {0, 1, 2, 4}) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); Face malformed = new Face(new int[length]);
            if (Face.class.desiredAssertionStatus()) { rejects(AssertionError.class, () -> malformed.serializeTo(new DataOutputStream(bytes))); require(bytes.size() == 0, "Assertion wrote partial bytes"); }
            else if (length < 3) { rejects(ArrayIndexOutOfBoundsException.class, () -> malformed.serializeTo(new DataOutputStream(bytes))); require(bytes.size() == length * 4, "Disabled assertion failure order changed"); }
            else { malformed.serializeTo(new DataOutputStream(bytes)); require(bytes.size() == 12, "Disabled assertion no longer writes first triangle"); }
            if (length == 0) rejects(Face.class.desiredAssertionStatus() ? AssertionError.class : ArrayIndexOutOfBoundsException.class, () -> malformed.serializeTo(null));
        }
    }
    private static void distinct(List<String> records) throws Exception {
        for (int size : new int[] {0, 3, 18, 96, 4096}) {
            RawMesh mesh = new RawMesh(new MaterialProp());
            for (int i = 0; i < size; i++) {
                Vertex vertex = new Vertex(new Vector3f(i % 9, i % 3, -0f), new Vector3f(0f, 1f, 0f));
                vertex.u = (i & 1) == 0 ? 0f : -0f; vertex.v = Float.intBitsToFloat(0x7fa12345); vertex.color = i % 2; vertex.light = 777;
                mesh.vertices.add(vertex);
            }
            for (int i = 2; i < size; i++) { mesh.faces.add(new Face(new int[] {i - 2, i - 1, i})); if (i % 3 == 0) mesh.faces.add(new Face(new int[] {i - 2, i - 1, i})); }
            List<Vertex> liveVertices = mesh.vertices; List<Face> liveFaces = mesh.faces;
            mesh.distinct(); require(mesh.vertices == liveVertices && mesh.faces == liveFaces, "Deduplication replaced public lists");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            for (Vertex vertex : mesh.vertices) { vertex.serializeTo(out); out.writeInt(vertex.color); out.writeInt(vertex.light); }
            for (Face face : mesh.faces) face.serializeTo(out);
            records.add("distinct-" + size + "\t" + mesh.vertices.size() + ":" + mesh.faces.size() + ":" + HexFormat.of().formatHex(bytes.toByteArray()));
        }
    }
    private static void callbacks(List<String> records) {
        Vertex vertex = new Vertex(); vertex.u = 0.25f; vertex.v = -0f; vertex.color = 1000; vertex.light = 2000;
        Vector3f normal = new CallbackVector("normal", 701, () -> {});
        vertex.normal = normal;
        vertex.position = new CallbackVector("position", 401, () -> { vertex.normal = null; vertex.u = 999f; vertex.v = 999f; vertex.color = 0; vertex.light = 0; });
        int expected = 1;
        for (int part : new int[] {401, 701, Float.hashCode(0.25f), Float.hashCode(-0f), 1000, 2000}) expected = 31 * expected + part;
        EVENTS.clear(); int actual = vertex.hashCode();
        require(actual == expected && EVENTS.equals(List.of("position", "normal")), "Hash failed to snapshot all fields before virtual callbacks");
        records.add("hash-callback\t" + actual + ":" + EVENTS + ":" + vertex.color);
        Vertex left = new Vertex(new Vector3f(0f, 0f, 0f)), right = left.copy();
        left.position = new Vector3f(0f, 0f, 0f) { @Override public boolean equals(Object other) { left.normal = null; return true; } };
        rejects(NullPointerException.class, () -> left.equals(right));
        left.color = 5; require(!left.equals(right), "Scalar short-circuit moved after vector comparison");
    }
    public static final class CallbackVector extends Vector3f {
        final String label; final int hash; final Runnable callback;
        CallbackVector(String label, int hash, Runnable callback) { super(0f, 0f, 0f); this.label = label; this.hash = hash; this.callback = callback; }
        @Override public int hashCode() { EVENTS.add(label); callback.run(); return hash; }
    }
    public static final class VertexChild extends Vertex {}
    public static final class FaceChild extends Face { FaceChild(int[] vertices) { super(vertices); } }
    private static DataInputStream input(byte[] bytes) { return new DataInputStream(new ByteArrayInputStream(bytes)); }
    private static byte[] write(Writer writer) throws Exception { ByteArrayOutputStream bytes = new ByteArrayOutputStream(); writer.write(new DataOutputStream(bytes)); return bytes.toByteArray(); }
    @FunctionalInterface private interface Writer { void write(DataOutputStream output) throws Exception; }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void rejects(Class<? extends Throwable> expected, Action action) {
        assertions++; Throwable caught = null;
        try { action.run(); } catch (Throwable error) { caught = error; }
        if (caught == null || caught.getClass() != expected) throw new AssertionError("Expected " + expected + ", got " + caught, caught);
    }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
