package me.asu.ai.util;


import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Map;

public class StringUtils {
    public static byte[] toBytes(String str) {
        return toBytes(str, "utf-8");
    }

    public static byte[] toBytes(String str, String charset) {
        if (isEmpty(str)) {
            return new byte[0];
        } else {
            try {
                return str.getBytes(charset);
            } catch (Exception var3) {
                var3.printStackTrace();
                return new byte[0];
            }
        }
    }
    /**
     * 复制字符串
     *
     * @param cs  字符串
     * @param num 数量
     * @return 新字符串
     */
    public static String dup(String cs, int num) {
        if (isEmpty(cs) || num <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder(cs.length() * num);
        for (int i = 0; i < num; i++) {
            sb.append(cs);
        }
        return sb.toString();
    }

    /**
     * 复制字符
     *
     * @param c   字符
     * @param num 数量
     * @return 新字符串
     */
    public static String dup(char c, int num) {
        if (c == 0 || num < 1) {
            return "";
        }
        StringBuilder sb = new StringBuilder(num);
        for (int i = 0; i < num; i++) {
            sb.append(c);
        }
        return sb.toString();
    }


    /**
     * 如果此字符串为 null 或者全为空白字符，则返回 true
     *
     * @param cs 字符串
     * @return 如果此字符串为 null 或者全为空白字符，则返回 true
     */
    public static boolean isBlank(CharSequence cs) {
        if (null == cs) {
            return true;
        }
        int length = cs.length();
        for (int i = 0; i < length; i++) {
            if (!(Character.isWhitespace(cs.charAt(i)))) {
                return false;
            }
        }
        return true;
    }

    public static boolean isNotBlank(CharSequence cs) {
        return !isBlank(cs);
    }


    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }


    public static boolean isNotEmpty(String src) {
        return !isEmpty(src);
    }

    public static String encodeFormData(Map<String, Object> params)
            throws UnsupportedEncodingException {
        StringBuilder sb = new StringBuilder();
        if (params != null && !params.isEmpty()) {
            for (String s : params.keySet()) {
                //@formatter:off
                Object o = params.get(s);
                String key = encodeURIComponent(s);
                if (o == null) {
                    sb.append(key).append('=').append('&');
                } else {
                    String value = encodeURIComponent(o.toString());
                    sb.append(key).append('=').append(value).append('&');
                }
                //@formatter:on
            }
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString();
    }

    public static String encodeURIComponent(String str) {
        if (StringUtils.isEmpty(str)) {
            return "";
        }

        try {
            return URLEncoder.encode(str, "utf-8");
        } catch (UnsupportedEncodingException e) {
            return str;
        }
    }

    
    public static String safe(String value) {
        return value == null ? "" : value;
    }

    
    public static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

        
    public static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String shorten(String text, int maxChars) {
        String value = StringUtils.safe(text).replace('\r', ' ').replace('\n', ' ').trim();
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars)).trim() + "...";
    }
}