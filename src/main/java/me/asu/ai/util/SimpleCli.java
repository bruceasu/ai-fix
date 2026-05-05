package me.asu.ai.util;

import java.io.File;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class SimpleCli {
    Map<String, String> args = new HashMap<>();

    /**
     * 解析命令行参数，支持 `--k v` 与 `--flag`。
     */
    public static SimpleCli parseArgs(String[] args) {
        SimpleCli cli = new SimpleCli();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--") || a.startsWith("-")) {
                String v = (i + 1 < args.length && !args[i + 1].startsWith("--") && !args[i + 1].startsWith("-")) ? args[++i]
                        : "true";
                cli.put(a, v);
            }
        }
        return cli;
    }

    public SimpleCli put(String k, String v) {
        args.put(k, v);
        return this;
    }

    public String get(String k) {
        if (!args.containsKey(k))
            return null;
        return get(k).strip();
    }

    public String get(String k, String d) {
        if (!args.containsKey(k))
            return d;
        String v = get(k).strip();
        return v.isEmpty() ? d : v;
    }

    /**
     * 获取必填参数；缺失时抛异常。
     */
    public String require(String key) {
        String v = get(key);
        if (v == null || v.isBlank())
            throw new IllegalArgumentException("Missing arg: " + key);
        return v;
    }

    /**
     * 读取整型参数，未提供时使用默认值。
     */
    public Integer intv(String k, Integer d) {
        if (!args.containsKey(k))
            return d;
        try {
            return Integer.parseInt(get(k).trim());
        } catch (Exception e) {
            return d;
        }
    }

    /**
     * 读取布尔参数，未提供时使用默认值。
     */
    public Boolean bool(String k, Boolean dv) {
        if (!args.containsKey(k))
            return dv;
        return Utils.parseBool(get(k).trim());
    }

    public boolean hasHelp() {
        return args.containsKey("--help") || args.containsKey("-h");
    }

    public boolean has(String key) {
        return args.containsKey(key);
    }

    public Boolean isTrue(String key) {
        String v = get(key);
        return v != null && (v.equalsIgnoreCase("true") || v.equalsIgnoreCase("yes")
                || v.equalsIgnoreCase("1"));
    }

    public Long longv(String k, Long d) {
        if (!args.containsKey(k))
            return d;
        try {
            return Long.parseLong(get(k).trim());
        } catch (Exception e) {
            return d;
        }
    }

    public Double doublev(String k, Double d) {
        if (!args.containsKey(k))
            return d;
        try {
            return Double.parseDouble(get(k).trim());
        } catch (Exception e) {
            return d;
        }
    }

    public Float floatv(String k, Float d) {
        if (!args.containsKey(k))
            return d;
        try {
            return Float.parseFloat(get(k).trim());
        } catch (Exception e) {
            return d;
        }
    }

    public Instant instant(String k, Instant d) {
        if (!args.containsKey(k))
            return d;
        try {
            return Instant.parse(get(k).trim());
        } catch (Exception e) {
            return d;
        }
    }

    /**
     * 解析 OffsetDateTime 参数，支持自定义格式；未提供或解析失败时使用默认值。
     *
     * @param k      参数名
     * @param format 日期时间格式，如 "yyyy-MM-dd'T'HH:mm:ssXXX"；可参考 DateTimeFormatter
     *               的预定义格式或自定义格式
     * @param d      默认值
     * @return 解析结果或默认值
     * @see DateTimeFormatter
     */
    public OffsetDateTime offsetDateTime(String k, String format, OffsetDateTime d) {
        if (!args.containsKey(k))
            return d;
        try {
            if (format == null || format.isBlank()) {
                // 默认使用 ISO_OFFSET_DATE_TIME 格式
                return OffsetDateTime.parse(get(k).trim());
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return OffsetDateTime.parse(get(k).trim(), formatter);
        } catch (Exception e) {
            return d;
        }

    }

    public OffsetDateTime offsetDateTime(String k, OffsetDateTime d) {
        if (!args.containsKey(k))
            return d;
        String[] formats = new String[] {
                "yyyy-MM-dd'T'HH:mm:ssXXX", // ISO_OFFSET_DATE_TIME 2024-01-01T12:00:00+08:00
                "yyyy-MM-dd HH:mm:ssXXX", // 2024-01-01 12:00:00+08:00
                "yyyy-MM-dd HH:mm:ssZ", // 2024-01-01 12:00:00+0800
                "yyyy-MM-dd HH:mm:ssX", // 2024-01-01 12:00:00+08 2024-01-01 12:00:00Z

        };
        String string = get(k);
        if (string == null || string.isBlank()) {
            return d;
        }
        String date = string.trim();
        for (String format : formats) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
                return OffsetDateTime.parse(date, formatter);
            } catch (Exception e) {
                // 继续尝试下一个格式
            }
        }

        try {
            // 2024-01-01 12:00:00/2024-01-01T12:00:00 plus UTC offset, without 'T'
            // separator
            // 兼容 ISO_OFFSET_DATE_TIME 格式，如 "2024-01-01T12:00:00Z" 或
            // "2024-01-01T12:00:00+08:00"
            date = date.replace(" ", "T") + "Z"; // 假设缺失时区信息默认为 UTC
            return OffsetDateTime.parse(date);

        } catch (Exception e) {
            return d;
        }
    }

    public LocalDateTime localDateTime(String k, String format, LocalDateTime d) {
        if (!args.containsKey(k))
            return d;
        try {
            if (format == null || format.isBlank()) {
                // 默认使用 ISO_LOCAL_DATE_TIME 格式
                return LocalDateTime.parse(get(k).trim());
            }
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
            return LocalDateTime.parse(get(k).trim(), formatter);
        } catch (Exception e) {
            return d;
        }
    }

    public ZonedDateTime zonedDateTime(String k, ZoneId zoneId, ZonedDateTime d) {
        if (!args.containsKey(k))
            return d;
        try {
            // 兼容 ISO_LOCAL_DATE_TIME 格式，如 "2024-01-01T12:00:00"
            if (get(k).contains("T")) {
                return LocalDateTime.parse(get(k).trim()).atZone(zoneId);
            }
            // 兼容空格分隔的日期时间格式，如 "2024-01-01 12:00:00"
            String s = get(k).trim().replace(" ", "T");
            return LocalDateTime.parse(s).atZone(zoneId);
        } catch (Exception e) {
            return d;
        }
    }

    /**
     * 解析 LocalDateTime 参数，未提供或解析失败时使用默认值。
     *
     * @param k 参数名
     * @param d 默认值
     * @return 解析结果或默认值
     */
    public LocalDateTime localDateTime(String k, LocalDateTime d) {
        if (!args.containsKey(k))
            return d;
        try {
            // 兼容 ISO_LOCAL_DATE_TIME 格式，如 "2024-01-01T12:00:00"
            if (get(k).contains("T")) {
                return LocalDateTime.parse(get(k).trim());
            }
            // 兼容空格分隔的日期时间格式，如 "2024-01-01 12:00:00"
            String s = get(k).trim().replace(" ", "T");
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return d;
        }
    }

    public LocalDate localDate(String k, LocalDate d) {
        if (!args.containsKey(k))
            return d;
        try {
            return LocalDate.parse(get(k).trim());
        } catch (Exception e) {
            return d;
        }
    }

    public LocalTime localTime(String k, LocalTime d) {
        if (!args.containsKey(k))
            return d;
        try {
            return LocalTime.parse(get(k).trim());
        } catch (Exception e) {
            return d;
        }
    }

    public Timestamp timestamp(String k, Timestamp d) {
        if (!args.containsKey(k))
            return d;
        try {
            return Timestamp.from(Instant.parse(get(k).trim()));
        } catch (Exception e) {
            return d;
        }
    }

    public Path path(String k) {
        if (!args.containsKey(k))
            return null;
        try {
            return Path.of(get(k).trim());
        } catch (Exception e) {
            return null;
        }
    }

    public File file(String k) {
        if (!args.containsKey(k))
            return null;
        try {
            return new File(get(k).trim());
        } catch (Exception e) {
            return null;
        }
    }

    public Map<String, String> toMap() {
        return args;
    }
}
