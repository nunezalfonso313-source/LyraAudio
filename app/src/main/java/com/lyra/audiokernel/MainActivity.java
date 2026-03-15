package com.lyra.audiokernel;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private LyraPlaybackService lyraService;
    private boolean serviceBound = false;

    private LyraVisualizer lyraVisualizer;
    private VUMeterView vuLeft, vuRight;
    private SpectrumView spectrumView;
    private NixieDisplayView nixieDisplay;
    private boolean visualizerReady = false;

    private ImageView albumArt;
    private TextView trackTitle, trackArtist, trackAlbum, trackMeta,
                     trackNumber, timeCurrent, timeTotal, libraryLabel;
    private SeekBar seekBar;
    private ListView playlistView;
    private Button btnPlay, btnShuffle, btnRepeat, btnEq, btnSort, btnSearch;
    private LinearLayout eqPanel, searchBar, plDrawer, plScrim, plNameBar;
    private EditText searchInput, plNameInput;
    private ListView plList;
    private SeekBar[] eqBands  = new SeekBar[5];
    private TextView[] eqVals  = new TextView[5];
    private TextView[] eqLabels = new TextView[5];
    private boolean eqVisible = false, eqInitialized = false;
    private boolean searchVisible = false;
    private boolean plDrawerVisible = false;
    private boolean addingToPlaylist = false;
    private String pendingPlaylistName = null;

    private List<TrackInfo> allTracks = new ArrayList<>();
    private List<TrackInfo> displayedTracks = new ArrayList<>();
    private int currentIndex = 0;
    private boolean isShuffled = false;
    private int repeatMode = 0;
    private int sortMode = 0;
    private TrackAdapter adapter;

    private List<LyraPlaylist> playlists = new ArrayList<>();
    private PlaylistAdapter plAdapter;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final String PREFS = "lyra_prefs";
    private static final String KEY_INDEX = "last_index";
    private static final String KEY_PLAYLISTS = "playlists_json";

    private final ServiceConnection eqConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName n, IBinder b) {
            lyraService = ((LyraPlaybackService.LyraBinder) b).getService();
            serviceBound = true;
            initEqUI();
            initVisualizer();
        }
        @Override public void onServiceDisconnected(ComponentName n) {
            serviceBound = false;
            lyraService = null;
        }
    };

    private final Runnable updateProgress = new Runnable() {
        @Override public void run() {
            if (mediaController != null) {
                long cur = mediaController.getCurrentPosition();
                long tot = mediaController.getDuration();
                boolean playing = mediaController.isPlaying();
                if (tot > 0) {
                    seekBar.setMax((int) tot);
                    seekBar.setProgress((int) cur);
                    timeCurrent.setText(formatTime(cur));
                    timeTotal.setText(formatTime(tot));
                }
                nixieDisplay.setTime(cur, playing);
            }
            handler.postDelayed(this, 100);
        }
    };

    private final ActivityResultLauncher<String> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) loadMediaStore();
            else trackTitle.setText("Permiso necesario para leer música");
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // === PROTECCIÓN CONTRA CRASHES ===
        try {
            setContentView(R.layout.activity_main);

            vuLeft       = findViewById(R.id.vu_left);
            vuRight      = findViewById(R.id.vu_right);
            spectrumView = findViewById(R.id.spectrum_view);
            nixieDisplay = findViewById(R.id.nixie_display);

            // Protección adicional por si alguna vista es null
            if (vuLeft != null) vuLeft.setChannelLabel("L");
            if (vuRight != null) vuRight.setChannelLabel("R");

            lyraVisualizer = new LyraVisualizer(vuLeft, vuRight, spectrumView);

            albumArt     = findViewById(R.id.album_art);
            trackTitle   = findViewById(R.id.track_title);
            trackArtist  = findViewById(R.id.track_artist);
            trackAlbum   = findViewById(R.id.track_album);
            trackMeta    = findViewById(R.id.track_meta);
            trackNumber  = findViewById(R.id.track_number);
            timeCurrent  = findViewById(R.id.time_current);
            timeTotal    = findViewById(R.id.time_total);
            libraryLabel = findViewById(R.id.library_label);
            seekBar      = findViewById(R.id.seek_bar);
            playlistView = findViewById(R.id.playlist_view);
            btnPlay      = findViewById(R.id.btn_play);
            btnShuffle   = findViewById(R.id.btn_shuffle);
            btnRepeat    = findViewById(R.id.btn_repeat);
            btnEq        = findViewById(R.id.btn_eq);
            btnSort      = findViewById(R.id.btn_sort);
            btnSearch    = findViewById(R.id.btn_search);
            eqPanel      = findViewById(R.id.eq_panel);
            searchBar    = findViewById(R.id.search_bar);
            searchInput  = findViewById(R.id.search_input);
            plDrawer     = findViewById(R.id.pl_drawer);
            plScrim      = findViewById(R.id.pl_scrim);
            plNameBar    = findViewById(R.id.pl_name_bar);
            plNameInput  = findViewById(R.id.pl_name_input);
            plList       = findViewById(R.id.pl_list);

            Button btnPrev      = findViewById(R.id.btn_prev);
            Button btnNext      = findViewById(R.id.btn_next);
            Button btnEqReset   = findViewById(R.id.btn_eq_reset);
            Button btnPlNew     = findViewById(R.id.btn_pl_new);
            Button btnPlClose   = findViewById(R.id.btn_pl_close);
            Button btnPlSave    = findViewById(R.id.btn_pl_save);
            Button btnSearchClear = findViewById(R.id.btn_search_clear);

            int[] bandIds  = {R.id.eq_band0, R.id.eq_band1, R.id.eq_band2, R.id.eq_band3, R.id.eq_band4};
            int[] valIds   = {R.id.eq_val0,  R.id.eq_val1,  R.id.eq_val2,  R.id.eq_val3,  R.id.eq_val4};
            int[] labelIds = {R.id.eq_label0,R.id.eq_label1,R.id.eq_label2,R.id.eq_label3,R.id.eq_label4};
            for (int i = 0; i < 5; i++) {
                eqBands[i]  = findViewById(bandIds[i]);
                eqVals[i]   = findViewById(valIds[i]);
                eqLabels[i] = findViewById(labelIds[i]);
                final short band = (short) i;
                eqBands[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                        if (!fromUser || !serviceBound) return;
                        short[] range = lyraService.getBandLevelRange();
                        int span = range[1] - range[0];
                        short level = (short)(range[0] + (span * p / 20));
                        lyraService.setBandLevel(band, level);
                        int db = level / 100;
                        eqVals[band].setText((db >= 0 ? "+" : "") + db + "dB");
                    }
                    @Override public void onStartTrackingTouch(SeekBar sb) {}
                    @Override public void onStopTrackingTouch(SeekBar sb) {}
                });
            }

            loadPlaylists();
            plAdapter = new PlaylistAdapter(this, playlists);
            plList.setAdapter(plAdapter);

            adapter = new TrackAdapter(this, displayedTracks);
            playlistView.setAdapter(adapter);

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override public void onProgressChanged(SeekBar sb, int p, boolean fromUser) {
                    if (fromUser && mediaController != null) mediaController.seekTo(p);
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });

            playlistView.setOnItemClickListener((parent, view, position, id) -> {
                if (addingToPlaylist) return;
                int realIndex = allTracks.indexOf(displayedTracks.get(position));
                if (realIndex < 0) realIndex = position;
                currentIndex = realIndex;
                playAt(currentIndex);
            });

            btnPlay.setOnClickListener(v -> {
                if (mediaController == null) return;
                if (mediaController.isPlaying()) {
                    mediaController.pause();
                    lyraVisualizer.stop();
                } else {
                    mediaController.play();
                    if (visualizerReady) lyraVisualizer.start();
                }
            });
            btnPrev.setOnClickListener(v -> advanceTrack(-1));
            btnNext.setOnClickListener(v -> advanceTrack(1));
            btnShuffle.setOnClickListener(v -> toggleShuffle());
            btnRepeat.setOnClickListener(v -> cycleRepeat());
            btnEq.setOnClickListener(v -> {
                eqVisible = !eqVisible;
                eqPanel.setVisibility(eqVisible ? View.VISIBLE : View.GONE);
                btnEq.setTextColor(eqVisible ? 0xFF00FF00 : 0xFFFFFFFF);
            });
            btnEqReset.setOnClickListener(v -> {
                if (serviceBound) {
                    lyraService.resetEq();
                    for (int i = 0; i < 5; i++) { eqBands[i].setProgress(10); eqVals[i].setText("0dB"); }
                }
            });
            btnSort.setOnClickListener(v -> cycleSort());
            btnSearch.setOnClickListener(v -> toggleSearch());
            btnSearchClear.setOnClickListener(v -> { searchInput.setText(""); toggleSearch(); });
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { filterTracks(s.toString()); }
                @Override public void afterTextChanged(Editable s) {}
            });
            btnPlNew.setOnClickListener(v -> {
                plNameBar.setVisibility(View.VISIBLE);
                plNameInput.setText("");
                plNameInput.requestFocus();
            });
            btnPlSave.setOnClickListener(v -> {
                String name = plNameInput.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show(); return; }
                pendingPlaylistName = name;
                plNameBar.setVisibility(View.GONE);
                hideSoftKeyboard();
                enterAddMode(name);
            });
            btnPlClose.setOnClickListener(v -> closePlDrawer());
            plScrim.setOnClickListener(v -> closePlDrawer());
            plList.setOnItemClickListener((parent, view, position, id) -> {
                if (!addingToPlaylist) loadPlaylistIntoQueue(playlists.get(position));
            });
            findViewById(R.id.btn_playlists).setOnClickListener(v -> togglePlDrawer());

            Intent eqIntent = new Intent(this, LyraPlaybackService.class);
            eqIntent.setAction("lyra.eq.bind");
            bindService(eqIntent, eqConnection, Context.BIND_AUTO_CREATE);

            SessionToken token = new SessionToken(this, new ComponentName(this, LyraPlaybackService.class));
            controllerFuture = new MediaController.Builder(this, token).buildAsync();
            controllerFuture.addListener(() -> {
                try {
                    mediaController = controllerFuture.get();
                    mediaController.addListener(new Player.Listener() {
                        @Override public void onIsPlayingChanged(boolean playing) {
                            btnPlay.setText(playing ? "⏸" : "▶");
                            if (!playing) lyraVisualizer.stop();
                            else if (visualizerReady) lyraVisualizer.start();
                        }
                        @Override public void onMediaItemTransition(MediaItem item, int reason) {
                            int idx = mediaController.getCurrentMediaItemIndex();
                            if (idx >= 0 && idx < allTracks.size()) updateUI(idx);
                        }
                    });
                    checkPermissionAndLoad();
                } catch (Exception e) { trackTitle.setText("Error al iniciar servicio"); }
            }, MoreExecutors.directExecutor());

            handler.post(updateProgress);
            
        } catch (Exception e) {
            // === MUESTRA EL ERROR EN PANTALLA ===
            e.printStackTrace();
            TextView errorView = new TextView(this);
            errorView.setText("ERROR: " + e.toString() + "\n\n" + e.getMessage());
            errorView.setTextColor(0xFFFF0000);
            errorView.setTextSize(16);
            errorView.setPadding(32, 32, 32, 32);
            setContentView(errorView);
        }
    }

    private void initVisualizer() {
        if (!serviceBound || lyraService == null) return;
        int sessionId = lyraService.getAudioSessionId();
        if (sessionId == 0) {
            handler.postDelayed(this::initVisualizer, 500);
            return;
        }
        lyraVisualizer.init(sessionId, new LyraVisualizer.VisualizerReadyCallback() {
            @Override public void onReady() {
                visualizerReady = true;
                if (mediaController != null && mediaController.isPlaying()) lyraVisualizer.start();
            }
            @Override public void onError(String msg) { visualizerReady = false; }
        });
    }

    private void enterAddMode(String plName) {
        addingToPlaylist = true;
        closePlDrawer();
        libraryLabel.setText("TOCA + PARA AGREGAR A: " + plName.toUpperCase());
        libraryLabel.setTextColor(0xFF00BFFF);
        adapter.setAddMode(true, position -> {
            TrackInfo t = displayedTracks.get(position);
            addTrackToPlaylist(plName, t);
            Toast.makeText(this, "Agregada: " + t.title, Toast.LENGTH_SHORT).show();
        });
        Button btnDone = new Button(this);
        btnDone.setText("✓ LISTO");
        btnDone.setTag("done_btn");
        btnDone.setOnClickListener(v -> exitAddMode());
        LinearLayout root = (LinearLayout) playlistView.getParent();
        root.addView(btnDone, root.indexOfChild(libraryLabel));
    }

    private void exitAddMode() {
        addingToPlaylist = false;
        pendingPlaylistName = null;
        libraryLabel.setText("LISTA DE REPRODUCCIÓN");
        libraryLabel.setTextColor(0xFF444444);
        adapter.setAddMode(false, null);
        LinearLayout root = (LinearLayout) playlistView.getParent();
        View doneBtn = root.findViewWithTag("done_btn");
        if (doneBtn != null) root.removeView(doneBtn);
        savePlaylists();
        plAdapter.notifyDataSetChanged();
    }

    private void addTrackToPlaylist(String plName, TrackInfo t) {
        for (LyraPlaylist pl : playlists) {
            if (pl.name.equals(plName)) { pl.uris.add(t.uri.toString()); return; }
        }
        LyraPlaylist pl = new LyraPlaylist(plName);
        pl.uris.add(t.uri.toString());
        playlists.add(pl);
    }

    private void loadPlaylistIntoQueue(LyraPlaylist pl) {
        List<TrackInfo> plTracks = new ArrayList<>();
        for (String uriStr : pl.uris) {
            Uri uri = Uri.parse(uriStr);
            for (TrackInfo t : allTracks) {
                if (t.uri.equals(uri)) { plTracks.add(t); break; }
            }
        }
        if (plTracks.isEmpty()) { Toast.makeText(this, "Playlist vacía", Toast.LENGTH_SHORT).show(); return; }
        displayedTracks.clear();
        displayedTracks.addAll(plTracks);
        adapter.notifyDataSetChanged();
        libraryLabel.setText("▶ " + pl.name.toUpperCase() + " — " + plTracks.size() + " pistas");
        libraryLabel.setTextColor(0xFF00FF00);
        List<MediaItem> items = plTracks.stream().map(t -> t.mediaItem).collect(Collectors.toList());
        mediaController.setMediaItems(items, 0, 0);
        mediaController.prepare();
        currentIndex = 0;
        updateUI(0);
        closePlDrawer();
    }

    private void togglePlDrawer() {
        plDrawerVisible = !plDrawerVisible;
        plDrawer.setVisibility(plDrawerVisible ? View.VISIBLE : View.GONE);
        plScrim.setVisibility(plDrawerVisible ? View.VISIBLE : View.GONE);
        if (plDrawerVisible) plAdapter.notifyDataSetChanged();
    }

    private void closePlDrawer() {
        plDrawerVisible = false;
        plDrawer.setVisibility(View.GONE);
        plScrim.setVisibility(View.GONE);
        plNameBar.setVisibility(View.GONE);
    }

    private void cycleSort() {
        sortMode = (sortMode + 1) % 3;
        switch (sortMode) {
            case 0: Collections.sort(allTracks, Comparator.comparing(t -> t.title.toLowerCase())); btnSort.setText("AZ TÍTULO"); break;
            case 1: Collections.sort(allTracks, Comparator.comparing(t -> t.artist.toLowerCase())); btnSort.setText("AZ ARTISTA"); break;
            case 2: Collections.sort(allTracks, Comparator.comparing(t -> t.album.toLowerCase())); btnSort.setText("AZ ÁLBUM"); break;
        }
        filterTracks(searchInput.getText().toString());
    }

    private void toggleSearch() {
        searchVisible = !searchVisible;
        searchBar.setVisibility(searchVisible ? View.VISIBLE : View.GONE);
        btnSearch.setTextColor(searchVisible ? 0xFF00FF00 : 0xFFFFFFFF);
        if (searchVisible) {
            searchInput.requestFocus();
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT);
        } else {
            searchInput.setText("");
            filterTracks("");
            hideSoftKeyboard();
        }
    }

    private void filterTracks(String query) {
        displayedTracks.clear();
        if (query.isEmpty()) {
            displayedTracks.addAll(allTracks);
            libraryLabel.setText("LISTA DE REPRODUCCIÓN");
            libraryLabel.setTextColor(0xFF444444);
        } else {
            String low = query.toLowerCase();
            for (TrackInfo t : allTracks) {
                if (t.title.toLowerCase().contains(low) || t.artist.toLowerCase().contains(low))
                    displayedTracks.add(t);
            }
            libraryLabel.setText("RESULTADOS: " + displayedTracks.size());
            libraryLabel.setTextColor(0xFF00BFFF);
        }
        adapter.notifyDataSetChanged();
    }

    private void hideSoftKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        View focus = getCurrentFocus();
        if (focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
    }

    private void savePlaylists() {
        try {
            JSONArray arr = new JSONArray();
            for (LyraPlaylist pl : playlists) {
                JSONObject obj = new JSONObject();
                obj.put("name", pl.name);
                JSONArray uriArr = new JSONArray();
                for (String u : pl.uris) uriArr.put(u);
                obj.put("uris", uriArr);
                arr.put(obj);
            }
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_PLAYLISTS, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    private void loadPlaylists() {
        playlists.clear();
        try {
            String json = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_PLAYLISTS, "[]");
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                LyraPlaylist pl = new LyraPlaylist(obj.getString("name"));
                JSONArray uriArr = obj.getJSONArray("uris");
                for (int j = 0; j < uriArr.length(); j++) pl.uris.add(uriArr.getString(j));
                playlists.add(pl);
            }
        } catch (Exception ignored) {}
    }

    private void initEqUI() {
        if (eqInitialized || !serviceBound) return;
        eqInitialized = true;
        short numBands = lyraService.getNumBands();
        short[] range = lyraService.getBandLevelRange();
        for (short i = 0; i < Math.min(numBands, 5); i++) {
            int freqHz = lyraService.getBandFreq(i) / 1000;
            eqLabels[i].setText(freqHz >= 1000 ? (freqHz / 1000) + "kHz" : freqHz + "Hz");
            short current = lyraService.getBandLevel(i);
            int span = range[1] - range[0];
            int progress = span > 0 ? (int)(((current - range[0]) * 20.0f) / span) : 10;
            eqBands[i].setProgress(Math.max(0, Math.min(20, progress)));
            int db = current / 100;
            eqVals[i].setText((db >= 0 ? "+" : "") + db + "dB");
        }
    }

    private void checkPermissionAndLoad() {
        String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
            ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) loadMediaStore();
        else permissionLauncher.launch(perm);
    }

    private void loadMediaStore() {
        allTracks.clear();
        displayedTracks.clear();
        new Thread(() -> {
            Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.MIME_TYPE
            };
            try (Cursor cursor = getContentResolver().query(collection, projection,
                    MediaStore.Audio.Media.IS_MUSIC + " != 0", null, null)) {
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(0);
                        String tit = cursor.getString(1);
                        String art = cursor.getString(2);
                        String alb = cursor.getString(3);
                        long durMs = cursor.getLong(4);
                        long sizeKb = cursor.getLong(5) / 1024;
                        String mime = cursor.getString(6);
                        String ext  = mime != null ? mime.substring(mime.lastIndexOf("/") + 1).toUpperCase() : "AUDIO";
                        String meta = ext + " | " + String.format(Locale.US, "%.1f MB", sizeKb / 1024.0);
                        Uri uri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, String.valueOf(id));
                        allTracks.add(new TrackInfo(MediaItem.fromUri(uri), tit, art, alb, meta, formatTime(durMs), null, uri));
                    }
                }
            } catch (Exception ignored) {}
            runOnUiThread(() -> {
                displayedTracks.addAll(allTracks);
                adapter.notifyDataSetChanged();
                if (!allTracks.isEmpty()) {
                    currentIndex = getSharedPreferences(PREFS, MODE_PRIVATE).getInt(KEY_INDEX, 0);
                    List<MediaItem> items = allTracks.stream().map(t -> t.mediaItem).collect(Collectors.toList());
                    mediaController.setMediaItems(items, currentIndex, 0);
                    mediaController.prepare();
                    updateUI(currentIndex);
                    handler.postDelayed(this::initVisualizer, 800);
                }
            });
        }).start();
    }

    private void loadAlbumArt(int index) {
        if (index >= allTracks.size()) return;
        TrackInfo t = allTracks.get(index);
        new Thread(() -> {
            Bitmap art = null;
            try {
                MediaMetadataRetriever mmr = new MediaMetadataRetriever();
                mmr.setDataSource(getApplicationContext(), t.uri);
                byte[] bytes = mmr.getEmbeddedPicture();
                mmr.release();
                if (bytes != null) art = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            } catch (Exception ignored) {}
            
            final Bitmap finalArt = art;
            t.albumArt = finalArt;
            
            runOnUiThread(() -> {
                if (currentIndex == index) {
                    if (finalArt != null) albumArt.setImageBitmap(finalArt);
                    else albumArt.setImageResource(android.R.drawable.ic_media_play);
                }
            });
        }).start();
    }

    private void updateUI(int index) {
        currentIndex = index;
        if (index >= allTracks.size()) return;
        TrackInfo t = allTracks.get(index);
        trackTitle.setText(t.title);
        trackArtist.setText(t.artist.isEmpty() ? "Artista desconocido" : t.artist);
        trackAlbum.setText(t.album);
        trackMeta.setText(t.meta);
        trackNumber.setText((index + 1) + " / " + allTracks.size());
        timeTotal.setText(t.duration);
        adapter.setCurrentIndex(displayedTracks.indexOf(t));
        loadAlbumArt(index);
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_INDEX, index).apply();
    }

    private void playAt(int index) {
        if (mediaController == null) return;
        mediaController.seekToDefaultPosition(index);
        mediaController.play();
    }

    private void advanceTrack(int direction) {
        if (allTracks.isEmpty()) return;
        int next = (currentIndex + direction + allTracks.size()) % allTracks.size();
        playAt(next);
    }

    private void toggleShuffle() {
        isShuffled = !isShuffled;
        btnShuffle.setTextColor(isShuffled ? 0xFF00FF00 : 0xFF555555);
    }

    private void cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3;
        btnRepeat.setTextColor(repeatMode == 0 ? 0xFF555555 : (repeatMode == 1 ? 0xFF00FF00 : 0xFF00BFFF));
    }

    private String formatTime(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);

        public class MainActivity extends AppCompatActivity {

    // otros métodos...

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.menu_all_music) {
            openAllMusic();
            return true;
        }

        if (id == R.id.menu_folders) {
            openFolders();
            return true;
        }

        if (id == R.id.menu_playlists) {
            openPlaylists();
            return true;
        }

        if (id == R.id.menu_equalizer) {
            openEqualizer();
            return true;
        }

        if (id == R.id.menu_search) {
            openSearch();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateProgress);
        lyraVisualizer.release();
        if (serviceBound) unbindService(eqConnection);
        MediaController.releaseFuture(controllerFuture);
    }
    }
