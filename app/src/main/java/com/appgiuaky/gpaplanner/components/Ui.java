package com.appgiuaky.gpaplanner.components;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.TextViewCompat;

import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.types.Course;
import com.google.android.material.chip.Chip;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Các hàm dựng giao diện dùng chung cho mọi màn hình. */
public final class Ui {

    private static final int FADE_IN_OFFSET_DP = 12;
    private static final long FADE_IN_MS = 220;
    private static final long GROW_IN_MS = 700;

    private Ui() {
    }

    public static int dp(Context c, float value) {
        return Math.round(value * c.getResources().getDisplayMetrics().density);
    }

    /** Màu của từng điểm chữ, dùng chung cho nhãn điểm và biểu đồ. */
    public static int gradeColor(String letter) {
        switch (letter) {
            case "A": return 0xFF2E7D32;
            case "B+": return 0xFF558B2F;
            case "B": return 0xFF1565C0;
            case "C+": return 0xFF00838F;
            case "C": return 0xFFF57F17;
            case "D+": return 0xFFEF6C00;
            case "D": return 0xFFD84315;
            default: return 0xFFC62828;
        }
    }

    /** "MATH101 • 3 TC", with a "not counted in GPA" note when it applies. */
    public static String courseMeta(Context c, Course course) {
        return c.getString(course.countInGpa ? R.string.course_meta : R.string.course_meta_no_gpa,
                course.code, course.credits);
    }

    /** Màu lấy từ theme, ví dụ R.attr.colorOnSurfaceVariant (tự đổi theo chế độ sáng/tối). */
    public static int color(Context c, int attr) {
        return MaterialColors.getColor(c, attr, Color.BLACK);
    }

    /** Nhãn điểm chữ có nền màu, ví dụ [B+]. */
    public static TextView badge(Context c, String letter) {
        TextView tv = new TextView(c);
        tv.setText(letter);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setMinWidth(dp(c, 32));
        tv.setPadding(dp(c, 8), dp(c, 2), dp(c, 8), dp(c, 2));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(gradeColor(letter));
        bg.setCornerRadius(dp(c, 6));
        tv.setBackground(bg);
        return tv;
    }

    /**
     * Nhãn bo góc có nền và viền, ví dụ viên "GPA 3.20" hay ô "Cần ≥ 8.6".
     * Màu truyền vào là màu thật (đã lấy từ theme), để nhãn tự đổi theo chế độ sáng/tối.
     */
    public static TextView tag(Context c, CharSequence s, int background, int stroke, int textColor, int radiusDp) {
        TextView tv = text(c, s, com.google.android.material.R.attr.textAppearanceLabelLarge);
        tv.setTextColor(textColor);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(c, 10), dp(c, 4), dp(c, 10), dp(c, 4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(background);
        bg.setStroke(dp(c, 1), stroke);
        bg.setCornerRadius(dp(c, radiusDp));
        tv.setBackground(bg);
        return tv;
    }

    /** TextView với kiểu chữ lấy từ theme, ví dụ R.attr.textAppearanceTitleMedium. */
    public static TextView text(Context c, CharSequence s, int appearanceAttr) {
        TextView tv = new TextView(c);
        TypedValue value = new TypedValue();
        if (c.getTheme().resolveAttribute(appearanceAttr, value, true)) {
            TextViewCompat.setTextAppearance(tv, value.resourceId);
        }
        tv.setText(s);
        return tv;
    }

    public static TextView text(Context c, CharSequence s, int appearanceAttr, int colorAttr) {
        TextView tv = text(c, s, appearanceAttr);
        tv.setTextColor(color(c, colorAttr));
        return tv;
    }

    /** Thêm child (rộng hết hàng) vào cuối parent dọc, cách phần tử phía trên gapDp. */
    public static <T extends View> T add(LinearLayout parent, T child, int gapDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) lp.topMargin = dp(parent.getContext(), gapDp);
        parent.addView(child, lp);
        return child;
    }

    /** Thêm một thẻ (MaterialCardView) vào parent; trả về vùng nội dung dọc bên trong thẻ. */
    public static LinearLayout card(LinearLayout parent, int gapDp) {
        View card = LayoutInflater.from(parent.getContext()).inflate(R.layout.view_card, parent, false);
        add(parent, card, gapDp);
        return card.findViewById(R.id.card_content);
    }

    /** Chấm tròn màu. */
    public static View dot(Context c, int sizeDp, int color) {
        View v = new View(c);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(color);
        v.setBackground(bg);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(c, sizeDp), dp(c, sizeDp)));
        return v;
    }

    /** Một mục chú thích biểu đồ: chấm màu + nhãn. */
    public static LinearLayout legend(Context c, int color, String label) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(dot(c, 12, color));
        TextView tv = text(c, label, com.google.android.material.R.attr.textAppearanceBodySmall);
        tv.setPadding(dp(c, 6), 0, 0, 0);
        row.addView(tv);
        return row;
    }

    public static Chip filterChip(ViewGroup parent, String text) {
        Chip chip = (Chip) LayoutInflater.from(parent.getContext()).inflate(R.layout.view_filter_chip, parent, false);
        chip.setText(text);
        return chip;
    }

    /**
     * Hộp thoại nhập một giá trị. Nút Lưu chỉ bật khi [valid] trả về true; [onSave] nhận chuỗi đã bỏ khoảng trắng.
     */
    public static void inputDialog(Context c, String title, String hint, String initial, int inputType, String helper,
                                   Predicate<String> valid, Consumer<String> onSave) {
        View view = LayoutInflater.from(c).inflate(R.layout.dialog_text_input, null);
        TextInputLayout layout = view.findViewById(R.id.input_layout);
        TextInputEditText input = view.findViewById(R.id.input);
        layout.setHint(hint);
        if (helper != null) layout.setHelperText(helper);
        input.setInputType(inputType);
        input.setText(initial);
        input.setSelection(input.length());

        AlertDialog dialog = new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            ok.setEnabled(valid.test(input.getText().toString().trim()));
            input.addTextChangedListener(new SimpleWatcher(
                    () -> ok.setEnabled(valid.test(input.getText().toString().trim()))));
            ok.setOnClickListener(v -> {
                onSave.accept(input.getText().toString().trim());
                dialog.dismiss();
            });
        });
        input.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    public static void confirm(Context c, String title, String message, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, (d, w) -> onConfirm.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Hiện dần và trượt nhẹ lên, dùng khi một khối nội dung vừa xuất hiện (đổi mục con, tin nhắn mới). */
    public static void fadeIn(View v) {
        v.setAlpha(0f);
        v.setTranslationY(dp(v.getContext(), FADE_IN_OFFSET_DP));
        v.animate().alpha(1f).translationY(0f).setDuration(FADE_IN_MS)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Dài dần từ trái sang, dùng cho thanh tiến độ. */
    public static void growIn(View v) {
        v.setPivotX(0f);
        v.setScaleX(0f);
        v.animate().scaleX(1f).setDuration(GROW_IN_MS).setInterpolator(new DecelerateInterpolator()).start();
    }

    /** TextWatcher chỉ cần biết "nội dung vừa đổi". */
    public static final class SimpleWatcher implements TextWatcher {
        private final Runnable onChange;

        public SimpleWatcher(Runnable onChange) {
            this.onChange = onChange;
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onChange.run();
        }
    }
}
