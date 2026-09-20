package com.example.storagereader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class SyncService extends Service {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);
    private volatile boolean rerunRequested;
    private SyncWatcher watcher;
    private AppSettings settings;
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        createChannel(); startForeground(1, notification("Preparing sync"));
        if (settings == null) settings = new AppSettings(this);
        boolean watch = settings.watchEnabled();
        if (watch && watcher == null) {
            watcher = new SyncWatcher(() -> {
                rerunRequested = true;
                scheduleSync(1500);
            });
            watcher.start();
            notifyStatus("Watching folders for changes");
        }
        scheduleSync(0);
        return watch ? START_STICKY : START_NOT_STICKY;
    }
    private void scheduleSync(long delay) {
        handler.removeCallbacks(syncTask);
        handler.postDelayed(syncTask, delay);
    }
    private final Runnable syncTask = () -> {
        if (!syncRunning.compareAndSet(false, true)) return;
        rerunRequested = false;
        executor.execute(() -> {
            try {
                SyncEngine.run(this, this::notifyStatus);
            } catch (Exception e) {
                String message = "Sync failed: " + e.getMessage();
                settings.saveLastSyncStatus(message);
                DebugLog.add(this, message);
                Log.e("ASync", message, e);
                notifyStatus(message);
            } finally {
                syncRunning.set(false);
                if (rerunRequested) scheduleSync(1500);
                else if (!settings.watchEnabled()) stopSelf();
            }
        });
    };
    private void notifyStatus(String text) {
        settings.saveLastSyncStatus(text);
        DebugLog.add(this, text);
        getSystemService(NotificationManager.class).notify(1, notification(text));
    }
    private void createChannel() { getSystemService(NotificationManager.class).createNotificationChannel(new NotificationChannel("sync", "File sync", NotificationManager.IMPORTANCE_LOW)); }
    private android.app.Notification notification(String text) { return new NotificationCompat.Builder(this, "sync").setSmallIcon(android.R.drawable.stat_sys_upload).setContentTitle("ASync").setContentText(text).setOngoing(true).build(); }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (watcher != null) watcher.stop();
        executor.shutdownNow();
        super.onDestroy();
    }
}
