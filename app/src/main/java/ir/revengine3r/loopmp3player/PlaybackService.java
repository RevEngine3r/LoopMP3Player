package ir.revengine3r.loopmp3player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.IOException;

/**
 * Foreground service that loops an MP3 indefinitely.
 * Android 4.0+ (API 14) compatible.
 *
 * Permission strategy:
 *   The Activity calls takePersistableUriPermission() with ACTION_OPEN_DOCUMENT,
 *   so the system grants this process permanent read access to the URI.
 *   The service opens it via ContentResolver — no SecurityException.
 *
 * startForeground() is called as the VERY FIRST statement in onStartCommand
 *   (before any IO) to satisfy Android 8+ 5-second rule.
 */
public class PlaybackService extends Service
        implements MediaPlayer.OnPreparedListener,
                   MediaPlayer.OnErrorListener {

    public static final String ACTION_PLAY = "ir.revengine3r.loopmp3player.ACTION_PLAY";
    public static final String ACTION_STOP = "ir.revengine3r.loopmp3player.ACTION_STOP";
    public static final String EXTRA_URI   = "uri";

    private static final String CHANNEL_ID = "loop_mp3_playback";
    private static final int    NOTIF_ID   = 1;

    private MediaPlayer mediaPlayer;
    private String      currentUriString;

    // ------------------------------------------------------------------ lifecycle

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // *** startForeground MUST be the very first call ***
        // Even if the URI is missing we post the notification first,
        // then bail out. This prevents ForegroundServiceDidNotStartInTimeException.
        String uriString = (intent != null) ? intent.getStringExtra(EXTRA_URI) : null;
        String action    = (intent != null) ? intent.getAction() : null;

        if (ACTION_STOP.equals(action)) {
            // For STOP we were not started via startForegroundService so no
            // need to call startForeground — just stop.
            stopSelf();
            return START_NOT_STICKY;
        }

        // For PLAY (or re-delivery after START_STICKY): call startForeground first
        startForegroundWithNotification(
                uriString != null ? uriString : "");

        if (uriString == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        currentUriString = uriString;
        play(Uri.parse(uriString));

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        releasePlayer();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // ------------------------------------------------------------------ playback

    private void play(Uri uri) {
        releasePlayer();
        try {
            mediaPlayer = new MediaPlayer();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Modern API: AudioAttributes
                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build());
            } else {
                // Legacy API needed for Android 4.x
                //noinspection deprecation
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnErrorListener(this);

            // Open via AssetFileDescriptor so the service process uses
            // the persisted URI permission granted by the Activity.
            AssetFileDescriptor afd = getContentResolver()
                    .openAssetFileDescriptor(uri, "r");
            if (afd == null) {
                releasePlayer();
                stopSelf();
                return;
            }
            mediaPlayer.setDataSource(
                    afd.getFileDescriptor(),
                    afd.getStartOffset(),
                    afd.getLength());
            afd.close();

            mediaPlayer.prepareAsync();

        } catch (IOException | IllegalStateException
                | IllegalArgumentException | SecurityException e) {
            releasePlayer();
            stopSelf();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mp.start();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        releasePlayer();
        stopSelf();
        return true;
    }

    private void releasePlayer() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (IllegalStateException ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    // ------------------------------------------------------------------ notification

    private void startForegroundWithNotification(String uriString) {
        createNotificationChannel();

        Intent openApp = new Intent(this, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
                : PendingIntent.FLAG_UPDATE_CURRENT;
        PendingIntent openPi = PendingIntent.getActivity(this, 0, openApp, piFlags);

        Intent stopIntent = new Intent(this, PlaybackService.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPi = PendingIntent.getService(this, 1, stopIntent, piFlags);

        // Use saved display name if available, else extract from URI path
        String fileName;
        try {
            SharedPreferencesHelper prefs = new SharedPreferencesHelper(
                    getSharedPreferences(MainActivity.PREF_NAME, MODE_PRIVATE));
            String saved = prefs.getString(MainActivity.PREF_FILE_NAME);
            if (saved != null && !saved.isEmpty()) {
                fileName = saved;
            } else {
                String path = Uri.parse(uriString).getPath();
                fileName = (path != null && path.contains("/"))
                        ? path.substring(path.lastIndexOf('/') + 1)
                        : uriString;
            }
        } catch (Exception e) {
            fileName = getString(R.string.app_name);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(fileName)
                .setContentIntent(openPi)
                .addAction(android.R.drawable.ic_delete,
                        getString(R.string.btn_stop), stopPi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();

        startForeground(NOTIF_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "MP3 Playback",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Loop MP3 Player background playback");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    // Small helper to avoid importing SharedPreferences in the notification builder
    private static class SharedPreferencesHelper {
        private final android.content.SharedPreferences prefs;
        SharedPreferencesHelper(android.content.SharedPreferences p) { this.prefs = p; }
        String getString(String key) { return prefs.getString(key, null); }
    }
}
