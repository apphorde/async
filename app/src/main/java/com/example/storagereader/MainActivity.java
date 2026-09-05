package com.example.storagereader;

import android.content.Intent;
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

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);
        status = findViewById(R.id.status);
        if (android.os.Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, "android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{"android.permission.POST_NOTIFICATIONS"}, 2);
        }
        restoreSettings();
        findViewById(R.id.grant_storage).setOnClickListener(v -> requestStorage());
        findViewById(R.id.sign_in).setOnClickListener(v -> signIn());
        findViewById(R.id.sync_now).setOnClickListener(v -> startSync());
        findViewById(R.id.save_folders).setOnClickListener(v -> saveFolders());
        updatePermissionStatus();
    }

    @Override protected void onResume() { super.onResume(); updatePermissionStatus(); }

    private void requestStorage() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    private void updatePermissionStatus() {
        TextView permission = findViewById(R.id.permission_status);
        permission.setText(Environment.isExternalStorageManager()
                ? "All-files access is enabled."
                : "All-files access is required to scan selected folders.");
    }

    private void restoreSettings() {
        AppSettings settings = new AppSettings(this);
        ((EditText) findViewById(R.id.server_url)).setText(settings.serverUrl());
        ((EditText) findViewById(R.id.email)).setText(settings.email());
        ((CheckBox) findViewById(R.id.folder_dcim)).setChecked(settings.enabled("DCIM"));
        ((CheckBox) findViewById(R.id.folder_download)).setChecked(settings.enabled("Download"));
        ((CheckBox) findViewById(R.id.folder_pictures)).setChecked(settings.enabled("Pictures"));
        ((CheckBox) findViewById(R.id.folder_movies)).setChecked(settings.enabled("Movies"));
        ((CheckBox) findViewById(R.id.delete_dcim)).setChecked(settings.autoDelete("DCIM"));
        ((CheckBox) findViewById(R.id.delete_download)).setChecked(settings.autoDelete("Download"));
        ((CheckBox) findViewById(R.id.delete_pictures)).setChecked(settings.autoDelete("Pictures"));
        ((CheckBox) findViewById(R.id.delete_movies)).setChecked(settings.autoDelete("Movies"));
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
        executor.execute(() -> {
            try {
                ApiClient api = new ApiClient(url, null);
                JSONObject response = api.post("/api/login", new JSONObject().put("email", email).put("password", password));
                AppSettings settings = new AppSettings(this);
                settings.saveLogin(url, email, response.getString("token"));
                if (settings.deviceId().isEmpty()) {
                    JSONObject device = api.withToken(response.getString("token")).post("/api/devices",
                            new JSONObject().put("name", android.os.Build.MODEL).put("platform", "android"));
                    settings.saveDeviceId(device.getString("ID"));
                }
                runOnUiThread(() -> status.setText("Signed in. Select folders and start sync."));
            } catch (Exception e) { showError(e); }
        });
    }

    private void saveFolders() {
        AppSettings settings = new AppSettings(this);
        saveFolder(settings, "DCIM", R.id.folder_dcim, R.id.delete_dcim);
        saveFolder(settings, "Download", R.id.folder_download, R.id.delete_download);
        saveFolder(settings, "Pictures", R.id.folder_pictures, R.id.delete_pictures);
        saveFolder(settings, "Movies", R.id.folder_movies, R.id.delete_movies);
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
        status.setText("Sync service started. Keep its notification enabled.");
    }

    private void showError(Exception error) {
        runOnUiThread(() -> status.setText("Error: " + error.getMessage()));
    }
}
