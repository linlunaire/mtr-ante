package cn.zbx1425.mtrsteamloco.scripting.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.Strictness;
import net.minecraft.nbt.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Exercises the actual 26.2 NBT visitor without starting Minecraft or a script engine. */
public final class JsonTagCompatibilityCheck {
    private static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    private static int checks;

    @SuppressWarnings("removal") // Direct record construction is needed to retain the negative-zero test inputs.
    public static void main(String[] args) {
        checkNumber(ByteTag.valueOf((byte) -128), "-128");
        checkNumber(ShortTag.valueOf((short) -32768), "-32768");
        checkNumber(IntTag.valueOf(Integer.MIN_VALUE), "-2147483648");
        checkNumber(LongTag.valueOf(Long.MIN_VALUE), "-9223372036854775808");
        checkNumber(LongTag.valueOf(Long.MAX_VALUE), "9223372036854775807");
        checkNumber(FloatTag.valueOf(1.25F), "1.25");
        checkNumber(FloatTag.valueOf(Float.MAX_VALUE), Float.toString(Float.MAX_VALUE));
        checkNumber(DoubleTag.valueOf(-0.125D), "-0.125");
        checkNumber(DoubleTag.valueOf(Double.MIN_VALUE), Double.toString(Double.MIN_VALUE));
        // valueOf caches zero and discards its sign before the visitor sees it.
        require(Float.floatToRawIntBits(checkTag(new FloatTag(-0.0F)).getAsFloat())
                == Float.floatToRawIntBits(-0.0F), "Float negative zero changed");
        require(Double.doubleToRawLongBits(checkTag(new DoubleTag(-0.0D)).getAsDouble())
                == Double.doubleToRawLongBits(-0.0D), "Double negative zero changed");
        require(checkTag(ByteTag.valueOf(true)).getAsJsonPrimitive().isNumber(), "NBT byte became a JSON boolean");

        checkArray(new ByteArrayTag(new byte[] {Byte.MIN_VALUE, 0, Byte.MAX_VALUE}), "[-128,0,127]");
        checkArray(new IntArrayTag(new int[] {Integer.MIN_VALUE, 0, Integer.MAX_VALUE}), "[-2147483648,0,2147483647]");
        checkArray(new LongArrayTag(new long[] {Long.MIN_VALUE, 0, Long.MAX_VALUE}), "[-9223372036854775808,0,9223372036854775807]");
        checkArray(new ByteArrayTag(new byte[0]), "[]");
        checkArray(new IntArrayTag(new int[0]), "[]");
        checkArray(new LongArrayTag(new long[0]), "[]");
        checkArray(new ListTag(), "[]");
        require(checkTag(new CompoundTag()).equals(new JsonObject()), "Empty compound changed");

        List<String> strings = new ArrayList<>(List.of("", "plain.value-1", "a space", "\"double\"", "'single'",
                "both \" and '", "back\\slash", "line\nreturn\rtab\t", "中文 🚂", "<>&=", "\u2028\u2029"));
        for (char control = 0; control < 32; control++) {
            strings.add("control" + control + "end");
        }
        for (String text : strings) {
            JsonElement value = checkTag(StringTag.valueOf(text));
            require(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), "String became a non-string");
            require(value.getAsString().equals(text), "String escape did not round-trip");
        }

        CompoundTag escapedKeys = new CompoundTag();
        JsonObject expectedKeys = new JsonObject();
        List<String> sortedKeys = new ArrayList<>(strings);
        Collections.sort(sortedKeys);
        for (String key : strings) {
            escapedKeys.putString(key, key);
        }
        for (String key : sortedKeys) {
            expectedKeys.addProperty(key, key);
        }
        require(checkTag(escapedKeys).equals(expectedKeys), "Compound keys or values did not round-trip");
        require(new JsonStringTagVisitor().visit(escapedKeys).equals(expectedKeys.toString()), "Compound key order changed");

        CompoundTag nested = new CompoundTag();
        nested.putString("name", "train \"A\"\nroute");
        nested.putInt("speed", 42);
        nested.put("bytes", new ByteArrayTag(new byte[] {-1, 0, 1}));
        ListTag list = new ListTag();
        list.add(nested);
        list.add(StringTag.valueOf("mixed list"));
        list.add(LongTag.valueOf(Long.MAX_VALUE));
        ListTag innerList = new ListTag();
        innerList.add(DoubleTag.valueOf(2.5));
        innerList.add(new IntArrayTag(new int[] {3, 4}));
        list.add(innerList);
        CompoundTag root = new CompoundTag();
        root.put("list", list);
        root.put("empty", new CompoundTag());
        JsonObject expectedNested = new JsonObject();
        expectedNested.addProperty("name", "train \"A\"\nroute");
        expectedNested.addProperty("speed", 42);
        expectedNested.add("bytes", JSON.fromJson("[-1,0,1]", JsonElement.class));
        JsonArray expectedList = new JsonArray();
        expectedList.add(expectedNested);
        expectedList.add("mixed list");
        expectedList.add(Long.MAX_VALUE);
        expectedList.add(JSON.fromJson("[2.5,[3,4]]", JsonElement.class));
        JsonObject expectedRoot = new JsonObject();
        expectedRoot.add("list", expectedList);
        expectedRoot.add("empty", new JsonObject());
        require(checkTag(root).equals(expectedRoot), "Nested compound/list structure changed");
        require(new JsonStringTagVisitor().visit(EndTag.INSTANCE).equals("END"), "Legacy end-tag marker changed");

        System.out.println("PASS: " + checks + " real 26.2 NBT values produce strict JSON with preserved numbers, arrays, strings, sorted keys and nested data; input tags remain unchanged (no game/script-engine test)");
    }

    private static JsonElement checkTag(Tag tag) {
        Tag before = tag.copy();
        String serializedBefore = tag.toString();
        String actual = new JsonStringTagVisitor().visit(tag);
        JsonElement parsed = JSON.fromJson(actual, JsonElement.class);
        require(parsed != null, "Visitor returned no JSON value");
        require(tag.equals(before) && tag.toString().equals(serializedBefore), "Visitor mutated the source NBT");
        checks++;
        return parsed;
    }

    private static void checkNumber(Tag tag, String expected) {
        JsonPrimitive actual = checkTag(tag).getAsJsonPrimitive();
        require(actual.isNumber(), "Number became a string/boolean");
        require(actual.getAsBigDecimal().compareTo(new BigDecimal(expected)) == 0, "Numeric precision changed: " + expected);
    }

    private static void checkArray(Tag tag, String expected) {
        JsonElement actual = checkTag(tag);
        require(actual.isJsonArray() && actual.equals(JSON.fromJson(expected, JsonElement.class)), "Array values/order changed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
