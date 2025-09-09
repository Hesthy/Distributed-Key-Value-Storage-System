
# Distributed Key-Value Storage (Java, Minimal)

A lightweight, educational distributed key-value store with:
- Consistent hashing ring for sharding
- Simple replication (primary + N-1 replicas)
- HTTP API using the built-in `com.sun.net.httpserver.HttpServer`
- Proxying to the primary node when a request hits a non-primary node

> This is **single-project multi-process**: run multiple JVM processes (ports) on one machine to simulate a cluster.

## API

- `GET /get?key=K`
- `POST /set` with JSON body `{ "key": "...", "value": "..." }`
- `DELETE /delete?key=K`

Internal (replication):
- `POST /replicate` with JSON body `{ "key": "...", "value": "...", "op": "set|delete" }`

## Build & Run

Requires JDK 17+.

```bash
# compile
javac -d out $(find src/main/java -name "*.java")

# start 3 nodes in separate terminals
java -cp out kv.Main --port 7001 --peers 7001,7002,7003 --replicas 2
java -cp out kv.Main --port 7002 --peers 7001,7002,7003 --replicas 2
java -cp out kv.Main --port 7003 --peers 7001,7002,7003 --replicas 2
```

### Examples

```bash
# write (to any node; it will route to the primary)
curl -X POST localhost:7001/set -H "Content-Type: application/json" -d '{"key":"foo","value":"bar"}'

# read
curl "http://localhost:7002/get?key=foo"

# delete
curl -X DELETE "http://localhost:7003/delete?key=foo"
```

## Design (brief)

- **ConsistentHashRing** maps keys to a primary node (and next R-1 replicas) using virtual nodes for better load balance.
- **Node** runs an HTTP server, proxies requests to the primary when needed, and accepts replication events.
- **Store** is a thread-safe in-memory map.
- **Replication** is best-effort demo (no quorum/acks) to keep code compact.
- Failure handling is minimal; for a production design, add heartbeats, quorum writes (W), reads (R), hinted handoff, etc.

## Notes

- This is intentionally concise to be a good resume/demo project.
- Extend with: persistence (RDB/AOF), Raft for leader election, metrics, Docker compose, etc.
```