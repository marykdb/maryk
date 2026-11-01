# Cluster-wide live updates

By default, `executeFlow` only receives updates originating from the current process (in-memory update flow).

Enable the cluster update log to propagate updates between multiple processes connected to the same FoundationDB cluster + `directoryPath`:

```kotlin
val store = FoundationDBDataStore.open(
    directoryPath = listOf("maryk", "app"),
    dataModelsById = mapOf(1u to Account),
    clusterUpdateLogConfiguration = FoundationDBClusterUpdateLogConfiguration(
        enableClusterUpdateLog = true,
        clusterUpdateLogConsumerId = "node-1",
    ),
)
```

Typical use cases:
- Multiple app nodes serving realtime subscriptions: any node write becomes visible to listeners on all nodes.
- Read/write split: API nodes listen for updates while worker nodes write in background.
- Service decomposition: independent services share one Maryk store root but still receive consistent update events.
- Short catch-up after restart/outage: consumer cursor resumes inside retention window.

Notes:
- Models containing sensitive properties cannot use the cluster update log. Startup rejects this combination rather than persisting logical update payloads in plaintext.
- Uses FDB itself (append-only, sharded) and writes the log entry in the same transaction as the data mutation.
- Retention is time-based. If a node is offline longer than the retention window, it will resume at the retention cutoff (no replay beyond retention).
- Cluster HLC sync:
  - writers update `__updates__/v1/hlc_max/<shard>` using FDB atomic `BYTE_MAX` (8-byte big-endian HLC), so cluster max advances without read-modify-write contention or per-consumer marker growth.
  - each node runs a background HLC syncer (independent from update listeners) which watches heads and refreshes the `hlc_max/*` values to keep local version generation safely at/above the cluster floor.
- `clusterUpdateLogConfiguration.clusterUpdateLogConsumerId` should be stable per node/process across restarts. Changing it creates a fresh cursor and can duplicate delivery for up to the retention window.
- Log keys include `modelId` early, so consumers can range-scan only the models they care about.
- Upgrading from the legacy HLC-ordered cluster log to the commit-ordered log is a coordinated operation: stop or quiesce every reader and writer, upgrade all binaries, then restart them. Consumers drain persisted legacy entries before storing a one-way commit-ordered cursor. Do not run mixed old/new binaries or roll back after that cursor is stored; old binaries cannot enforce or understand the transition.

Observability:
- `FoundationDBDataStore.getClusterUpdateLogStats()` exposes tail/GC counters, HLC sync counters/backoff, last activity timestamps, observed cluster HLC, and active listener counts per model.
- Use it to detect stalled tailers (`lastDecodedAtUnixMs` / `lastTailAtUnixMs`), error spikes (`tailErrors` / `gcErrors`), or unnecessary tail load (`tailTransactions` growth).

## Configuration reference

- `clusterUpdateLogConfiguration.enableClusterUpdateLog`: Persist each local write (add/change/delete) into an FDB-backed update log and tail that log back into this process to drive `executeFlow` listeners across a whole cluster (multi-writer, multi-reader). It cannot be enabled when a registered model contains sensitive properties because cluster-log payload encryption is not yet supported.
- `clusterUpdateLogConfiguration.clusterUpdateLogConsumerId`: Required when cluster update logging is enabled. Must be unique per node/process (cursor stored under `__updates__/v1/consumers/<id>`).
- `clusterUpdateLogConfiguration.clusterUpdateLogOriginId`: Optional. Defaults to the consumer id. Used to skip “echo” of updates written by this same node when tailing.
- `clusterUpdateLogConfiguration.clusterUpdateLogShardCount`: Number of log shards (per store root). Higher spreads write hot-spotting; tailers read per-shard cursors. The first enabled open persists this value; later opens must match it. Changing it requires an explicit offline log migration so backlog and retention GC remain reachable.
- `clusterUpdateLogConfiguration.clusterUpdateLogRetention`: Time window to keep log entries (default 1 hour). A background job clears old ranges by timestamp.
