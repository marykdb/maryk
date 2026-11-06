# Serving a remote store

See the [Remote Store overview](../README.md) for a first connection.

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

Change requests also require permission for their contents: soft deletion requires
`Delete`, creation requires `Add`, and restoring a soft-deleted object requires
`Change`. These checks also apply inside collected requests and execute batches.

State backup requires the `StateBackup` operation for every exported model.
State import requires `StateRestore` and the existing `ProcessUpdate` permissions
for the batch's add/change/delete contents. Update allowlists explicitly when
enabling these endpoints. The advertised capability is backend support, not an
authorization grant. See [state transport](protocol.md#state-archive-transport).

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
bearer-token-file: ./maryk-token
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
- `bearer-token-env`: name of an environment variable containing the credential
- `bearer-token-file`: path to a UTF-8 credential file
- `bearer-token-stdin`: read the credential from stdin (use a blank value in config)
- `bearer-token`: literal credential retained for compatibility
- `allow-insecure-remote-binding`: explicit unsafe opt-in for any non-loopback plaintext bind, including bearer or custom authentication

Choose at most one token source. Prefer environment, file, or stdin to a literal
command-line token; see the [CLI reference](../../../cli/docs/commands.md#serve).
