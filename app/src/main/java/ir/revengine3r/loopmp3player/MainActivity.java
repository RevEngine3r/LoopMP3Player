package ir.revengine3r.loopmp3player;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_PICK_AUDIO = 1;
    private static final int REQUEST_PERMISSION  = 2;
    private static final String PREF_NAME        = "mp3prefs";
    private static final String PREF_FILE_URI    = "last_file_uri";

    private MediaPlayer   mediaPlayer;
    private TextView      tvFileName;
    private TextView      tvStatus;
    private Button        btnBrowse;
    private Button        btnPlayPause;
    private Button        btnStop;
    private SharedPreferences prefs;
    private Uri           currentUri;
    private boolean       isPaused = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs       = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        tvFileName  = findViewById(R.id.tvFileName);
        tvStatus    = findViewById(R.id.tvStatus);
        btnBrowse   = findViewById(R.id.btnBrowse);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnStop     = findViewById(R.id.btnStop);

        btnBrowse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                checkPermissionAndBrowse();
            }
        });

        btnPlayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlayPause();
            }
        });

        btnStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopPlayback();
            }
        });

        // Restore last file URI from prefs
        String savedUri = prefs.getString(PREF_FILE_URI, null);
        if (savedUri != null) {
            currentUri = Uri.parse(savedUri);
            String fileName = getFileNameFromUri(currentUri);
            tvFileName.setText(fileName != null ? fileName : savedUri);
            tvStatus.setText(getString(R.string.status_ready));
            btnPlayPause.setEnabled(true);
            btnStop.setEnabled(false);
        } else {
            tvStatus.setText(getString(R.string.status_no_file));
            btnPlayPause.setEnabled(false);
            btnStop.setEnabled(false);
        }
    }

    // ─── Permission handling ───────────────────────────────────────────────────

    private void checkPermissionAndBrowse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+: READ_MEDIA_AUDIO
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_PERMISSION);
            } else {
                openFilePicker();
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // Android 4.1 – 12: READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
            } else {
                openFilePicker();
            }
        } else {
            // Android 4.0: no runtime permission needed
            openFilePicker();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openFilePicker();
            } else {
                Toast.makeText(this, R.string.perm_denied, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ─── File picker ───────────────────────────────────────────────────────────

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("audio/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                Intent.createChooser(intent, getString(R.string.pick_audio)),
                REQUEST_PICK_AUDIO);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_file_manager, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_PICK_AUDIO && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                // Persist permission for content:// URIs (Android 5+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    getContentResolver().takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                stopPlayback();
                currentUri = uri;
                prefs.edit().putString(PREF_FILE_URI, uri.toString()).apply();
                String name = getFileNameFromUri(uri);
                tvFileName.setText(name != null ? name : uri.toString());
                tvStatus.setText(getString(R.string.status_ready));
                btnPlayPause.setEnabled(true);
                btnStop.setEnabled(false);
            }
        }
    }

    // ─── Playback control ──────────────────────────────────────────────────────

    private void togglePlayPause() {
        if (mediaPlayer == null) {
            startPlayback();
        } else if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            isPaused = true;
            tvStatus.setText(getString(R.string.status_paused));
            btnPlayPause.setText(getString(R.string.btn_play));
        } else if (isPaused) {
            mediaPlayer.start();
            isPaused = false;
            tvStatus.setText(getString(R.string.status_playing));
            btnPlayPause.setText(getString(R.string.btn_pause));
        } else {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (currentUri == null) return;
        releasePlayer();
        mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(this, currentUri);
            mediaPlayer.setLooping(true);   // ← loop forever
            mediaPlayer.prepare();
            mediaPlayer.start();
            isPaused = false;
            tvStatus.setText(getString(R.string.status_playing));
            btnPlayPause.setText(getString(R.string.btn_pause));
            btnStop.setEnabled(true);
        } catch (IOException | IllegalStateException e) {
            Toast.makeText(this, getString(R.string.error_play) + e.getMessage(),
                           Toast.LENGTH_LONG).show();
            releasePlayer();
            tvStatus.setText(getString(R.string.status_error));
        }
    }

    private void stopPlayback() {
        releasePlayer();
        isPaused = false;
        tvStatus.setText(currentUri != null
            ? getString(R.string.status_ready)
            : getString(R.string.status_no_file));
        btnPlayPause.setText(getString(R.string.btn_play));
        btnStop.setEnabled(false);
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private String getFileNameFromUri(Uri uri) {
        String path = uri.getPath();
        if (path == null) return uri.toString();
        int cut = path.lastIndexOf('/');
        return cut >= 0 ? path.substring(cut + 1) : path;
    }

    // ─── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }
}
