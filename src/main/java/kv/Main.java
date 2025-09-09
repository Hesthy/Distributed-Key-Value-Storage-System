
package kv;

import java.util.*;
import java.util.stream.Collectors;

public class Main {
    public static void main(String[] args) throws Exception {
        int port = 7001;
        String peersArg = "7001,7002,7003";
        int replicas = 2;
        int vnodes = 100;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port": port = Integer.parseInt(args[++i]); break;
                case "--peers": peersArg = args[++i]; break;
                case "--replicas": replicas = Integer.parseInt(args[++i]); break;
                case "--vnodes": vnodes = Integer.parseInt(args[++i]); break;
                default: System.err.println("Unknown arg: " + args[i]); System.exit(1);
            }
        }
        List<Integer> ports = Arrays.stream(peersArg.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toList());

        List<NodeInfo> nodes = new ArrayList<>();
        for (int p : ports) nodes.add(new NodeInfo("localhost", p));

        ConsistentHashRing ring = new ConsistentHashRing(nodes, vnodes);
        Store store = new Store();
        Node node = new Node(new NodeInfo("localhost", port), ring, store, replicas);
        node.start();
        System.out.printf("Node started on port %d with peers %s, replicas=%d, vnodes=%d%n", port, peersArg, replicas, vnodes);
    }
}
