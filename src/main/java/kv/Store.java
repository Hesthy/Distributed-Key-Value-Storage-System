
package kv;

import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();

    public String get(String key) { return map.get(key); }
    public void set(String key, String val) { map.put(key, val); }
    public void delete(String key) { map.remove(key); }
}
