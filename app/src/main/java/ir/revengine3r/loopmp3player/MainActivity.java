package ir.revengine3r.loopmp3player;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    public static final String PREF_NAME     = "mp3prefs";
    public static final String PREF_FILE_URI = "last_file_uri";

    private static final int REQUEST_PICK_AUDIO = 1;
    private static final int REQUEST_PERMISSION = 2;

    private TextView tvFileName;
    private TextView tvStatus;
    private Button   btnPlayPause;
    private Button   btnStop;

    private SharedPreferences prefs;
    private Uri               currentUri;
    private boolean           isPlaying = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs        = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        tvFileName   = findViewById(R.id.tvFileName);
        tvStatus     = findViewById(R.id.tvStatus);
        btnPlayPause = findViewById(R.id.btnPlayPause);
        btnStop      = findViewById(R.id.btnStop);

        findViewById(R.id.btnBrowse).setOnClickListener(v -> checkPermissionAndBrowse());
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnStop.setOnClickListener(v -> stopPlayback());

        // Restore last saved file
        String savedUri = prefs.getString(PREF_FILE_URI, null);
        if (savedUri != null) {
            currentUri = Uri.parse(savedUri);
            tvFileName.setText(fileNameFromUri(currentUri));
            tvStatus.setText(R.string.status_ready);
            btnPlayPause.setEnabled(true);
        } else {
            tvStatus.setText(R.string.status_no_file);
            btnPlayPause.setEnabled(false);
        }
        btnStop.setEnabled(false);
    }

    // ---- permission -------------------------------------------------------

    private void checkPermissionAndBrowse() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_AUDIO}, REQUEST_PERMISSION);
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
                return;
            }
        }
        openFilePicker();
    }

    @Override
    public void onRequestPermissionsResult(int req, @NonNull String[] perms,
                                           @NonNull int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        if (req == REQUEST_PERMISSION && results.length > 0
                && results[0] == PackageManager.PERMISSION_GRANTED) {
            openFilePicker();
        } else {
            Toast.makeText(this, R.string.perm_denied, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- file picker ------------------------------------------------------

    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(
                    Intent.createChooser(i, getString(R.string.pick_audio)),
                    REQUEST_PICK_AUDIO);
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, R.string.no_file_manager, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, @Nullable Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQUEST_PICK_AUDIO && res == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri == null) return;

            // Persist permission so the URI survives app restarts (API 19+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                    // Some file managers don't support persistable permissions — ignore
                }
            }

            // Stop any current playback before switching file
            stopPlayback();

            currentUri = uri;
            prefs.edit().putString(PREF_FILE_URI, uri.toString()).apply();
            tvFileName.setText(fileNameFromUri(uri));
            btnPlayPause.setEnabled(true);

            // AUTO-PLAY immediately after file is selected
            startPlayback();
        }
    }

    // ---- playback (delegates to PlaybackService) --------------------------

    private void togglePlayPause() {
        if (!isPlaying) startPlayback();
        else stopPlayback();
    }

    private void startPlayback() {
        if (currentUri == null) return;
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_PLAY);
        intent.putExtra(PlaybackService.EXTRA_URI, currentUri.toString());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        isPlaying = true;
        tvStatus.setText(R.string.status_playing);
        btnPlayPause.setText(R.string.btn_stop);
        btnStop.setEnabled(true);
    }

    private void stopPlayback() {
        Intent intent = new Intent(this, PlaybackService.class);
        intent.setAction(PlaybackService.ACTION_STOP);
        startService(intent);
        isPlaying = false;
        tvStatus.setText(currentUri != null
                ? getString(R.string.status_ready)
                : getString(R.string.status_no_file));
        btnPlayPause.setText(R.string.btn_play);
        btnStop.setEnabled(false);
    }

    // ---- helpers ----------------------------------------------------------

    private String fileNameFromUri(Uri uri) {
        String path = uri.getPath();
        if (path == null) return uri.toString();
        int cut = path.lastIndexOf('/');
        return cut >= 0 ? path.substring(cut + 1) : path;
    }
}
