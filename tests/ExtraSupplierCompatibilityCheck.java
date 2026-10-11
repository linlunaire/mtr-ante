package cn.zbx1425.mtrsteamloco.compatibility;

import cn.zbx1425.mtrsteamloco.data.*;
import mtr.data.*;
import net.minecraft.core.BlockPos;
import sun.misc.Unsafe;
import java.nio.file.*;
import java.util.*;

/** Real static extension helpers with controlled rail/train data, callable from Java. */
public final class ExtraSupplierCompatibilityCheck {
    public static void main(String[] args) throws Exception {
        Path source = Path.of(args[1]).toRealPath();
        for (Class<?> type : List.of(RailExtraSupplier.class, TrainExtraSupplier.class, RailAngleExtra.class, RailActionsModuleExtraSupplier.class, VehicleRidingClientExtraSupplier.class)) {
            if (!Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(source)) throw new AssertionError("Wrong interface source " + type);
            if (Arrays.stream(type.getDeclaredAnnotations()).anyMatch(annotation -> annotation.annotationType().getName().equals("kotlin.Metadata")) != Files.isDirectory(source)) throw new AssertionError("Wrong interface language " + type);
        }
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true); Unsafe unsafe = (Unsafe) unsafeField.get(null);
        List<String> records = new ArrayList<>();
        RailProbe rail = (RailProbe) unsafe.allocateInstance(RailProbe.class);
        Random random = new Random(0x26233);
        for (int i = 0; i < 256; i++) {
            rail.length = random.nextDouble() * 400; rail.height = random.nextInt(); rail.reversed = (i & 1) == 0;
            rail.angles = new HashMap<>();
            for (int j = 0; j < i % 16; j++) rail.angles.put(random.nextDouble() * 500 - 100, random.nextFloat() * 6 - 3);
            double position = random.nextDouble() * 700 - 200;
            records.add("rail:" + i + "\t" + bits(RailExtraSupplier.getRollAngle(rail, position)) + "," + bits(RailExtraSupplier.getVTheta(rail, random.nextDouble() * 100)));
        }
        for (Map<Double, Float> map : List.of(Map.<Double, Float>of(), Map.of(0D, 2F), Map.of(-0D, -0F, 0D, 0F), Map.of(Double.NEGATIVE_INFINITY, 1F, Double.POSITIVE_INFINITY, 2F), Map.of(Double.NaN, 4F, 5D, -3F))) {
            rail.angles = new HashMap<>(map); rail.length = 20;
            for (boolean reversed : new boolean[]{false, true}) {
                rail.reversed = reversed;
                for (double value : new double[]{Double.NEGATIVE_INFINITY, -0D, 0D, 3D, 20D, Double.POSITIVE_INFINITY, Double.NaN}) records.add("rail-edge\t" + bits(RailExtraSupplier.getRollAngle(rail, value)));
            }
        }
        rail.angles = null; fails(() -> RailExtraSupplier.getRollAngle(rail, 0));
        rail.angles = new HashMap<>(); rail.angles.put(1D, null); fails(() -> RailExtraSupplier.getRollAngle(rail, 0));
        rail.angles.clear(); rail.angles.put(null, 1F); fails(() -> RailExtraSupplier.getRollAngle(rail, 0));
        fails(() -> RailExtraSupplier.getRollAngle(null, 0)); fails(() -> RailExtraSupplier.getVTheta(null, 0));
        TrainProbe train = (TrainProbe) unsafe.allocateInstance(TrainProbe.class); train.calls = new ArrayList<>();
        for (int i = 0; i < 512; i++) {
            set(train, "trainCars", random.nextInt()); set(train, "spacing", random.nextInt()); set(train, "reversed", (i & 1) == 0); set(train, "railProgress", random.nextDouble() * 1E12);
            train.calls.clear(); float result = TrainExtraSupplier.getRollAngleAt(train, random.nextInt());
            if (train.calls.size() != 2) throw new AssertionError("Train helper callback count changed");
            records.add("train:" + i + "\t" + train.calls.stream().map(Double::toHexString).toList() + ":" + bits(result));
        }
        fails(() -> TrainExtraSupplier.getRollAngleAt(null, 0));
        String actual = String.join("\n", records) + "\n";
        if (args.length == 3 && args[2].equals("--record")) { if (Files.isDirectory(source)) throw new AssertionError("Only record Java baseline"); Files.writeString(Path.of(args[0]), actual); }
        else if (!Files.readString(Path.of(args[0])).replace("\r\n", "\n").equals(actual)) throw new AssertionError("Extension-helper Java golden differs");
        System.out.println("PASS: " + records.size() + " extension-helper records, 512 ordered train axle queries, integer overflow, roll-map interpolation and nullable failures");
    }
    private static int bits(float value) { return Float.floatToIntBits(value); }
    private static void set(Train train, String name, Object value) throws Exception { var field = Train.class.getDeclaredField(name); field.setAccessible(true); field.set(train, value); }
    private static void fails(Runnable action) { try { action.run(); } catch (NullPointerException expected) { return; } throw new AssertionError("Expected null failure"); }
    public static final class TrainProbe extends TrainServer implements TrainExtraSupplier {
        List<Double> calls;
        private TrainProbe() { super(1, 1, 1F, "train", "train", 1, List.of(), List.of(), 0, 0, .01F, List.of(), false, 1, 1); }
        @Override public float getRollAngleAt(double value) { calls.add(value); return (float) Math.sin(value); }
        public Map<String, String> getCustomConfigs() { return null; }
        public void setCustomConfigs(Map<String, String> value) { }
        public boolean isConfigsChanged() { return false; }
        public void isConfigsChanged(boolean value) { }
        public Map<String, ConfigResponder> getConfigResponders() { return null; }
        public void setConfigResponders(Map<String, ConfigResponder> value) { }
    }
    public static final class RailProbe extends Rail implements RailExtraSupplier {
        double length; int height; boolean reversed; Map<Double, Float> angles;
        private RailProbe() { super(Map.of()); }
        @Override public double getLength() { return length; }
        public int getHeight() { return height; }
        public boolean getRenderReversed() { return reversed; }
        public Map<Double, Float> getRollAngleMap() { return angles; }
        public void setRollAngleMap(Map<Double, Float> value) { angles = value; }
        public String getModelKey() { return null; }
        public void setModelKey(String value) { }
        public void setRenderReversed(boolean value) { reversed = value; }
        public float getVerticalCurveRadius() { return 0; }
        public void setVerticalCurveRadius(float value) { }
        public Map<String, String> getCustomConfigs() { return null; }
        public void setCustomConfigs(Map<String, String> value) { }
        public Map<String, ConfigResponder> getCustomResponders() { return null; }
        public void setCustomResponders(Map<String, ConfigResponder> value) { }
        public void setOpeningDirection(int value) { }
        public void setOpeningDirectionRaw(int value) { }
        public int getOpeningDirection() { return 0; }
        public int getOpeningDirectionRaw() { return 0; }
        public void setRailType(RailType value) { }
        public void partialCopyFrom(Rail value) { }
        public void setRollingOffset(float value) { }
        public float getRollingOffset() { return 0; }
        public boolean isStraightOnly() { return false; }
        public void changePathMode(int value) { }
        public int getPathMode() { return 0; }
        public boolean isBetween(double x, double y, double z, double radius) { return false; }
        public Rail getTransposition(RailType value) { return null; }
        public void setBezier(BezierCurve value) { }
        public boolean couldSwitchModeTo(int value) { return false; }
        public void sendUpdateC2S() { }
        public BlockPos getPosStart() { return null; }
        public BlockPos getPosEnd() { return null; }
    }
}
