package com.lyra.audiokernel;

import android.content.Intent;
import android.media.audiofx.Equalizer;
import android.os.Binder;
import android.os.IBinder;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

public class LyraPlaybackService extends MediaSessionService {
    private MediaSession mediaSession;
    private ExoPlayer player;
    private Equalizer equalizer;
    private final LyraBinder binder = new LyraBinder();

    public class LyraBinder extends Binder {
        public LyraPlaybackService getService() { return LyraPlaybackService.this; }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (intent != null && "lyra.eq.bind".equals(intent.getAction())) return binder;
        return super.onBind(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        player = new ExoPlayer.Builder(this).build();
        mediaSession = new MediaSession.Builder(this, player).build();
        try {
            equalizer = new Equalizer(0, player.getAudioSessionId());
            equalizer.setEnabled(true);
        } catch (Exception ignored) {}
    }

    public int getAudioSessionId() { return player != null ? player.getAudioSessionId() : 0; }
    public short getNumBands() { return equalizer != null ? equalizer.getNumberOfBands() : 5; }
    public int getBandFreq(short band) { return equalizer != null ? equalizer.getCenterFreq(band) : 0; }
    public short[] getBandLevelRange() { return equalizer != null ? equalizer.getBandLevelRange() : new short[]{-1500, 1500}; }
    public short getBandLevel(short band) { return equalizer != null ? equalizer.getBandLevel(band) : 0; }
    public void setBandLevel(short band, short level) { if (equalizer != null) equalizer.setBandLevel(band, level); }
    public void resetEq() {
        if (equalizer != null) {
            short n = getNumBands();
            for (short i = 0; i < n; i++) setBandLevel(i, (short) 0);
        }
    }

    @Override
    public MediaSession onGetSession(MediaSession.ControllerInfo c) { return mediaSession; }
    @Override
    public void onDestroy() {
        if (equalizer != null) equalizer.release();
        mediaSession.getPlayer().release();
        mediaSession.release();
        super.onDestroy();
    }
}
