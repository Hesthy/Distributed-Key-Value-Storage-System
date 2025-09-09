
package kv;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;
import java.util.concurrent.Executors;

public class Node {
    private final NodeInfo self;
    private final ConsistentHashRing ring;
    private final Store store;
    private final int replicas;

    public Node(NodeInfo self, ConsistentHashRing ring, Store store, int replicas) {
        this.self = self;
        this.ring = ring;
        this.store = store;
        this.replicas = Math.max(1, replicas);
    }

    public void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(self.port), 0);
        server.createContext("/get", this::handleGet);
        server.createContext("/set", this::handleSet);
        server.createContext("/delete", this::handleDelete);
        server.createContext("/replicate", this::handleReplicate);
        server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        server.start();
    }

    private void handleGet(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex.getRequestURI());
        String key = q.get("key");
        if (key == null) { send(ex, 400, "missing key"); return; }
        NodeInfo primary = ring.primaryFor(key);
        if (!primary.equals(self)) {
            // proxy to primary
            String resp = HttpUtil.get("http://" + primary.id() + "/get?key=" + urlEncode(key));
            sendJson(ex, 200, resp);
            return;
        }
        String val = store.get(key);
        String body = "{"key":"" + escape(key) + "","value":" + (val==null?"null":"""+escape(val)+""") + "}";
        sendJson(ex, 200, body);
    }

    private void handleSet(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String,String> json = parseJson(body);
        String key = json.get("key");
        String val = json.get("value");
        if (key == null || val == null) { send(ex, 400, "missing key/value"); return; }
        NodeInfo primary = ring.primaryFor(key);
        if (!primary.equals(self)) {
            String resp = HttpUtil.postJson("http://" + primary.id() + "/set", body);
            sendJson(ex, 200, resp);
            return;
        }
        // apply locally
        store.set(key, val);
        // replicate to others (best-effort)
        List<NodeInfo> repls = ring.replicasFor(key, replicas);
        for (NodeInfo n : repls) {
            if (n.equals(self)) continue;
            try {
                HttpUtil.postJson("http://" + n.id() + "/replicate", "{"op":"set","key":""+escape(key)+"","value":""+escape(val)+""}");
            } catch (Exception ignored) {}
        }
        sendJson(ex, 200, "{"status":"ok"}");
    }

    private void handleDelete(HttpExchange ex) throws IOException {
        Map<String,String> q = parseQuery(ex.getRequestURI());
        String key = q.get("key");
        if (key == null) { send(ex, 400, "missing key"); return; }
        NodeInfo primary = ring.primaryFor(key);
        if (!primary.equals(self)) {
            String resp = HttpUtil.delete("http://" + primary.id() + "/delete?key=" + urlEncode(key));
            sendJson(ex, 200, resp);
            return;
        }
        store.delete(key);
        List<NodeInfo> repls = ring.replicasFor(key, replicas);
        for (NodeInfo n : repls) {
            if (n.equals(self)) continue;
            try {
                HttpUtil.postJson("http://" + n.id() + "/replicate", "{"op":"delete","key":""+escape(key)+""}");
            } catch (Exception ignored) {}
        }
        sendJson(ex, 200, "{"status":"ok"}");
    }

    private void handleReplicate(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String,String> json = parseJson(body);
        String op = json.get("op");
        String key = json.get("key");
        String val = json.get("value");
        if (op == null || key == null) { send(ex, 400, "bad replication msg"); return; }
        if ("set".equals(op)) store.set(key, val);
        else if ("delete".equals(op)) store.delete(key);
        sendJson(ex, 200, "{"status":"ok"}");
    }

    // ---- helpers ----

    private static Map<String,String> parseQuery(URI uri) {
        Map<String,String> m = new HashMap<>();
        String q = uri.getQuery();
        if (q == null) return m;
        for (String p : q.split("&")) {
            int i = p.indexOf('=');
            if (i < 0) m.put(urlDecode(p), "");
            else m.put(urlDecode(p.substring(0,i)), urlDecode(p.substring(i+1)));
        }
        return m;
    }

    private static Map<String,String> parseJson(String s) {
        // super tiny JSON parser for key/value pairs (flat only) to avoid dependencies
        Map<String,String> m = new HashMap<>();
        s = s.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length()-1);
            if (!s.isEmpty()) {
                // split on commas not inside quotes (naive but fine for simple bodies)
                List<String> parts = new ArrayList<>();
                StringBuilder cur = new StringBuilder();
                boolean inQ = false;
                for (int i=0;i<s.length();i++) {
                    char c = s.charAt(i);
                    if (c=='"') inQ = !inQ;
                    if (c==',' && !inQ) { parts.add(cur.toString()); cur.setLength(0); }
                    else cur.append(c);
                }
                parts.add(cur.toString());
                for (String kv : parts) {
                    int i = kv.indexOf(':');
                    if (i<0) continue;
                    String k = strip(kv.substring(0,i));
                    String v = strip(kv.substring(i+1));
                    k = unquote(k);
                    if (v.equals("null")) m.put(k, null);
                    else m.put(k, unquote(v));
                }
            }
        }
        return m;
    }

    private static String strip(String s) { return s.trim(); }
    private static String unquote(String s) {
        s = s.trim();
        if (s.startsWith(""") && s.endsWith(""")) s = s.substring(1, s.length()-1);
        return s.replace("\"", """);
    }
    private static String escape(String s) { return s.replace("\", "\\").replace(""","\""); }

    private static void send(HttpExchange ex, int code, String text) throws IOException {
        byte[] b = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static void sendJson(HttpExchange ex, int code, String json) throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(b); }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
    private static String urlDecode(String s) {
        return java.net.URLDecoder.decode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
