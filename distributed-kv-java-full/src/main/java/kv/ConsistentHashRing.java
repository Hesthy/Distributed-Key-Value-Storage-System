
package kv;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.*;

public class ConsistentHashRing {
    private final TreeMap<BigInteger, NodeInfo> ring = new TreeMap<>();
    private final int vnodes;
    private final List<NodeInfo> nodes;

    public ConsistentHashRing(List<NodeInfo> nodes, int vnodes) {
        this.vnodes = vnodes;
        this.nodes = new ArrayList<>(nodes);
        build();
    }

    private void build() {
        ring.clear();
        for (NodeInfo n : nodes) {
            for (int i = 0; i < vnodes; i++) {
                ring.put(hash(n.id() + "#" + i), n);
            }
        }
    }

    public NodeInfo primaryFor(String key) {
        BigInteger h = hash(key);
        Map.Entry<BigInteger, NodeInfo> e = ring.ceilingEntry(h);
        if (e == null) return ring.firstEntry().getValue();
        return e.getValue();
    }

    public List<NodeInfo> replicasFor(String key, int replicas) {
        // primary + next replicas-1 distinct nodes around the ring
        Set<String> seen = new LinkedHashSet<>();
        List<NodeInfo> result = new ArrayList<>();
        BigInteger h = hash(key);
        SortedMap<BigInteger, NodeInfo> tail = ring.tailMap(h);
        for (NodeInfo n : tail.values()) {
            if (seen.add(n.id())) {
                result.add(n);
                if (result.size() >= replicas) return result;
            }
        }
        for (NodeInfo n : ring.values()) {
            if (seen.add(n.id())) {
                result.add(n);
                if (result.size() >= replicas) return result;
            }
        }
        return result;
    }

    private static BigInteger hash(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes());
            return new BigInteger(1, d);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
