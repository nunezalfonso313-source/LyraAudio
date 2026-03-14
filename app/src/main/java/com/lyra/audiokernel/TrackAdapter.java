package com.lyra.audiokernel;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.List;

public class TrackAdapter extends ArrayAdapter<TrackInfo> {
    private int currentIndex = -1;
    private boolean addMode = false;
    public interface OnAddClickListener { void onAdd(int position); }
    private OnAddClickListener addListener;

    public TrackAdapter(Context context, List<TrackInfo> tracks) { 
        super(context, 0, tracks); 
    }

    public void setCurrentIndex(int index) { 
        this.currentIndex = index; 
        notifyDataSetChanged(); 
    }
    
    public void setAddMode(boolean addMode, OnAddClickListener listener) {
        this.addMode = addMode; 
        this.addListener = listener; 
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null)
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_track, parent, false);
        
        TrackInfo track = getItem(position);
        TextView number = convertView.findViewById(R.id.item_number);
        TextView title = convertView.findViewById(R.id.item_title);
        TextView artist = convertView.findViewById(R.id.item_artist);
        TextView duration = convertView.findViewById(R.id.item_duration);

        if (addMode) {
            convertView.setBackgroundColor(Color.TRANSPARENT);
            number.setText("+"); 
            number.setTextColor(Color.parseColor("#00BFFF"));
            title.setTextColor(Color.WHITE);
            convertView.setOnClickListener(v -> { 
                if (addListener != null) addListener.onAdd(position); 
            });
        } else if (position == currentIndex) {
            convertView.setBackgroundColor(Color.parseColor("#1A2A1A"));
            number.setText("▶"); 
            number.setTextColor(Color.parseColor("#00FF00"));
            title.setTextColor(Color.parseColor("#00FF00"));
        } else {
            convertView.setBackgroundColor(Color.TRANSPARENT);
            number.setText(String.valueOf(position + 1));
            number.setTextColor(Color.parseColor("#444444"));
            title.setTextColor(Color.WHITE);
        }

        if (track != null) {
            title.setText(track.title); 
            artist.setText(track.artist); 
            duration.setText(track.duration);
        }
        return convertView;
    }
}
