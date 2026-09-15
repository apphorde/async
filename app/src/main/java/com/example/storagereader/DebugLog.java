package com.example.storagereader;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class DebugLog {
    private static final String KEY = "debug_log";
    private static final int MAX_LENGTH = 12000;

    private DebugLog() {}

    static synchronized void add(Context context, String message) {
        try {
            AppSettings settings = new AppSettings(context.getApplicationContext());
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            String current = settings.raw().getString(KEY, "");
            String updated = current + (current.isEmpty() ? "" : "\n") + timestamp + " " + message;
            if (updated.length() > MAX_LENGTH) updated = updated.substring(updated.length() - MAX_LENGTH);
            settings.raw().edit().putString(KEY, updated).apply();
        } catch (Exception ignored) {
            // Diagnostics must never stop a sync or crash the UI.
        }
    }

    static String read(Context context) {
        return new AppSettings(context).raw().getString(KEY, "");
    }

    static void clear(Context context) {
        new AppSettings(context).raw().edit().remove(KEY).apply();
    }
}
