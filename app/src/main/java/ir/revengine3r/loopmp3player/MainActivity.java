package ir.revengine3r.loopmp3player;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    public static final String PREF_NAME      = "mp3prefs";
    public static final String PREF_FILE_URI  = "last_file_uri";
    public static final String PREF_FILE_NAME = "last_file_name";

    private static final int REQUEST_PICK_AUDIO = 1;
    private static final int REQUEST_PERMISSION = 2;

    private TextView tvFileName;
    private TextView tvStatus;
    private Button   btnPlayPause;

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

        findViewById(R.id.btnBrowse).setOnClickListener(v -> checkPermissionAndBrowse());
        btnPlayPause.setOnClickListener(v -> togglePlayPause());

        String savedUri  = prefs.getString(PREF_FILE_URI, null);
        String savedName = prefs.getString(PREF_FILE_NAME, null);
        if (savedUri != null) {
            currentUri = Uri.parse(savedUri);
            tvFileName.setText(savedName != null ? savedName : savedUri);
            tvStatus.setText(R.string.status_ready);
            btnPlayPause.setEnabled(true);
        } else {
            tvStatus.setText(R.string.status_no_file);
            btnPlayPause.setEnabled(false);
        }
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
    // Use ACTION_OPEN_DOCUMENT (not ACTION_GET_CONTENT) so the system grants
    // a *persistable* URI permission that survives across process boundaries
    // (i.e. the Service and BootReceiver can open it too).
    private void openFilePicker() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
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

            // Persist the URI permission so Service + BootReceiver can open it
            getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            stopPlayback();
            currentUri = uri;

            String displayName = queryDisplayName(uri);
            prefs.edit()
                    .putString(PREF_FILE_URI, uri.toString())
                    .putString(PREF_FILE_NAME, displayName)
                    .apply();
            tvFileName.setText(displayName);
            btnPlayPause.setEnabled(true);

            startPlayback();
        }
    }

    // ---- playback ---------------------------------------------------------

    private void togglePlayPause() {
        if (!isPlaying) startPlayback();
        else stopPlayback();
    }

    void startPlayback() {
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
    }

    // ---- helpers ----------------------------------------------------------

    private String queryDisplayName(Uri uri) {
        String name = null;
        try {
            ContentResolver cr = getContentResolver();
            try (Cursor cursor = cr.query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) name = cursor.getString(idx);
                }
            }
        } catch (Exception ignored) {}
        if (name == null) {
            String path = uri.getPath();
            name = (path != null && path.contains("/"))
                    ? path.substring(path.lastIndexOf('/') + 1)
                    : uri.toString();
        }
        return name;
    }
}
