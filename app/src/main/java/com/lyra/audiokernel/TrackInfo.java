package com.lyra.audiokernel;

import androidx.media3.common.MediaItem;

public class TrackInfo {

    public MediaItem mediaItem;
    public String title;
    public String artist;
    public String album;
    public String meta;
    public String duration;
    public String uri;

    public TrackInfo(
            MediaItem mediaItem,
            String title,
            String artist,
            String album,
            String meta,
            String duration,
            String uri
    ) {
        this.mediaItem = mediaItem;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.meta = meta;
        this.duration = duration;
        this.uri = uri;
    }
}
