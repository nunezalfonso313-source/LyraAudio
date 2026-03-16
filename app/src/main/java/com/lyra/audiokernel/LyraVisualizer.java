package com.lyra.audiokernel;

import android.media.audiofx.Visualizer;

public class LyraVisualizer {
    private static final int CAPTURE_SIZE = 512;
    private Visualizer visualizer;
    private SpectrumView spectrumView;
    private NixieDisplayView nixieDisplay;
    private boolean active = false;

    public LyraVisualizer(SpectrumView spectrum, NixieDisplayView nixie) {
        this.spectrumView = spectrum;
        this.nixieDisplay = nixie;
    }

    public interface VisualizerReadyCallback {
        void onReady();
        void onError(String msg);
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
                    // Código de VUMeters eliminado para evitar errores
                }

                @Override
                public void onFftDataCapture(Visualizer v, byte[] fft, int sr) {
                    if (active && fft != null && spectrumView != null) {
                        spectrumView.updateFromFFT(fft, captureSize);
                    }
                }
            }, Visualizer.getMaxCaptureRate() / 2, true, true);

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
        if (visualizer != null) {
            visualizer.release();
            visualizer = null;
        }
    }
}
