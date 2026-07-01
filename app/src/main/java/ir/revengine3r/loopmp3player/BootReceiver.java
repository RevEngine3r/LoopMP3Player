package ir.revengine3r.loopmp3player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

/**
 * Receives BOOT_COMPLETED / LOCKED_BOOT_COMPLETED and starts PlaybackService
 * if a saved file URI exists in SharedPreferences.
 */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.LOCKED_BOOT_COMPLETED".equals(action)) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(
                MainActivity.PREF_NAME, Context.MODE_PRIVATE);
        String savedUri = prefs.getString(MainActivity.PREF_FILE_URI, null);

        if (savedUri == null) return; // nothing saved, do nothing

        Intent serviceIntent = new Intent(context, PlaybackService.class);
        serviceIntent.setAction(PlaybackService.ACTION_PLAY);
        serviceIntent.putExtra(PlaybackService.EXTRA_URI, savedUri);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
