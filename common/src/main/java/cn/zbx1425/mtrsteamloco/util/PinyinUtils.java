package cn.zbx1425.mtrsteamloco.util;

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PinyinUtils {
    private static final Pattern HAN_WORD = Pattern.compile("\\p{IsHan}+");

    public static String getPinyin(String hanzi) {
        return convert(hanzi, PinyinStyleEnum.INPUT);
    }

    public static String getPinyinInitials(String hanzi) {
        return convert(hanzi, PinyinStyleEnum.FIRST_LETTER);
    }

    private static String convert(String input, PinyinStyleEnum style) {
        Matcher words = HAN_WORD.matcher(input);
        StringBuilder result = new StringBuilder();
        int end = 0;
        while (words.find()) {
            // Preserve spaces, symbols and Latin text outside the Chinese words.
            result.append(input, end, words.start());
            String simplified = ZhConverterUtil.toSimple(words.group());
            result.append(PinyinHelper.toPinyin(simplified, style, ""));
            end = words.end();
        }
        return result.append(input, end, input.length()).toString().toLowerCase(Locale.ROOT);
    }
}
