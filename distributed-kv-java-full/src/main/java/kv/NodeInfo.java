
package kv;

public class NodeInfo {
    public final String host;
    public final int port;

    public NodeInfo(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String id() { return host + ":" + port; }

    @Override
    public String toString() {
        return id();
    }

    @Override
    public int hashCode() { return id().hashCode(); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NodeInfo)) return false;
        NodeInfo other = (NodeInfo) o;
        return this.port == other.port && this.host.equals(other.host);
    }
}
