package com.appgiuaky.gpaplanner.utils;

import java.util.List;
import java.util.Locale;

/** Định dạng và đọc số hiển thị trên giao diện. */
public final class Format {

    private Format() {
    }

    /** 3.1125 -> "3.11" (dấu chấm thập phân, không phụ thuộc ngôn ngữ máy). */
    public static String fmt(double x, int digits) {
        return String.format(Locale.US, "%." + digits + "f", x);
    }

    public static String fmt(double x) {
        return fmt(x, 2);
    }

    /** Số không kèm ".0" thừa: 10.0 -> "10", 8.5 -> "8.5". */
    public static String plain(double x) {
        return x % 1.0 == 0.0 ? Long.toString((long) x) : Double.toString(x);
    }

    /** Đọc số người dùng nhập, chấp nhận cả dấu phẩy thập phân ("8,5"); null nếu không phải số. */
    public static Double parseNumber(String text) {
        try {
            return Double.parseDouble(text.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Số nguyên người dùng nhập; null nếu không phải số nguyên. */
    public static Integer parseInt(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static String join(String separator, List<String> parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0) sb.append(separator);
            sb.append(p);
        }
        return sb.toString();
    }
}
