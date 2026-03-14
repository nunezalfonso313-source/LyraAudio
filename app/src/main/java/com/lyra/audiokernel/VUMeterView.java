package com.lyra.audiokernel;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class VUMeterView extends View {
    private static final float ATTACK = 0.45f;
    private static final float DECAY = 0.05f;
    private static final float ANGLE_MIN = -50f;
    private static final float ANGLE_MAX = 50f;

    private float rawLevel = 0f;
    private float displayLevel = 0f;
    private String label = "L";

    private Paint bgPaint, arcGreenPaint, arcRedPaint, tickPaint;
    private Paint needlePaint, needleShadowPaint, pivotPaint, labelPaint;

    public VUMeterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#1C1200"));
        bgPaint.setStyle(Paint.Style.FILL);

        arcGreenPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcGreenPaint.setStyle(Paint.Style.STROKE);
        arcGreenPaint.setStrokeWidth(5f);
        arcGreenPaint.setColor(Color.parseColor("#44BB44"));
        arcGreenPaint.setAlpha(120);

        arcRedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        arcRedPaint.setStyle(Paint.Style.STROKE);
        arcRedPaint.setStrokeWidth(5f);
        arcRedPaint.setColor(Color.parseColor("#FF3300"));
        arcRedPaint.setAlpha(140);

        tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickPaint.setStrokeWidth(1.2f);

        needleShadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        needleShadowPaint.setColor(Color.parseColor("#CC2200"));
        needleShadowPaint.setStrokeWidth(4f);
        needleShadowPaint.setAlpha(80);

        needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        needlePaint.setStrokeWidth(2f);
        needlePaint.setShadowLayer(6f, 0f, 0f, Color.parseColor("#FF4400"));
        needlePaint.setStyle(Paint.Style.STROKE);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);

        pivotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        pivotPaint.setColor(Color.parseColor("#888888"));
        pivotPaint.setStyle(Paint.Style.FILL);

        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(Color.parseColor("#E89009"));
        labelPaint.setTextSize(18f);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTypeface(Typeface.MONOSPACE);
        labelPaint.setShadowLayer(6f, 0f, 0f, Color.parseColor("#FF8800"));
    }

    public void setLevel(float level) {
        rawLevel = Math.max(0f, Math.min(1f, level));
        float diff = rawLevel - displayLevel;
        displayLevel += (diff > 0) ? diff * ATTACK : diff * DECAY;
        postInvalidateOnAnimation();
    }

    public void setChannelLabel(String l) {
        this.label = l;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        float cx = w / 2f;
        float pivotY = h * 1.05f;
        float arcRadius = pivotY - h * 0.12f;

        // Fondo
        canvas.drawRoundRect(new RectF(0, 0, w, h), 12f, 12f, bgPaint);

        // Arco
        RectF arcRect = new RectF(cx - arcRadius, pivotY - arcRadius, cx + arcRadius, pivotY + arcRadius);
        float startAngle = 270f + ANGLE_MIN;
        float sweep = ANGLE_MAX - ANGLE_MIN;
        canvas.drawArc(arcRect, startAngle, sweep * 0.75f, false, arcGreenPaint);
        canvas.drawArc(arcRect, startAngle + sweep * 0.75f, sweep * 0.25f, false, arcRedPaint);

        // Marcas
        for (int i = 0; i <= 9; i++) {
            float frac = i / 9f;
            double rad = Math.toRadians(270f + ANGLE_MIN + frac * sweep);
            float ox = cx + (float) ((arcRadius + 4f) * Math.cos(rad));
            float oy = pivotY + (float) ((arcRadius + 4f) * Math.sin(rad));
            float ix = cx + (float) ((arcRadius - (i % 2 == 0 ? 10f : 6f)) * Math.cos(rad));
            float iy = pivotY + (float) ((arcRadius - (i % 2 == 0 ? 10f : 6f)) * Math.sin(rad));
            tickPaint.setColor(frac > 0.75f ? Color.parseColor("#FF4400") : Color.parseColor("#AA8833"));
            tickPaint.setStrokeWidth(i % 2 == 0 ? 1.5f : 1f);
            canvas.drawLine(ox, oy, ix, iy, tickPaint);
        }

        // Aguja
        double rad = Math.toRadians(270f + ANGLE_MIN + displayLevel * sweep);
        float tipX = cx + (float) ((arcRadius - 8f) * Math.cos(rad));
        float tipY = pivotY + (float) ((arcRadius - 8f) * Math.sin(rad));
        canvas.drawLine(cx, pivotY, tipX + 1f, tipY + 1f, needleShadowPaint);
        needlePaint.setColor(displayLevel > 0.8f ? Color.parseColor("#FF2200") : Color.parseColor("#DDCCBB"));
        canvas.drawLine(cx, pivotY, tipX, tipY, needlePaint);

        // Pivote
        canvas.drawCircle(cx, pivotY, 5f, pivotPaint);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(Color.parseColor("#222222"));
        canvas.drawCircle(cx, pivotY, 2.5f, inner);

        // Etiquetas
        canvas.drawText("VU", cx, h - 4f, labelPaint);
        Paint ch = new Paint(labelPaint);
        ch.setTextSize(13f);
        ch.setColor(Color.parseColor("#AA7722"));
        canvas.drawText(label, 14f, h - 6f, ch);
    }
}
