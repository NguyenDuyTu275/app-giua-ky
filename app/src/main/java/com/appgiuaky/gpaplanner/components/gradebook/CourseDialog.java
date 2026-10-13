package com.appgiuaky.gpaplanner.components.gradebook;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.components.Ui;
import com.appgiuaky.gpaplanner.types.Course;
import com.appgiuaky.gpaplanner.types.GradeComponent;
import com.appgiuaky.gpaplanner.utils.Format;
import com.appgiuaky.gpaplanner.utils.GpaCalculator;
import com.appgiuaky.gpaplanner.utils.MockData;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** Hộp thoại thêm/sửa môn học và các điểm thành phần. */
public final class CourseDialog {

    private final Context context;
    /** null = thêm môn mới. */
    private final Course initial;
    private final EditText code;
    private final EditText name;
    private final EditText credits;
    private final MaterialSwitch countInGpa;
    private final LinearLayout components;
    private final TextView weightSum;
    private final TextView error;

    private CourseDialog(Context context, Course initial, View view) {
        this.context = context;
        this.initial = initial;
        code = view.findViewById(R.id.code);
        name = view.findViewById(R.id.name);
        credits = view.findViewById(R.id.credits);
        countInGpa = view.findViewById(R.id.count_in_gpa);
        components = view.findViewById(R.id.components);
        weightSum = view.findViewById(R.id.weight_sum);
        error = view.findViewById(R.id.error);
    }

    /** Mở hộp thoại; [onDelete] = null khi thêm môn mới (không có nút xóa). */
    public static void show(Context context, Course initial, Consumer<Course> onSave, Runnable onDelete) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_course, null);
        CourseDialog d = new CourseDialog(context, initial, view);

        Course base = initial != null ? initial
                : new Course("", "", 3, MockData.defaultComponents(), true);
        d.code.setText(base.code);
        d.name.setText(base.name);
        d.credits.setText(String.valueOf(base.credits));
        d.countInGpa.setChecked(base.countInGpa);
        for (GradeComponent g : base.components) {
            d.addRow(g.name, Format.plain(g.weight), g.score == null ? "" : Format.plain(g.score));
        }
        d.updateWeightSum();
        view.findViewById(R.id.add_component).setOnClickListener(v -> d.addRow("", "", ""));

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(initial == null ? R.string.add_course : R.string.edit_course)
                .setView(view)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        if (onDelete != null) {
            View delete = view.findViewById(R.id.delete);
            delete.setVisibility(View.VISIBLE);
            delete.setOnClickListener(v -> {
                dialog.dismiss();
                onDelete.run();
            });
        }
        // Tự xử lý nút Lưu để hộp thoại không đóng khi dữ liệu chưa hợp lệ
        dialog.setOnShowListener(x -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Course course = d.buildCourse(base);
            if (course != null) {
                onSave.accept(course);
                dialog.dismiss();
            }
        }));
        dialog.show();
    }

    private void addRow(String componentName, String weight, String score) {
        View row = LayoutInflater.from(context).inflate(R.layout.item_component_input, components, false);
        ((EditText) row.findViewById(R.id.component_name)).setText(componentName);
        EditText weightInput = row.findViewById(R.id.component_weight);
        weightInput.setText(weight);
        weightInput.addTextChangedListener(new Ui.SimpleWatcher(this::updateWeightSum));
        ((EditText) row.findViewById(R.id.component_score)).setText(score);
        row.findViewById(R.id.component_remove).setOnClickListener(v -> {
            components.removeView(row);
            updateWeightSum();
        });
        components.addView(row);
    }

    private static String textOf(View row, int id) {
        return ((EditText) row.findViewById(id)).getText().toString().trim();
    }

    private double weightSum() {
        double sum = 0;
        for (int i = 0; i < components.getChildCount(); i++) {
            Double w = Format.parseNumber(textOf(components.getChildAt(i), R.id.component_weight));
            if (w != null) sum += w;
        }
        return sum;
    }

    private void updateWeightSum() {
        double sum = weightSum();
        weightSum.setText(context.getString(R.string.weight_sum, Format.plain(GpaCalculator.round2(sum))));
        weightSum.setTextColor(Ui.color(context, Math.abs(sum - 100) > 0.01
                ? androidx.appcompat.R.attr.colorError
                : com.google.android.material.R.attr.colorOnSurfaceVariant));
    }

    /** Kiểm tra dữ liệu nhập; trả về môn học mới (giữ id cũ khi sửa) hoặc null và hiện lỗi. */
    private Course buildCourse(Course base) {
        String message = validate();
        error.setVisibility(message == null ? View.GONE : View.VISIBLE);
        error.setText(message);
        if (message != null) return null;

        List<GradeComponent> list = new ArrayList<>();
        for (int i = 0; i < components.getChildCount(); i++) {
            View row = components.getChildAt(i);
            String score = textOf(row, R.id.component_score);
            list.add(new GradeComponent(textOf(row, R.id.component_name),
                    Format.parseNumber(textOf(row, R.id.component_weight)),
                    score.isEmpty() ? null : Format.parseNumber(score)));
        }
        return new Course(base.id, code.getText().toString().trim(), name.getText().toString().trim(),
                Format.parseInt(credits.getText().toString()), list, countInGpa.isChecked());
    }

    private String validate() {
        if (code.getText().toString().trim().isEmpty()) return context.getString(R.string.error_code_required);
        if (name.getText().toString().trim().isEmpty()) return context.getString(R.string.error_name_required);
        Integer creditsValue = Format.parseInt(credits.getText().toString());
        if (creditsValue == null || !Course.isValidCredits(creditsValue)) {
            return context.getString(R.string.error_credits_range, Course.MIN_CREDITS, Course.MAX_CREDITS);
        }
        if (components.getChildCount() == 0) return context.getString(R.string.error_no_components);
        for (int i = 0; i < components.getChildCount(); i++) {
            View row = components.getChildAt(i);
            if (textOf(row, R.id.component_name).isEmpty()) return context.getString(R.string.error_component_name);
            Double w = Format.parseNumber(textOf(row, R.id.component_weight));
            if (w == null || w <= 0) return context.getString(R.string.error_weight_positive);
        }
        if (Math.abs(weightSum() - 100) > 0.01) return context.getString(R.string.error_weight_sum);
        for (int i = 0; i < components.getChildCount(); i++) {
            String score = textOf(components.getChildAt(i), R.id.component_score);
            if (score.isEmpty()) continue;
            Double s = Format.parseNumber(score);
            if (s == null || s < 0 || s > 10) return context.getString(R.string.error_score_range);
        }
        return null;
    }
}
