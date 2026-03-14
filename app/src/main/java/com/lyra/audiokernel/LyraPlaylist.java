package com.lyra.audiokernel;

import java.util.ArrayList;
import java.util.List;

public class LyraPlaylist {
    public String name;
    public List<String> uris = new ArrayList<>();
    
    public LyraPlaylist(String name) {
        this.name = name;
    }
}
