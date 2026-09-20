package com.example.storagereader;

import android.os.FileObserver;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class SyncWatcher {
    interface Listener { void changed(); }

    private final Listener listener;
    private final List<FileObserver> observers = new ArrayList<>();

    SyncWatcher(Listener listener) { this.listener = listener; }

    synchronized void start() {
        for (String root : new String[]{"DCIM", "Download", "Pictures", "Movies"}) {
            addTree(Environment.getExternalStoragePublicDirectory(root));
        }
    }

    synchronized void stop() {
        for (FileObserver observer : observers) observer.stopWatching();
        observers.clear();
    }

    private void addTree(File directory) {
        if (!directory.isDirectory()) return;
        FileObserver observer = new FileObserver(directory.getAbsolutePath(),
                FileObserver.CREATE | FileObserver.CLOSE_WRITE | FileObserver.MOVED_TO) {
            @Override public void onEvent(int event, String path) {
                if (path != null && (event & FileObserver.CREATE) != 0) {
                    File created = new File(directory, path);
                    synchronized (SyncWatcher.this) {
                        if (created.isDirectory()) addTree(created);
                    }
                }
                listener.changed();
            }
        };
        observers.add(observer);
        observer.startWatching();
        File[] children = directory.listFiles();
        if (children != null) for (File child : children) if (child.isDirectory()) addTree(child);
    }
}
