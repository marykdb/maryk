# Remote Store (HTTP/SSH)

Expose a local Maryk store over HTTP and connect through `RemoteDataStore`, using
the same typed request API as a local store. This is useful for the desktop App,
remote tools and clients that share the server's model definitions.

## What it provides

- `RemoteStoreServer`: a Ktor server over a local store.
- `RemoteDataStore`: an `IsDataStore` client for HTTP/HTTPS.
- Optional SSH forwarding through the system `ssh` binary.

The server and client support JVM and Kotlin/Native desktop targets.

## Start a server

Serve an existing local RocksDB store on loopback:

```text
maryk --exec "serve rocksdb --dir ./data --host 127.0.0.1 --port 8210"
```

For remote access, keep the server loopback-bound behind TLS termination or use
SSH forwarding. Non-loopback plaintext binds require an explicit insecure opt-in,
even with authentication. See [server configuration](docs/server.md) for tokens,
authorization, FoundationDB and config files.

## Connect as a client

Call `connect` from a coroutine and close the client when finished:

```kotlin
import maryk.datastore.remote.RemoteDataStore
import maryk.datastore.remote.RemoteStoreConfig

val remote = RemoteDataStore.connect(
    RemoteStoreConfig(baseUrl = "http://127.0.0.1:8210"),
)
try {
    // Execute typed requests for the models registered by the server.
} finally {
    remote.close()
}
```

Use matching model definitions on both peers. A newer client cannot add a missing
server capability. See [client configuration and recovery](docs/client.md) for
authenticated connections, batches, reconnect behavior and migration controls.

## SSH tunneling

Supply `RemoteSshConfig` to forward a local loopback port before connecting.
See the [SSH example](docs/client.md#ssh-tunneling). Flow reconnection is opt-in;
reconnecting obtains a fresh state and can repeat updates.

## Update history

History requests require server support, retained versions and a ready history
index. See [history requirements](docs/client.md#update-history).

## State backup and restore

`backupState` and `restoreState` use the shared portable state format through one
server-owned capture and bounded atomic import batches. Both peers and the
backing store must advertise `supportsStateBackup`; older servers default to
unsupported. Source history is optional, while state restore requires an isolated
history-disabled target. See [state recovery](docs/client.md#state-backup-and-restore)
and the [backup choice and backend matrix](../shared/docs/backup-restore.md).

## Atomic backup restore

Both peers and the backend must support atomic source-version replay. Restore
into a disposable target and publish it only after complete success. See
[restore and uncertain outcomes](docs/client.md#atomic-backup-restore).

## Serializable transaction scopes

Remote advertises its backend's transaction capability. Retain the transaction
handle for uncertain commits; an outer ordinary request batch is sequential.
See [transactions and authorization](docs/client.md#serializable-transaction-scopes).

## HTTP protocol overview

Wire payloads use Maryk ProtoBuf. Deploy compatible peers, especially for NANOS
DateTime and packed floating value objects. See the [protocol reference](docs/protocol.md)
for endpoints, framing, negotiation and request/response limits.

### Remote regular-expression filters

The server accepts a bounded regex subset. Unsupported patterns fail before
execution; see [regex limits](docs/protocol.md#remote-regular-expression-filters).

## When to use

Use Remote to expose an existing RocksDB or FoundationDB store to clients and
tooling. For optional SQL, follow the [SQL guide](../../sql/README.md).
