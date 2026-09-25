package cn.zbx1425.mtrsteamloco.compatibility;

import mtr.mappings.CompoundTagMapper;
import net.minecraft.nbt.CompoundTag;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Actual source hooks and MC 26.2 NBT defaults, without launching a game. */
public final class NbtSourceCompatibilityCheck {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Path.of(args.length == 0 ? "." : args[0]);
        Map<String, String> receivers = new LinkedHashMap<>();
        receivers.put("block/BlockDirectNode", "compoundTag");
        receivers.put("block/BlockEyeCandy", "compoundTag");
        receivers.put("gui/BrushEditRailScreen", "brushTag|railBrushProp");
        receivers.put("gui/CompoundCreatorScreen", "tag|tasksTag|task");
        receivers.put("gui/EyeCandyScreen", "");
        receivers.put("gui/RoutePathCreatorScreen", "tag");
        receivers.put("item/BlockItemDirectNode", "tag");
        receivers.put("item/BlockItemEyeCandy", "tag");
        receivers.put("item/CompoundCreator", "tag|tasksTag|taskTag|compoundTag");
        receivers.put("item/RailPathEditor", "tag");
        receivers.put("item/RoutePathCreator", "tag|itemTag|compoundTag");
        receivers.put("mixin/ItemNodeModifierBaseMixin", "compoundTag");
        receivers.put("mixin/ItemRailModifierMixin", "compoundTag");
        receivers.put("mixin/ItemRendererMixin", "et");
        receivers.put("mixin/ItemWithCreativeTabBaseMixin", "railBrushProp");
        receivers.put("mixin/TrainMixin", "compoundTag");
        receivers.put("scripting/util/JsonStringTagVisitor", "p_178166_");
        for (Map.Entry<String, String> entry : receivers.entrySet()) {
            String result = Files.readString(root.resolve("common/src/main/java/cn/zbx1425/mtrsteamloco/" + entry.getKey() + ".java"));
            if (!entry.getValue().isEmpty()) {
                Pattern oldGetter = Pattern.compile("(?<![\\w.$])(?:" + entry.getValue() + ")\\.(?:getInt|getLong|getFloat|getDouble|getBoolean|getString|getByteArray|getCompound|getAllKeys)\\(");
                require(!oldGetter.matcher(result).find(), "Legacy NBT getter survived: " + entry.getKey());
            }
            require(!Pattern.compile("ItemStackUtilities\\.getCustomData[^;\\n]+\\.getCompound\\(").matcher(result).find(), "Chained compound getter survived: " + entry.getKey());
            if (entry.getKey().equals("mixin/TrainMixin")) {
                require(result.contains("messagePackHelper.getString(\"custom_configs\")"), "MessagePack getter was changed");
            }
            if (entry.getKey().equals("gui/CompoundCreatorScreen")) {
                require(result.contains("for (Task task : tasks)"), "Business Task declaration was changed");
                require(result.contains("copy.get(i).task.toCompoundTag()"), "Entry.task was treated as NBT");
                require(result.contains("CompoundTagMapper.getString(task, Task.TAG_TYPE)"), "NBT task type was not read");
            }
        }

        CompoundTag tag = new CompoundTag();
        checkDefaults(tag, "missing");
        tag.put("wrong", new CompoundTag());
        checkDefaults(tag, "wrong");
        tag.putString("numericWrong", "42");
        require(CompoundTagMapper.getInt(tag, "numericWrong") == 0, "String was coerced into a number");
        tag.putInt("i", Integer.MIN_VALUE);
        tag.putLong("l", Long.MAX_VALUE);
        tag.putFloat("f", -1.25F);
        tag.putDouble("d", -123.25D);
        tag.putBoolean("b", true);
        tag.putString("s", "中文\n\"train\"");
        tag.putByteArray("bytes", new byte[]{-128, 0, 127});
        tag.putLongArray("longs", new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE});
        require(CompoundTagMapper.getInt(tag, "i") == Integer.MIN_VALUE, "Integer changed");
        require(CompoundTagMapper.getLong(tag, "l") == Long.MAX_VALUE, "Long changed");
        require(CompoundTagMapper.getFloat(tag, "f") == -1.25F, "Float changed");
        require(CompoundTagMapper.getDouble(tag, "d") == -123.25D, "Double changed");
        require(CompoundTagMapper.getBoolean(tag, "b"), "Boolean changed");
        require(CompoundTagMapper.getString(tag, "s").equals("中文\n\"train\""), "String changed");
        require(Arrays.equals(CompoundTagMapper.getByteArray(tag, "bytes"), new byte[]{-128, 0, 127}), "Byte array changed");
        require(Arrays.equals(CompoundTagMapper.getLongArray(tag, "longs"), new long[]{Long.MIN_VALUE, 0, Long.MAX_VALUE}), "Long array changed");
        tag.putByte("numeric", (byte) 7);
        require(CompoundTagMapper.getInt(tag, "numeric") == 7 && CompoundTagMapper.getDouble(tag, "numeric") == 7D, "NBT numeric conversion changed");
        CompoundTag child = new CompoundTag();
        child.putString("prefabId", "ante:example");
        tag.put("child", child);
        require(tag.getCompoundOrEmpty("child") == child, "Existing compound identity changed");
        require(tag.getCompoundOrEmpty("missing").isEmpty() && tag.getCompoundOrEmpty("s").isEmpty(), "Compound fallback changed");
        require(!tag.contains("missing"), "Reading a missing compound inserted it");
        require(child.keySet().equals(Set.of("prefabId")), "Compound keys changed");
        require(tag.getCompoundOrEmpty("child").copy() != child, "Explicit copy stopped isolating mutations");
        System.out.println("NBT source compatibility passed (" + checks + " checks; 17 actual source files).");
    }

    private static void checkDefaults(CompoundTag tag, String key) {
        require(CompoundTagMapper.getInt(tag, key) == 0, "Missing/wrong int default");
        require(CompoundTagMapper.getLong(tag, key) == 0L, "Missing/wrong long default");
        require(CompoundTagMapper.getFloat(tag, key) == 0F, "Missing/wrong float default");
        require(CompoundTagMapper.getDouble(tag, key) == 0D, "Missing/wrong double default");
        require(!CompoundTagMapper.getBoolean(tag, key), "Missing/wrong boolean default");
        require(CompoundTagMapper.getString(tag, key).isEmpty(), "Missing/wrong string default");
        require(CompoundTagMapper.getByteArray(tag, key).length == 0, "Missing/wrong byte array default");
        require(CompoundTagMapper.getLongArray(tag, key).length == 0, "Missing/wrong long array default");
    }

    private static void require(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
