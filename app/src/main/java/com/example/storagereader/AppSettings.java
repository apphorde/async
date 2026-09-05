package com.example.storagereader;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

class AppSettings {
    private final SharedPreferences preferences;
    AppSettings(Context context) {
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            preferences = EncryptedSharedPreferences.create(context, "reader_vault", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception error) { throw new IllegalStateException("Cannot initialize encrypted settings", error); }
    }
    String serverUrl() { return preferences.getString("server_url", ""); }
    String token() { return preferences.getString("token", ""); }
    String email() { return preferences.getString("email", ""); }
    String deviceId() { return preferences.getString("device_id", ""); }
    boolean enabled(String folder) { return preferences.getBoolean("folder_" + folder, folder.equals("DCIM")); }
    boolean autoDelete(String folder) { return preferences.getBoolean("delete_" + folder, false); }
    void saveLogin(String url, String email, String token) { preferences.edit().putString("server_url", url.replaceAll("/+$", "")).putString("email", email).putString("token", token).apply(); }
    void saveDeviceId(String id) { preferences.edit().putString("device_id", id).apply(); }
    void saveFolder(String folder, boolean enabled, boolean delete) { preferences.edit().putBoolean("folder_" + folder, enabled).putBoolean("delete_" + folder, delete).apply(); }
    SharedPreferences raw() { return preferences; }
}
