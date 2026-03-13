package com.lyra.audiokernel;

import android.Manifest;
import android.content.ComponentName;
import android.media.audiofx.Visualizer;
import android.os.*;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

public class MainActivity extends AppCompatActivity {
    private MediaController controller;
    private VUMeterView vuL, vuR;
    private TextView n1, n2, n3, n4;
    private Visualizer visualizer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); 
        setContentView(R.layout.activity_main);
        
        vuL = findViewById(R.id.vu_left); vuR = findViewById(R.id.vu_right);
        n1 = findViewById(R.id.nixie_m1); n2 = findViewById(R.id.nixie_m2);
        n3 = findViewById(R.id.nixie_s1); n4 = findViewById(R.id.nixie_s2);

        SessionToken t = new SessionToken(this, new ComponentName(this, LyraPlaybackService.class));
        ListenableFuture<MediaController> f = new MediaController.Builder(this, t).buildAsync();
        f.addListener(() -> {
            try {
                controller = f.get();
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == 0) startVisualizer();
                else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
            } catch (Exception ignored) {}
        }, MoreExecutors.directExecutor());
        
        handler.post(updateNixie);
    }

    private void startVisualizer() {
        visualizer = new Visualizer(0); // Audio Global
        visualizer.setCaptureSize(Visualizer.getCaptureSizeRange()[1]);
        visualizer.setDataCaptureListener(new Visualizer.OnDataCaptureListener() {
            @Override public void onWaveFormDataCapture(Visualizer v, byte[] b, int r) {
                float peak = 0; for (byte x : b) peak = Math.max(peak, Math.abs(x));
                runOnUiThread(() -> { 
                    vuL.updateLevel(peak/128f); 
                    vuR.updateLevel((peak/128f) * 0.95f); 
                });
            }
            @Override public void onFftDataCapture(Visualizer v, byte[] b, int r) {}
        }, Visualizer.getMaxCaptureRate() / 2, true, false);
        visualizer.setEnabled(true);
    }

    private final Runnable updateNixie = new Runnable() {
        @Override public void run() {
            if (controller != null && controller.isPlaying()) {
                long p = controller.getCurrentPosition();
                int m = (int)(p/60000), s = (int)((p%60000)/1000);
                n1.setText(String.valueOf(m/10)); n2.setText(String.valueOf(m%10));
                n3.setText(String.valueOf(s/10)); n4.setText(String.valueOf(s%10));
            }
            handler.postDelayed(this, 150);
        }
    };

    @Override protected void onDestroy() {
        if (visualizer != null) visualizer.release();
        super.onDestroy();
    }
}
