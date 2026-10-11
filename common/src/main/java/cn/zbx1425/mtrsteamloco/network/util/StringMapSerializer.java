package cn.zbx1425.mtrsteamloco.network.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

public class StringMapSerializer {

    private static final String BINARY_TEXT_PREFIX = "YLTE-SM1:";

    private static ByteArrayOutputStream serializeIn(Map<String, String> map) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(map.size());

        for (Map.Entry<String, String> entry : map.entrySet()) {
            dos.writeUTF(entry.getKey());
            dos.writeUTF(entry.getValue());
        }

        dos.flush();
        return baos;
    }

    private static Map<String, String> deserializeIn(byte[] bytes) throws IOException {
        Map<String, String> map = new HashMap<>();
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        DataInputStream dis = new DataInputStream(bais);

        int size = dis.readInt();

        for (int i = 0; i < size; i++) {
            String key = dis.readUTF();
            String value = dis.readUTF();
            map.put(key, value);
        }

        dis.close();
        return map;
    }

    public static byte[] serializeToByteArray(Map<String, String> map) throws IOException {
        return serializeIn(map).toByteArray();
    }

    public static Map<String, String> deserialize(byte[] bytes) throws IOException {
        return deserializeIn(bytes);
    }

    public static String serializeToString(Map<String, String> map) throws IOException {
        byte[] bytes = serializeIn(map).toByteArray();
        String legacy = new String(bytes, StandardCharsets.UTF_8);
        // Preserve readable old saves and packets exactly. Binary length prefixes and
        // modified UTF-8 are not always valid UTF-8; only those cases need an envelope.
        return Arrays.equals(bytes, legacy.getBytes(StandardCharsets.UTF_8))
                ? legacy : BINARY_TEXT_PREFIX + Base64.getEncoder().encodeToString(bytes);
    }

    public static Map<String, String> deserialize(String str) throws IOException {
        if (str.startsWith(BINARY_TEXT_PREFIX)) {
            try {
                return deserializeIn(Base64.getDecoder().decode(str.substring(BINARY_TEXT_PREFIX.length())));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid YLTE string-map encoding", e);
            }
        }
        return deserializeIn(str.getBytes(StandardCharsets.UTF_8));
    }
}
