import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** The selected final JAR is the only application entry on this isolated classpath. */
public final class StringMapSerializationCheck {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toRealPath();
        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> type = loader.loadClass("cn.zbx1425.mtrsteamloco.network.util.StringMapSerializer");
            require(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).equals(jar), "Wrong artifact origin");
            Method encode = type.getMethod("serializeToString", Map.class);
            Method decode = type.getMethod("deserialize", String.class);
            Method encodeBytes = type.getMethod("serializeToByteArray", Map.class);
            Method decodeBytes = type.getMethod("deserialize", byte[].class);
            check(Map.of(), encode, decode, encodeBytes, decodeBytes);
            check(Map.of("mode", "auto"), encode, decode, encodeBytes, decodeBytes);
            check(Map.of("emoji", "🚆", "nul", "a\0b"), encode, decode, encodeBytes, decodeBytes);
            for (int length = 0; length <= 512; length++) {
                check(Map.of("key", "a".repeat(length)), encode, decode, encodeBytes, decodeBytes);
                check(Map.of("轨道", "车".repeat(length)), encode, decode, encodeBytes, decodeBytes);
            }
            Map<String, String> many = new LinkedHashMap<>();
            for (int count = 0; count <= 256; count++) {
                check(many, encode, decode, encodeBytes, decodeBytes);
                many.put("key" + count, "value" + count);
            }
            Random random = new Random(1211);
            for (int sample = 0; sample < 250; sample++) {
                Map<String, String> values = new LinkedHashMap<>();
                for (int index = 0; index < random.nextInt(20); index++) {
                    StringBuilder value = new StringBuilder();
                    for (int c = 0; c < random.nextInt(300); c++) value.append((char) random.nextInt(0x10000));
                    values.put("键" + index, value.toString());
                }
                check(values, encode, decode, encodeBytes, decodeBytes);
            }
        }
        System.out.println("PASS: selected final-JAR string-map round trips, binary layout and readable legacy strings; " + assertions + " assertions");
    }

    private static void check(Map<String, String> values, Method encode, Method decode, Method encodeBytes, Method decodeBytes) throws Exception {
        byte[] golden;
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream data = new DataOutputStream(bytes)) {
            data.writeInt(values.size());
            for (var entry : values.entrySet()) { data.writeUTF(entry.getKey()); data.writeUTF(entry.getValue()); }
            data.flush();
            golden = bytes.toByteArray();
        }
        byte[] binary = (byte[]) encodeBytes.invoke(null, values);
        require(Arrays.equals(golden, binary), "Existing binary format changed");
        require(values.equals(decodeBytes.invoke(null, (Object) golden)), "Binary control did not round trip");
        String text = (String) encode.invoke(null, values);
        Object restored;
        try { restored = decode.invoke(null, text); }
        catch (InvocationTargetException error) { throw new AssertionError("Text round trip failed for " + values.size() + " entries", error.getCause()); }
        require(values.equals(restored), "String form changed Unicode or NUL values");
        String legacy = new String(golden, StandardCharsets.UTF_8);
        if (Arrays.equals(golden, legacy.getBytes(StandardCharsets.UTF_8))) {
            require(text.equals(legacy), "Previously safe serialized text changed unnecessarily");
            require(values.equals(decode.invoke(null, legacy)), "A readable old save no longer decodes");
        }
    }

    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
}
