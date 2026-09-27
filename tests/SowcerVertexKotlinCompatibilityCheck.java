package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.vertex.VertAttrMapping;
import cn.zbx1425.sowcer.vertex.VertAttrSrc;
import cn.zbx1425.sowcer.vertex.VertAttrState;
import cn.zbx1425.sowcer.vertex.VertAttrType;
import net.minecraft.client.renderer.texture.OverlayTexture;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

/** Java caller regression for the real Kotlin vertex state and layout classes, without GPU ownership. */
public final class SowcerVertexKotlinCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            for (Class<?> type : new Class<?>[]{VertAttrMapping.class, VertAttrMapping.Builder.class,
                    VertAttrSrc.class, VertAttrState.class, VertAttrType.class}) {
                require(java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()
                        .equals(java.nio.file.Path.of(args[0]).toRealPath()), "Unexpected implementation source: " + type);
            }
        }
        javaFieldsAndEnums();
        mappingCopiesAndFailures();
        randomizedLayoutParity();
        stateAttributesAndPacking();
        nullableCallbacksAndCopy();
        boxedEquality();
        billboard();
        System.out.println("PASS: Kotlin vertex layer via Java, enum and field ABI, 1000 packed-layout comparisons, map snapshots, nullable fields/functions, signed packing, process flag transitions, deep vector/shallow callback copies and boxed-float equality (no GPU)");
    }

    private static void javaFieldsAndEnums() throws Exception {
        require(!Modifier.isFinal(VertAttrMapping.class.getModifiers()) && !Modifier.isFinal(VertAttrMapping.Builder.class.getModifiers()) && !Modifier.isFinal(VertAttrState.class.getModifiers()), "Former Java classes became final");
        require(!Modifier.isFinal(VertAttrSrc.class.getMethod("inVertBuf").getModifiers()), "Enum method gained final");
        require(VertAttrState.class.getField("color").getType() == Integer.class && VertAttrState.class.getField("texU").getType() == Float.class, "Nullable boxed field became primitive");
        require(VertAttrState.class.getField("useMatixProcess").getType() == boolean.class, "Primitive process flag changed");
        require(VertAttrMapping.class.getField("sources").getType() == HashMap.class && Modifier.isFinal(VertAttrMapping.class.getField("sources").getModifiers()), "Concrete mutable map field ABI changed");
        require(VertAttrState.class.getField("matrixModel").getGenericType().getTypeName().equals("java.util.function.Function<cn.zbx1425.sowcer.math.Matrix4f, cn.zbx1425.sowcer.math.Matrix4f>"), "Function generic signature changed");
        require(VertAttrMapping.class.getField("pointers").getGenericType().getTypeName().equals("java.util.HashMap<cn.zbx1425.sowcer.vertex.VertAttrType, java.lang.Integer>"), "Pointer map generic signature changed");
        require(Arrays.equals(Arrays.stream(VertAttrType.values()).map(Enum::name).toArray(String[]::new), new String[]{"POSITION", "COLOR", "UV_TEXTURE", "UV_OVERLAY", "UV_LIGHTMAP", "NORMAL", "MATRIX_MODEL"}), "Attribute enum order changed");
        require(Arrays.equals(Arrays.stream(VertAttrSrc.values()).map(Enum::name).toArray(String[]::new), new String[]{"GLOBAL", "VERTEX_BUF", "VERTEX_BUF_OR_GLOBAL", "INSTANCE_BUF", "INSTANCE_BUF_OR_GLOBAL"}), "Source enum order changed");
        int[] bytes = {12, 4, 8, 4, 4, 3, 64};
        int[] types = {0x1406, 0x1401, 0x1406, 0x1402, 0x1402, 0x1400, 0x1406};
        for (var attr : VertAttrType.values()) {
            require(attr.location == attr.ordinal() && attr.type == types[attr.ordinal()] && attr.byteSize == bytes[attr.ordinal()], "Packed attribute constants changed");
            require(attr.span == (attr == VertAttrType.MATRIX_MODEL ? 4 : 1), "Attribute span changed");
            require(attr.normalized == (attr == VertAttrType.COLOR || attr == VertAttrType.NORMAL), "Normalization flag changed");
            require(attr.iPointer == (attr == VertAttrType.UV_OVERLAY || attr == VertAttrType.UV_LIGHTMAP), "Integer-pointer flag changed");
            require(VertAttrType.valueOf(attr.name()) == attr, "Java valueOf changed");
        }
        for (var src : VertAttrSrc.values()) {
            require(src.inVertBuf() == (src == VertAttrSrc.VERTEX_BUF || src == VertAttrSrc.VERTEX_BUF_OR_GLOBAL), "Vertex-buffer selection changed");
            require(src.isToggleable() == (src == VertAttrSrc.VERTEX_BUF_OR_GLOBAL || src == VertAttrSrc.INSTANCE_BUF_OR_GLOBAL), "Toggleable selection changed");
        }
        var values = VertAttrType.values(); values[0] = null;
        require(VertAttrType.values()[0] == VertAttrType.POSITION, "values() stopped returning an independent array");
    }

    private static void mappingCopiesAndFailures() {
        var builder = allSources(VertAttrSrc.GLOBAL);
        require(builder.set(VertAttrType.NORMAL, VertAttrSrc.VERTEX_BUF) == builder, "Builder chaining changed");
        builder.set(null, null);
        var first = builder.build();
        require(first.sources.containsKey(null) && first.sources.get(null) == null, "Builder stopped accepting HashMap null key/value");
        require(first.strideVertex == 4 && first.paddingVertex == 1 && first.strideInstance == 0 && first.paddingInstance == 0, "Normal-only vertex alignment");
        require(first.pointers.get(VertAttrType.NORMAL) == 0 && first.pointers.size() == 1, "Global attributes gained offsets");
        builder.set(VertAttrType.NORMAL, VertAttrSrc.INSTANCE_BUF);
        var second = builder.build();
        require(first.sources.get(VertAttrType.NORMAL) == VertAttrSrc.VERTEX_BUF, "Build retained mutable builder map");
        require(second.strideInstance == 4 && second.paddingInstance == 1, "Normal-only instance alignment");
        first.sources.put(VertAttrType.NORMAL, null);
        first.pointers.put(null, null);
        require(second.sources.get(VertAttrType.NORMAL) == VertAttrSrc.INSTANCE_BUF && first.pointers.containsKey(null), "Public map mutation contract changed");
        expectNullPointer(() -> new VertAttrMapping.Builder().build(), "Missing sources silently defaulted");
        expectNullPointer(() -> allSources(VertAttrSrc.GLOBAL).set(VertAttrType.POSITION, null).build(), "Explicit null source silently defaulted");
        var full = allSources(VertAttrSrc.VERTEX_BUF).build();
        require(full.strideVertex == 100 && full.paddingVertex == 1, "Full packed layout changed");
    }

    private static void randomizedLayoutParity() {
        var random = new Random(262);
        var sources = VertAttrSrc.values();
        for (int iteration = 0; iteration < 1000; iteration++) {
            var builder = new VertAttrMapping.Builder();
            var expectedOffsets = new HashMap<VertAttrType, Integer>();
            int vertex = 0, instance = 0;
            for (var type : VertAttrType.values()) {
                var source = sources[random.nextInt(sources.length)];
                builder.set(type, source);
                switch (source) {
                    case VERTEX_BUF, VERTEX_BUF_OR_GLOBAL -> { expectedOffsets.put(type, vertex); vertex += type.byteSize; }
                    case INSTANCE_BUF, INSTANCE_BUF_OR_GLOBAL -> { expectedOffsets.put(type, instance); instance += type.byteSize; }
                    default -> { }
                }
            }
            int vertexPadding = vertex % 4 == 0 ? 0 : 4 - vertex % 4;
            int instancePadding = instance % 4 == 0 ? 0 : 4 - instance % 4;
            var mapping = builder.build();
            require(mapping.pointers.equals(expectedOffsets), "Mixed layout pointer order changed");
            require(mapping.strideVertex == vertex + vertexPadding && mapping.paddingVertex == vertexPadding, "Mixed vertex stride/padding changed");
            require(mapping.strideInstance == instance + instancePadding && mapping.paddingInstance == instancePadding, "Mixed instance stride/padding changed");
        }
    }

    private static void stateAttributesAndPacking() {
        var state = new VertAttrState();
        for (var type : VertAttrType.values()) require(!state.hasAttr(type), "Unset attribute became present");
        require(state.setPosition(new Vector3f(1F, 2F, 3F)) == state && state.setNormal(new Vector3f(0F, 1F, 0F)) == state, "Attribute chaining changed");
        state.setColor(0x1122, -1, 0x3333, 0x4444);
        require(state.color == (0x1122 << 24 | -1 << 16 | 0x3333 << 8 | 0x4444), "Color packing introduced masks or clamping");
        state.setColor(0x80123456); require(state.color == 0x80123456, "Packed color changed");
        state.setTextureUV(0.25F, 0.75F);
        state.setLightmapUV((short) 1, (short) -1); require(state.lightmapUV == -1, "Negative low short stopped sign-extending");
        state.setLightmapUV((short) -1, (short) 1); require(state.lightmapUV == 0xFFFF0001, "Negative high short changed");
        state.setLightmapUV(0x12345678); require(state.lightmapUV == 0x12345678, "Packed lightmap changed");
        state.setOverlayUV(0x1234ABCD); require(state.overlayUV == (0x1234ABCD >>> 16 | ((short) 0x1234ABCD << 16)), "Overlay word exchange changed");
        state.setOverlayUVNoOverlay(); require(state.overlayUV == (OverlayTexture.NO_OVERLAY >>> 16 | ((short) OverlayTexture.NO_OVERLAY << 16)), "No-overlay constant changed");
        state.setModelMatrix(new Matrix4f());
        for (var type : VertAttrType.values()) require(state.hasAttr(type), "Set attribute remains absent");
        state.texV = null; require(!state.hasAttr(VertAttrType.UV_TEXTURE), "Partial UV became present"); state.texV = 0.75F;
        for (var type : VertAttrType.values()) { state.clearAttr(type); require(!state.hasAttr(type), "clearAttr retained state"); }
        require(state.texU == null && state.texV == null, "Texture clear retained half the UV");
        expectNullPointer(() -> state.hasAttr(null), "hasAttr(null) stopped rejecting invalid enum");
        expectNullPointer(() -> state.clearAttr(null), "clearAttr(null) stopped rejecting invalid enum");
    }

    private static void nullableCallbacksAndCopy() {
        var state = new VertAttrState().setPosition(new Vector3f(1F, 2F, 3F)).setNormal(new Vector3f(0F, 1F, 0F));
        Function<Matrix4f, Matrix4f> callback = matrix -> matrix;
        state.setMatixProcess(callback);
        require(state.matrixModel == callback && state.useMatixProcess() && state.matrixModel.apply(null) == null, "Nullable Java Function contract changed");
        state.setModelMatrix(null);
        require(state.matrixModel != null && state.matrixModel.apply(null) == null && state.useMatixProcess(), "Constant null matrix or process-flag preservation changed");
        state.clearAttr(VertAttrType.MATRIX_MODEL);
        require(!state.hasAttr(VertAttrType.MATRIX_MODEL) && state.useMatixProcess(), "clearAttr reset the independent process flag");
        state.setMatixProcess(null);
        require(state.matrixModel == null && !state.useMatixProcess(), "Null process did not clear flag");
        state.setMatixProcess(callback).setColor(123).setTextureUV(0.25F, 0.5F).setOverlayUV(1234).setLightmapUV(5678);
        var copy = state.copy();
        require(copy.equals(state) && copy.hashCode() == state.hashCode(), "Copy value equality changed");
        require(copy.position != state.position && copy.normal != state.normal && copy.matrixModel == state.matrixModel, "Deep vector/shallow callback copy contract changed");
        state.position.add(9F, 0F, 0F); state.normal.mul(0F);
        require(copy.position.x() == 1F && copy.normal.y() == 1F, "Copied vectors remained aliased");
        require(state.setPosition(null) == state && state.position == null && state.setNormal(null).normal == null, "Null attribute setters changed");
        require(!new VertAttrState().equals(new StateSubclass()), "Equality stopped checking exact runtime class");
    }

    private static void boxedEquality() {
        var first = new VertAttrState(); var second = new VertAttrState();
        first.texU = Float.NaN; second.texU = Float.intBitsToFloat(0x7FC00001);
        require(first.equals(second) && first.hashCode() == second.hashCode(), "Boxed Float NaN equality changed to IEEE equality");
        first.texU = 0F; second.texU = -0F;
        require(!first.equals(second), "Boxed signed-zero equality changed");
        first.setColor(123).setTextureUV(0.25F, 0.75F).setLightmapUV(56).setOverlayUV(78);
        int expected = Objects.hash(first.position, first.color, first.texU, first.texV, first.overlayUV, first.lightmapUV, first.normal, first.matrixModel, first.useMatixProcess);
        require(first.hashCode() == expected, "Hash field order changed");
    }

    private static void billboard() {
        var matrix = new Matrix4f(new org.joml.Matrix4f().translation(1, 2, 3).rotateXYZ(0.1F, 0.2F, 0.3F).scale(4F));
        var before = matrix.copy();
        var billboard = VertAttrState.BILLBOARD.apply(matrix);
        require(matrix.equals(before) && billboard != matrix, "Billboard mutated or returned input storage");
        require(billboard.equals(Matrix4f.translation(1F, 2F, 3F)), "Billboard retained rotation or scale");
    }

    private static VertAttrMapping.Builder allSources(VertAttrSrc source) {
        var builder = new VertAttrMapping.Builder();
        for (var type : VertAttrType.values()) builder.set(type, source);
        return builder;
    }
    private static final class StateSubclass extends VertAttrState { }
    private static void expectNullPointer(Runnable action, String message) {
        try { action.run(); throw new AssertionError(message); } catch (NullPointerException expected) { }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
