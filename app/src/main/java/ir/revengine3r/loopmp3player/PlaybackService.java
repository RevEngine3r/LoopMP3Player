package ir.revengine3r.loopmp3player;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
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
 * CRITICAL ORDER in onStartCommand:
 *   1. startForeground() FIRST — must happen within 5 s of startForegroundService()
 *   2. then prepareAsync() — async, does NOT block the call
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

    // ------------------------------------------------------------------ lifecycle

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (ACTION_PLAY.equals(intent.getAction())) {
            String uriString = intent.getStringExtra(EXTRA_URI);
            if (uriString == null) {
                stopSelf();
                return START_NOT_STICKY;
            }

            // *** MUST call startForeground() immediately — before any async work ***
            // Android 8+ requires this within 5 seconds of startForegroundService().
            startForegroundWithNotification(uriString);

            // Now start async preparation — safe because startForeground already called
            play(Uri.parse(uriString));
        }

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

            // Required on Android 4.x to route audio to speaker
            //noinspection deprecation
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);

            mediaPlayer.setLooping(true);
            mediaPlayer.setOnPreparedListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.setDataSource(getApplicationContext(), uri);

            // Async — won't block or crash the service thread
            mediaPlayer.prepareAsync();

        } catch (IOException | IllegalStateException | IllegalArgumentException | SecurityException e) {
            releasePlayer();
            stopSelf();
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        // Called on main thread when media is buffered and ready
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

    /**
     * Builds and posts the foreground notification.
     * Called SYNCHRONOUSLY at the top of onStartCommand so Android
     * does not throw ForegroundServiceDidNotStartInTimeException.
     */
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

        String path = Uri.parse(uriString).getPath();
        String fileName = (path != null && path.contains("/"))
                ? path.substring(path.lastIndexOf('/') + 1)
                : uriString;

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
                    CHANNEL_ID,
                    "MP3 Playback",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Loop MP3 Player background playback");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }
}
