package com.example.storagereader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SyncService extends Service {
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private boolean started;
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel(); startForeground(1, notification("Waiting to sync"));
        if (!started) {
            started = true;
            executor.scheduleWithFixedDelay(() -> { try { SyncEngine.run(this, text -> getSystemService(NotificationManager.class).notify(1, notification(text))); } catch (Exception e) { getSystemService(NotificationManager.class).notify(1, notification("Sync failed: " + e.getMessage())); } }, 0, 15, TimeUnit.MINUTES);
        }
        return START_STICKY;
    }
    private void createChannel() { getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("sync", "File sync", NotificationManager.IMPORTANCE_LOW)); }
    private android.app.Notification notification(String text) { return new NotificationCompat.Builder(this, "sync").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("Reader Vault").setContentText(text).setOngoing(true).build(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
