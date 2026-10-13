package com.appgiuaky.gpaplanner.components.dashboard;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.ColorUtils;

import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.components.Ui;
import com.appgiuaky.gpaplanner.utils.Format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Biểu đồ đường GPA (thang 0-4) theo học kỳ: GPA học kỳ (đường liền, có tô nền) và GPA tích lũy (đường đứt).
 * Đường được vẽ dần từ trái sang khi có dữ liệu mới; chạm vào một kỳ để xem số liệu của kỳ đó.
 */
public class LineChartView extends View {

    private static final double MAX_GPA = 4.0;
    private static final int GRID_STEPS = 4;
    private static final long DRAW_IN_MS = 900;
    /** Longer semester names are cut with "…" so they cannot run into each other. */
    private static final int MAX_LABEL_DP = 64;
    private static final int LABEL_GAP_DP = 8;
    private static final int PAD_LEFT_DP = 28;
    private static final int PAD_RIGHT_DP = 16;
    /** Chừa chỗ phía trên cho số GPA của điểm cao nhất. */
    private static final int PAD_TOP_DP = 24;
    private static final int PAD_BOTTOM_DP = 24;
    private static final int DOT_DP = 5;
    private static final int DOT_SELECTED_DP = 7;
    /** Chấm là vòng tròn rỗng: lỗ giữa bằng nửa bán kính, cùng màu nền thẻ. */
    private static final float DOT_HOLE_RATIO = 0.5f;
    private static final int VALUE_OFFSET_DP = 10;
    /** Độ đậm (0-255) của vùng tô ở mép trên; nhạt dần về 0 ở trục ngang. */
    private static final int AREA_ALPHA = 0x59;
    private static final int TOOLTIP_PAD_DP = 8;
    private static final int TOOLTIP_GAP_DP = 12;
    private static final int TOOLTIP_RADIUS_DP = 8;
    private static final int SWATCH_DP = 8;
    private static final float LINE_HEIGHT = 1.4f;
    private static final int NO_SELECTION = -1;

    private final int semesterColor;
    private final int cumulativeColor;
    private final int cardColor;

    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint guide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint axisLabel = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint valueLabel = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Paint semesterLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cumulativeLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tooltip = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint tooltipText = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF box = new RectF();

    private float left;
    private float right;
    private float top;
    private float bottom;

    private List<String> fullLabels = new ArrayList<>();
    private List<String> labels = new ArrayList<>();
    private float widestLabel;
    private double[] semester = new double[0];
    private double[] cumulative = new double[0];
    private float progress = 1f;
    private int selected = NO_SELECTION;
    private float touchX = Float.NaN;
    private ValueAnimator animator;

    public LineChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        semesterColor = Ui.color(context, androidx.appcompat.R.attr.colorPrimary);
        cumulativeColor = Ui.color(context, com.google.android.material.R.attr.colorTertiary);
        cardColor = ContextCompat.getColor(context, R.color.card_background);
        Typeface font = ResourcesCompat.getFont(context, R.font.be_vietnam_pro);

        grid.setStyle(Paint.Style.STROKE);
        grid.setColor(Ui.color(context, com.google.android.material.R.attr.colorOutlineVariant));
        grid.setStrokeWidth(Ui.dp(context, 1));
        grid.setPathEffect(new DashPathEffect(new float[]{Ui.dp(context, 4), Ui.dp(context, 4)}, 0));
        guide.set(grid);
        guide.setColor(Ui.color(context, com.google.android.material.R.attr.colorOutline));

        axisLabel.setColor(Ui.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        axisLabel.setTextSize(sp(11));
        axisLabel.setTypeface(font);
        valueLabel.setColor(semesterColor);
        valueLabel.setTextSize(sp(11));
        valueLabel.setTypeface(Typeface.create(font, Typeface.BOLD));

        semesterLine.setStyle(Paint.Style.STROKE);
        semesterLine.setStrokeWidth(Ui.dp(context, 3));
        semesterLine.setStrokeCap(Paint.Cap.ROUND);
        semesterLine.setStrokeJoin(Paint.Join.ROUND);
        semesterLine.setColor(semesterColor);
        cumulativeLine.set(semesterLine);
        cumulativeLine.setStrokeWidth(Ui.dp(context, 2.5f));
        cumulativeLine.setColor(cumulativeColor);
        // Nét đứt để hai đường vẫn phân biệt được khi không nhìn rõ màu
        cumulativeLine.setPathEffect(new DashPathEffect(new float[]{Ui.dp(context, 6), Ui.dp(context, 5)}, 0));

        tooltip.setColor(Ui.color(context, com.google.android.material.R.attr.colorSurfaceInverse));
        tooltipText.setColor(Ui.color(context, com.google.android.material.R.attr.colorOnSurfaceInverse));
        tooltipText.setTextSize(sp(12));
        tooltipText.setTypeface(font);

        setClickable(true);
    }

    public int getSemesterColor() {
        return semesterColor;
    }

    public int getCumulativeColor() {
        return cumulativeColor;
    }

    public void setData(List<String> labels, double[] semester, double[] cumulative) {
        boolean changed = !labels.equals(fullLabels) || !Arrays.equals(semester, this.semester)
                || !Arrays.equals(cumulative, this.cumulative);
        float maxWidth = Ui.dp(getContext(), MAX_LABEL_DP);
        fullLabels = new ArrayList<>(labels);
        this.labels = new ArrayList<>();
        widestLabel = 0;
        for (String l : labels) {
            String shortened = TextUtils.ellipsize(l, axisLabel, maxWidth, TextUtils.TruncateAt.END).toString();
            this.labels.add(shortened);
            widestLabel = Math.max(widestLabel, axisLabel.measureText(shortened));
        }
        this.semester = semester;
        this.cumulative = cumulative;
        if (changed) {
            selected = NO_SELECTION;
            animateIn();
        } else {
            invalidate();
        }
    }

    /** Vẽ lại đường từ trái sang; gọi khi dữ liệu đổi hoặc khi tab Tổng quan vừa được mở. */
    public void animateIn() {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(DRAW_IN_MS);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            progress = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        progress = 1f;
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        left = Ui.dp(getContext(), PAD_LEFT_DP);
        right = w - Ui.dp(getContext(), PAD_RIGHT_DP);
        top = Ui.dp(getContext(), PAD_TOP_DP);
        bottom = h - Ui.dp(getContext(), PAD_BOTTOM_DP);
        area.setShader(new LinearGradient(0, top, 0, bottom,
                ColorUtils.setAlphaComponent(semesterColor, AREA_ALPHA), Color.TRANSPARENT, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawGrid(canvas);
        if (labels.isEmpty()) return;
        drawSemesterLabels(canvas);

        // Phần đã "vẽ tới" theo tiến độ hiệu ứng; vẽ xong thì bỏ cắt để số GPA ở điểm cuối không bị cụt
        canvas.save();
        if (progress < 1f) canvas.clipRect(0, 0, left + (right - left) * progress, getHeight());
        if (semester.length > 1) {
            smoothPath(semester);
            path.lineTo(x(semester.length - 1), bottom);
            path.lineTo(x(0), bottom);
            path.close();
            canvas.drawPath(path, area);
        }
        smoothPath(cumulative);
        canvas.drawPath(path, cumulativeLine);
        smoothPath(semester);
        canvas.drawPath(path, semesterLine);
        for (int i = 0; i < cumulative.length; i++) drawDot(canvas, x(i), y(cumulative[i]), cumulativeColor, false);
        for (int i = 0; i < semester.length; i++) {
            drawDot(canvas, x(i), y(semester[i]), semesterColor, false);
            drawValue(canvas, i);
        }
        canvas.restore();

        if (selected != NO_SELECTION && progress >= 1f) drawSelection(canvas);
    }

    private void drawGrid(Canvas canvas) {
        for (int g = 0; g <= GRID_STEPS; g++) {
            double gpa = MAX_GPA * g / GRID_STEPS;
            float gy = y(gpa);
            path.reset();
            path.moveTo(left, gy);
            path.lineTo(right, gy);
            canvas.drawPath(path, grid);
            String text = Format.plain(gpa);
            canvas.drawText(text, left - axisLabel.measureText(text) - Ui.dp(getContext(), 8),
                    gy + axisLabel.getTextSize() / 3, axisLabel);
        }
    }

    /** Tên học kỳ dưới trục ngang; with many semesters only every [step]-th label fits. */
    private void drawSemesterLabels(Canvas canvas) {
        int n = labels.size();
        float slot = n <= 1 ? right - left : (right - left) / (n - 1);
        int step = Math.max(1, (int) Math.ceil((widestLabel + Ui.dp(getContext(), LABEL_GAP_DP)) / slot));
        for (int i = 0; i < n; i += step) {
            String text = labels.get(i);
            float width = axisLabel.measureText(text);
            // Keep the first and last labels inside the view instead of cutting them at the edge
            float lx = Math.max(0, Math.min(getWidth() - width, x(i) - width / 2));
            canvas.drawText(text, lx, bottom + axisLabel.getTextSize() + Ui.dp(getContext(), 6), axisLabel);
        }
    }

    /**
     * Đường cong qua các điểm: điểm điều khiển nằm ở giữa hai điểm theo chiều ngang và ngang bằng từng điểm,
     * nên đường không bao giờ vọt lên trên 4 hay xuống dưới 0.
     */
    private void smoothPath(double[] values) {
        path.reset();
        for (int i = 0; i < values.length; i++) {
            float px = x(i);
            float py = y(values[i]);
            if (i == 0) {
                path.moveTo(px, py);
                continue;
            }
            float prevX = x(i - 1);
            float prevY = y(values[i - 1]);
            float midX = (prevX + px) / 2;
            path.cubicTo(midX, prevY, midX, py, px, py);
        }
    }

    private void drawDot(Canvas canvas, float cx, float cy, int color, boolean big) {
        float r = Ui.dp(getContext(), big ? DOT_SELECTED_DP : DOT_DP);
        dot.setColor(color);
        canvas.drawCircle(cx, cy, r, dot);
        dot.setColor(cardColor);
        canvas.drawCircle(cx, cy, r * DOT_HOLE_RATIO, dot);
    }

    /** Số GPA kỳ, đặt về phía không có đường tích lũy để hai thứ không đè nhau. */
    private void drawValue(Canvas canvas, int i) {
        String text = Format.fmt(semester[i]);
        float px = x(i);
        float py = y(semester[i]);
        float offset = Ui.dp(getContext(), VALUE_OFFSET_DP);
        float below = py + offset + valueLabel.getTextSize();
        boolean placeBelow = cumulative[i] > semester[i] && below < bottom;
        float baseline = placeBelow ? below : py - offset;
        float width = valueLabel.measureText(text);
        canvas.drawText(text, Math.max(0, Math.min(getWidth() - width, px - width / 2)), baseline, valueLabel);
    }

    private void drawSelection(Canvas canvas) {
        float cx = x(selected);
        path.reset();
        path.moveTo(cx, top);
        path.lineTo(cx, bottom);
        canvas.drawPath(path, guide);
        float semesterY = y(semester[selected]);
        float cumulativeY = y(cumulative[selected]);
        drawDot(canvas, cx, cumulativeY, cumulativeColor, true);
        drawDot(canvas, cx, semesterY, semesterColor, true);

        String[] lines = {
                fullLabels.get(selected),
                getContext().getString(R.string.chart_tooltip_semester, Format.fmt(semester[selected])),
                getContext().getString(R.string.chart_tooltip_cumulative, Format.fmt(cumulative[selected])),
        };
        int[] swatches = {Color.TRANSPARENT, semesterColor, cumulativeColor};
        float pad = Ui.dp(getContext(), TOOLTIP_PAD_DP);
        float swatch = Ui.dp(getContext(), SWATCH_DP);
        float lineHeight = tooltipText.getTextSize() * LINE_HEIGHT;
        float textWidth = 0;
        for (int i = 0; i < lines.length; i++) {
            float indent = swatches[i] == Color.TRANSPARENT ? 0 : swatch + pad / 2;
            textWidth = Math.max(textWidth, indent + tooltipText.measureText(lines[i]));
        }
        float w = textWidth + pad * 2;
        float h = lineHeight * lines.length + pad * 2;

        // Đặt trên điểm cao hơn; nếu không đủ chỗ thì đặt dưới điểm thấp hơn
        float gap = Ui.dp(getContext(), TOOLTIP_GAP_DP);
        float boxTop = Math.min(semesterY, cumulativeY) - gap - h;
        if (boxTop < 0) boxTop = Math.min(getHeight() - h, Math.max(semesterY, cumulativeY) + gap);
        float boxLeft = Math.max(0, Math.min(getWidth() - w, cx - w / 2));
        box.set(boxLeft, boxTop, boxLeft + w, boxTop + h);
        float radius = Ui.dp(getContext(), TOOLTIP_RADIUS_DP);
        canvas.drawRoundRect(box, radius, radius, tooltip);

        for (int i = 0; i < lines.length; i++) {
            float baseline = boxTop + pad + lineHeight * i + tooltipText.getTextSize();
            float textX = boxLeft + pad;
            if (swatches[i] != Color.TRANSPARENT) {
                dot.setColor(swatches[i]);
                canvas.drawCircle(textX + swatch / 2, baseline - tooltipText.getTextSize() / 3, swatch / 2, dot);
                textX += swatch + pad / 2;
            }
            canvas.drawText(lines[i], textX, baseline, tooltipText);
        }
    }

    // super.onTouchEvent gọi performClick khi chạm (không kéo), nên lint báo nhầm ở đây
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Ghi lại chỗ chạm; việc chọn kỳ làm trong performClick để kéo cuộn trang không bị tính là chạm
        if (event.getActionMasked() == MotionEvent.ACTION_UP) touchX = event.getX();
        return super.onTouchEvent(event);
    }

    /** Chọn kỳ gần chỗ chạm (chạm lại để bỏ chọn). TalkBack không có toạ độ nên chuyển lần lượt từng kỳ. */
    @Override
    public boolean performClick() {
        if (!labels.isEmpty()) {
            int target;
            if (Float.isNaN(touchX)) target = selected + 1 < labels.size() ? selected + 1 : NO_SELECTION;
            else target = nearestIndex(touchX);
            selected = target == selected ? NO_SELECTION : target;
            touchX = Float.NaN;
            invalidate();
        }
        return super.performClick();
    }

    private int nearestIndex(float touch) {
        int n = labels.size();
        if (n == 1) return 0;
        int i = Math.round((touch - left) / ((right - left) / (n - 1)));
        return Math.max(0, Math.min(n - 1, i));
    }

    private float x(int i) {
        int n = labels.size();
        return n == 1 ? (left + right) / 2 : left + i * (right - left) / (n - 1);
    }

    private float y(double gpa) {
        return bottom - (float) (gpa / MAX_GPA) * (bottom - top);
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
