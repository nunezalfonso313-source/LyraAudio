package com.lyra.audiokernel;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.audiofx.Visualizer;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

public class MainActivity extends AppCompatActivity {
    private Visualizer visualizer;
    private VUMeterView vuL, vuR;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vuL = findViewById(R.id.vu_left);
        vuR = findViewById(R.id.vu_right);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 101);
        } else {
            initAudioEngine();
        }
    }

    private void initAudioEngine() {
        SessionToken sessionToken = new SessionToken(this, new ComponentName(this, LyraPlaybackService.class));
        ListenableFuture<MediaController> controllerFuture = new MediaController.Builder(this, sessionToken).buildAsync();
        
        controllerFuture.addListener(() -> {
            try {
                visualizer = new Visualizer(0);
                visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
                
                // Referencias finales para la Lambda (Corrección de error 1000141634)
                final VUMeterView finalVuL = vuL;
                final VUMeterView finalVuR = vuR;

                visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
                    @Override
                    public void onWaveFormDataCapture(Visualizer v, byte[] bytes, int samplingRate) {
                        float peak = 0;
                        for (byte b : bytes) peak = Math.max(peak, Math.abs(b));
                        if (finalVuL != null) finalVuL.updateLevel(peak / 128f);
                        if (finalVuR != null) finalVuR.updateLevel((peak / 128f) * 0.95f);
                    }

                    @Override
                    public void onFftDataCapture(Visualizer v, byte[] bytes, int samplingRate) {}
                }, Visualizer.getMaxCaptureRate() / 2, true, false);

                visualizer.setEnabled(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    @Override
    protected void onDestroy() {
        if (visualizer != null) visualizer.release();
        super.onDestroy();
    }
}
