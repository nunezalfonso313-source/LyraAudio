package com.lyra.audiokernel;

import android.graphics.Bitmap;
import android.net.Uri;
import androidx.media3.common.MediaItem;

public class TrackInfo {
    public MediaItem mediaItem;
    public String title;
    public String artist;
    public String album;
    public String meta;
    public String duration;
    public Bitmap albumArt;
    public Uri uri;

    // Constructor con 8 parámetros (el que usa MainActivity)
    public TrackInfo(MediaItem mediaItem, String title, String artist, String album,
                     String meta, String duration, Bitmap albumArt, Uri uri) {
        this.mediaItem = mediaItem;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.meta = meta;
        this.duration = duration;
        this.albumArt = albumArt;
        this.uri = uri;
    }

    // (Opcional) Constructor con 7 parámetros por si acaso
    public TrackInfo(MediaItem mediaItem, String title, String artist, String album,
                     String meta, String duration, Uri uri) {
        this(mediaItem, title, artist, album, meta, duration, null, uri);
    }
}
