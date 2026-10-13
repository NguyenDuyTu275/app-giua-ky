package com.appgiuaky.gpaplanner;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.appcompat.app.AppCompatDelegate;

/** Chế độ sáng / tối / theo hệ thống, lưu trong cùng file cài đặt với API key. */
public final class ThemeSettings {

    private ThemeSettings() {
    }

    /** Thứ tự trùng với R.array.theme_options. */
    private static final int[] MODES = {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
    };

    private static final String PREFS = "settings";
    private static final String KEY_MODE = "theme_mode";

    /** Gọi trước super.onCreate của Activity để màn hình dựng đúng chế độ ngay từ đầu. */
    public static void apply(Context c) {
        AppCompatDelegate.setDefaultNightMode(MODES[selectedIndex(c)]);
    }

    /** Vị trí của chế độ đang chọn trong R.array.theme_options. */
    public static int selectedIndex(Context c) {
        int mode = prefs(c).getInt(KEY_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        for (int i = 0; i < MODES.length; i++) if (MODES[i] == mode) return i;
        return 0;
    }

    /** Lưu lựa chọn và áp dụng ngay; AppCompat tự dựng lại Activity nếu chế độ thay đổi. */
    public static void select(Context c, int index) {
        prefs(c).edit().putInt(KEY_MODE, MODES[index]).apply();
        AppCompatDelegate.setDefaultNightMode(MODES[index]);
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
