package com.appgiuaky.gpaplanner.components.dashboard;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.res.ResourcesCompat;

import com.appgiuaky.gpaplanner.R;
import com.appgiuaky.gpaplanner.components.Ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Biểu đồ vành khuyên: số môn theo từng điểm chữ, tổng số môn ở giữa.
 * Vành quay dần khi có dữ liệu mới; chạm vào một phần để làm nổi phần đó và xem số môn của nó.
 */
public class PieChartView extends View {

    private static final long SWEEP_IN_MS = 900;
    private static final float START_ANGLE = -90f;
    private static final float FULL_CIRCLE = 360f;
    /** Khe hở giữa hai phần, để các màu gần nhau (A và B+) vẫn tách bạch. */
    private static final float GAP_DEGREES = 3f;
    private static final int RING_DP = 22;
    private static final int SELECTED_EXTRA_DP = 8;
    /** Chạm hơi lệch vào trong vành vẫn tính là chạm vào vành. */
    private static final int TOUCH_SLOP_DP = 12;
    private static final int CENTER_GAP_DP = 2;
    /** Phần chữ nằm trên đường chân chữ, so với cỡ chữ (chữ số không có phần đuôi bên dưới). */
    private static final float ASCENT_RATIO = 0.85f;
    private static final int NO_SELECTION = -1;

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint centerValue = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint centerLabel = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bounds = new RectF();
    private final int onSurface;

    private List<String> letters = new ArrayList<>();
    private int[] counts = new int[0];
    private String totalValue = "";
    private String totalLabel = "";
    private float progress = 1f;
    private int selected = NO_SELECTION;
    private float touchX = Float.NaN;
    private float touchY = Float.NaN;
    private ValueAnimator animator;

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Typeface font = ResourcesCompat.getFont(context, R.font.be_vietnam_pro);
        onSurface = Ui.color(context, com.google.android.material.R.attr.colorOnSurface);
        arc.setStyle(Paint.Style.STROKE);
        track.setStyle(Paint.Style.STROKE);
        track.setColor(Ui.color(context, com.google.android.material.R.attr.colorSurfaceContainerHighest));
        centerValue.setTextSize(sp(24));
        centerValue.setTypeface(Typeface.create(font, Typeface.BOLD));
        centerLabel.setColor(Ui.color(context, com.google.android.material.R.attr.colorOnSurfaceVariant));
        centerLabel.setTextSize(sp(12));
        centerLabel.setTypeface(font);
        setClickable(true);
    }

    /** [totalValue] và [totalLabel] hiện ở giữa vành, ví dụ "14" và "môn". */
    public void setData(List<String> letters, int[] counts, String totalValue, String totalLabel) {
        boolean changed = !letters.equals(this.letters) || !Arrays.equals(counts, this.counts);
        this.letters = new ArrayList<>(letters);
        this.counts = counts;
        this.totalValue = totalValue;
        this.totalLabel = totalLabel;
        if (changed) {
            selected = NO_SELECTION;
            animateIn();
        } else {
            invalidate();
        }
    }

    /** Quay vành từ đầu; gọi khi dữ liệu đổi hoặc khi tab Tổng quan vừa được mở. */
    public void animateIn() {
        if (animator != null) animator.cancel();
        animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(SWEEP_IN_MS);
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

    private int total() {
        int total = 0;
        for (int n : counts) total += n;
        return total;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int total = total();
        if (total == 0) return;

        float ring = Ui.dp(getContext(), RING_DP);
        float extra = Ui.dp(getContext(), SELECTED_EXTRA_DP);
        float size = Math.min(getWidth(), getHeight());
        float inset = (ring + extra) / 2;
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        bounds.set(cx - size / 2 + inset, cy - size / 2 + inset, cx + size / 2 - inset, cy + size / 2 - inset);

        track.setStrokeWidth(ring);
        canvas.drawOval(bounds, track);

        float gap = counts.length > 1 ? GAP_DEGREES : 0f;
        float shown = FULL_CIRCLE * progress;
        float start = 0f;
        for (int i = 0; i < counts.length; i++) {
            float sweep = FULL_CIRCLE * counts[i] / total;
            float visible = Math.min(sweep, shown - start) - gap;
            if (visible > 0) {
                arc.setColor(Ui.gradeColor(letters.get(i)));
                arc.setStrokeWidth(i == selected ? ring + extra : ring);
                canvas.drawArc(bounds, START_ANGLE + start + gap / 2, visible, false, arc);
            }
            start += sweep;
        }
        drawCenter(canvas, cx, cy);
    }

    /** Giữa vành: tổng số môn, hoặc điểm chữ và số môn của phần đang chọn. */
    private void drawCenter(Canvas canvas, float cx, float cy) {
        String value = totalValue;
        String label = totalLabel;
        centerValue.setColor(onSurface);
        if (selected != NO_SELECTION) {
            value = letters.get(selected);
            label = getContext().getString(R.string.course_count, counts[selected]);
            centerValue.setColor(Ui.gradeColor(value));
        }
        float gap = Ui.dp(getContext(), CENTER_GAP_DP);
        float blockTop = cy - (centerValue.getTextSize() + gap + centerLabel.getTextSize()) / 2;
        float valueBaseline = blockTop + centerValue.getTextSize() * ASCENT_RATIO;
        float labelBaseline = valueBaseline + gap + centerLabel.getTextSize();
        canvas.drawText(value, cx - centerValue.measureText(value) / 2, valueBaseline, centerValue);
        canvas.drawText(label, cx - centerLabel.measureText(label) / 2, labelBaseline, centerLabel);
    }

    // super.onTouchEvent gọi performClick khi chạm (không kéo), nên lint báo nhầm ở đây
    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Ghi lại chỗ chạm; việc chọn phần làm trong performClick để kéo cuộn trang không bị tính là chạm
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            touchX = event.getX();
            touchY = event.getY();
        }
        return super.onTouchEvent(event);
    }

    /** Chọn phần dưới ngón tay (chạm lại hoặc chạm ra ngoài vành để bỏ chọn). TalkBack chuyển lần lượt từng phần. */
    @Override
    public boolean performClick() {
        if (total() > 0) {
            int target;
            if (Float.isNaN(touchX)) target = selected + 1 < counts.length ? selected + 1 : NO_SELECTION;
            else target = sliceAt(touchX, touchY);
            selected = target == selected ? NO_SELECTION : target;
            touchX = Float.NaN;
            touchY = Float.NaN;
            invalidate();
        }
        return super.performClick();
    }

    private int sliceAt(float x, float y) {
        float dx = x - bounds.centerX();
        float dy = y - bounds.centerY();
        double distance = Math.hypot(dx, dy);
        float radius = bounds.width() / 2;
        float halfRing = (Ui.dp(getContext(), RING_DP) + Ui.dp(getContext(), SELECTED_EXTRA_DP)) / 2f;
        if (distance < radius - halfRing - Ui.dp(getContext(), TOUCH_SLOP_DP) || distance > radius + halfRing) {
            return NO_SELECTION;
        }
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        double fromStart = ((angle - START_ANGLE) % FULL_CIRCLE + FULL_CIRCLE) % FULL_CIRCLE;
        int total = total();
        float start = 0f;
        for (int i = 0; i < counts.length; i++) {
            start += FULL_CIRCLE * counts[i] / total;
            if (fromStart < start) return i;
        }
        return counts.length - 1;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, getResources().getDisplayMetrics());
    }
}
