package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.sowcer.math.Matrices;
import cn.zbx1425.sowcer.math.Matrix3f;
import cn.zbx1425.sowcer.math.Matrix4f;
import cn.zbx1425.sowcer.math.Pose;
import cn.zbx1425.sowcer.math.PoseStack;
import cn.zbx1425.sowcer.math.PoseStackUtil;
import cn.zbx1425.sowcer.math.Quaternionf;
import cn.zbx1425.sowcer.math.Vector3f;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Modifier;
import java.nio.FloatBuffer;
import java.util.EmptyStackException;
import java.util.Random;

/** Real Java callers verify the migrated Kotlin math layer without Minecraft startup or a GPU. */
public final class SowcerMathKotlinCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            for (Class<?> type : new Class<?>[]{Vector3f.class, Matrix3f.class, Matrix4f.class, Quaternionf.class,
                    Matrices.class, Pose.class, PoseStack.class, PoseStackUtil.class, cn.zbx1425.sowcer.math.Posture.class}) {
                require(java.nio.file.Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath()
                        .equals(java.nio.file.Path.of(args[0]).toRealPath()), "Unexpected implementation source: " + type);
            }
        }
        javaSurface();
        vectorsAndProtectedStorage();
        matricesAndBuffers();
        quaternions();
        postureOwnership();
        eulerFormulaParity();
        nullableAliases();
        System.out.println("PASS: Kotlin math via Java, mutable static fields, subclass/void/static ABI, direct protected storage, JOML aliases/copies, buffer positions, posture ownership, nullable aliases and 1000 Euler formula comparisons");
    }

    private static void javaSurface() throws Exception {
        for (var type : new Class<?>[]{Vector3f.class, Matrix3f.class, Matrix4f.class, Quaternionf.class, Matrices.class, Pose.class, PoseStack.class, PoseStackUtil.class}) {
            require(!Modifier.isFinal(type.getModifiers()), "Former Java class became final: " + type);
        }
        for (String field : new String[]{"ZERO", "XP", "YP", "ZP"}) {
            int modifiers = Vector3f.class.getField(field).getModifiers();
            require(Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers) && Modifier.isFinal(modifiers), "Vector constant lost Java field ABI");
        }
        require(Matrix4f.class.getField("IDENTITY").getType() == Matrix4f.class, "Identity field ABI");
        require(Modifier.isProtected(Vector3f.class.getDeclaredField("impl").getModifiers()), "Vector backing field no longer protected");
        require(Modifier.isProtected(Matrix4f.class.getDeclaredField("impl").getModifiers()), "Matrix backing field no longer protected");
        require(Modifier.isStatic(Matrix4f.class.getMethod("translation", float.class, float.class, float.class).getModifiers()), "Translation factory lost static ABI");
        require(!Modifier.isFinal(Matrix4f.class.getMethod("translation", float.class, float.class, float.class).getModifiers()), "Translation factory stopped allowing Java static hiding");
        require(StaticMatrixSubclass.translation(0F, 0F, 0F) instanceof StaticMatrixSubclass, "Java static factory hiding failed");
        require(Vector3f.class.getMethod("add", float.class, float.class, float.class).getReturnType() == void.class, "Vector mutator no longer returns void");
        require(Matrix4f.class.getMethod("rotateX", float.class).getReturnType() == void.class, "Matrix mutator no longer returns void");
        require(!Modifier.isFinal(Vector3f.class.getMethod("x").getModifiers()), "Vector accessor no longer overridable");
        require(Modifier.isStatic(PoseStackUtil.class.getMethod("rotX", com.mojang.blaze3d.vertex.PoseStack.class, float.class).getModifiers()), "Pose helper lost static ABI");
        require(!Modifier.isFinal(PoseStackUtil.class.getMethod("rotX", com.mojang.blaze3d.vertex.PoseStack.class, float.class).getModifiers()), "Pose helper stopped allowing Java static hiding");
        StaticPoseStackUtilSubclass.rotX(null, 0F);
    }

    private static void vectorsAndProtectedStorage() {
        var raw = new org.joml.Vector3f(1, 2, 3);
        var vector = new Vector3f(raw);
        require(vector.asMoj() == raw, "JOML vector constructor stopped aliasing");
        var copy = vector.copy();
        vector.add(2F, 3F, 4F);
        near(copy.x(), 1, "Vector copy alias");
        near(raw.x, 3, "Vector wrapper stopped mutating caller storage");
        vector.add(1D, 1D, 1D);
        vector.sub(new Vector3f(1F, 1F, 1F));
        vector.mul(2F, 3F, 4F);
        near(vector.x(), 6, "Vector operations");
        var direction = new Vector3f(1F, 0F, 0F);
        direction.rotDeg(Vector3f.ZP, 90F);
        near(direction.y(), 1, "Degree conversion");
        near(new Vector3f(0F, 0F, 0F).distance(new Vector3f(3F, 4F, 0F)), 5, "Vector distance");
        near(new Vector3f(0F, 0F, 0F).distanceSq(new Vector3f(3F, 4F, 0F)), 25, "Vector squared distance");
        require(new Vector3f(-0.1F, 1.9F, -1.1F).toBlockPos().equals(new BlockPos(-1, 1, -2)), "Block position flooring");
        require(new Vector3f(new Vec3(1, 2, 3)).toVec3().equals(new Vec3(1, 2, 3)), "Vec3 conversion");
        var subclass = new MisleadingVector();
        require(subclass.original().x == 1, "Protected field inaccessible to Java subclass");
        near(new Matrix4f().transform(subclass).x(), 1, "Matrix transform invoked overridden asMoj instead of protected storage");
        near(new Matrix3f().transform(subclass).x(), 1, "Normal transform invoked overridden asMoj instead of protected storage");
        require(!new Vector3f(1F, 2F, 3F).equals(subclass), "Vector equality stopped checking exact runtime class");
        near(Vector3f.ZERO.lengthSquared(), 0, "Shared constants were mutated");
    }

    private static void matricesAndBuffers() {
        var raw = new org.joml.Matrix4f().translation(2, 3, 4).rotateXYZ(0.2F, 0.3F, 0.4F);
        var matrix = new Matrix4f(raw);
        require(matrix.asMoj() == raw, "JOML matrix constructor stopped aliasing");
        var clone = matrix.copy();
        matrix.translate(5F, 6F, 7F);
        require(!matrix.equals(clone), "Matrix copy retained caller storage");
        require(clone.equals(new Matrix4f(clone)), "Matrix value equality");
        require(clone.hashCode() == new Matrix4f(clone).hashCode(), "Matrix hashCode");
        var source = new Vector3f(1F, 2F, 3F);
        var moved = Matrix4f.translation(4F, 5F, 6F).transform(source);
        near(moved.x(), 5, "Position transform");
        near(Matrix4f.translation(4F, 5F, 6F).transform3(source).x(), 1, "Direction gained translation");
        near(source.x(), 1, "Transform mutated input vector");
        FloatBuffer stored = FloatBuffer.allocate(20);
        stored.position(3);
        matrix.store(stored);
        require(stored.position() == 3, "Matrix store moved buffer position");
        float[] expected = raw.get(new float[16]);
        for (int i = 0; i < 16; i++) bits(stored.get(i), expected[i], "Column-major matrix store");
        FloatBuffer loaded = FloatBuffer.allocate(18);
        loaded.position(2); loaded.put(expected); loaded.position(2);
        var destination = new Matrix4f(); destination.load(loaded);
        require(loaded.position() == 18 && destination.equals(matrix), "Matrix load no longer consumes from current position");
        var raw3 = new org.joml.Matrix3f(raw);
        var normal = new Matrix3f(raw3);
        require(normal.asMoj() == raw3, "Normal matrix constructor stopped aliasing");
        FloatBuffer normalBuffer = FloatBuffer.allocate(11);
        normalBuffer.position(2); normal.store(normalBuffer);
        require(normalBuffer.position() == 2, "Normal matrix store moved position");
        float[] expected3 = raw3.get(new float[9]);
        for (int i = 0; i < 9; i++) bits(normalBuffer.get(i), expected3[i], "Normal matrix layout");
        normalBuffer.clear().position(2); normalBuffer.put(expected3).position(2);
        var normalCopy = new Matrix3f(); normalCopy.load(normalBuffer);
        require(normalBuffer.position() == 11 && normalCopy.asMoj().equals(raw3), "Normal matrix load semantics");
    }

    private static void quaternions() {
        var quaternion = new Quaternionf(Vector3f.YP, 0.5F);
        var original = new org.joml.Quaternionf(quaternion.asMoj());
        var copy = new Quaternionf(quaternion);
        require(quaternion.rotateX(0.2D) == quaternion && quaternion.rotateY(0.3F) == quaternion, "Quaternion chaining identity");
        require(copy.asMoj().equals(original), "Quaternion copy alias");
        quaternion.set(1F, 2F, 3F, 4F);
        quaternion.i(5F); quaternion.j(6F); quaternion.k(7F); quaternion.r(8F);
        require(quaternion.i() == 5F && quaternion.j() == 6F && quaternion.k() == 7F && quaternion.r() == 8F, "Quaternion component ABI");
    }

    private static void postureOwnership() {
        var matrix = Matrix4f.translation(1F, 2F, 3F);
        var normal = new Matrix3f();
        var pose = new Pose(matrix, normal);
        require(pose.pose() == matrix && pose.normal() == normal, "Explicit pose constructor stopped aliasing");
        var copy = new Pose(pose);
        matrix.translate(5F, 0F, 0F);
        near(copy.pose().getTranslationPart().x(), 1, "Pose copy alias");
        var converted = pose.asMoj();
        near(converted.pose().m30(), 6, "Minecraft pose conversion");
        near(new Pose(converted).pose().getTranslationPart().x(), 6, "Minecraft pose wrapper");
        var stack = new PoseStack(pose);
        require(stack.clear(), "Pose stack initial depth");
        stack.pushPose(); require(!stack.clear(), "Pose stack push"); stack.popPose();
        var matrices = new Matrices(matrix);
        matrix.translate(100F, 0F, 0F);
        near(matrices.last().getTranslationPart().x(), 6, "Matrices constructor did not copy");
        matrices.pushPose(); matrices.translate(1D, 0D, 0D);
        var matricesCopy = matrices.copy();
        matrices.popPose(); matrices.translate(2F, 0F, 0F);
        near(matricesCopy.last().getTranslationPart().x(), 7, "Matrices copy lost stack levels/independence");
        matrices.setIdentity(); near(matrices.last().getTranslationPart().x(), 0, "Matrices identity");
        matrices.popPose();
        try { matrices.last(); throw new AssertionError("Empty Java Stack behavior changed"); } catch (EmptyStackException expected) { }
    }

    private static void eulerFormulaParity() {
        var random = new Random(262);
        for (int iteration = 0; iteration < 1000; iteration++) {
            var matrix = new Matrix4f(new org.joml.Matrix4f().rotateXYZ(random.nextFloat(), random.nextFloat(), random.nextFloat()));
            float[] src = matrix.asMoj().get(new float[16]);
            var zyx = matrix.getEulerAnglesZYX();
            bits(zyx.x(), (float) Math.atan2(src[9], src[10]), "ZYX x");
            bits(zyx.y(), (float) Math.atan2(-src[8], Math.sqrt(1F - src[8] * src[8])), "ZYX y");
            bits(zyx.z(), (float) Math.atan2(src[4], src[0]), "ZYX z");
            var xyz = matrix.getEulerAnglesXYZ();
            bits(xyz.x(), (float) Math.atan2(-src[6], src[10]), "XYZ x");
            bits(xyz.y(), (float) Math.atan2(src[2], Math.sqrt(1F - src[2] * src[2])), "XYZ y");
            bits(xyz.z(), (float) Math.atan2(-src[1], src[0]), "XYZ z");
            var yxz = matrix.getEulerAnglesYXZ();
            bits(yxz.x(), (float) Math.atan2(-src[6], Math.sqrt(1F - src[6] * src[6])), "YXZ x");
            bits(yxz.y(), (float) Math.atan2(src[2], src[10]), "YXZ y");
            bits(yxz.z(), (float) Math.atan2(src[1], src[5]), "YXZ z");
        }
    }

    private static void nullableAliases() {
        require(new Vector3f((org.joml.Vector3f) null).asMoj() == null, "Nullable vector storage was rejected eagerly");
        require(new Matrix4f((org.joml.Matrix4f) null).asMoj() == null, "Nullable matrix storage was rejected eagerly");
        require(new Matrix3f((org.joml.Matrix3f) null).asMoj() == null, "Nullable normal storage was rejected eagerly");
        var pose = new Pose(null, null);
        require(pose.pose() == null && pose.normal() == null && pose.getAsMatrix4f() == null && pose.getAsMatrix3f() == null, "Nullable explicit pose changed");
        new PoseStack((com.mojang.blaze3d.vertex.PoseStack) null);
    }

    private static final class MisleadingVector extends Vector3f {
        MisleadingVector() { super(1F, 2F, 3F); }
        @Override public org.joml.Vector3f asMoj() { return new org.joml.Vector3f(100, 200, 300); }
        org.joml.Vector3f original() { return impl; }
    }

    private static final class StaticMatrixSubclass extends Matrix4f {
        public static Matrix4f translation(float x, float y, float z) { return new StaticMatrixSubclass(); }
    }

    private static final class StaticPoseStackUtilSubclass extends PoseStackUtil {
        public static void rotX(com.mojang.blaze3d.vertex.PoseStack matrices, float rad) { }
    }

    private static void bits(float actual, float expected, String message) {
        require(Float.floatToIntBits(actual) == Float.floatToIntBits(expected), message + ": " + actual + " != " + expected);
    }
    private static void near(float actual, float expected, String message) { require(Math.abs(actual - expected) < 0.00001F, message); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
