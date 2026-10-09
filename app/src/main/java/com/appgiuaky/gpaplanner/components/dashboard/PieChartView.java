package com.appgiuaky.gpaplanner.components.dashboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.appgiuaky.gpaplanner.components.Ui;

import java.util.ArrayList;
import java.util.List;

/** Biểu đồ vành khuyên: số môn theo từng điểm chữ, tổng số môn ở giữa. */
public class PieChartView extends View {

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint center = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();

    private List<String> letters = new ArrayList<>();
    private int[] counts = new int[0];

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(Ui.dp(context, 26));
        center.setColor(Ui.color(context, com.google.android.material.R.attr.colorOnSurface));
        center.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 14, getResources().getDisplayMetrics()));
        center.setFakeBoldText(true);
    }

    public void setData(List<String> letters, int[] counts) {
        this.letters = letters;
        this.counts = counts;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int total = 0;
        for (int n : counts) total += n;
        if (total == 0) return;

        float stroke = arc.getStrokeWidth();
        bounds.set(stroke / 2, stroke / 2, getWidth() - stroke / 2, getHeight() - stroke / 2);
        float start = -90f;
        for (int i = 0; i < counts.length; i++) {
            float sweep = 360f * counts[i] / total;
            arc.setColor(Ui.gradeColor(letters.get(i)));
            canvas.drawArc(bounds, start, sweep, false, arc);
            start += sweep;
        }
        String text = total + " môn";
        canvas.drawText(text, (getWidth() - center.measureText(text)) / 2, getHeight() / 2f + center.getTextSize() / 3, center);
    }
}
