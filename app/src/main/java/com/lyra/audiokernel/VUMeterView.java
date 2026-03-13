package com.lyra.audiokernel;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class VUMeterView extends View {
    private float currentLevel = 0f;
    private float targetLevel = 0f;
    private Paint paintArc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Paint paintNeedle = new Paint(Paint.ANTI_ALIAS_FLAG);

    public VUMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paintArc.setColor(Color.parseColor("#332200"));
        paintArc.setStyle(Paint.Style.STROKE);
        paintArc.setStrokeWidth(4f);
        
        paintNeedle.setColor(Color.parseColor("#FF8800"));
        paintNeedle.setStrokeWidth(6f);
        paintNeedle.setStrokeCap(Paint.Cap.ROUND);
    }

    public void updateLevel(float level) {
        this.targetLevel = level;
        // Inercia magnética calculada
        this.currentLevel += (targetLevel - currentLevel) * 0.2f; 
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() * 0.9f;
        float radius = getWidth() * 0.8f;

        RectF oval = new RectF(cx - radius, cy - radius, cx + radius, cy + radius);
        canvas.drawArc(oval, 210, 120, false, paintArc);

        float angle = 210 + (currentLevel * 120);
        float stopX = (float) (cx + Math.cos(Math.toRadians(angle)) * radius);
        float stopY = (float) (cy + Math.sin(Math.toRadians(angle)) * radius);

        canvas.drawLine(cx, cy, stopX, stopY, paintNeedle);
    }
}

