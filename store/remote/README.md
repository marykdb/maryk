# Remote Store (HTTP/SSH)

Maryk Remote Store exposes a local Maryk store over HTTP and provides a `RemoteDataStore` client that implements `IsDataStore`.
It lets the CLI/app connect to a store running elsewhere and still use the same request API.

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
server.start(host = "0.0.0.0", port = 8210, wait = true)
```

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

## Connect as a client

```kotlin
val remote = RemoteDataStore.connect(
    RemoteStoreConfig(baseUrl = "http://127.0.0.1:8210")
)
```

Notes:
- `RemoteDataStore.connect` is `suspend`; call it from a coroutine.
- Only plain HTTP is supported; use SSH tunneling for encryption.
- `baseUrl` must not contain query params, fragments, user info, or leading/trailing whitespace.

Use it like any other store:

```kotlin
val add = remote.execute(SimpleMarykModel.add(SimpleMarykModel.create { value with "haha" }))
val get = remote.execute(SimpleMarykModel.get(add.statuses.first().key))
```

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

## HTTP protocol overview

All payloads are Maryk ProtoBuf bytes.
Request requirements:
- `Content-Type: application/x-maryk-protobuf` on all `POST` endpoints.
- Empty request bodies are rejected.
- Request body max size is 16 MiB.

- `GET /v1/info` → `RemoteStoreInfo` (definitions + model id map + capabilities)
- `POST /v1/execute` → `Requests` in, length-prefixed response(s) out
- `POST /v1/flow` → `Requests` (single fetch) in, stream of length-prefixed `UpdatesResponse`
- `POST /v1/process-update` → `UpdateResponse` in, `ProcessResponse` out

Streaming format:
- Each message is `length (4 bytes, big-endian)` + `ProtoBuf payload`.
- Client rejects zero/negative lengths, truncated frames, trailing bytes, and frames larger than 16 MiB.

## When to use

- Serve a local RocksDB/FoundationDB store for remote tooling.
- Connect the app/CLI to a server-side store over HTTP or SSH.
- Build future thin gateways without rewriting store logic.
