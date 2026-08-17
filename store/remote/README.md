# Remote Store (HTTP/SSH)

Maryk Remote Store exposes a local Maryk store over HTTP and provides a `RemoteDataStore` client that implements `IsDataStore`.
It lets the desktop App and programmatic clients connect to a store running elsewhere and still use the same request API.

## What it provides

- **RemoteDataStore**: a client-side `IsDataStore` that sends serialized Maryk requests/responses over HTTP.
- **RemoteStoreServer**: a small Ktor server that proxies requests to a local store.
- **SSH tunneling**: optional local port forwarding for secure remote access.

Platform notes:
- Server runs with Ktor CIO on JVM and Kotlin/Native desktop targets.
- Native client uses Ktor curl engine and the system `ssh` binary for tunnels.

## Start a server

CLI (recommended for local serving):

```text
maryk --exec "serve rocksdb --dir ./data --host 127.0.0.1 --port 8210"
```

Programmatic server:

```kotlin
val server = RemoteStoreServer(dataStore)
server.start(host = "127.0.0.1", port = 8210, wait = true)
```

Non-loopback plaintext binds require an explicit insecure opt-in, even when authentication is configured. Keep the Maryk server loopback-bound behind TLS termination or use SSH tunneling:

```kotlin
server.start(
    host = "127.0.0.1",
    port = 8210,
    wait = true,
    config = RemoteStoreServerConfig(bearerToken = System.getenv("MARYK_BEARER_TOKEN")),
)
```

If a deliberately plaintext LAN deployment must listen on every interface, the
CLI opt-in belongs inside the `--exec` command:

```text
MARYK_BEARER_TOKEN=replace-with-a-secret maryk --exec "serve rocksdb --dir ./data --host 0.0.0.0 --port 8210 --bearer-token-env MARYK_BEARER_TOKEN --allow-insecure-remote-binding"
```

This authenticates requests but does not encrypt them. Prefer a loopback bind
behind TLS or SSH whenever traffic crosses a machine boundary.

Programmatic deployments can replace the shared token with an identity provider
and authorize each decoded operation by principal, request type, and model:

```kotlin
RemoteStoreServerConfig(
    authenticator = RemoteStoreAuthenticator { authorizationHeader ->
        identityProvider.authenticate(authorizationHeader)
    },
    authorizer = RemoteStoreAuthorizer { request ->
        acl.allows(
            principal = request.principal,
            operation = request.operation,
            requestType = request.requestType,
            modelName = request.modelName,
        )
    },
)
```

Denied add/change/delete requests return an `AuthFail` status per input object.
Denied reads and unsupported update shapes return HTTP 403. Authentication failures
return HTTP 401.

FoundationDB:

```text
maryk --exec "serve foundationdb --dir maryk/app/store --cluster /path/to/fdb.cluster --port 8210"
```

Config file (simple key/value or YAML-style):

```text
# serve.conf
store: rocksdb
dir: ./data
host: 127.0.0.1
port: 8210
bearer-token: replace-with-a-secret
```

```text
maryk --exec "serve --config ./serve.conf"
```

Accepted config keys:
- `store` or `type`: `rocksdb` | `foundationdb`
- `dir` or `directory`: store path
- `cluster` or `clusterFile`: FoundationDB cluster file
- `host`: bind host (default `127.0.0.1`)
- `port`: bind port (default `8210`)
- `bearer-token`: optional bearer credential required by every endpoint
- `allow-insecure-remote-binding`: explicit unsafe opt-in for any non-loopback plaintext bind, including bearer or custom authentication

## Connect as a client

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

Use it like any other store:

```kotlin
val add = remote.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha" }))
val get = remote.execute(SimpleMarykModel.get(add.statuses.first().key))
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
        SimpleMarykModel.add(firstValues),
        SimpleMarykModel.add(secondValues),
    )
)
```

`Requests.create(context = ...)` also supports Collect & Inject batches containing
unresolved `Inject` values. Responses preserve request order. Execution is fail-fast
and is not transactionally atomic.

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
- Uses `ssh -N -L localPort:remoteHost:remotePort` with `ExitOnForwardFailure=yes`.
- An SSH tunnel can safely connect to a loopback-bound server without bearer authentication.

## HTTP protocol overview

All payloads are Maryk ProtoBuf bytes.
Request requirements:
- `Authorization: Bearer <token>` on every endpoint when server authentication is configured.
- `Content-Type: application/x-maryk-protobuf` on all `POST` endpoints.
- Empty request bodies are rejected.
- Request body max size is 16 MiB.
- Each response frame is limited to 16 MiB; a batched execute response is limited
  to 64 MiB total.

- `GET /v1/info` → `RemoteStoreInfo` (definitions + model id map + capabilities)
- `GET /v1/snapshot-version` → authoritative 8-byte point-in-time read boundary
- `POST /v1/execute` → `Requests` in. Legacy single-request clients receive one
  raw ProtoBuf response; clients requesting `X-Maryk-Execute-Protocol: 2`
  receive length-prefixed response frames and may send batches.
- `POST /v1/flow` → `Requests` (single fetch) in, stream of length-prefixed `UpdatesResponse`
- `POST /v1/process-update` → `UpdateResponse` in, `ProcessResponse` out
- `POST /v1/admin/migrations` → versioned migration status/control payloads

Point-in-time export and backup require `GET /v1/snapshot-version`. Upgrade the
Remote client and server together before relying on that guarantee: a newer
client intentionally fails closed when an older server lacks the endpoint rather
than exporting pages from different points in time.

Remote execute protocol changes require a matched client and server deployment.
Upgrade both together before using `X-Maryk-Execute-Protocol: 2` batches or
relying on their framed response contract. Mixed protocol generations are not a
supported batch compatibility target; keep legacy single-request framing only as
a transition aid.

Streaming format:
- Each message is `length (4 bytes, big-endian)` + `ProtoBuf payload`.
- Legacy clients reject zero/negative lengths, truncated frames, trailing bytes,
  and frames larger than 16 MiB.
- Clients requesting `X-Maryk-Flow-Protocol: 2` accept zero-length heartbeat frames.
  The payload frame format remains unchanged.

## When to use

- Serve a local RocksDB/FoundationDB store for remote tooling.
- Connect the App or a programmatic client to a server-side store over HTTP or SSH.
- Build future thin gateways without rewriting store logic.
