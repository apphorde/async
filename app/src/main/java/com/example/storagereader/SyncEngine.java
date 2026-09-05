package com.example.storagereader;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Base64;

import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.ArrayDeque;

class SyncEngine {
    static final long DELETE_AFTER_MS = 7L * 24 * 60 * 60 * 1000;
    static final long FULL_HASH_INTERVAL_MS = 24L * 60 * 60 * 1000;
    interface Reporter { void status(String value); }
    static void run(Context context, Reporter reporter) throws Exception {
        AppSettings settings = new AppSettings(context);
        if (settings.token().isEmpty() || settings.deviceId().isEmpty()) throw new Exception("sign in is required");
        ApiClient api = new ApiClient(settings.serverUrl(), settings.token());
        for (String root : new String[]{"DCIM", "Download", "Pictures", "Movies"}) {
            if (!settings.enabled(root)) continue;
            scan(api, settings, root, reporter);
        }
        reporter.status("Sync complete");
    }
    private static void scan(ApiClient api, AppSettings settings, String root, Reporter reporter) throws Exception {
        File folder = Environment.getExternalStoragePublicDirectory(root);
        if (!folder.isDirectory()) return;
        ArrayDeque<File> pending = new ArrayDeque<>(); pending.add(folder);
        while (!pending.isEmpty()) {
            File current = pending.removeFirst(); File[] children = current.listFiles(); if (children == null) continue;
            for (File file : children) {
                if (file.isDirectory()) { if (!file.getName().startsWith(".")) pending.add(file); continue; }
                if (!file.isFile() || file.getName().startsWith(".")) continue;
                String relative = root + "/" + file.getAbsolutePath().substring(folder.getAbsolutePath().length() + 1).replace(File.separatorChar, '/');
                syncFile(api, settings, root, relative, file, reporter);
            }
        }
    }
    private static void syncFile(ApiClient api, AppSettings settings, String root, String path, File file, Reporter reporter) throws Exception {
        String key = "file_" + Base64.encodeToString(path.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP);
        SharedPreferences prefs = settings.raw(); String known = prefs.getString(key + "_hash", "");
        long now = System.currentTimeMillis();
        boolean metadataChanged = prefs.getLong(key + "_size", -1) != file.length() || prefs.getLong(key + "_modified", -1) != file.lastModified();
        boolean mustHash = metadataChanged || known.isEmpty() || now - prefs.getLong(key + "_hashed", 0) >= FULL_HASH_INTERVAL_MS;
        String hash = mustHash ? hash(file) : known;
        if (!hash.equals(known)) {
            reporter.status("Uploading " + path);
            JSONObject prepared = api.post("/api/uploads/prepare", new JSONObject().put("path", path).put("size", file.length()).put("sha256", hash).put("device_id", settings.deviceId()));
            api.putFile(prepared.getString("upload_url"), file);
            api.post(prepared.getString("commit_url"), new JSONObject());
            JSONObject verified = api.get("/api/verify?path=" + java.net.URLEncoder.encode(path, "UTF-8"));
            if (!verified.optBoolean("ok") || !hash.equals(verified.optString("sha256"))) throw new Exception("remote verification failed for " + path);
            prefs.edit().putString(key + "_hash", hash).putLong(key + "_verified", now).putLong(key + "_size", file.length()).putLong(key + "_modified", file.lastModified()).putLong(key + "_hashed", now).apply();
            return;
        }
        if (mustHash) prefs.edit().putLong(key + "_size", file.length()).putLong(key + "_modified", file.lastModified()).putLong(key + "_hashed", now).apply();
        long verified = prefs.getLong(key + "_verified", 0);
        if (settings.autoDelete(root) && verified > 0 && System.currentTimeMillis() - verified >= DELETE_AFTER_MS) {
            // Metadata is fast change detection; deletion requires byte-level confirmation.
            hash = hash(file);
            JSONObject remote = api.get("/api/verify?path=" + java.net.URLEncoder.encode(path, "UTF-8"));
            if (remote.optBoolean("ok") && hash.equals(remote.optString("sha256"))) {
                reporter.status("Deleting verified file " + path);
                if (!file.delete()) throw new Exception("could not delete " + path);
                prefs.edit().remove(key + "_hash").remove(key + "_verified").remove(key + "_size").remove(key + "_modified").remove(key + "_hashed").apply();
            }
        }
    }
    private static String hash(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256"); byte[] buffer = new byte[64 * 1024]; int n;
        try (FileInputStream input = new FileInputStream(file)) { while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n); }
        StringBuilder result = new StringBuilder(); for (byte b : digest.digest()) result.append(String.format("%02x", b)); return result.toString();
    }
}
