package me.asu.ai.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Scanner;

import me.asu.ai.cli.IndexCli;

public class Utils {

     public static void printUsage(String resourceName) {
        try {
            Utils.class.getClassLoader().getResourceAsStream(resourceName).transferTo(System.out);
        } catch (Exception e) {
            System.err.println("Failed to load usage instructions: " + e.getMessage());
        }

    }
    /**
     * Find a file by searching upwards from the current directory.
     * @param fileName The name of the file to find.
     * @return The absolute path of the file if found, otherwise null.
     */
    public static Path findFileUpwards(String fileName) {
        Path current = Paths.get(".").toAbsolutePath().normalize();
        while (current != null) {
            Path target = current.resolve(fileName);
            if (Files.exists(target) && Files.isRegularFile(target)) {
                return target;
            }
            current = current.getParent();
        }
        return null;
    }

    public static boolean containsHelpFlag(String[] args) {
        for (String arg : args) {
            if ("--help".equals(arg) || "-h".equals(arg) || "help".equals(arg)) {
                return true;
            }
        }
        return false;
    }

    public static String findOptionValue(String[] args, String optionName) {
        for (int i = 0; i < args.length - 1; i++) {
            if (optionName.equals(args[i])) {
                return args[i + 1];
            }
        }
        return null;
    }

    public static String readTaskInteractively() {
        try(Scanner scanner = new Scanner(System.in, StandardCharsets.UTF_8);) {
            System.out.print("Enter task: ");
            String line = scanner.nextLine();
            return line == null ? null : line.trim();
        }
        
    }

    /**
     * 安全解析 double，失败时返回 null。
     */
    public static Double parseDoubleSafe(String s) {
        try {
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            return Double.parseDouble(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 安全解析 long，失败时返回 null。
     */
    public static Long parseLongSafe(String s) {
        try {
            if (s == null) return null;
            s = s.trim();
            if (s.isEmpty()) return null;
            return Long.parseLong(s);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 宽松解析 int，失败返回 0。
     */
    public static int parseInt(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 宽松解析 double，失败返回 0.0。
     */
    public static double parseDouble(String s) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * 宽松解析布尔值，支持 true/1/yes/y。
     */
    public static boolean parseBool(String s) {
        s = s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
        return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("y");
    }

    /**
     * double 转 8 位小数；空值或非法数返回空字符串。
     */
    public static String fmt8(Double v) {
        if (v == null || v.isNaN() || v.isInfinite()) return "";
        return String.format(Locale.ROOT, "%.8f", v);
    }

    /**
     * double 转 10 位小数。
     */
    public static String fmt10(double v) {
        return String.format(Locale.ROOT, "%.10f", v);
    }

    /**
     * double 转 4 位小数。
     */
    public static String fmt4(double v) {
        return fmt(v, 4);
    }

    /**
     * double 转 2 位小数。
     */
    public static String fmt2(double v) {
        return fmt(v, 2);
    }

    /**
     * double 转 6 位小数。
     */
    public static String fmt6(double v) {
        return fmt(v, 6);
    }

    /**
     * double 转 4 位小数（简写）。
     */
    public static String fmt(double v) {
        return fmt(v, 4);
    }

    public static String fmt(double v, int decimals) {
        String fmt = String.format("%%.%df", decimals);
        return String.format(Locale.ROOT, fmt, v);
    }

}
