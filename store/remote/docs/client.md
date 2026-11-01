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

## Atomic backup restore

The client negotiates version-group restore support through store information.
Current clients send each complete source-version group to `/v1/restore-version`.
The server checks every affected model and add/change/delete permission using the
existing `ProcessUpdate` authorization operation before replaying the group.
The backing store validates final unique ownership and commits the whole group
atomically, preserving source versions. Old servers without this capability are
rejected before replay instead of receiving individual updates. Upgrade both
peers for this restore path; the portable backup format is version 3.

Restore staging, group ceilings and disposable-target publication follow the
[shared backup/restore contract](../../shared/docs/backup-restore.md).
An interrupted HTTP response can leave the commit outcome uncertain; publish the
target only after the complete restore succeeds.

## Serializable transaction scopes

Remote carries normalized transaction journals through the same request protocol and reports the backend transaction capabilities through `/info`. The server authorizes every read and mutation in the journal before executing it. Unauthorized commit resolution reports an uncertain outcome because authorization failure cannot establish whether an earlier attempt committed. No server-side native transaction is held between RPC calls.

The [shared contract](../../../core/docs/query.md#serializable-transactions) describes
read-your-own-writes, savepoints, conservative revision conflicts, deadlines and
commit resolution. Query and mutation APIs shipped in 0.12.1 retain their existing
contracts outside an explicit transaction.
