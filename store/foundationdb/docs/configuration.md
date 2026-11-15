# FoundationDB configuration and limits

Start with the [store overview](../README.md) for a first connection. This guide
covers opening options, transaction policies, memory, read limits, bulk import
and field encryption. These settings apply to the current source branch; use
the matching release documentation for published artifacts.

## Opening options

| Option | Default and purpose |
| --- | --- |
| `dataModelsById` | Required stable model-ID map; use the same IDs when reopening |
| `keepAllVersions` | `true`; retain historic values for time travel and change history |
| `keepUpdateHistoryIndex` | `false`; maintain the model-wide index needed for finite update-history pages |
| `fdbClusterFilePath` | `null`; use FoundationDB's default cluster-file lookup |
| `directoryPath` | `listOf("maryk")`; directory root containing the model directories |
| `maxConcurrentReads` | `4`; maximum read actions processed concurrently by this store instance; must be positive |
| `databaseOptionsSetter` | No-op; configure the underlying `DatabaseOptions` once during startup |
| `transactionConfiguration` | Default request/maintenance policies and a per-attempt options hook, described below |
| `executionConfiguration` | Default read/work budgets and maintenance settings, described below |
| `fieldEncryptionProvider` | `null`; required for models with sensitive properties; see [encryption](#sensitive-field-encryption-optional) |

`keepUpdateHistoryIndex` adds a per-model `update_history` subspace keyed by
change version and record key. `ScanUpdates` may select records through this
index, but its emitted synchronization events remain chronological. Enabling
history cannot recreate values that were never retained.

For schema handling, use `onlyCheckModelVersion`, `migrationConfiguration`,
`migrationLeaseConfiguration` and `versionUpdateHandler` as described in the
[migration guide](migrations.md). Cross-process listeners use
`clusterUpdateLogConfiguration`; see the [cluster-update guide](cluster-updates.md).
The `directoryLayer` overload supports explicitly configured directory trees.
Pass the same layer to the stored-model inspection helpers; opening does not
copy data from another layer.

Example: set database-level retry options

```kotlin
import maryk.datastore.foundationdb.FoundationDBDataStore

val store = FoundationDBDataStore.open(
    dataModelsById = mapOf(1u to Account),
    databaseOptionsSetter = {
        setTransactionRetryLimit(3)
        setTransactionMaxRetryDelay(5000)
    }
)
```

## Server and application memory

Plan at least **4 GB RAM per `fdbserver` process**, as specified by the
[FoundationDB system requirements](https://apple.github.io/foundationdb/configuration.html#system-requirements).
A node hosting several processes needs their combined capacity, plus room for
the OS and any colocated Maryk application. Maryk's JVM heap and the native
FoundationDB client are separate from the server process.

The server's `memory` setting is an emergency termination threshold, not a
reservation or a way to reduce allocations. `cache-memory` controls disk-page
caching; `storage-memory` applies only to the memory storage engine. See the
[server configuration reference](https://apple.github.io/foundationdb/configuration.html#fdbserver-section).
Size and monitor actual workloads, including import, backup and restore, before
setting deployment resource limits.

## Transaction policies and diagnostics

Ordinary requests default to a 4.5-second native transaction timeout, ten
retries, a one-second maximum retry delay and FoundationDB's existing 10 MB size
limit. These bounds apply to each native transaction; multi-object requests and
maintenance jobs can use several transactions. Explicit atomic requests retain
their existing 4 MiB budget and 4.5-second timeout.

`FoundationDBTransactionPolicy` exposes `timeoutMillis` (default `4_500`),
`retryLimit` (`10`), `maxRetryDelayMillis` (`1_000`) and `sizeLimitBytes`
(`10_000_000`). Timeout must be positive; retry count and delay can be zero;
size must be between 32 and 10,000,000 bytes. Changing a native policy does not
extend Maryk's fixed snapshot lifetime or explicit transaction safety limits.

Use the `open` overload with `transactionConfiguration` to configure request and
maintenance policies independently and inspect each transaction attempt:

```kotlin
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.FoundationDBTransactionConfiguration
import maryk.datastore.foundationdb.FoundationDBTransactionPolicy

val store = FoundationDBDataStore.open(
    dataModelsById = mapOf(1u to Account),
    transactionConfiguration = FoundationDBTransactionConfiguration(
        requestPolicy = FoundationDBTransactionPolicy(timeoutMillis = 3000),
        maintenancePolicy = FoundationDBTransactionPolicy(timeoutMillis = 4500, retryLimit = 20),
        optionsSetter = { context ->
            setTag(context.kind.name)
            setDebugTransactionIdentifier("maryk-${context.kind.name}-${context.dataModelId}")
            // Enable client trace logging separately before adding setLogTransaction().
        },
    ),
)
```

Request policies become database defaults before `databaseOptionsSetter` runs,
so existing database overrides retain precedence for requests. Maintenance
policies override those defaults. The transaction hook then runs on every retry
attempt; it must be thread-safe and must not perform external side effects.
`context.kind` distinguishes `Request`, `Read`, `Atomic` and `Maintenance`;
`dataModelId` is provided when one model is known. Atomic safety limits and the
no-retry rule for pinned reads are applied after the hook. Nested members of an
atomic request share its transaction and do not invoke a separate hook.

## Bounded reads, bulk import and maintenance

| `FoundationDBExecutionConfiguration` option | Default | Constraint |
| --- | --- | --- |
| `maxRecordReadBytes` | 16 MiB | Positive |
| `maxRequestReadBytes` | 32 MiB | At least `maxRecordReadBytes` |
| `maintenancePartitionBytes` | 8 MiB | Positive |
| `maintenanceConcurrency` | `1` | `1..16` |
| `watchFallbackMillis` | `30_000` | `1..Int.MAX_VALUE` milliseconds |
| `maxTransactionWorkBytes` | `null` | Positive when set; `null` uses native commit admission only |

The overload accepting `transactionConfiguration` also accepts
`executionConfiguration = FoundationDBExecutionConfiguration(...)`. Defaults
limit a materialized record to 16 MiB and a request to 32 MiB of storage-row
bytes. Exceeding a limit throws `FoundationDBReadLimitExceededException` before
value decoding. Use a projection or smaller page, or explicitly raise the
budgets. Record budgets include keys and stored payloads; they are not an exact
measurement of decoded heap usage. Aggregate/filter helper work and custom
codec/provider allocations are not covered by this accounting.

For example, raise the read budgets for an intentionally larger bounded request:

```kotlin
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.foundationdb.FoundationDBExecutionConfiguration
import maryk.datastore.foundationdb.FoundationDBTransactionConfiguration

val store = FoundationDBDataStore.open(
    dataModelsById = mapOf(1u to Account),
    transactionConfiguration = FoundationDBTransactionConfiguration(),
    executionConfiguration = FoundationDBExecutionConfiguration(
        maxRecordReadBytes = 32L * 1024 * 1024,
        maxRequestReadBytes = 64L * 1024 * 1024,
    ),
)
```

The standard overload requires `transactionConfiguration` when passing execution
settings; pass its default instance if no policy customization is needed.
These snippets assume the application's `Account` model and normal store
ownership/closing, as shown in the [overview](../README.md#getting-started).

Full record and change/history ranges stream through a bounded collector.
Change/history limits apply to all raw rows traversed before projection and
version filtering. A small selected result can therefore exceed the record
budget when its stored history is large; projections and `maxVersions` do not
reduce this raw-history charge. Reconstructed collection snapshots count toward the same record
and request budgets; update responses also charge reconstructed creation values
to the request budget.

### Bulk ingestion

For explicit bulk ingestion of distinct record keys:

```kotlin
import maryk.core.query.requests.add
import maryk.datastore.foundationdb.bulkAdd

val response = store.bulkAdd(Account.add(*accounts.toTypedArray()), concurrency = 8)
```

Here `accounts` is a list of values created through the `Account` model.
Concurrency defaults to 8 and must be in `1..64`. Duplicate record keys are
rejected before import. Each record commits independently and retains normal
validation and uniqueness checks. Response statuses and local notifications
retain input order. At most one concurrency window of notification payloads is
buffered. Ordinary `execute`
ordering and explicit atomic requests keep their existing contracts. Bulk import
is not available inside a transaction scope.

Inspect every returned status: a failed record does not roll back successful
records. The input request and status list remain materialized; the concurrency
bound is not a streaming-input or total-heap guarantee.

### Maintenance and cluster watches

Index rebuilding uses FoundationDB range-size estimates and split points from
current and historic value ranges. Boundaries are moved to complete record keys
before scheduling disjoint workers. Configure `maintenancePartitionBytes`
(default 8 MiB) and `maintenanceConcurrency` (default 1, maximum 16) to tune this.
`store.maintenanceProgress(modelId)` exposes the last rebuild's estimated source
bytes and partition completion. Estimates are approximate; progress is local to
the process and resets on reopening.

Cluster log readers use committed watches with a periodic fallback (30 seconds
by default, controlled by `watchFallbackMillis`). Listener activation also wakes
the local tailer, including when an existing backlog has no new remote writes.
The tailer rotates shard/model pairs after each batch, so a busy shard cannot
starve another. The configured poll interval remains the error fallback and a
lower bound for the watch fallback.

## Snapshot lifetime and shutdown

Ordinary read requests retain one coherent snapshot. After four seconds the
store throws `FoundationDBSnapshotExpiredException`; retry with a smaller page
and use the scan cursor to resume completed pages. Index scans are preferable
for large filtered queries. State backups use a separate revision fence; see
[state capture limits](../../shared/docs/state-backups.md#capture-limits-that-affect-operation).

Closing cancels active transactions and outstanding futures before releasing
handles. Shutdown is bounded to five seconds; timeout is reported as
`StorageException`. Cancellation during an in-flight commit does not prove
rollback, and native work may still be in flight after a shutdown timeout.

## Ordinary mutation retries

Every ordinary per-record write has a private retry marker committed with the
mutation and a retained in-memory result. If FoundationDB retries an ambiguous
commit, the same invocation returns that result without allocating or changing
values again. Accepted mutations advance the application revision; read-only
maintenance, failed validations and unchanged values do not.

Completed private markers are removed in bounded background batches from their
own metadata directory. Normal close also attempts a separately bounded cleanup
of only that instance's private namespace. A crash or an exhausted cleanup queue
can leave small orphan markers; these contain no application payload. Markers
protect automatic retries within one invocation; they do not identify a new call
after restart or resolve a terminal timeout. Use an explicit transaction with a
stable transaction ID when cross-call commit resolution is required.

The native client enforces final commit admission. Set `maxTransactionWorkBytes`
to add a conservative staged-work budget. Its estimate counts overwritten
mutations and can exceed the final commit size.

## Update history

`getUpdateHistory(key, ...)` and `scanUpdateHistory(...)` return finite,
newest-first pages when both history options are enabled and the history index
is ready. Continue with `UpdatesResponse.nextCursor`; the limit counts complete
record-version entries, independently of property-history `maxVersions`.
Both queries scan the existing model-wide history index. See
[history paging](../../../core/docs/query.md#getscan-update-history) for filters,
projections, and hard-delete retention limits.

## Sensitive Field Encryption (Optional)

Mark a property as sensitive in a model:

```kotlin
import maryk.core.properties.definitions.string

val secret by string(index = 3u, sensitive = true)
```

Provision and persist key material once in application-owned secret storage,
then load those same keys before every reopen. The following illustrates initial
key provisioning and provider construction; do not generate replacement keys on
every application startup:

```kotlin
import maryk.datastore.foundationdb.FoundationDBDataStore
import maryk.datastore.shared.encryption.AesGcmHmacSha256EncryptionProvider

val keyMaterial = AesGcmHmacSha256EncryptionProvider.generateKeyMaterial()

val store = FoundationDBDataStore.open(
    dataModelsById = mapOf(1u to MyModel),
    fieldEncryptionProvider = AesGcmHmacSha256EncryptionProvider(
        encryptionKey = keyMaterial.encryptionKey,
        tokenKey = keyMaterial.tokenKey
    )
)
```

Provider contracts live in the shared module:

- `maryk.datastore.shared.encryption.FieldEncryptionProvider`
- `maryk.datastore.shared.encryption.ContextualFieldEncryptionProvider` (required
  for sensitive fields; authenticates model, record and property identity)
- `maryk.datastore.shared.encryption.SensitiveIndexTokenProvider` (needed for sensitive+unique)

Notes:

- Sensitive values are encrypted in table value payloads (latest + historic).
- Reads auto-decrypt based on an encrypted payload marker.
- Supported for simple value properties.
- Cluster update logging cannot be enabled for models with sensitive properties;
  its persisted update payloads are not encrypted.
- Sensitive+`unique` is supported when `fieldEncryptionProvider` also implements `SensitiveIndexTokenProvider`.
- Sensitive properties cannot contribute to primary keys or secondary indexes,
  including composite and transformed indexes. Use a separate explicit blind-index
  field when search is required. Custom indexes without a dependency contract are
  rejected when the model contains sensitive properties.
- Earlier versions could accept unsafe composite indexes. This validation does not
  erase existing plaintext key/index bytes or physical backups. Recover affected
  data into a fresh store with safe definitions, then securely retire old copies.

The built-in provider implements both contextual encryption and sensitive unique
tokens. Retain encryption and token keys for historic values, unique entries and
backups that still depend on them. Follow the
[shared key-rotation guide](../../shared/docs/encryption-key-rotation.md) for key
ownership, retained keys and resumable migration.
