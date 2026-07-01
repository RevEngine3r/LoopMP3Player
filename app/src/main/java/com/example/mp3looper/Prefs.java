package com.example.mp3looper;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String PREFS_NAME = "mp3_looper_prefs";
    private static final String KEY_MP3_PATH = "mp3_path";

    private Prefs() {
    }

    static void saveMp3Path(Context context, String path) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        preferences.edit().putString(KEY_MP3_PATH, path).commit();
    }

    static String loadMp3Path(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return preferences.getString(KEY_MP3_PATH, null);
    }
}
