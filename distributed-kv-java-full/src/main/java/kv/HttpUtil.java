
package kv;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class HttpUtil {
    public static String get(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setRequestMethod("GET");
        return read(conn);
    }
    public static String delete(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setRequestMethod("DELETE");
        return read(conn);
    }
    public static String postJson(String url, String body) throws IOException {
        HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }
        return read(conn);
    }
    private static String read(HttpURLConnection conn) throws IOException {
        try (InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            InputStream es = conn.getErrorStream();
            if (es != null) {
                String msg = new String(es.readAllBytes(), StandardCharsets.UTF_8);
                throw new IOException(conn.getResponseCode() + " " + msg);
            }
            throw e;
        }
    }
}
