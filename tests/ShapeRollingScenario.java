package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.data.Rolling;
import cn.zbx1425.mtrsteamloco.data.ShapeSerializer;
import cn.zbx1425.sowcer.math.Vector3f;
import cn.zbx1425.sowcer.math.Quaternionf;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.*;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public final class ShapeRollingScenario {
    private static final List<String> records = new ArrayList<>();
    private static int assertions;
    public static String run(boolean cache) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        if (cache) {
            String shape = "1,2,3,12,13,14/0,0,0,2,2,2";
            VoxelShape stored = ShapeSerializer.getShape(shape, 90);
            ShapeRollingCompatibilityCheck.parseCalls = 0;
            for (int i = 0; i < 1024; i++) require(ShapeSerializer.isValid(shape, 90), "Valid shape rejected");
            require(ShapeRollingCompatibilityCheck.parseCalls == 0, "Cached validation reparsed geometry " + ShapeRollingCompatibilityCheck.parseCalls + " times");
            require(ShapeSerializer.getShape(shape, 90) == stored, "Validation replaced cached shape identity");
            return "";
        }
        new ShapeSerializer(); new Rolling();
        for (String shape : Arrays.asList(null, "", "/", "//", "0,0,0,16,16,16", "1,2,3,10,11,12", "1,2,3,10,11,12/0,0,0,4,4,4/", "-2,0,-4,20,8,17", "0,0,0,0,0,0", " 1 , 2 , 3 , 10 , 11 , 12 ", "bad", "0,0,0,1,2", "0,0,0,1,2,3,", "0,0,0,1,2,3//0,0,0,2,3,4", "16,16,16,0,0,0", "NaN,0,0,1,2,3", "0,0,0,Infinity,2,3", "0,0,0,Infinity,2,3/0,0,0,16,16,16/bad", "NaN,0,0,1,2,3/0,0,0,16,16,16/bad")) {
            for (int angle : new int[]{-90, 0, 90, 180, 270, 360, 45}) {
                boolean valid = ShapeSerializer.isValid(shape, angle);
                String geometry;
                try { VoxelShape result = ShapeSerializer.getShape(shape, angle); geometry = describe(result); require(ShapeSerializer.getShape(shape, angle) == result, "Repeated shape query lost cache identity"); }
                catch (Exception error) { geometry = error.getClass().getName() + ":" + error.getMessage(); }
                records.add("shape:" + shape + ":" + angle + "\t" + valid + ":" + geometry);
            }
        }
        Rolling.setRotation(null); Rolling.update();
        Vector3f source = new Vector3f(2F, 3F, 4F);
        require(Rolling.applyRolling(source, 1.6F) == source && Rolling.applyRolling((Vector3f) null, 1F) == null, "Identity changed nullable/reference no-op");
        Rolling.applyRolling((PoseStack) null);
        Random random = new Random(0x26_2_2026);
        for (int i = 0; i < 256; i++) {
            float yaw = (random.nextFloat() - .5F) * 12, pitch = (random.nextFloat() - .5F) * 4, roll = (random.nextFloat() - .5F) * 6;
            Rolling.Rotation rotation = new Rolling.Rotation(1.2345678901, -2, 30, yaw, pitch, roll, (i & 1) != 0);
            Rolling.Rotation copy = rotation.copy();
            require(copy != rotation && copy.pos != rotation.pos && vector(copy.pos).equals(vector(rotation.pos)), "Rotation copy aliases position");
            Rolling.setRotation(rotation); Rolling.update();
            for (boolean enabled : new boolean[]{false, true}) {
                ShapeRollingCompatibilityCheck.enableRolling = enabled;
                Vector3f result = Rolling.applyRolling(source, 1.625F);
                require(result != source && vector(source).equals(vector(new Vector3f(2F, 3F, 4F))), "Rolling mutated source position");
                StringBuilder values = new StringBuilder(vector(result));
                for (boolean reversed : new boolean[]{false, true}) values.append('|').append(quaternion(Rolling.getRollQuaternion(reversed))).append('|').append(quaternion(Rolling._getRollQuaternion(reversed)));
                values.append('|').append(quaternion(Rolling.getRollQuaternion()));
                PoseStack poses = new PoseStack(); Rolling.applyRolling(poses);
                float[] matrix = new float[16]; poses.last().pose().get(matrix); for (float value : matrix) values.append(':').append(Float.floatToRawIntBits(value));
                records.add("rolling:" + i + ":" + enabled + "\t" + digest(values.toString()));
            }
            Rolling.update(); require(Rolling.applyRolling(source, 1F) == source, "Pending rotation not consumed once");
        }
        for (float value : new float[]{0F, -0F, Float.NaN, Float.POSITIVE_INFINITY, Float.MIN_VALUE}) {
            Rolling.Rotation rotation = new Rolling.Rotation(0, 0, 0, value, 0, 0, false);
            records.add("identity:" + Float.floatToRawIntBits(value) + "\t" + rotation.isIdentity());
        }
        Rolling.setRotation(new Rolling.Rotation(0, 0, 0, 1, 2, 3, false)); Rolling.setRotation(null); Rolling.update();
        require(Rolling.applyRolling(source, 1F) == source, "Null pending rotation did not cancel");
        ShapeRollingCompatibilityCheck.enableRolling = false;
        Rolling.setRotation(new Rolling.Rotation(0, 0, 0, 1, 2, 3, false)); Rolling.update(); Rolling.applyRolling((PoseStack) null);
        try { Rolling.applyRolling((Vector3f) null, 1F); throw new AssertionError("Nonidentity nullable position accepted"); } catch (NullPointerException expected) { }
        System.out.println("Shape/rolling assertions: " + assertions + ", records: " + records.size());
        return String.join("\n", records) + "\n";
    }
    private static String describe(VoxelShape shape) { StringBuilder result = new StringBuilder(); shape.toAabbs().forEach(box -> result.append(Double.toHexString(box.minX)).append(',').append(Double.toHexString(box.minY)).append(',').append(Double.toHexString(box.minZ)).append(',').append(Double.toHexString(box.maxX)).append(',').append(Double.toHexString(box.maxY)).append(',').append(Double.toHexString(box.maxZ)).append(';')); return result.toString(); }
    private static String vector(Vector3f value) { return Float.floatToRawIntBits(value.x()) + "," + Float.floatToRawIntBits(value.y()) + "," + Float.floatToRawIntBits(value.z()); }
    private static String quaternion(Quaternionf value) { var q = value.asMoj(); return Float.floatToRawIntBits(q.x()) + "," + Float.floatToRawIntBits(q.y()) + "," + Float.floatToRawIntBits(q.z()) + "," + Float.floatToRawIntBits(q.w()); }
    private static String digest(String text) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
