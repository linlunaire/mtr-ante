import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.ZipFile;

/** Checks the delivered JAR alone, without letting Gradle's compile classpath hide missing libraries. */
public final class PinyinPackagingCheck {
    public static void main(String[] args) throws Exception {
        Path jar = Path.of(args[0]).toRealPath();
        try (ZipFile archive = new ZipFile(jar.toFile())) {
            if (archive.stream().anyMatch(entry -> entry.getName().contains("stuxuhai")
                    || entry.getName().contains("pinyin4j"))) {
                throw new AssertionError("Unexpected GPL pinyin library in " + jar);
            }
            if (archive.getEntry("licenses/Pinyin-notices.txt") == null
                    || archive.getEntry("licenses/Apache-2.0.txt") == null) {
                throw new AssertionError("Missing pinyin license notices in " + jar);
            }
        }
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{jar.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> utils = Class.forName("cn.zbx1425.mtrsteamloco.util.PinyinUtils", true, loader);
            check(utils, "getPinyin", "轨道A1", "guidaoa1");
            check(utils, "getPinyinInitials", "轨道A1", "gda1");
            check(utils, "getPinyin", "地铁", "ditie");
            check(utils, "getPinyinInitials", "地铁", "dt");
            check(utils, "getPinyin", "ABC_123", "abc_123");
            check(utils, "getPinyinInitials", "ABC_123", "abc_123");
            check(utils, "getPinyin", "", "");
            check(utils, "getPinyinInitials", "", "");
            check(utils, "getPinyin", "重慶長安", "chongqingchangan");
            check(utils, "getPinyinInitials", "重慶長安", "cqca");
            check(utils, "getPinyin", "重庆银行", "chongqingyinhang");
            check(utils, "getPinyinInitials", "重庆银行", "cqyh");
            check(utils, "getPinyin", "轨道 A1_[🚆]", "guidao a1_[🚆]");
            check(utils, "getPinyinInitials", "轨道 A1_[🚆]", "gd a1_[🚆]");
            check(utils, "getPinyin", "女绿", "nvlv");
            check(utils, "getPinyinInitials", "女绿", "nl");
            check(utils, "getPinyin", "地鐵", "ditie");
            check(utils, "getPinyinInitials", "地鐵", "dt");
            check(utils, "getPinyin", "𠮷\t中\u0000文", "𠮷\tzhong\u0000wen");
            check(utils, "getPinyinInitials", "𠮷\t中\u0000文", "𠮷\tz\u0000w");
            Locale previous = Locale.getDefault();
            try {
                Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                check(utils, "getPinyin", "地铁I", "ditiei");
                check(utils, "getPinyinInitials", "地铁I", "dti");
            } finally {
                Locale.setDefault(previous);
            }
        }
        System.out.println("PASS: packaged pinyin and license notices");
    }

    private static void check(Class<?> utils, String method, String input, String expected) throws Exception {
        Object actual = utils.getMethod(method, String.class).invoke(null, input);
        if (!expected.equals(actual)) throw new AssertionError(method + "(" + input + "): expected " + expected + ", got " + actual);
    }
}
