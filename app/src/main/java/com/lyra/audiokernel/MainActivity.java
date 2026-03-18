package com.lyra.audiokernel;

import android.view.Menu;
import android.view.MenuItem;
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
import android.graphics.Color;
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
import android.util.Log;
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
import android.widget.ScrollView;
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

    private TextView errorTextView;
    
    private void showErrorOnScreen(Exception e) {
        e.printStackTrace();
        ScrollView scrollView = new ScrollView(this);
        errorTextView = new TextView(this);
        errorTextView.setText("ERROR:\n" + Log.getStackTraceString(e));
        errorTextView.setTextColor(Color.RED);
        errorTextView.setTextSize(16);
        errorTextView.setBackgroundColor(Color.BLACK);
        errorTextView.setPadding(50, 50, 50, 50);
        errorTextView.setTextIsSelectable(true);
        
        scrollView.addView(errorTextView);
        setContentView(scrollView);
    }
    private final ActivityResultLauncher<Intent> filePickerLauncher = 
    registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == android.app.Activity.RESULT_OK && result.getData() != null) {
            Uri uri = result.getData().getData();
            if (uri != null) {
                // Aquí puedes añadir la lógica para cargar el audio seleccionado
                android.widget.Toast.makeText(this, "Archivo seleccionado correctamente", android.widget.Toast.LENGTH_SHORT).show();
            }
        }
    });


    private MediaController mediaController;
    private ListenableFuture<MediaController> controllerFuture;
    private LyraPlaybackService lyraService;
    private boolean serviceBound = false;

    private LyraVisualizer lyraVisualizer;
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
    private TextView[] eqVals   = new TextView[5];
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
            try {
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
            } catch (Exception e) {
                showErrorOnScreen(e);
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
        try {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);
            androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);

            spectrumView = findViewById(R.id.spectrum_view);
            nixieDisplay = findViewById(R.id.nixie_display);

            lyraVisualizer = new LyraVisualizer(spectrumView, nixieDisplay);

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
                        try {
                            if (!fromUser || !serviceBound) return;
                            short[] range = lyraService.getBandLevelRange();
                            int span = range[1] - range[0];
                            short level = (short)(range[0] + (span * p / 20));
                            lyraService.setBandLevel(band, level);
                            int db = level / 100;
                            eqVals[band].setText((db >= 0 ? "+" : "") + db + "dB");
                        } catch (Exception e) {
                            showErrorOnScreen(e);
                        }
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
                    try {
                        if (fromUser && mediaController != null) mediaController.seekTo(p);
                    } catch (Exception e) {
                        showErrorOnScreen(e);
                    }
                }
                @Override public void onStartTrackingTouch(SeekBar sb) {}
                @Override public void onStopTrackingTouch(SeekBar sb) {}
            });

            playlistView.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    if (addingToPlaylist) return;
                    int realIndex = allTracks.indexOf(displayedTracks.get(position));
                    if (realIndex < 0) realIndex = position;
                    currentIndex = realIndex;
                    playAt(currentIndex);
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });

            btnPlay.setOnClickListener(v -> {
                try {
                    if (mediaController == null) return;
                    if (mediaController.isPlaying()) {
                        mediaController.pause();
                        lyraVisualizer.stop();
                    } else {
                        mediaController.play();
                        if (visualizerReady) lyraVisualizer.start();
                    }
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnPrev.setOnClickListener(v -> {
                try {
                    advanceTrack(-1);
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnNext.setOnClickListener(v -> {
                try {
                    advanceTrack(1);
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnShuffle.setOnClickListener(v -> {
                try {
                    toggleShuffle();
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnRepeat.setOnClickListener(v -> {
                try {
                    cycleRepeat();
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnEq.setOnClickListener(v -> {
                try {
                    eqVisible = !eqVisible;
                    eqPanel.setVisibility(eqVisible ? View.VISIBLE : View.GONE);
                    btnEq.setTextColor(eqVisible ? 0xFF00FF00 : 0xFFFFFFFF);
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnEqReset.setOnClickListener(v -> {
                try {
                    if (serviceBound) {
                        lyraService.resetEq();
                        for (int i = 0; i < 5; i++) { 
                            eqBands[i].setProgress(10); 
                            eqVals[i].setText("0dB"); 
                        }
                    }
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnSort.setOnClickListener(v -> {
                try {
                    cycleSort();
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnSearch.setOnClickListener(v -> {
                try {
                    toggleSearch();
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnSearchClear.setOnClickListener(v -> { 
                try {
                    searchInput.setText(""); 
                    toggleSearch(); 
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            searchInput.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
                @Override public void onTextChanged(CharSequence s, int st, int b, int c) { 
                    try {
                        filterTracks(s.toString()); 
                    } catch (Exception e) {
                        showErrorOnScreen(e);
                    }
                }
                @Override public void afterTextChanged(Editable s) {}
            });
            
            btnPlNew.setOnClickListener(v -> {
                try {
                    plNameBar.setVisibility(View.VISIBLE);
                    plNameInput.setText("");
                    plNameInput.requestFocus();
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnPlSave.setOnClickListener(v -> {
                try {
                    String name = plNameInput.getText().toString().trim();
                    if (name.isEmpty()) { 
                        Toast.makeText(this, "Escribe un nombre", Toast.LENGTH_SHORT).show(); 
                        return; 
                    }
                    pendingPlaylistName = name;
                    plNameBar.setVisibility(View.GONE);
                    hideSoftKeyboard();
                    enterAddMode(name);
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            btnPlClose.setOnClickListener(v -> {
                try {
                    closePlDrawer();
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            plScrim.setOnClickListener(v -> {
                try {
                    closePlDrawer();
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            plList.setOnItemClickListener((parent, view, position, id) -> {
                try {
                    if (!addingToPlaylist) loadPlaylistIntoQueue(playlists.get(position));
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });
            
            findViewById(R.id.btn_playlists).setOnClickListener(v -> {
                try {
                    togglePlDrawer();
                } catch (Exception e) {
                    showErrorOnScreen(e);
                }
            });

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
                            try {
                                btnPlay.setText(playing ? "⏸" : "▶");
                                if (!playing) lyraVisualizer.stop();
                                else if (visualizerReady) lyraVisualizer.start();
                            } catch (Exception e) {
                                showErrorOnScreen(e);
                            }
                        }
                        @Override public void onMediaItemTransition(MediaItem item, int reason) {
                            try {
                                int idx = mediaController.getCurrentMediaItemIndex();
                                if (idx >= 0 && idx < allTracks.size()) updateUI(idx);
                            } catch (Exception e) {
                                showErrorOnScreen(e);
                            }
                        }
                    });
                    checkPermissionAndLoad();
                } catch (Exception e) { 
                    trackTitle.setText("Error al iniciar servicio");
                    showErrorOnScreen(e);
                }
            }, MoreExecutors.directExecutor());

            handler.post(updateProgress);
            
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void initVisualizer() {
        try {
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
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void enterAddMode(String plName) {
        try {
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
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void exitAddMode() {
        try {
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
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void addTrackToPlaylist(String plName, TrackInfo t) {
        try {
            for (LyraPlaylist pl : playlists) {
                if (pl.name.equals(plName)) { 
                    pl.uris.add(t.uri.toString()); 
                    return; 
                }
            }
            LyraPlaylist pl = new LyraPlaylist(plName);
            pl.uris.add(t.uri.toString());
            playlists.add(pl);
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void loadPlaylistIntoQueue(LyraPlaylist pl) {
        try {
            List<TrackInfo> plTracks = new ArrayList<>();
            for (String uriStr : pl.uris) {
                Uri uri = Uri.parse(uriStr);
                for (TrackInfo t : allTracks) {
                    if (t.uri.equals(uri)) { 
                        plTracks.add(t); 
                        break; 
                    }
                }
            }
            if (plTracks.isEmpty()) { 
                Toast.makeText(this, "Playlist vacía", Toast.LENGTH_SHORT).show(); 
                return; 
            }
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
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void togglePlDrawer() {
        try {
            plDrawerVisible = !plDrawerVisible;
            plDrawer.setVisibility(plDrawerVisible ? View.VISIBLE : View.GONE);
            plScrim.setVisibility(plDrawerVisible ? View.VISIBLE : View.GONE);
            if (plDrawerVisible) plAdapter.notifyDataSetChanged();
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void closePlDrawer() {
        try {
            plDrawerVisible = false;
            plDrawer.setVisibility(View.GONE);
            plScrim.setVisibility(View.GONE);
            plNameBar.setVisibility(View.GONE);
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void cycleSort() {
        try {
            sortMode = (sortMode + 1) % 3;
            switch (sortMode) {
                case 0: 
                    Collections.sort(allTracks, Comparator.comparing(t -> t.title.toLowerCase())); 
                    btnSort.setText("AZ TÍTULO"); 
                    break;
                case 1: 
                    Collections.sort(allTracks, Comparator.comparing(t -> t.artist.toLowerCase())); 
                    btnSort.setText("AZ ARTISTA"); 
                    break;
                case 2: 
                    Collections.sort(allTracks, Comparator.comparing(t -> t.album.toLowerCase())); 
                    btnSort.setText("AZ ÁLBUM"); 
                    break;
            }
            filterTracks(searchInput.getText().toString());
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void toggleSearch() {
        try {
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
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void filterTracks(String query) {
        try {
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
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void hideSoftKeyboard() {
        try {
            InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
            View focus = getCurrentFocus();
            if (focus != null) imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
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
        try {
            playlists.clear();
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
        try {
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
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void checkPermissionAndLoad() {
        try {
            String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_AUDIO : Manifest.permission.READ_EXTERNAL_STORAGE;
            if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) loadMediaStore();
            else permissionLauncher.launch(perm);
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void loadMediaStore() {
        try {
            allTracks.clear();
            displayedTracks.clear();
            new Thread(() -> {
                try {
                    Uri collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                    String[] projection = {
                        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE,
                        MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.RELATIVE_PATH
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
                                String relPath = cursor.getString(7);
String folder = (relPath != null && !relPath.isEmpty()) ? relPath.replaceAll("/$", "") : "Raíz";
allTracks.add(new TrackInfo(MediaItem.fromUri(uri), tit, art, alb, meta, formatTime(durMs), null, uri, folder));
                            }
                        }
                    }
                    runOnUiThread(() -> {
                        try {
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
                        } catch (Exception e) {
                            showErrorOnScreen(e);
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> showErrorOnScreen(e));
                }
            }).start();
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void loadAlbumArt(int index) {
        try {
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
                    try {
                        if (currentIndex == index) {
                            if (finalArt != null) albumArt.setImageBitmap(finalArt);
                            else albumArt.setImageResource(android.R.drawable.ic_media_play);
                        }
                    } catch (Exception e) {
                        showErrorOnScreen(e);
                    }
                });
            }).start();
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void updateUI(int index) {
        try {
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
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void playAt(int index) {
        try {
            if (mediaController == null) return;
            mediaController.seekToDefaultPosition(index);
            mediaController.play();
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void advanceTrack(int direction) {
        try {
            if (allTracks.isEmpty()) return;
            int next = (currentIndex + direction + allTracks.size()) % allTracks.size();
            playAt(next);
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void toggleShuffle() {
        try {
            isShuffled = !isShuffled;
            btnShuffle.setTextColor(isShuffled ? 0xFF00FF00 : 0xFF555555);
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private void cycleRepeat() {
        try {
            repeatMode = (repeatMode + 1) % 3;
            btnRepeat.setTextColor(repeatMode == 0 ? 0xFF555555 : (repeatMode == 1 ? 0xFF00FF00 : 0xFF00BFFF));
        } catch (Exception e) {
            showErrorOnScreen(e);
        }
    }

    private String formatTime(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    @Override
    protected void onDestroy(){
        try {
            super.onDestroy();
            handler.removeCallbacks(updateProgress);
            lyraVisualizer.release();
            if (serviceBound) unbindService(eqConnection);
            MediaController.releaseFuture(controllerFuture);
        } catch (Exception e){
            showErrorOnScreen(e);
        }     
        }
   @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }  

    @Override
    public boolean onOptionsItemSelected(android.view.MenuItem item) {

    int id = item.getItemId();

        if (id == R.id.menu_all_music) {
            android.widget.Toast.makeText(this,"Toda la música",android.widget.Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.menu_folders) {
    java.util.Set<String> folderSet = new java.util.LinkedHashSet<>();
    for (TrackInfo t : allTracks) {
        if (t.uri != null) {
            java.io.File f = new java.io.File(t.uri.getPath());
            folderSet.add(f.getParent() != null ? f.getParent() : "Raíz");
        }
    }
    String[] folders = folderSet.toArray(new String[0]);
    new android.app.AlertDialog.Builder(this)
        .setTitle("Carpetas")
        .setItems(folders, (dialog, which) -> {
            String selected = folders[which];
            displayedTracks.clear();
            for (TrackInfo t : allTracks) {
                if (t.uri != null) {
                    java.io.File f = new java.io.File(t.uri.getPath());
                    String parent = f.getParent() != null ? f.getParent() : "Raíz";
                    if (parent.equals(selected)) displayedTracks.add(t);
                }
            }
            adapter.notifyDataSetChanged();
        })
        .show();
    return true;
        }

        if (id == R.id.menu_playlists) {
            android.widget.Toast.makeText(this,"Playlists",android.widget.Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.menu_equalizer) {
            android.widget.Toast.makeText(this,"Ecualizador",android.widget.Toast.LENGTH_SHORT).show();
            return true;
        }

        if (id == R.id.menu_search) {
            android.widget.Toast.makeText(this,"Buscar",android.widget.Toast.LENGTH_SHORT).show();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

}
    
