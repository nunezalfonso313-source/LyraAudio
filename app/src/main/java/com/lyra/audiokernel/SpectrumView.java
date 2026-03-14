package com.lyra.audiokernel;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

public class SpectrumView extends View {
    private static final int BANDS = 32;
    private static final int PEAK_HOLD = 35;
    private static final float ATTACK = 0.40f;
    private static final float DECAY = 0.07f;
    
    private float[] rawBands = new float[BANDS];
    private float[] smoothBands = new float[BANDS];
    private float[] peaks = new float[BANDS];
    private int[] peakTimers = new int[BANDS];
    
    private Paint gridPaint, peakPaint, bgPaint, barPaint;
    private RectF barRect = new RectF();

    public SpectrumView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setColor(Color.parseColor("#050A0D"));
        
        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#0D2020"));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);
        
        barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        peakPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        peakPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        barPaint.setShader(new LinearGradient(0, h, 0, 0,
            new int[]{
                Color.parseColor("#00BCD4"),
                Color.parseColor("#00E676"),
                Color.parseColor("#AAFF00"),
                Color.parseColor("#FF8800"),
                Color.parseColor("#FF2200")
            },
            new float[]{0f, 0.45f, 0.72f, 0.88f, 1f},
            Shader.TileMode.CLAMP));
    }

    public void updateFromFFT(byte[] fft, int size) {
        int n = Math.min(BANDS, size / 2);
        for (int i = 0; i < n; i++) {
            float re = fft[i * 2];
            float im = (i * 2 + 1 < size) ? fft[i * 2 + 1] : 0f;
            rawBands[i] = (float) Math.min(1.0, Math.log10(Math.hypot(re, im) + 1) / 2.5);
        }
        postInvalidateOnAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        
        canvas.drawRect(0, 0, w, h, bgPaint);
        
        for (int i = 1; i <= 4; i++) {
            canvas.drawLine(0, h * (i / 4f), w, h * (i / 4f), gridPaint);
        }
        
        float barW = (w - 1.5f * (BANDS - 1)) / BANDS;
        
        for (int i = 0; i < BANDS; i++) {
            float diff = rawBands[i] - smoothBands[i];
            smoothBands[i] = Math.max(0f, Math.min(1f, 
                smoothBands[i] + (diff > 0 ? diff * ATTACK : diff * DECAY)));
            
            if (smoothBands[i] >= peaks[i]) {
                peaks[i] = smoothBands[i];
                peakTimers[i] = PEAK_HOLD;
            } else if (peakTimers[i] > 0) {
                peakTimers[i]--;
            } else {
                peaks[i] = Math.max(0f, peaks[i] - 0.006f);
            }
            
            float x0 = i * (barW + 1.5f);
            float barH = smoothBands[i] * h;
            barRect.set(x0, h - barH, x0 + barW, h);
            canvas.drawRect(barRect, barPaint);
            
            if (barH > 3f) {
                Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
                t.setColor(Color.argb(60, 255, 255, 255));
                canvas.drawRect(x0, h - barH, x0 + barW, h - barH + 2f, t);
            }
            
            float py = h - peaks[i] * h;
            peakPaint.setColor(peaks[i] > 0.88f ? 
                Color.parseColor("#FF2200") : 
                (peaks[i] > 0.65f ? Color.parseColor("#AAFF00") : Color.parseColor("#00E676")));
            canvas.drawRect(x0, py, x0 + barW, py + 2.5f, peakPaint);
        }
    }
}
