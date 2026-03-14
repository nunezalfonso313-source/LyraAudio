package com.lyra.audiokernel;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.TextView;
import java.util.List;

public class PlaylistAdapter extends ArrayAdapter<LyraPlaylist> {
    private final List<LyraPlaylist> playlists;
    private final Context context;

    public PlaylistAdapter(Context context, List<LyraPlaylist> playlists) {
        super(context, 0, playlists);
        this.context = context;
        this.playlists = playlists;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = LayoutInflater.from(context).inflate(R.layout.item_playlist, parent, false);
        
        LyraPlaylist pl = getItem(position);
        TextView name = convertView.findViewById(R.id.pl_item_name);
        TextView count = convertView.findViewById(R.id.pl_item_count);
        Button delete = convertView.findViewById(R.id.pl_item_delete);
        
        if (pl != null) { 
            name.setText(pl.name); 
            count.setText(pl.uris.size() + " pistas"); 
        }
        
        delete.setOnClickListener(v -> { 
            playlists.remove(position); 
            notifyDataSetChanged(); 
        });
        
        return convertView;
    }
}
