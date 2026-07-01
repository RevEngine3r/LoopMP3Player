package com.example.mp3looper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MEDIA_MOUNTED.equals(action)) {
            return;
        }

        String savedPath = Prefs.loadMp3Path(context);
        if (TextUtils.isEmpty(savedPath)) {
            return;
        }

        PlaybackService.start(context);
    }
}
