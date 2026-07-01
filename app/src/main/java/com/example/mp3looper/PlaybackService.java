package com.example.mp3looper;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class PlaybackService extends Service implements MediaPlayer.OnPreparedListener,
        MediaPlayer.OnErrorListener {

    private static final int NOTIFICATION_ID = 7;

    private MediaPlayer mediaPlayer;

    public static void start(Context context) {
        Intent intent = new Intent(context, PlaybackService.class);
        context.startService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        startInForeground();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startPlaybackFromSavedPath();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopPlayback();
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onPrepared(MediaPlayer player) {
        player.start();
    }

    @Override
    public boolean onError(MediaPlayer player, int what, int extra) {
        Toast.makeText(this, R.string.error_cannot_play, Toast.LENGTH_SHORT).show();
        stopPlayback();
        stopSelf();
        return true;
    }

    private void startPlaybackFromSavedPath() {
        String savedPath = Prefs.loadMp3Path(this);
        if (TextUtils.isEmpty(savedPath)) {
            stopSelf();
            return;
        }

        File file = new File(savedPath);
        if (!file.isFile()) {
            Toast.makeText(this, R.string.error_file_missing, Toast.LENGTH_SHORT).show();
            stopSelf();
            return;
        }

        stopPlayback();

        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        mediaPlayer.setLooping(true);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnErrorListener(this);

        try {
            mediaPlayer.setDataSource(savedPath);
            mediaPlayer.prepareAsync();
        } catch (IOException exception) {
            Toast.makeText(this, R.string.error_cannot_play, Toast.LENGTH_SHORT).show();
            stopPlayback();
            stopSelf();
        }
    }

    private void stopPlayback() {
        if (mediaPlayer == null) {
            return;
        }
        try {
            mediaPlayer.stop();
        } catch (IllegalStateException ignored) {
        }
        mediaPlayer.reset();
        mediaPlayer.release();
        mediaPlayer = null;
    }

    private void startInForeground() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, flags);

        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setContentIntent(contentIntent)
                .setOngoing(true);

        Notification notification;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            notification = builder.build();
        } else {
            notification = builder.getNotification();
        }

        startForeground(NOTIFICATION_ID, notification);
    }
}
