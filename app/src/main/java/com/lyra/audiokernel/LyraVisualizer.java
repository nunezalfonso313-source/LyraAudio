package com.lyra.audiokernel;

import android.media.audiofx.Visualizer;

public class LyraVisualizer {
    private static final int CAPTURE_SIZE = 512;
    private Visualizer visualizer;
    private VUMeterView vuLeft, vuRight;
    private SpectrumView spectrumView;
    private boolean active = false;

    public interface VisualizerReadyCallback {
        void onReady();
        void onError(String msg);
    }

    public LyraVisualizer(VUMeterView left, VUMeterView right, SpectrumView spectrum) {
        this.vuLeft = left;
        this.vuRight = right;
        this.spectrumView = spectrum;
    }

    public void init(int audioSessionId, VisualizerReadyCallback callback) {
        release();
        try {
            visualizer = new Visualizer(audioSessionId);
            int[] range = Visualizer.getCaptureSizeRange();
            int size = Math.max(range[0], Math.min(CAPTURE_SIZE, range[1]));
            visualizer.setCaptureSize(size);
            final int captureSize = visualizer.getCaptureSize();

            visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                @Override
                public void onWaveFormDataCapture(Visualizer v, byte[] waveform, int samplingRate) {
                    if (!active || waveform == null) return;
                    int half = waveform.length / 2;
                    float rmsL = computeRMS(waveform, 0, half);
                    float rmsR = computeRMS(waveform, half, waveform.length);
                    float normL = Math.min(1f, rmsL / 64f);
                    float normR = Math.min(1f, rmsR / 64f);
                    if (vuLeft != null) vuLeft.post(() -> vuLeft.setLevel(normL));
                    if (vuRight != null) vuRight.post(() -> vuRight.setLevel(normR));
                }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int sr) {
                    if (active && fft != null && spectrumView != null)
                        spectrumView.updateFromFFT(fft, captureSize);
                }
            }, Math.min(Visualizer.getMaxCaptureRate(), 30000), true, true);

            visualizer.setEnabled(true);
            active = true;
            if (callback != null) callback.onReady();
        } catch (Exception e) { 
            if (callback != null) callback.onError(e.getMessage()); 
        }
    }

    private float computeRMS(byte[] data, int from, int to) {
        long sum = 0;
        for (int i = from; i < to; i++) { 
            int v = data[i]; 
            sum += v * v; 
        }
        return (float) Math.sqrt((double) sum / (to - from));
    }

    public void start() { 
        if (visualizer != null) { 
            visualizer.setEnabled(true); 
            active = true; 
        } 
    }
    
    public void stop() { 
        if (visualizer != null) { 
            visualizer.setEnabled(false); 
            active = false; 
        } 
    }
    
    public void release() { 
        active = false; 
        if (visualizer != null) { 
            visualizer.release(); 
            visualizer = null; 
        } 
    }
}
