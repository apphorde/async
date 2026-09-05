package com.example.storagereader;

import org.json.JSONObject;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

class ApiClient {
    private final String baseUrl, token;
    ApiClient(String baseUrl, String token) { this.baseUrl = baseUrl; this.token = token; }
    ApiClient withToken(String value) { return new ApiClient(baseUrl, value); }
    JSONObject post(String path, JSONObject body) throws Exception { return request("POST", path, body.toString().getBytes()); }
    JSONObject get(String path) throws Exception { return request("GET", path, null); }
    void putFile(String path, File file) throws Exception {
        HttpURLConnection c = connection("PUT", path);
        c.setFixedLengthStreamingMode(file.length()); c.setDoOutput(true);
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(file)); OutputStream out = c.getOutputStream()) {
            byte[] buffer = new byte[64 * 1024]; int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
        }
        if (c.getResponseCode() / 100 != 2) throw new Exception("upload failed (HTTP " + c.getResponseCode() + ")");
        c.disconnect();
    }
    private JSONObject request(String method, String path, byte[] body) throws Exception {
        HttpURLConnection c = connection(method, path);
        if (body != null) { c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json"); c.getOutputStream().write(body); }
        int code = c.getResponseCode();
        java.io.InputStream input = code / 100 == 2 ? c.getInputStream() : c.getErrorStream();
        byte[] response = readAll(input); c.disconnect();
        JSONObject result = new JSONObject(new String(response));
        if (code / 100 != 2) throw new Exception(result.optString("error", "HTTP " + code));
        return result;
    }
    private HttpURLConnection connection(String method, String path) throws Exception {
        URL url = new URL(path.startsWith("http") ? path : baseUrl + path);
        HttpURLConnection c = (HttpURLConnection) url.openConnection(); c.setRequestMethod(method); c.setConnectTimeout(15000); c.setReadTimeout(120000);
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
        return c;
    }
    private byte[] readAll(java.io.InputStream input) throws Exception {
        if (input == null) return new byte[0];
        try (java.io.InputStream stream = input; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096]; int count;
            while ((count = stream.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }
}
