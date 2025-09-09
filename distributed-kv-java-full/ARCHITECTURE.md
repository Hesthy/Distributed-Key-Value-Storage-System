
# Architecture of Distributed Key-Value Storage System

## Overview
This project simulates a distributed key-value store with sharding and replication.
It is inspired by Redis/Dynamo-style systems, designed as an educational demo.

## Components
- **Store**: In-memory hashmap storing key-value pairs.
- **Consistent Hash Ring**: Maps keys to a primary node (and replicas).
- **Node**: Runs an HTTP server to handle client requests (`/get`, `/set`, `/delete`) and internal replication.
- **Replication**: Best-effort replication of updates to replica nodes.

## Data Flow (Set Operation)
1. Client sends `POST /set {key, value}` to any node.
2. Node checks the consistent hash ring for the primary.
3. If it is not primary, request is proxied to the primary node.
4. Primary applies the write to its local store.
5. Primary sends replication requests to other replica nodes.
6. Replicas apply the update and respond.

## High Availability
- Replication factor ensures copies of data on multiple nodes.
- If a node fails, another replica still has the data.
- No automated leader election yet (can be extended with Raft).

## Diagram (simplified)

```
     +---------+          +---------+          +---------+
     |  Node1  | <------> |  Node2  | <------> |  Node3  |
     +---------+          +---------+          +---------+
         ^                     ^                     ^
         |                     |                     |
        Key1                 Key2                  Key3
```

## Extensions
- Persistence (RDB/AOF)
- Quorum reads/writes
- Dockerized cluster
- Monitoring & metrics
