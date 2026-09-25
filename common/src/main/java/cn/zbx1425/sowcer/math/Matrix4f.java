package cn.zbx1425.sowcer.math;

import org.apache.commons.lang3.builder.EqualsBuilder;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
public class Matrix4f implements Posture {


    protected final org.joml.Matrix4f impl;
    public Matrix4f() {
        this.impl = new org.joml.Matrix4f();
        this.impl.identity();
    }

    public Matrix4f(org.joml.Matrix4f moj) {
        this.impl = moj;
    }

    public Matrix4f(Matrix3f mat3) {
        this.impl = new org.joml.Matrix4f(mat3.asMoj());
    } 

    public Matrix4f(Matrix4f other) {
        this.impl = new org.joml.Matrix4f(other.impl);
    }

    public Matrix4f copy() {
        return new Matrix4f(this);
    }

    public org.joml.Matrix4f asMoj() {
        return impl;
    }

    public static Matrix4f translation(float x, float y, float z) {
        Matrix4f result = new Matrix4f();
        result.impl.translation(x, y, z);
        return result;
    }

    public void multiply(Matrix4f other) {
        impl.mul(other.impl);
    }

    public void multiply(Quaternionf q) {
        impl.rotate(q.asMoj());
    }

    public void store(FloatBuffer buffer) {
        buffer
                .put(0,  impl.m00())
                .put(1,  impl.m01())
                .put(2,  impl.m02())
                .put(3,  impl.m03())
                .put(4,  impl.m10())
                .put(5,  impl.m11())
                .put(6,  impl.m12())
                .put(7,  impl.m13())
                .put(8,  impl.m20())
                .put(9,  impl.m21())
                .put(10, impl.m22())
                .put(11, impl.m23())
                .put(12, impl.m30())
                .put(13, impl.m31())
                .put(14, impl.m32())
                .put(15, impl.m33());
    }

    public void load(FloatBuffer buffer) {
        float[] bufferValues = new float[16];
        buffer.get(bufferValues);
        impl.set(bufferValues);
    }

    public void rotateX(float rad) {
        impl.rotateX(rad);
    }

    public void rotateY(float rad) {
        impl.rotateY(rad);
    }

    public void rotateZ(float rad) {
        impl.rotateZ(rad);
    }

    public void rotate(Vector3f axis, float rad) {
        impl.rotate(rad, axis.impl);
    }

    public void translate(float x, float y, float z) {
        impl.translate(x, y, z);
    }

    public Vector3f transform(Vector3f src) {
        org.joml.Vector3f srcCpy = new org.joml.Vector3f(src.impl);
        return new Vector3f(impl.transformPosition(srcCpy));
    }

    public Vector3f transform3(Vector3f src) {
        org.joml.Vector3f srcCpy = new org.joml.Vector3f(src.impl);
        return new Vector3f(impl.transformDirection(srcCpy));
    }

    public org.joml.Matrix3f getRotationPart() {
        org.joml.Matrix3f result = new org.joml.Matrix3f();
        return impl.get3x3(result);
    }

    public Vector3f getTranslationPart() {
       org.joml.Vector3f result = new org.joml.Vector3f();
       return new Vector3f(impl.getTranslation(result));
    }

    public void scale(float x, float y, float z) {
        impl.scale(x, y, z);
    }


    public void mul(Matrix4f other) {
        multiply(other);
    }

    public void scale(float factor) {
        scale(factor, factor, factor);
    }

    public void translate(Vector3f vec) {
        translate(vec.x(), vec.y(), vec.z());
    }

    public void rotateXYZ(float x, float y, float z) {
        rotateX(x);
        rotateY(y);
        rotateZ(z);
    }

    public void rotateXYZ(Vector3f vec) {
        rotateXYZ(vec.x(), vec.y(), vec.z());
    }

    public void rotateYXZ(float y, float x, float z) {
        rotateY(y);
        rotateX(x);
        rotateZ(z);
    }

    public void rotateYXZ(Vector3f vec) {
        rotateYXZ(vec.y(), vec.x(), vec.z());
    }

    public void rotateZYX(float z, float y, float x) {
        rotateZ(z);
        rotateY(y);
        rotateX(x);
    }

    public void rotateZYX(Vector3f vec) {
        rotateZYX(vec.z(), vec.y(), vec.x());
    }

    public Vector3f getEulerAnglesZYX() {
        float[] src = new float[16];
        FloatBuffer srcFloatBuffer = FloatBuffer.wrap(src);
        store(srcFloatBuffer);

        float x = (float) Math.atan2(src[index(1, 2)], src[index(2, 2)]);
        float y = (float) Math.atan2(-src[index(0, 2)], Math.sqrt(1.0F - src[index(0, 2)] * src[index(0, 2)]));
        float z = (float) Math.atan2(src[index(0, 1)], src[index(0, 0)]);
        return new Vector3f(x, y, z);
    }

    public Vector3f getEulerAnglesXYZ() {
        float[] src = new float[16];
        FloatBuffer srcFloatBuffer = FloatBuffer.wrap(src);
        store(srcFloatBuffer);

        float x = (float) Math.atan2(-src[index(2, 1)], src[index(2, 2)]);
        float y = (float) Math.atan2(src[index(2, 0)], Math.sqrt(1.0F - src[index(2, 0)] * src[index(2, 0)]));
        float z = (float) Math.atan2(-src[index(1, 0)], src[index(0, 0)]);
        return new Vector3f(x, y, z);
    }

    public Vector3f getEulerAnglesYXZ() {
        float[] src = new float[16];
        FloatBuffer srcFloatBuffer = FloatBuffer.wrap(src);
        store(srcFloatBuffer);

        float x = (float) Math.atan2(-src[index(2, 1)], Math.sqrt(1.0F - src[index(2, 1)] * src[index(2, 1)]));
        float y = (float) Math.atan2(src[index(2, 0)], src[index(2, 2)]);
        float z = (float) Math.atan2(src[index(1, 0)], src[index(1, 1)]);
        return new Vector3f(x, y, z);
    }

    int index(int p_27642_, int p_27643_) {
      return p_27643_ * 4 + p_27642_;
    }

    @Override
    public int hashCode() {
        return impl.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        Matrix4f matrix4f = (Matrix4f) o;

        return impl.equals(matrix4f.impl);
    }

    public String toString() {
        float[] src = new float[16];
        FloatBuffer srcFloatBuffer = FloatBuffer.wrap(src);
        store(srcFloatBuffer);
        StringBuilder sb = new StringBuilder();
        sb.append("Matrix4f[\n");
        for (int i = 0; i < 4; i++) {
            sb.append("  ");
            for (int j = 0; j < 4; j++) {
                sb.append(src[i * 4 + j]);
                sb.append(", ");
            }
            sb.append("\n");
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public Matrix4f getAsMatrix4f() {
        return this;
    }

    @Override
    public Matrix3f getAsMatrix3f() {
        return new Matrix3f(this);
    }

    @Override
    public Pose getAsPose() {
        return new Pose(this);
    }

    @Override
    public PoseStack getAsPoseStack() {
        return new PoseStack(getAsPose());
    }

    @Override
    public Matrices getAsMatrices() {
        return new Matrices(this);
    }

    public static final Matrix4f IDENTITY = new Matrix4f();
}