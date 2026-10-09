package com.appgiuaky.gpaplanner.components.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.appgiuaky.gpaplanner.components.Ui;

import java.util.ArrayList;
import java.util.List;

/** Biểu đồ đường GPA (thang 0-4) theo học kỳ: GPA học kỳ và GPA tích lũy. */
public class LineChartView extends View {

    // Cặp xanh dương - cam dễ phân biệt (kể cả với người mù màu), rõ trên cả nền sáng và tối
    public static final int SEMESTER_COLOR = 0xFF1E88E5;
    public static final int CUMULATIVE_COLOR = 0xFFF4511E;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);

    private List<String> labels = new ArrayList<>();
    private double[] semester = new double[0];
    private double[] cumulative = new double[0];

    public LineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        grid.setColor(Ui.color(context, com.google.android.material.R.attr.colorOutlineVariant));
        grid.setStrokeWidth(Ui.dp(context, 1));
        label.setColor(Ui.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        label.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics()));
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Ui.dp(context, 3));
    }

    public void setData(List<String> labels, double[] semester, double[] cumulative) {
        this.labels = labels;
        this.semester = semester;
        this.cumulative = cumulative;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float left = Ui.dp(getContext(), 24);
        float right = getWidth() - Ui.dp(getContext(), 16);
        float top = Ui.dp(getContext(), 8);
        float bottom = getHeight() - Ui.dp(getContext(), 22);

        // Lưới ngang 0..4 kèm số
        for (int g = 0; g <= 4; g++) {
            float gy = y(g, top, bottom);
            canvas.drawLine(left, gy, right, gy, grid);
            String text = String.valueOf(g);
            canvas.drawText(text, left - label.measureText(text) - Ui.dp(getContext(), 8), gy + label.getTextSize() / 3, label);
        }
        // Tên học kỳ dưới trục ngang
        for (int i = 0; i < labels.size(); i++) {
            String text = labels.get(i);
            canvas.drawText(text, x(i, left, right) - label.measureText(text) / 2, bottom + label.getTextSize() + Ui.dp(getContext(), 4), label);
        }
        series(canvas, cumulative, CUMULATIVE_COLOR, left, right, top, bottom);
        series(canvas, semester, SEMESTER_COLOR, left, right, top, bottom);
    }

    private void series(Canvas canvas, double[] values, int color, float left, float right, float top, float bottom) {
        Path path = new Path();
        for (int i = 0; i < values.length; i++) {
            if (i == 0) path.moveTo(x(i, left, right), y(values[i], top, bottom));
            else path.lineTo(x(i, left, right), y(values[i], top, bottom));
        }
        line.setColor(color);
        canvas.drawPath(path, line);
        point.setColor(color);
        for (int i = 0; i < values.length; i++) {
            canvas.drawCircle(x(i, left, right), y(values[i], top, bottom), Ui.dp(getContext(), 5), point);
        }
    }

    private float x(int i, float left, float right) {
        int n = labels.size();
        return n == 1 ? (left + right) / 2 : left + i * (right - left) / (n - 1);
    }

    private static float y(double gpa, float top, float bottom) {
        return bottom - (float) (gpa / 4.0) * (bottom - top);
    }
}
