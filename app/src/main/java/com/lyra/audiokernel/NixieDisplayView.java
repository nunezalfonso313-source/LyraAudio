package com.lyra.audiokernel;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class NixieDisplayView extends View {
    private String displayStr = "0000";
    private boolean sepVisible = true;
    private long lastBlink = 0;
    
    private Paint tubeBgPaint, tubeGlowPaint, digitPaint, dimDigitPaint, sepPaint, glassPaint;
    private float tubeW, tubeH, tubeRadius, digitSize;
    private float[] tubeX = new float[4];
    private float sepX;

    public NixieDisplayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    private void init() {
        tubeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tubeBgPaint.setColor(Color.parseColor("#120A00"));
        tubeBgPaint.setStyle(Paint.Style.FILL);
        
        tubeGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tubeGlowPaint.setColor(Color.parseColor("#FF8C00"));
        tubeGlowPaint.setStyle(Paint.Style.STROKE);
        tubeGlowPaint.setStrokeWidth(1.5f);
        tubeGlowPaint.setShadowLayer(10f, 0f, 0f, Color.parseColor("#FFAA00"));
        
        digitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        digitPaint.setColor(Color.parseColor("#FFB300"));
        digitPaint.setTextAlign(Paint.Align.CENTER);
        digitPaint.setTypeface(Typeface.MONOSPACE);
        digitPaint.setShadowLayer(16f, 0f, 0f, Color.parseColor("#FF8C00"));
        
        dimDigitPaint = new Paint(digitPaint);
        dimDigitPaint.setColor(Color.parseColor("#2A1800"));
        dimDigitPaint.setShadowLayer(0, 0, 0, 0);
        dimDigitPaint.setAlpha(60);
        
        sepPaint = new Paint(digitPaint);
        
        glassPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassPaint.setColor(Color.argb(18, 255, 255, 255));
        glassPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        recalcLayout(w, h);
    }

    private void recalcLayout(int w, int h) {
        float sepRatio = 0.38f;
        float gapRatio = 0.12f;
        
        tubeW = (w * 0.92f) / (4f + sepRatio + 4f * gapRatio);
        tubeH = h * 0.88f;
        tubeRadius = tubeW * 0.18f;
        digitSize = tubeH * 0.62f;
        
        digitPaint.setTextSize(digitSize);
        dimDigitPaint.setTextSize(digitSize);
        sepPaint.setTextSize(digitSize * 0.9f);
        
        float gap = tubeW * gapRatio;
        float offsetX = (w - (4 * tubeW + (tubeW * sepRatio) + 4 * gap)) / 2f;
        
        tubeX[0] = offsetX;
        tubeX[1] = tubeX[0] + tubeW + gap;
        sepX = tubeX[1] + tubeW + gap * 0.5f;
        tubeX[2] = sepX + tubeW * sepRatio + gap * 0.5f;
        tubeX[3] = tubeX[2] + tubeW + gap;
    }

    public void setTime(long ms, boolean isPlaying) {
        long s = ms / 1000;
        displayStr = String.format("%02d%02d", (int) (s / 60) % 100, (int) (s % 60));
        
        if (isPlaying && System.currentTimeMillis() - lastBlink > 500) {
            sepVisible = !sepVisible;
            lastBlink = System.currentTimeMillis();
        } else if (!isPlaying) {
            sepVisible = true;
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int h = getHeight();
        float tubeTop = (h - tubeH) / 2f;
        
        if (tubeW <= 0) recalcLayout(getWidth(), h);
        
        char[] digits = displayStr.length() == 4 ? 
            displayStr.toCharArray() : new char[]{'0', '0', '0', '0'};
        
        for (int i = 0; i < 4; i++) {
            drawTube(canvas, tubeX[i], tubeTop, digits[i]);
        }
        
        if (sepVisible) {
            sepPaint.setColor(Color.parseColor("#FFB300"));
            sepPaint.setShadowLayer(14f, 0f, 0f, Color.parseColor("#FF8C00"));
        } else {
            sepPaint.setColor(Color.parseColor("#2A1800"));
            sepPaint.setShadowLayer(0f, 0f, 0f, 0);
        }
        
        canvas.drawText(":", sepX + (tubeW * 0.38f) / 2f, 
            tubeTop + tubeH * 0.68f, sepPaint);
    }

    private void drawTube(Canvas canvas, float x, float top, char d) {
        RectF r = new RectF(x, top, x + tubeW, top + tubeH);
        
        canvas.drawRoundRect(r, tubeRadius, tubeRadius, tubeBgPaint);
        canvas.drawRoundRect(r, tubeRadius, tubeRadius, tubeGlowPaint);
        canvas.drawRoundRect(new RectF(x + 2, top + 2, x + tubeW - 2, top + tubeH * 0.32f), 
            tubeRadius, tubeRadius, glassPaint);
        
        canvas.drawText("8", x + tubeW / 2f, top + tubeH * 0.70f, dimDigitPaint);
        canvas.drawText(String.valueOf(d), x + tubeW / 2f, top + tubeH * 0.70f, digitPaint);
    }
}
