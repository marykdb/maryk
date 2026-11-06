# Remote clients and recovery

See the [Remote Store overview](../README.md) for a first connection.

```kotlin
val remote = RemoteDataStore.connect(
    RemoteStoreConfig(
        baseUrl = "https://store.example.test",
        bearerToken = System.getenv("MARYK_BEARER_TOKEN"),
        flowRetryPolicy = RemoteFlowRetryPolicy.Default,
    )
)
```

Notes:
- `RemoteDataStore.connect` is `suspend`; call it from a coroutine.
- HTTP and HTTPS are supported. A bearer token over public plain HTTP is rejected by default before network I/O. Loopback HTTP and SSH tunnel connections remain allowed; passing `allowInsecureBearerTransport = true` to the two-argument `RemoteDataStore.connect` overload is the deliberate unsafe opt-in for a public plaintext connection.
- `baseUrl` must not contain query params, fragments, user info, or leading/trailing whitespace.
- For direct internet exposure, terminate TLS in a reverse proxy and forward to the loopback server.
- Flow reconnect is opt-in to preserve legacy completion behavior. `Default` retries
  five times with bounded exponential backoff. A reconnect obtains a fresh state and
  delivers it at least once: it can repeat updates, and consumers must handle duplicates.
  It does not provide durable or exactly-once replay.
- Set `heartbeatTimeoutMillis` when the client must reconnect stalled connections.
  Protocol-v2 clients negotiate server heartbeat frames; legacy clients never
  receive them.

Use it like any other store. This example assumes that the server registers the
[Person model](../../../core/docs/datamodel.md) and the client uses the same definition:

```kotlin
import maryk.core.query.requests.scan

val people = remote.execute(Person.scan(limit = 20u))
```

Stores with managed migrations also expose the shared `MigrationAdmin` API through
the remote client. The desktop Operations → Migrations dialog uses the same
endpoint; when the App connection has a bearer-token file configured, that token
is sent with the administration request. Authorization callbacks receive one of
`MigrationStatus`, `MigrationPause`, `MigrationResume`, or `MigrationCancel`, plus
the target model name for control operations. The CLI currently manages migrations
only on its directly connected local RocksDB or FoundationDB store; it does not
connect to Remote Store endpoints.

Execute several ordered requests in one round trip:

```kotlin
val responses = remote.execute(
    Requests(
        Person.add(firstValues),
        Person.add(secondValues),
    )
)
```

`Requests.create(context = ...)` also supports Collect & Inject batches containing
unresolved `Inject` values. Responses preserve request order. Execution is fail-fast
and is not transactionally atomic.

The `/info` response advertises atomic limit metadata whenever atomic requests
are supported. Remote exposes it through `atomicCapabilities` and rejects count
limits before sending the request. Advertising atomic support without its limits
is invalid and prevents connection. Structured backend limit failures survive
transport.

An explicit `AtomicRequest` is transported as one batch member. The `/info`
response advertises the backend's `supportsAtomicRequests` capability (absent
means false for older servers). All participating models and semantic mutation
permissions are checked before execution, including guards and soft-delete
changes. The atomic request is forwarded once; Remote never decomposes it into
ordinary requests. An outer `Requests` batch still has its existing sequential
semantics. Retain the original atomic request and its ID when retrying an uncertain
network outcome.

## SSH tunneling

Remote store supports SSH port forwarding via the system `ssh` binary.
Provide an SSH config and the client will open a local tunnel before connecting.

```kotlin
val remote = RemoteDataStore.connect(
    RemoteStoreConfig(
        baseUrl = "http://remote-host:8210",
        ssh = RemoteSshConfig(
            host = "remote-host",
            user = "maryk",
            remotePort = 8210,
            localPort = 9821,
            identityFile = "~/.ssh/id_ed25519",
        )
    )
)
```

Notes:
- `remotePort`/`remoteHost` default to the `baseUrl` host/port if omitted.
- `localPort` can be omitted to auto-select a free port.
- Uses `ssh -N -L 127.0.0.1:localPort:remoteHost:remotePort` with `ExitOnForwardFailure=yes`, so the forwarded port is never exposed on other local interfaces.
- An SSH tunnel can safely connect to a loopback-bound server without bearer authentication.

## Update history

The client transports `getUpdateHistory` and `scanUpdateHistory`, including
their continuation cursors. The server must support these requests and its
backing store must retain values and a ready update-history index. An older
server does not gain the new request type through a client upgrade. See
[history paging](../../../core/docs/query.md#getscan-update-history).

## State backup and restore

Use the shared `backupState` / `restoreState` APIs on `RemoteDataStore`; check
`remote.supportsStateBackup` first. Both peers and the backing store must support
it. Export uses one server-owned capture and inherits the backing store's limits.
Follow the [state backup guide](../../shared/docs/state-backups.md) for storage
adapters, target settings and restore steps.

Closing the remote store cancels its state operations without closing an
application-supplied HTTP client. On export failure, the SDK also attempts an
explicit server cancellation for up to five seconds. Cleanup is best effort if
the network is unavailable. There is no automatic reconnect or archive resume.

State restore applies atomic batches, not one whole-archive transaction. A lost
acknowledgement can follow a committed batch: discard the isolated target and
restart rather than retrying that batch blindly. The server requires `StateBackup`
for export, and `StateRestore` plus applicable `ProcessUpdate` permissions for import.

See [protocol details](protocol.md#state-archive-transport) for framing, idle
bounds, admission and cancellation behavior.

## Atomic backup restore

For retained history, both peers and the backing store must support atomic
source-version replay. The client negotiates that capability and sends each group
to `/v1/restore-version`; an older unsupported server is refused. Authorization
checks cover every affected model and the group's `ProcessUpdate` operations
before replay. The group commits atomically and preserves original versions.

Follow the [history backup guide](../../shared/docs/history-backups.md) for source
and target settings. A lost response can have a committed outcome; use an isolated,
disposable target and publish it only after complete restore and validation.

## Serializable transaction scopes

Remote carries normalized transaction journals through the same request protocol and reports the backend transaction capabilities through `/info`. The server authorizes every read and mutation in the journal before executing it. Unauthorized commit resolution reports an uncertain outcome because authorization failure cannot establish whether an earlier attempt committed. No server-side native transaction is held between RPC calls.

The [shared contract](../../../core/docs/query.md#serializable-transactions) describes
read-your-own-writes, savepoints, conservative revision conflicts, deadlines and
commit resolution. Query and mutation APIs shipped in 0.12.1 retain their existing
contracts outside an explicit transaction.
