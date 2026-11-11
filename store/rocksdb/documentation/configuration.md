# RocksDB configuration and tuning

Existing `RocksDBDataStore.open(...)` calls need no extra configuration. The
defaults balance memory, CPU and storage costs; tune them against your workload.
For lifecycle and model registration, start with the [module overview](../README.md).

## Defaults

| Setting | Default |
| --- | --- |
| Shared block cache | 64 MiB |
| Shared memtable budget | 32 MiB, charged to the block cache |
| SST Bloom filters | 10 bits per key |
| Bytewise index search | Automatic |
| Historic index search | Binary, using Maryk's custom comparator |
| General compression | LZ4 |
| Bottommost compression | Zstandard |
| Statistics | Enabled for Maryk-owned DB options |
| Row cache, persistent cache and logging callback | Opt-in |

History retention is configured separately; see [history and transactions](history-and-transactions.md).

## Memory budgets and ownership

Existing `open(...)` calls use the defaults above. To tune resources, use
`tuning = RocksDBTuning(...)`. Defaults share a 64 MiB LRU block cache across
all column families and use a 32 MiB write-buffer budget. Memtable reservations
are charged to that cache; these numbers must not be summed as independent
allocations. They are engine budgets, not a strict process-memory limit.

With caller-owned `rocksDBOptions`, configure the write-buffer manager and
statistics on those options yourself. `tuning.memtableBudgetBytes` and
`tuning.enableStatistics` apply only to Maryk-owned DB options. Keep attached
resources alive until the store closes. The shared table cache still uses
`tuning.blockCacheBytes`.

### Choosing a cache budget

Start with the default budget and measure before reducing it substantially.
RocksDB divides its cache into shards. Large SST index or Bloom-filter blocks can
compete for space within one shard, causing repeated metadata reads and a sharp
performance drop even when total cache usage appears reasonable.

A local Maryk workload exhibited this behavior at 8 MiB but not at the default
64 MiB. This is workload-dependent: 64 MiB is a starting point, not a guaranteed
minimum or optimum. Check cache misses and workload latency when tuning.

## Compression and existing stores

Column families use LZ4 for general compression and Zstandard for the bottommost
level, following [RocksDB's compression guidance](https://github.com/facebook/rocksdb/wiki/Compression)
for balancing CPU and storage costs. This is a general-purpose default; the best balance depends on the workload.

Existing Snappy-compressed and uncompressed SST files remain readable. No rewrite
is required to open a store: new flushes and normal compactions gradually apply
the new settings. No compression migration or forced compaction runs at startup.
Any other binary opening newly written files must also support LZ4 and Zstandard;
this does not guarantee downgrade compatibility with older codec-limited builds.

## Table settings

Existing `open(...)` calls need no extra configuration. New SST files use a shared
10-bit-per-key Bloom filter to avoid unnecessary reads for missing keys. Bytewise
column families use automatic index search; historic column families retain
binary search because their custom comparator cannot use interpolation. Existing
SSTs remain readable. Startup does not rewrite them.

The examples below assume an application model named `Account` and a coroutine.
Use `tableConfiguration` only when overriding the defaults:

```kotlin
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.datastore.rocksdb.RocksDBTableConfiguration
import maryk.rocksdb.CompressionType

RocksDBDataStore.open(
    relativePath = "accounts.db",
    dataModelsById = mapOf(1u to Account),
    tableConfiguration = RocksDBTableConfiguration(
        bloomFilterBitsPerKey = 8.0, // null disables filters for future SSTs
        compression = CompressionType.LZ4_COMPRESSION,
        bottommostCompression = CompressionType.ZSTD_COMPRESSION,
    ),
).use { /* execute requests */ }
```

`compressionPerLevel` is empty by default, using the general codec. A nonempty
list overrides compression by LSM level; `bottommostCompression` still controls
the bottommost level. These choices apply during flush and compaction. Maryk
copies the list when opening. `indexSearchType` overrides bytewise families only;
historic families always retain binary search.

Persistent caching is disabled by default. To cache SST pages on a separate
persistent volume, pass a binding `PersistentCache` as `persistentCache` in the
table configuration. Its cache directory is disposable and is not a backup or
part of portable exports. Keep the caller-owned cache, environment and logger
alive until all stores using them close; close the cache before its logger.

The binding's optional row cache and logging callback use the existing
`rocksDBOptions` argument, without another Maryk configuration layer:

```kotlin
import maryk.datastore.rocksdb.RocksDBDataStore
import maryk.rocksdb.DBOptions
import maryk.rocksdb.InfoLogLevel
import maryk.rocksdb.LRUCache
import maryk.rocksdb.createLogger
import maryk.rocksdb.loadRocksDBLibrary
import maryk.rocksdb.setLogger

loadRocksDBLibrary()
createLogger(InfoLogLevel.INFO_LEVEL) { _, message -> println(message) }.use { logger ->
    LRUCache(4L * 1024 * 1024).use { rowCache ->
        DBOptions().setCreateIfMissing(true).setCreateMissingColumnFamilies(true)
            .setLogger(logger).setRowCache(rowCache).use { options ->
                RocksDBDataStore.open(
                    relativePath = "accounts.db",
                    dataModelsById = mapOf(1u to Account),
                    rocksDBOptions = options,
                ).use { /* execute requests */ }
            }
    }
}
```

The logger can run on engine threads; keep callbacks short and thread-safe.
Row caching adds memory beyond the default budgets and is opt-in. Supplied
`DBOptions` remain caller-owned, including their statistics and write-buffer
configuration. Maryk does not attach a custom logger, row cache or persistent cache by default.

## Diagnostics and integrity checks

Migration, repair, and index-building reads avoid inserting scanned data blocks
into the application cache. RocksDB may still cache small index/table metadata.
Bounded prefix predicates preserve the request snapshot and pending
transaction writes; historic comparator scans retain their own boundary logic.
Iterator read failures propagate instead of appearing as successful exhaustion.

`diagnostics()` returns approximate memory accounting, cache usage and pins,
compaction backlog, immutable memtables, write-stop state, and cumulative cache
and write-stall counters when Maryk owns the statistics configuration.
`verifyStorageChecksums()` checks RocksDB's SST files on the IO dispatcher and throws on corruption.
Call these while the store is open and coordinate them with store shutdown.

`storageDiagnostics()` samples live SST file count/bytes, bytes by LSM level, and
WAL file count/bytes on the IO dispatcher. These are separate live inventories,
not an atomic filesystem snapshot or total directory size: obsolete files and
other metadata are not included. WAL usage can remain significant even when SST
compression is effective. Pinned cache usage may also include write-buffer
reservations after application iterators have closed.
