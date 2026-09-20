package com.example.storagereader;

import android.content.Intent;
import android.content.ActivityNotFoundException;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.text.method.ScrollingMovementMethod;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_EXPORT_SETTINGS = 10;
    private static final int REQUEST_IMPORT_SETTINGS = 11;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        DebugLog.add(this, "ASync build " + BuildConfig.BUILD_REVISION + " " + BuildConfig.BUILD_DATE);
        ((TextView) findViewById(R.id.build_info)).setText("Build " + BuildConfig.BUILD_REVISION + " | " + BuildConfig.BUILD_DATE);
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.POST_NOTIFICATIONS"}, 2);
        }
        restoreSettings();
        findViewById(R.id.grant_storage).setOnClickListener(v -> requestStorage());
        findViewById(R.id.sign_in).setOnClickListener(v -> signIn());
        findViewById(R.id.sync_now).setOnClickListener(v -> startSync());
        findViewById(R.id.save_folders).setOnClickListener(v -> saveFolders());
        findViewById(R.id.clear_debug_log).setOnClickListener(v -> {
            DebugLog.clear(this);
            refreshDebugLog();
        });
        findViewById(R.id.export_settings).setOnClickListener(v -> exportSettings());
        findViewById(R.id.import_settings).setOnClickListener(v -> importSettings());
        updatePermissionStatus();
        refreshDebugLog();
    }

    @Override protected void onResume() { super.onResume(); updatePermissionStatus(); }

    private void requestStorage() {
        if (Environment.isExternalStorageManager()) {
            status.setText("All-files access is already enabled.");
            DebugLog.add(this, "All-files access already enabled");
            return;
        }
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            DebugLog.add(this, "Opened all-files access settings");
        } catch (ActivityNotFoundException first) {
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                DebugLog.add(this, "Opened general all-files access settings");
            } catch (Exception error) {
                status.setText("Open Android Settings > Special app access > All files access, then enable ASync.");
                DebugLog.add(this, "Could not open all-files settings: " + error);
            }
        } catch (Exception error) {
            status.setText("Open Android Settings > Special app access > All files access, then enable ASync.");
            DebugLog.add(this, "Could not open app all-files settings: " + error);
        }
    }

    private void updatePermissionStatus() {
        TextView permission = findViewById(R.id.permission_status);
        permission.setText(Environment.isExternalStorageManager()
                ? "All-files access is enabled."
                : "All-files access is required to scan selected folders.");
        refreshDebugLog();
    }

    private void restoreSettings() {
        AppSettings settings = new AppSettings(this);
        ((EditText) findViewById(R.id.server_url)).setText(settings.serverUrl());
        ((EditText) findViewById(R.id.email)).setText(settings.email());
        status.setText(settings.lastSyncStatus());
        ((CheckBox) findViewById(R.id.folder_dcim)).setChecked(settings.enabled("DCIM"));
        ((CheckBox) findViewById(R.id.folder_download)).setChecked(settings.enabled("Download"));
        ((CheckBox) findViewById(R.id.folder_pictures)).setChecked(settings.enabled("Pictures"));
        ((CheckBox) findViewById(R.id.folder_movies)).setChecked(settings.enabled("Movies"));
        ((CheckBox) findViewById(R.id.delete_dcim)).setChecked(settings.autoDelete("DCIM"));
        ((CheckBox) findViewById(R.id.delete_download)).setChecked(settings.autoDelete("Download"));
        ((CheckBox) findViewById(R.id.delete_pictures)).setChecked(settings.autoDelete("Pictures"));
        ((CheckBox) findViewById(R.id.delete_movies)).setChecked(settings.autoDelete("Movies"));
        ((CheckBox) findViewById(R.id.watch_enabled)).setChecked(settings.watchEnabled());
    }

    private void signIn() {
        String url = ((EditText) findViewById(R.id.server_url)).getText().toString().trim();
        String email = ((EditText) findViewById(R.id.email)).getText().toString().trim();
        String password = ((EditText) findViewById(R.id.password)).getText().toString();
        if (!url.startsWith("https://") || email.isEmpty() || password.isEmpty()) {
            status.setText("Enter an HTTPS server URL, email, and password.");
            return;
        }
        status.setText("Signing in...");
        DebugLog.add(this, "Sign-in started for " + url);
        executor.execute(() -> {
            try {
                ApiClient api = new ApiClient(url, null);
                JSONObject response = api.post("/api/login", new JSONObject().put("email", email).put("password", password));
                AppSettings settings = new AppSettings(this);
                settings.saveLogin(url, email, response.getString("token"));
                if (settings.deviceId().isEmpty()) {
                    JSONObject device = api.withToken(response.getString("token")).post("/api/devices",
                            new JSONObject().put("name", android.os.Build.MODEL).put("platform", "android"));
                    String deviceId = device.optString("ID", device.optString("id", ""));
                    if (deviceId.isEmpty()) throw new Exception("device registration returned no device ID");
                    settings.saveDeviceId(deviceId);
                }
                runOnUiThread(() -> status.setText("Signed in. Select folders and start sync."));
                DebugLog.add(this, "Sign-in succeeded");
            } catch (Exception e) { DebugLog.add(this, "Sign-in failed: " + e.getMessage()); showError(e); }
        });
    }

    private void exportSettings() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "async-settings.json");
        startActivityForResult(intent, REQUEST_EXPORT_SETTINGS);
    }

    private void importSettings() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_IMPORT_SETTINGS);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            if (requestCode == REQUEST_EXPORT_SETTINGS) {
                JSONObject settings = new JSONObject();
                settings.put("server_url", ((EditText) findViewById(R.id.server_url)).getText().toString().trim());
                settings.put("email", ((EditText) findViewById(R.id.email)).getText().toString().trim());
                settings.put("password", ((EditText) findViewById(R.id.password)).getText().toString());
                settings.put("folders", folderSettings(false));
                settings.put("auto_delete", folderSettings(true));
                try (OutputStream output = getContentResolver().openOutputStream(data.getData())) {
                    if (output == null) throw new Exception("could not open export file");
                    output.write(settings.toString(2).getBytes(StandardCharsets.UTF_8));
                }
                status.setText("Settings exported. The file contains your password in plaintext.");
                DebugLog.add(this, "Settings exported");
            } else if (requestCode == REQUEST_IMPORT_SETTINGS) {
                StringBuilder content = new StringBuilder();
                try (InputStream input = getContentResolver().openInputStream(data.getData())) {
                    if (input == null) throw new Exception("could not open import file");
                    byte[] buffer = new byte[4096]; int count;
                    while ((count = input.read(buffer)) != -1) content.append(new String(buffer, 0, count, StandardCharsets.UTF_8));
                }
                JSONObject settings = new JSONObject(content.toString());
                ((EditText) findViewById(R.id.server_url)).setText(settings.optString("server_url", ""));
                ((EditText) findViewById(R.id.email)).setText(settings.optString("email", ""));
                ((EditText) findViewById(R.id.password)).setText(settings.optString("password", ""));
                applyFolderSettings(settings.optJSONObject("folders"), false);
                applyFolderSettings(settings.optJSONObject("auto_delete"), true);
                saveFolders();
                status.setText("Settings imported. Sign in to use the imported account.");
                DebugLog.add(this, "Settings imported");
            }
        } catch (Exception error) {
            status.setText("Settings transfer failed: " + error.getMessage());
            DebugLog.add(this, "Settings transfer failed: " + error.getMessage());
        }
    }

    private JSONObject folderSettings(boolean autoDelete) throws Exception {
        JSONObject result = new JSONObject();
        result.put("DCIM", ((CheckBox) findViewById(autoDelete ? R.id.delete_dcim : R.id.folder_dcim)).isChecked());
        result.put("Download", ((CheckBox) findViewById(autoDelete ? R.id.delete_download : R.id.folder_download)).isChecked());
        result.put("Pictures", ((CheckBox) findViewById(autoDelete ? R.id.delete_pictures : R.id.folder_pictures)).isChecked());
        result.put("Movies", ((CheckBox) findViewById(autoDelete ? R.id.delete_movies : R.id.folder_movies)).isChecked());
        return result;
    }

    private void applyFolderSettings(JSONObject values, boolean autoDelete) {
        if (values == null) return;
        ((CheckBox) findViewById(autoDelete ? R.id.delete_dcim : R.id.folder_dcim)).setChecked(values.optBoolean("DCIM", false));
        ((CheckBox) findViewById(autoDelete ? R.id.delete_download : R.id.folder_download)).setChecked(values.optBoolean("Download", false));
        ((CheckBox) findViewById(autoDelete ? R.id.delete_pictures : R.id.folder_pictures)).setChecked(values.optBoolean("Pictures", false));
        ((CheckBox) findViewById(autoDelete ? R.id.delete_movies : R.id.folder_movies)).setChecked(values.optBoolean("Movies", false));
    }

    private void saveFolders() {
        AppSettings settings = new AppSettings(this);
        saveFolder(settings, "DCIM", R.id.folder_dcim, R.id.delete_dcim);
        saveFolder(settings, "Download", R.id.folder_download, R.id.delete_download);
        saveFolder(settings, "Pictures", R.id.folder_pictures, R.id.delete_pictures);
        saveFolder(settings, "Movies", R.id.folder_movies, R.id.delete_movies);
        settings.saveWatchEnabled(((CheckBox) findViewById(R.id.watch_enabled)).isChecked());
        status.setText("Folder settings saved.");
    }

    private void saveFolder(AppSettings settings, String folder, int enabled, int cleanup) {
        settings.saveFolder(folder, ((CheckBox) findViewById(enabled)).isChecked(), ((CheckBox) findViewById(cleanup)).isChecked());
    }

    private void startSync() {
        saveFolders();
        if (!Environment.isExternalStorageManager()) { requestStorage(); return; }
        if (new AppSettings(this).token().isEmpty()) { status.setText("Sign in before starting sync."); return; }
        startService(new Intent(this, SyncService.class));
        SyncScheduler.schedule(this);
        status.setText("Sync started. Future Wi-Fi syncs are scheduled every 15 minutes.");
    }

    private void showError(Exception error) {
        runOnUiThread(() -> status.setText("Error: " + error.getMessage()));
    }

    private void refreshDebugLog() {
        TextView log = findViewById(R.id.debug_log);
        if (log != null) {
            log.setText(DebugLog.read(this));
            log.setMovementMethod(ScrollingMovementMethod.getInstance());
            log.post(() -> log.scrollTo(0, log.getBottom()));
        }
    }
}
