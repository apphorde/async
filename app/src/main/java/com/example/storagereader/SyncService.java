package com.example.storagereader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SyncService extends Service {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel(); startForeground(1, notification("Waiting to sync"));
        executor.execute(() -> {
            AppSettings settings = new AppSettings(this);
            try {
                SyncEngine.run(this, text -> {
                    settings.saveLastSyncStatus(text);
                    DebugLog.add(this, text);
                    getSystemService(NotificationManager.class).notify(1, notification(text));
                });
            } catch (Exception e) {
                String message = "Sync failed: " + e.getMessage();
                settings.saveLastSyncStatus(message);
                DebugLog.add(this, message);
                Log.e("ASync", message, e);
                getSystemService(NotificationManager.class).notify(1, notification(message));
            } finally { stopSelf(startId); }
        });
        return START_NOT_STICKY;
    }
    private void createChannel() { getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("sync", "File sync", NotificationManager.IMPORTANCE_LOW)); }
    private android.app.Notification notification(String text) { return new NotificationCompat.Builder(this, "sync").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("ASync").setContentText(text).setOngoing(true).build(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
