package com.appgiuaky.gpaplanner.components.curriculum;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.appgiuaky.gpaplanner.components.Ui;

/** Đường nối hình "├─" (hoặc "└─" cho môn cuối) giữa học kỳ và môn học trên cây chương trình. */
public class TreeLineView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private boolean last;

    public TreeLineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint.setColor(Ui.color(context, com.google.android.material.R.attr.colorOutlineVariant));
        paint.setStrokeWidth(Ui.dp(context, 2));
    }

    public void setLast(boolean last) {
        this.last = last;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float x = Ui.dp(getContext(), 4);
        float y = getHeight() / 2f;
        canvas.drawLine(x, 0, x, last ? y : getHeight(), paint);
        canvas.drawLine(x, y, getWidth(), y, paint);
    }
}
